package com.myplan.app.api;

/** Remote authentication contract. Local AccountAuth remains source of truth until Backend exists. */
public interface RemoteAuthRepository {
    ApiResult<RemoteModels.RemoteUser> register(String email, String password, String displayName);
    ApiResult<RemoteModels.RemoteUser> login(String email, String password);
    ApiResult<Void> logout();
    ApiResult<RemoteModels.RemoteUser> currentUser();
    ApiResult<Void> refreshSession();
    ApiResult<Void> forgotPassword(String email);
    ApiResult<Void> resetPassword(String token, String newPassword);
    ApiResult<Void> sendEmailVerification();
}
