package com.myplan.app.sync;

/** Multi-device registry entry — backend required for real multi-device. */
public final class DeviceInfo {
    public String deviceId;
    public String userId;
    public String deviceName;
    public String platform;
    public String appVersion;
    public long lastSeenMs;
    public String syncState;
}
