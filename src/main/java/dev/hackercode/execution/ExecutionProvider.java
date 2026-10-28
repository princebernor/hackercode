package dev.hackercode.execution;

/**
 * An isolated code-execution backend. Implementations must never run submitted code inside the application JVM
 * or as an unsandboxed host process.
 */
public interface ExecutionProvider {

    enum Status { OK, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT, MEMORY_LIMIT, OUTPUT_LIMIT, PROVIDER_ERROR }

    record Limits(int timeoutSeconds, int memoryMb, int maxOutputBytes) {}

    record Result(Status status, String stdout, String stderr, String compileOutput, Integer exitCode, Long timeMs, Long memoryKb,
                  String message) {
        public static Result providerError(String message) {
            return new Result(Status.PROVIDER_ERROR, null, null, null, null, null, null, message);
        }
    }

    String name();

    boolean configured();

    /** What an operator must set to make this provider work (shown when it is not configured). */
    String setupHint();

    Result execute(Language language, String code, String stdin, Limits limits);
}
