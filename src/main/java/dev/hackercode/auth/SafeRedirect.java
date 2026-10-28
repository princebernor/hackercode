package dev.hackercode.auth;

/** Prevents open redirects: only same-origin, absolute-path targets are accepted. */
public final class SafeRedirect {
    private SafeRedirect() {}

    public static String path(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 300) return fallback;
        if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.startsWith("/\\")) return fallback;
        if (candidate.contains("\\") || candidate.contains("\r") || candidate.contains("\n") || candidate.contains("://")) return fallback;
        return candidate;
    }
}
