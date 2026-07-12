package com.quizmaster.attempt.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helpers for pulling values out of an {@link HttpServletRequest} when
 * the app sits behind one or more reverse proxies (load balancer, CDN,
 * ingress controller).
 */
public final class HttpRequestUtils {

    private HttpRequestUtils() {}

    /**
     * Best-effort client IP. Checks the conventional proxy headers in
     * preference order; falls back to {@code request.getRemoteAddr()}.
     *
     * X-Forwarded-For can be a comma-separated chain
     * ("client, proxy1, proxy2") so we take the left-most value, which
     * is what most proxies treat as the originating client.
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String header = firstNonBlank(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getHeader("CF-Connecting-IP"),
                request.getHeader("True-Client-IP"));
        if (header == null) return request.getRemoteAddr();
        int comma = header.indexOf(',');
        return comma >= 0 ? header.substring(0, comma).trim() : header.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
