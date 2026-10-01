package com.myplan.app.api;

/** DTOs for future server payloads — not bound to UI. */
public final class RemoteModels {
    private RemoteModels() {}

    public static final class RemoteUser {
        public String userId;
        public String email;
        public String name;
        public String accountStatus;
        public long createdAt;
        public long lastLoginAt;
    }

    public static final class RemoteEntitlement {
        public String entitlementId;
        public String userId;
        public String type;
        public String status;
        public String source; // purchase | server | local — never auto-set from client fake
        public long startedAt;
        public long expiresAt;
        public int serverVersion;
    }

    public static final class RemoteFeatureFlag {
        public String flagKey;
        public boolean enabled;
        public String minimumAppVersion;
        public boolean premiumRequired;
        public boolean developerOnly;
        public long updatedAt;
    }

    public static final class RemoteConfigSnapshot {
        public long updatedAt;
        public String version;
        public String payloadJson;
    }

    public static final class AccountLinkState {
        public String localUserId;
        public String remoteUserId;
        public String linkedState; // unlinked | pending | linked | conflict
        public long lastSyncMs;
        public String conflictState;
    }

    public static final class ApiError {
        public String code;
        public String message;
        public int httpStatus;
    }
}
