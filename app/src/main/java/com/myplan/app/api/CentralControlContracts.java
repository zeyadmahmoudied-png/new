package com.myplan.app.api;

/**
 * Server-side Central Developer Control contracts only.
 * No admin secrets in the APK. Real control is Backend-only.
 */
public final class CentralControlContracts {
    private CentralControlContracts() {}

    public static final class AuditEntry {
        public String action;
        public String actorId;
        public String targetId;
        public long timestamp;
    }

    public interface UserAdminApi {
        ApiResult<RemoteModels.RemoteUser> searchByEmail(String email);
        ApiResult<RemoteModels.RemoteUser> getByUserId(String userId);
    }

    public interface PremiumAdminApi {
        ApiResult<RemoteModels.RemoteEntitlement> grant(String userId, String type, long expiresAt);
        ApiResult<Void> revoke(String userId, String entitlementId);
        ApiResult<RemoteModels.RemoteEntitlement> inspect(String userId);
    }

    public interface FlagAdminApi {
        ApiResult<RemoteModels.RemoteFeatureFlag> setFlag(String key, boolean enabled);
        ApiResult<RemoteModels.RemoteFeatureFlag> getFlag(String key);
    }

    public interface AppVersionAdminApi {
        ApiResult<String> minimumSupportedVersion();
        ApiResult<Boolean> isForceUpdateRequired(String clientVersion);
    }

    /** Always NOT_CONFIGURED until server admin APIs exist. */
    public static final class NotConnectedAdminFacades {
        private NotConnectedAdminFacades() {}
        public static <T> ApiResult<T> nc() { return ApiResult.notConfigured(); }
    }
}
