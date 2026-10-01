package com.myplan.app.sync;

/** Aggregate sync status for Developer Center. */
public final class SyncState {
    public String status; // NOT_CONFIGURED | NOT_CONNECTED | IDLE | SYNCING | ...
    public long lastSyncAttemptMs;
    public long lastSuccessMs;
    public int pendingOperations;
    public String lastError;
    public String userId;
    public String installationId;
    public boolean multiDeviceEnabled;

    public static SyncState notConfigured(String userId, String installationId) {
        SyncState s = new SyncState();
        s.status = "NOT_CONFIGURED";
        s.lastSyncAttemptMs = 0;
        s.lastSuccessMs = 0;
        s.pendingOperations = 0;
        s.lastError = "";
        s.userId = userId == null ? "" : userId;
        s.installationId = installationId == null ? "" : installationId;
        s.multiDeviceEnabled = false;
        return s;
    }
}
