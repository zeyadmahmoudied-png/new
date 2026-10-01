package com.myplan.app.api;

import android.content.Context;

/** Returns NOT_CONFIGURED for all remote auth ops until a real backend is wired. */
public final class NotConnectedRemoteAuthRepository implements RemoteAuthRepository {
    private final Context c;
    public NotConnectedRemoteAuthRepository(Context c) { this.c = c.getApplicationContext(); }

    private <T> ApiResult<T> nc() {
        ApiLog.append(c, "AUTH", "—", 0, 0, "not_configured", null);
        return ApiResult.notConfigured();
    }

    @Override public ApiResult<RemoteModels.RemoteUser> register(String email, String password, String displayName) { return nc(); }
    @Override public ApiResult<RemoteModels.RemoteUser> login(String email, String password) { return nc(); }
    @Override public ApiResult<Void> logout() { return nc(); }
    @Override public ApiResult<RemoteModels.RemoteUser> currentUser() { return nc(); }
    @Override public ApiResult<Void> refreshSession() { return nc(); }
    @Override public ApiResult<Void> forgotPassword(String email) { return nc(); }
    @Override public ApiResult<Void> resetPassword(String token, String newPassword) { return nc(); }
    @Override public ApiResult<Void> sendEmailVerification() { return nc(); }
}
