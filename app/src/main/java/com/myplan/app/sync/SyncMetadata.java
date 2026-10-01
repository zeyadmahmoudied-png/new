package com.myplan.app.sync;

/** Per-item sync metadata — does not replace local primary keys. */
public final class SyncMetadata {
    public String stableId;
    public String userId;
    public long createdAtMs;
    public long updatedAtMs;
    public long deletedAtMs; // 0 = not deleted (tombstone when set)
    public long version;
    public long lastSyncedVersion;
    public String syncState; // local_only | pending_sync | synced | conflict

    public static SyncMetadata localOnly(String stableId, String userId) {
        SyncMetadata m = new SyncMetadata();
        m.stableId = stableId == null ? "" : stableId;
        m.userId = userId == null ? "" : userId;
        m.createdAtMs = System.currentTimeMillis();
        m.updatedAtMs = m.createdAtMs;
        m.deletedAtMs = 0;
        m.version = 1;
        m.lastSyncedVersion = 0;
        m.syncState = "local_only";
        return m;
    }
}
