package dev.hackercode.execution;

final class OutputLimits {
    private OutputLimits() {}

    static String truncate(String s, int maxChars) {
        if (s == null || s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n… [output truncated at " + maxChars + " characters]";
    }
}
