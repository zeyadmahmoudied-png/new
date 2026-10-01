package com.myplan.app.api;

import android.content.Context;

/** Facade for resolving API repositories. Offline-first: never blocks local app. */
public final class ApiLayer {
    private ApiLayer() {}

    public static ApiClient client(Context c) {
        return new ApiClient(c);
    }

    public static RemoteAuthRepository auth(Context c) {
        return new NotConnectedRemoteAuthRepository(c);
    }

    public static RemoteEntitlementRepository entitlements(Context c) {
        return new NotConnectedRemoteEntitlementRepository(c);
    }

    public static RemoteConfigRepository remoteConfig(Context c) {
        if (com.myplan.app.supabase.SupabaseConfig.isConfigured(c)) {
            return new com.myplan.app.supabase.SupabaseRemoteConfigRepository(c);
        }
        return new NotConnectedRemoteConfigRepository(c);
    }

    public static String backendStatus(Context c) {
        if (!ApiConfig.isRemoteEnabled(c)) return "DISABLED";
        if (!ApiConfig.isConfigured(c)) return "NOT_CONFIGURED";
        return "CONFIGURED";
    }

    public static String connectionLabel(Context c) {
        if (!ApiConfig.isConfigured(c)) return "Backend Not Connected";
        long last = ApiConfig.getLastSuccessMs(c);
        if (last > 0) return "Configured · last success " + last;
        String err = ApiConfig.getLastError(c);
        if (err != null && !err.isEmpty()) return "Configured · last error recorded";
        return "Configured · not yet proven";
    }
}
