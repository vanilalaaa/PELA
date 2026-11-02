package com.pela.identity.application.port.out;

import java.util.UUID;

/** Phiên do Supabase Auth cấp. */
public record SupabaseSession(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String tokenType,
        UUID userId,
        boolean emailConfirmed) {

    @Override
    public String toString() {
        // Không để lộ token khi bị log
        return "SupabaseSession[userId=" + userId + ", expiresIn=" + expiresInSeconds + "]";
    }
}
