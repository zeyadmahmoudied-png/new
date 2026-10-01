package com.myplan.app.api;

/**
 * Token storage contract. Implementations must never log tokens.
 * Production implementation: SecureTokenStore (Android Keystore).
 */
public interface TokenStore {
    enum TokenStatus {
        VALID,
        EXPIRED,
        REFRESH_REQUIRED,
        REVOKED,
        MISSING,
        INVALID
    }

    String getAccessToken();
    String getRefreshToken();
    long getAccessExpiryMs();
    void saveTokens(String access, String refresh, long accessExpiryMs);
    void clear();
    TokenStatus getAccessStatus();
    void markRevoked();
}
