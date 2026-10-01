package com.myplan.app.sync;

import android.content.Context;

import com.myplan.app.api.ApiConfig;
import com.myplan.app.api.ApiLayer;

import java.util.Collections;
import java.util.List;

/**
 * Orchestrates local queue + remote repository.
 * Never uploads while backend is not configured.
 * Never deletes local user data because cloud is unavailable.
 */
public final class SyncEngine {
    private final Context app;
    private final RemoteSyncRepository remote;
    private final SyncQueue queue;

    public SyncEngine(Context c) {
        this(c, SyncLayer.remoteRepository(c), new SyncQueue(c));
    }

    public SyncEngine(Context c, RemoteSyncRepository remote, SyncQueue queue) {
        this.app = c.getApplicationContext();
        this.remote = remote == null ? new NotConfiguredRemoteSyncRepository() : remote;
        this.queue = queue == null ? new SyncQueue(c) : queue;
    }

    public SyncState currentState() {
        String userId = safeUserId();
        String inst = com.myplan.app.AppInfrastructure.getInstallationId(app);
        SyncState s = SyncState.notConfigured(userId, inst);
        s.pendingOperations = queue.pendingCount();
        if (!remote.isConfigured() || !ApiConfig.isConfigured(app)) {
            s.status = "NOT_CONFIGURED";
            return s;
        }
        s.status = "NOT_CONNECTED";
        return s;
    }

    /** Record a local change as pending — does not upload. */
    public void noteLocalChange(SyncEntityType type, String stableId, String operation) {
        SyncItem item = new SyncItem();
        item.operation = operation == null ? "UPDATE" : operation;
        item.entityType = type;
        item.stableId = stableId == null ? "" : stableId;
        item.enqueuedAtMs = System.currentTimeMillis();
        item.metadata = SyncMetadata.localOnly(stableId, safeUserId());
        queue.enqueue(item);
    }

    public SyncResult<Void> runSync() {
        if (!remote.isConfigured() || !ApiConfig.isConfigured(app)) {
            SyncLog.append(app, "runSync", "NOT_CONFIGURED", "no backend");
            return SyncResult.notConfigured();
        }
        // Auth gate — no anonymous sync
        String userId = safeUserId();
        if (userId == null || userId.isEmpty()) {
            SyncLog.append(app, "runSync", "AUTH_REQUIRED", "no user");
            return SyncResult.authRequired();
        }
        // Future: push queue then pull. Currently unreachable without backend.
        SyncResult<Void> push = remote.pushChanges(Collections.emptyList());
        SyncLog.append(app, "runSync", push.kind.name(), push.message);
        return push;
    }

    public SyncResult<List<DeviceInfo>> listDevices() {
        if (!remote.isConfigured()) return SyncResult.notConfigured();
        return remote.listDevices();
    }

    public SyncResult<Void> firstSyncPreview() {
        // Contract only — no real first sync
        return SyncResult.notConfigured();
    }

    private String safeUserId() {
        try {
            // Prefer local account if present; never invent
            return com.myplan.app.AppInfrastructure.getUserId(app);
        } catch (Throwable t) {
            return "";
        }
    }
}
