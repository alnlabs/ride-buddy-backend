package com.alnlabs.ridebuddy.common;

import java.util.UUID;

public final class AuthUser {
    private AuthUser() {}

    public static UUID requireUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw ApiException.unauthorized("Not authenticated");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}
