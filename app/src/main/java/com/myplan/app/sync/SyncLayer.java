package com.myplan.app.sync;

import android.content.Context;

import com.myplan.app.api.ApiResult;

/**
 * Facade for sync. Uses existing ApiClient contracts; no separate HTTP stack.
 * No real upload while backend not configured.
 */
public final class SyncLayer {
    private SyncLayer() {}

    public static RemoteSyncRepository remoteRepository(Context c) {
        return new NotConfiguredRemoteSyncRepository();
    }

    public static SyncEngine engine(Context c) {
        return new SyncEngine(c);
    }

    public static SyncQueue queue(Context c) {
        return new SyncQueue(c);
    }

    /** Future endpoints via ApiClient — always NOT_CONFIGURED until backend exists. */
    public static ApiResult<String> contractPull(Context c) {
        return ApiResult.notConfigured();
    }

    public static ApiResult<String> contractPush(Context c) {
        return ApiResult.notConfigured();
    }

    public static ApiResult<String> contractAck(Context c) {
        return ApiResult.notConfigured();
    }

    public static ApiResult<String> contractDevices(Context c) {
        return ApiResult.notConfigured();
    }

    /** Gate for future Premium cloud — does not block local offline use. */
    public static boolean isCloudSyncAllowed(Context c) {
        // Future: FeatureFlags / Premium. Local app always works regardless.
        return false;
    }
}
