package dev.hackercode.common;

import jakarta.servlet.http.HttpServletRequest;

/** Small helpers for request metadata. Only coarse, non-identifying hints are ever stored. */
public final class Requests {
    private Requests() {}

    /** The client address with the last IPv4 octet (or the IPv6 tail) masked, e.g. "203.0.113.x". */
    public static String ipHint(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip == null) return null;
        if (ip.contains(".")) {
            return ip.substring(0, ip.lastIndexOf('.')) + ".x";
        }
        int second = ip.indexOf(':', ip.indexOf(':') + 1);
        return (second > 0 ? ip.substring(0, second) : ip) + ":x";
    }

    public static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 300 ? ua.substring(0, 300) : ua;
    }
}
