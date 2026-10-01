package com.myplan.app.api;

public interface RemoteConfigRepository {
    ApiResult<RemoteModels.RemoteConfigSnapshot> fetch();
}
