package com.myplan.app.sync;

/** Queue / change unit. Payload is opaque local JSON — never uploaded while NOT_CONFIGURED. */
public final class SyncItem {
    public String operation; // CREATE | UPDATE | DELETE
    public SyncEntityType entityType;
    public String stableId;
    public String payloadJson; // local representation only
    public long enqueuedAtMs;
    public SyncMetadata metadata;
}
