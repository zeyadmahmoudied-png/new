package com.myplan.app.sync;

import java.util.List;

public final class NotConfiguredRemoteSyncRepository implements RemoteSyncRepository {
    @Override public boolean isConfigured() { return false; }

    @Override
    public SyncResult<List<SyncItem>> pullChanges(long sinceVersion) {
        return SyncResult.notConfigured();
    }

    @Override
    public SyncResult<Void> pushChanges(List<SyncItem> items) {
        return SyncResult.notConfigured();
    }

    @Override
    public SyncResult<Void> ack(long upToVersion) {
        return SyncResult.notConfigured();
    }

    @Override
    public SyncResult<List<DeviceInfo>> listDevices() {
        return SyncResult.notConfigured();
    }
}
