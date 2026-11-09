package io.casehub.connectors.web.model;

import java.time.Instant;

public record BrowserCookie(String name, String value, String domain, String path,
                            boolean secure, boolean httpOnly, String sameSite,
                            Instant expires) {

    public BrowserCookie {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Cookie name is required");
        if (value == null) throw new IllegalArgumentException("Cookie value is required");
        if (domain == null || domain.isBlank()) throw new IllegalArgumentException("Cookie domain is required");
        if (path == null) path = "/";
        if (sameSite == null) sameSite = "Lax";
    }

    public static BrowserCookie of(String name, String value, String domain) {
        return new BrowserCookie(name, value, domain, "/", false, false, "Lax", null);
    }
}
