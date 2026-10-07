package com.myplan.app.supabase;

import android.content.Context;

import com.myplan.app.AppInfrastructure;
import com.myplan.app.remote.RemoteControlService;

/**
 * Compatibility bridge for the existing app lifecycle.
 * The implementation is now the isolated Remote Control Layer.
 *
 * Planner and its scheduling data are never read or written here.
 */
public final class RemoteSyncCoordinator {
    private RemoteSyncCoordinator() {}

    public static volatile String lastStatus = "idle";

    public static void syncAsync(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                RemoteControlService.Result r = RemoteControlService.sync(app);
                lastStatus = !r.configured ? "not_configured"
                        : (r.networkOk ? "ok" : "offline_cache");
            } catch (Throwable t) {
                lastStatus = "error";
            }
        }, "remote-sync-v2").start();
    }

    public static String doSync(Context context) {
        try {
            RemoteControlService.Result r = RemoteControlService.sync(context);
            lastStatus = !r.configured ? "not_configured"
                    : (r.networkOk ? "ok" : "offline_cache");
        } catch (Throwable t) {
            lastStatus = "error";
        }
        return lastStatus;
    }

    /**
     * Legacy hook retained only for binary/source compatibility.
     * Support messages are now handled by RemoteMessagingService.
     */
    public static void flushPendingContactMessages(Context context, SupabaseRepository ignored) {
        // Intentionally empty: the old remote_messages/contact contract is retired.
    }
}
