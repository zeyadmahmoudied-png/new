package com.myplan.app.supabase;

import android.content.Context;

import com.myplan.app.api.ApiResult;
import com.myplan.app.api.RemoteConfigRepository;
import com.myplan.app.api.RemoteModels;

import org.json.JSONObject;

/** يربط RemoteConfigRepository الموجود بـ Supabase. */
public final class SupabaseRemoteConfigRepository implements RemoteConfigRepository {
    private final Context c;
    public SupabaseRemoteConfigRepository(Context c) { this.c = c.getApplicationContext(); }

    @Override
    public ApiResult<RemoteModels.RemoteConfigSnapshot> fetch() {
        SupabaseRepository repo = new SupabaseRepository(c);
        ApiResult<JSONObject> r = repo.fetchAndCacheControlState();
        if (!r.isSuccess() || r.data == null) {
            if (r.kind == ApiResult.Kind.NOT_CONFIGURED) return ApiResult.notConfigured();
            // offline: أعد الكاش إن وُجد
            JSONObject cached = RemoteControlCache.loadSnapshot(c);
            if (cached != null) {
                RemoteModels.RemoteConfigSnapshot s = new RemoteModels.RemoteConfigSnapshot();
                s.payloadJson = cached.toString();
                s.updatedAt = RemoteControlCache.updatedAt(c);
                s.version = "cache";
                return ApiResult.success(s);
            }
            return ApiResult.network(r.message);
        }
        RemoteModels.RemoteConfigSnapshot s = new RemoteModels.RemoteConfigSnapshot();
        s.payloadJson = r.data.toString();
        s.updatedAt = System.currentTimeMillis();
        s.version = "live";
        return ApiResult.success(s);
    }
}
