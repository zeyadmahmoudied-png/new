package com.myplan.app.api;

import android.content.Context;

public final class NotConnectedRemoteEntitlementRepository implements RemoteEntitlementRepository {
    private final Context c;
    public NotConnectedRemoteEntitlementRepository(Context c) { this.c = c.getApplicationContext(); }
    @Override
    public ApiResult<RemoteModels.RemoteEntitlement> fetchForCurrentUser() {
        ApiLog.append(c, "GET", "entitlements/me", 0, 0, "not_configured", null);
        return ApiResult.notConfigured();
    }
}
