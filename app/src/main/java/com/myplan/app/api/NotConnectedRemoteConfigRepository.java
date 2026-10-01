package com.myplan.app.api;

import android.content.Context;

public final class NotConnectedRemoteConfigRepository implements RemoteConfigRepository {
    private final Context c;
    public NotConnectedRemoteConfigRepository(Context c) { this.c = c.getApplicationContext(); }
    @Override
    public ApiResult<RemoteModels.RemoteConfigSnapshot> fetch() {
        ApiLog.append(c, "GET", "remote-config", 0, 0, "not_configured", null);
        return ApiResult.notConfigured();
    }
}
