package com.myplan.app.sync;

/** Conflict record — no automatic cloud-overwrites-local policy. */
public final class SyncConflict {
    public String itemId;
    public String entityType;
    public long localVersion;
    public long remoteVersion;
    public long localUpdatedAtMs;
    public long remoteUpdatedAtMs;
    public String resolutionState; // unresolved | prefer_local | prefer_remote | merged | deferred
}
