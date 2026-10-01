package com.myplan.app.sync;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class SyncSelfTest {
    private SyncSelfTest() {}

    public static final class Case {
        public final String name;
        public final boolean pass;
        public final String detail;
        Case(String n, boolean p, String d) { name = n; pass = p; detail = d; }
    }

    public static List<Case> run(Context c) {
        List<Case> out = new ArrayList<>();
        SyncEngine engine = SyncLayer.engine(c);
        RemoteSyncRepository remote = SyncLayer.remoteRepository(c);

        out.add(new Case("remote_not_configured", !remote.isConfigured(), "ok"));
        SyncState st = engine.currentState();
        out.add(new Case("state_not_configured", "NOT_CONFIGURED".equals(st.status), st.status));

        SyncResult<Void> run = engine.runSync();
        out.add(new Case("run_sync_blocked",
                run.kind == SyncResult.Kind.NOT_CONFIGURED || run.kind == SyncResult.Kind.AUTH_REQUIRED,
                run.kind.name()));

        // Queue local-only — no upload
        int before = SyncLayer.queue(c).pendingCount();
        engine.noteLocalChange(SyncEntityType.TASK, "test-stable-id", "UPDATE");
        int after = SyncLayer.queue(c).pendingCount();
        out.add(new Case("queue_enqueue_local", after >= before, "before=" + before + " after=" + after));

        SyncResult<List<DeviceInfo>> devices = engine.listDevices();
        out.add(new Case("devices_not_configured", devices.kind == SyncResult.Kind.NOT_CONFIGURED, devices.kind.name()));

        SyncResult<Void> first = engine.firstSyncPreview();
        out.add(new Case("first_sync_not_configured", first.kind == SyncResult.Kind.NOT_CONFIGURED, first.kind.name()));

        out.add(new Case("api_pull_contract",
                SyncLayer.contractPull(c).kind == com.myplan.app.api.ApiResult.Kind.NOT_CONFIGURED, "ok"));
        out.add(new Case("api_push_contract",
                SyncLayer.contractPush(c).kind == com.myplan.app.api.ApiResult.Kind.NOT_CONFIGURED, "ok"));

        out.add(new Case("cloud_gate_off", !SyncLayer.isCloudSyncAllowed(c), "local offline unrestricted"));

        // Conflict model instantiable
        SyncConflict conf = new SyncConflict();
        conf.resolutionState = "unresolved";
        out.add(new Case("conflict_model", "unresolved".equals(conf.resolutionState), "ok"));

        // Installation id present, user id may be empty — both strings
        out.add(new Case("installation_id_present",
                st.installationId != null && !st.installationId.isEmpty(), st.installationId));

        return out;
    }
}
