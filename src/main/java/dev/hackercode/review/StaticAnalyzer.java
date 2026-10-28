package dev.hackercode.review;

import dev.hackercode.execution.Language;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic, rule-based checks that run without any AI provider. They are pattern matches, not program
 * analysis: they can miss problems and can flag code that is actually fine, and the UI labels them that way.
 */
@Component
public class StaticAnalyzer {

    public record Finding(String rule, String category, String severity, int line, String title, String explanation, String suggestion) {}

    public record Result(List<Finding> findings, int maxLoopDepth, String estimatedTime, int lineCount, int longestFunctionLines) {}

    private record Rule(String id, Set<Language> languages, Pattern pattern, String category, String severity, String title,
                        String explanation, String suggestion) {}

    private static final Set<Language> C_FAMILY = EnumSet.of(Language.C, Language.CPP);
    private static final Set<Language> ALL = EnumSet.allOf(Language.class);

    private static final List<Rule> RULES = List.of(
            new Rule("c-gets", C_FAMILY, Pattern.compile("\\bgets\\s*\\("), "security", "critical", "gets() can overflow its buffer",
                    "gets() has no length limit, so long input overwrites memory beyond the buffer.", "Use fgets(buf, sizeof buf, stdin)."),
            new Rule("c-unbounded-copy", C_FAMILY, Pattern.compile("\\b(strcpy|strcat|sprintf)\\s*\\("), "security", "high",
                    "Unbounded string function", "strcpy/strcat/sprintf do not check the destination size and can overflow it.",
                    "Use snprintf, or strncpy/strncat with explicit sizes."),
            new Rule("c-scanf-string", C_FAMILY, Pattern.compile("scanf\\s*\\(\\s*\"[^\"]*%s"), "security", "medium", "scanf(\"%s\") without a width",
                    "Reading a string with %s has no length limit.", "Give a width, e.g. %99s for a 100-byte buffer."),
            new Rule("cpp-endl-loop", EnumSet.of(Language.CPP), Pattern.compile("<<\\s*endl"), "performance", "low", "std::endl flushes the stream",
                    "Every endl forces a flush, which is slow when printing many lines.", "Print '\\n' instead, and flush once at the end."),
            new Rule("cpp-bits", EnumSet.of(Language.CPP), Pattern.compile("#include\\s*<bits/stdc\\+\\+\\.h>"), "best_practices", "info",
                    "Non-standard <bits/stdc++.h>", "This header is GCC-specific and slows compilation. Fine for contests, not for production code.",
                    "Include only the standard headers you use."),
            new Rule("java-string-eq", EnumSet.of(Language.JAVA), Pattern.compile("\"[^\"]*\"\\s*==|==\\s*\"[^\"]*\""), "correctness", "high",
                    "String compared with ==", "== compares object references, not text, so equal strings can compare as different.",
                    "Use a.equals(b) (or Objects.equals)."),
            new Rule("java-empty-catch", EnumSet.of(Language.JAVA, Language.JAVASCRIPT), Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*}"),
                    "correctness", "medium", "Empty catch block", "Errors are silently swallowed, which hides bugs.",
                    "Handle the error, log it, or let it propagate."),
            new Rule("java-scanner-loop", EnumSet.of(Language.JAVA), Pattern.compile("new\\s+Scanner\\s*\\(\\s*System\\.in"), "performance", "info",
                    "Scanner is slow for large input", "java.util.Scanner is convenient but slow for inputs with 10^5+ tokens.",
                    "Use BufferedReader with StringTokenizer for large inputs."),
            new Rule("py-bare-except", EnumSet.of(Language.PYTHON), Pattern.compile("^\\s*except\\s*:"), "correctness", "medium", "Bare except",
                    "A bare except also catches KeyboardInterrupt and SystemExit and hides real errors.", "Catch the specific exception, e.g. except ValueError:"),
            new Rule("py-mutable-default", EnumSet.of(Language.PYTHON), Pattern.compile("def\\s+\\w+\\s*\\([^)]*=\\s*(\\[\\]|\\{\\}|set\\(\\))"),
                    "correctness", "high", "Mutable default argument", "The default list/dict is created once and shared between calls.",
                    "Default to None and create the object inside the function."),
            new Rule("py-list-pop0", EnumSet.of(Language.PYTHON), Pattern.compile("\\.pop\\(\\s*0\\s*\\)|\\.insert\\(\\s*0\\s*,"), "performance", "medium",
                    "O(n) operation at the front of a list", "list.pop(0) and insert(0, x) shift every element.", "Use collections.deque for queue operations."),
            new Rule("eval", EnumSet.of(Language.PYTHON, Language.JAVASCRIPT), Pattern.compile("\\beval\\s*\\("), "security", "critical",
                    "eval() on dynamic input", "eval executes arbitrary code; with untrusted input this is a code-injection vulnerability.",
                    "Parse the input explicitly (e.g. int(), JSON.parse) instead of evaluating it."),
            new Rule("js-var", EnumSet.of(Language.JAVASCRIPT), Pattern.compile("^\\s*var\\s+"), "best_practices", "low", "var declaration",
                    "var is function-scoped and hoisted, which causes subtle bugs.", "Use const by default and let when reassigning."),
            new Rule("js-loose-eq", EnumSet.of(Language.JAVASCRIPT), Pattern.compile("[^=!]==[^=]|!=[^=]"), "correctness", "low", "Loose equality",
                    "== and != apply type coercion (e.g. 0 == '' is true).", "Use === and !==."),
            new Rule("go-ignored-err", EnumSet.of(Language.GO), Pattern.compile(",\\s*_\\s*:?=\\s*\\w+.*\\("), "correctness", "medium", "Ignored error",
                    "The error result of this call is discarded.", "Check the error and handle it."),
            new Rule("todo", ALL, Pattern.compile("(//|#|/\\*)\\s*(TODO|FIXME|HACK)\\b"), "readability", "info", "Unfinished TODO/FIXME",
                    "The code marks unfinished work.", "Resolve it or track it outside the code."));

    private static final Pattern LOOP_BRACE = Pattern.compile("\\b(for|while)\\b\\s*\\(|\\bfor\\b[^;{]*\\{|\\bdo\\s*\\{");
    private static final Pattern PY_LOOP = Pattern.compile("^(\\s*)(for|while)\\b.*:\\s*(#.*)?$");
    private static final Pattern MAGIC = Pattern.compile("(?<![\\w.])(\\d{4,})(?![\\w.])");

    public Result analyze(Language language, String code) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = code.replace("\r\n", "\n").split("\n", -1);
        for (Rule rule : RULES) {
            if (!rule.languages().contains(language)) continue;
            for (int i = 0; i < lines.length; i++) {
                if (rule.pattern().matcher(stripStrings(lines[i], rule)).find()) {
                    findings.add(new Finding(rule.id(), rule.category(), rule.severity(), i + 1, rule.title(), rule.explanation(), rule.suggestion()));
                    break; // one report per rule keeps the list readable
                }
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > 140) {
                findings.add(new Finding("long-line", "readability", "info", i + 1, "Very long line",
                        "Lines over 140 characters are hard to read and review.", "Break the expression up or extract a helper."));
                break;
            }
        }
        int magicLine = firstMagicNumber(lines, language);
        if (magicLine > 0) {
            findings.add(new Finding("magic-number", "readability", "info", magicLine, "Unnamed numeric constant",
                    "A large literal number with no name makes the intent unclear.", "Give it a descriptive constant name (e.g. MOD = 1_000_000_007)."));
        }
        int[] depthAndLine = language == Language.PYTHON ? pythonLoopDepth(lines) : braceLoopDepth(lines);
        int depth = depthAndLine[0];
        if (depth >= 3) {
            findings.add(new Finding("deep-loops", "complexity", "medium", depthAndLine[1], depth + " levels of nested loops",
                    "Deeply nested loops usually mean O(n^" + depth + ") work, which times out on large inputs.",
                    "Look for a hash map, sorting + two pointers, prefix sums or dynamic programming to remove a level."));
        } else if (depth == 2) {
            findings.add(new Finding("nested-loops", "complexity", "low", depthAndLine[1], "Nested loops",
                    "Two nested loops over the input are typically O(n^2).", "Check whether a hash map or two pointers can make it O(n) or O(n log n)."));
        }
        int longest = longestBlock(lines, language);
        if (longest > 60) {
            findings.add(new Finding("long-function", "readability", "low", 0, "Long function (" + longest + " lines)",
                    "Long functions are harder to test and reason about.", "Split it into smaller, named helper functions."));
        }
        String time = switch (depth) {
            case 0 -> "O(1) or O(n) (no loops detected; recursion not analysed)";
            case 1 -> "O(n) (single loop level)";
            default -> "O(n^" + depth + ") (" + depth + " nested loop levels)";
        };
        return new Result(findings, depth, time, lines.length, longest);
    }

    private static String stripStrings(String line, Rule rule) {
        // Rules that inspect string literals themselves must see them.
        if (rule.id().startsWith("java-string") || rule.id().equals("c-scanf-string") || rule.id().equals("todo")) return line;
        return line.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"").replaceAll("'(\\\\.|[^'\\\\])*'", "''");
    }

    private static int firstMagicNumber(String[] lines, Language language) {
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.startsWith("#") && language != Language.PYTHON || l.startsWith("//") || l.startsWith("const ") || l.contains("final ")
                    || l.matches("^[A-Z_][A-Z0-9_]*\\s*=.*") || l.startsWith("#define")) continue;
            Matcher m = MAGIC.matcher(l.replaceAll("\"[^\"]*\"", ""));
            if (m.find()) return i + 1;
        }
        return 0;
    }

    /** Max nesting of for/while loops in brace languages; returns {depth, line of deepest loop}. */
    static int[] braceLoopDepth(String[] lines) {
        Deque<Boolean> stack = new ArrayDeque<>();
        int loops = 0;
        int max = 0;
        int maxLine = 0;
        boolean pendingLoop = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("//.*", "").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
            boolean loopHere = LOOP_BRACE.matcher(line).find();
            if (loopHere) pendingLoop = true;
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    stack.push(pendingLoop);
                    if (pendingLoop) {
                        loops++;
                        if (loops > max) { max = loops; maxLine = i + 1; }
                    }
                    pendingLoop = false;
                } else if (c == '}' && !stack.isEmpty()) {
                    if (stack.pop()) loops--;
                }
            }
            if (pendingLoop && line.trim().endsWith(";")) {
                // brace-less single-statement loop body
                if (loops + 1 > max) { max = loops + 1; maxLine = i + 1; }
                pendingLoop = false;
            }
        }
        return new int[] {max, maxLine};
    }

    static int[] pythonLoopDepth(String[] lines) {
        Deque<Integer> loopIndents = new ArrayDeque<>();
        int max = 0;
        int maxLine = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            int indent = line.length() - line.stripLeading().length();
            while (!loopIndents.isEmpty() && indent <= loopIndents.peek()) loopIndents.pop();
            Matcher m = PY_LOOP.matcher(line);
            if (m.find()) {
                loopIndents.push(indent);
                if (loopIndents.size() > max) { max = loopIndents.size(); maxLine = i + 1; }
            }
        }
        return new int[] {max, maxLine};
    }

    private static int longestBlock(String[] lines, Language language) {
        Pattern start = switch (language) {
            case PYTHON -> Pattern.compile("^\\s*def\\s+\\w+");
            case GO -> Pattern.compile("^\\s*func\\s+");
            case JAVASCRIPT -> Pattern.compile("function\\s+\\w+\\s*\\(|=>\\s*\\{");
            default -> Pattern.compile("^\\s*[\\w<>\\[\\],*&\\s]+\\s+\\w+\\s*\\([^;]*\\)\\s*(const)?\\s*\\{?\\s*$");
        };
        int longest = 0;
        int current = -1;
        for (String line : lines) {
            if (start.matcher(line).find()) {
                longest = Math.max(longest, current);
                current = 0;
            } else if (current >= 0 && !line.isBlank()) {
                current++;
            }
        }
        return Math.max(longest, current);
    }
}
