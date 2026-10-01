package com.myplan.app.sync;

import java.util.List;

/** Remote sync contract. Production implementation returns NOT_CONFIGURED until backend exists. */
public interface RemoteSyncRepository {
    SyncResult<List<SyncItem>> pullChanges(long sinceVersion);
    SyncResult<Void> pushChanges(List<SyncItem> items);
    SyncResult<Void> ack(long upToVersion);
    SyncResult<List<DeviceInfo>> listDevices();
    boolean isConfigured();
}
