package com.myplan.app.api;

public interface RemoteEntitlementRepository {
    ApiResult<RemoteModels.RemoteEntitlement> fetchForCurrentUser();
}
