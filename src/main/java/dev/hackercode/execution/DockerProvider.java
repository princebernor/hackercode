package dev.hackercode.execution;

import dev.hackercode.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs each submission in a throwaway Docker container: no network, read-only root filesystem, a small tmpfs,
 * memory/CPU/PID limits, all capabilities dropped, an unprivileged user, and a hard wall-clock kill.
 * The application only launches the container; user code never executes in the JVM or directly on the host.
 */
@Component
public class DockerProvider implements ExecutionProvider {
    private static final Logger log = LoggerFactory.getLogger(DockerProvider.class);
    private static final int COMPILE_FAILED = 97;
    private static final int COMPILE_BUDGET_SECONDS = 25;

    private final AppProperties.Execution cfg;

    public DockerProvider(AppProperties props) {
        this.cfg = props.execution();
    }

    @Override public String name() { return "docker"; }

    @Override
    public boolean configured() {
        return "docker".equalsIgnoreCase(cfg.provider());
    }

    @Override
    public String setupHint() {
        return "Install Docker, pull the language images (see README), and set EXECUTION_PROVIDER=docker.";
    }

    /** Builds the {@code docker run} argument list. Package-private for tests. */
    List<String> command(Language language, Path workDir, String containerName, Limits limits) {
        String run = "timeout -s KILL " + limits.timeoutSeconds() + " " + language.runCommand();
        String script = language.compiled()
                ? "(" + language.compileCommand() + ") > /tmp/compile.log 2>&1 || { cat /tmp/compile.log >&2; exit " + COMPILE_FAILED + "; }; " + run
                : run;
        List<String> cmd = new ArrayList<>(List.of(
                cfg.dockerCommand(), "run", "--rm", "-i",
                "--name", containerName,
                "--network", "none",
                "--memory", limits.memoryMb() + "m",
                "--memory-swap", limits.memoryMb() + "m",
                "--cpus", "1",
                "--pids-limit", "128",
                "--read-only",
                "--tmpfs", "/tmp:rw,exec,nosuid,size=128m",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--user", "65534:65534",
                "-e", "HOME=/tmp",
                "-e", "GOCACHE=/tmp/.gocache",
                "-e", "GOTMPDIR=/tmp",
                "-v", workDir.toAbsolutePath() + ":/code:ro",
                language.dockerImage(),
                "sh", "-c", script));
        return cmd;
    }

    @Override
    public Result execute(Language language, String code, String stdin, Limits limits) {
        Path dir = null;
        String name = "hc-run-" + UUID.randomUUID();
        Process process = null;
        try {
            dir = Files.createTempDirectory("hc-run-");
            Files.writeString(dir.resolve(language.fileName()), code, StandardCharsets.UTF_8);
            dir.toFile().setReadable(true, false);
            dir.resolve(language.fileName()).toFile().setReadable(true, false);

            long started = System.nanoTime();
            process = new ProcessBuilder(command(language, dir, name, limits)).start();
            CappedReader out = new CappedReader(process.getInputStream(), limits.maxOutputBytes());
            CappedReader err = new CappedReader(process.getErrorStream(), limits.maxOutputBytes());
            out.start();
            err.start();
            try (OutputStream in = process.getOutputStream()) {
                if (stdin != null) in.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the program may exit before reading its input
            }
            int budget = limits.timeoutSeconds() + (language.compiled() ? COMPILE_BUDGET_SECONDS : 0) + 15;
            boolean finished = process.waitFor(budget, TimeUnit.SECONDS);
            if (!finished || out.exceeded() || err.exceeded()) {
                kill(name);
                process.destroyForcibly();
            }
            out.join(2000);
            err.join(2000);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (!finished) return new Result(Status.TIME_LIMIT, out.text(), err.text(), null, null, elapsedMs, null, "Time limit exceeded");
            if (out.exceeded() || err.exceeded()) {
                return new Result(Status.OUTPUT_LIMIT, out.text(), err.text(), null, null, elapsedMs, null, "Output limit exceeded");
            }
            int exit = process.exitValue();
            if (exit == COMPILE_FAILED) return new Result(Status.COMPILE_ERROR, null, null, err.text(), exit, null, null, "Compilation failed");
            if (exit == 125 || exit == 126 || exit == 127 && err.text().contains("docker")) {
                log.warn("Docker could not start the sandbox (exit {}): {}", exit, err.text());
                return Result.providerError("The sandbox could not start. Is the " + language.dockerImage() + " image pulled?");
            }
            if (exit == 137) {
                boolean timeout = elapsedMs >= limits.timeoutSeconds() * 1000L;
                return new Result(timeout ? Status.TIME_LIMIT : Status.MEMORY_LIMIT, out.text(), err.text(), null, exit, elapsedMs, null,
                        timeout ? "Time limit exceeded" : "Killed (likely memory limit)");
            }
            Status status = exit == 0 ? Status.OK : Status.RUNTIME_ERROR;
            return new Result(status, out.text(), err.text(), null, exit, elapsedMs, null, exit == 0 ? null : "Exited with code " + exit);
        } catch (IOException e) {
            log.warn("Docker execution failed to start: {}", e.getMessage());
            return Result.providerError("Docker is not available on the server.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(name);
            return Result.providerError("Execution was interrupted.");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            deleteQuietly(dir);
        }
    }

    private void kill(String name) {
        try {
            new ProcessBuilder(cfg.dockerCommand(), "kill", name).start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("docker kill {} failed: {}", name, e.getMessage());
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp directory cleanup is best effort
        }
    }

    /** Drains a stream on its own thread, keeping at most {@code max} bytes. */
    private static final class CappedReader extends Thread {
        private final InputStream in;
        private final int max;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean exceeded;

        CappedReader(InputStream in, int max) {
            this.in = in;
            this.max = max;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    int room = max - buf.size();
                    if (room > 0) buf.write(chunk, 0, Math.min(room, n));
                    if (n > room) exceeded = true;
                }
            } catch (IOException ignored) {
                // stream closed when the process is killed
            }
        }

        boolean exceeded() { return exceeded; }

        String text() { return buf.toString(StandardCharsets.UTF_8); }
    }
}
