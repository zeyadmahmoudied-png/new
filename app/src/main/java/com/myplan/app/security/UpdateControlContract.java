package com.myplan.app.security;

/** Force/recommended update contract — NOT_CONFIGURED without backend. */
public final class UpdateControlContract {
    private UpdateControlContract() {}

    public static final class UpdateInfo {
        public String minimumSupportedVersion = "";
        public String latestVersion = "";
        public boolean forceUpdate;
        public boolean recommendedUpdate;
        public String updateUrl = "";
        public String releaseNotes = "";
        public String status = "NOT_CONFIGURED";
    }

    public static UpdateInfo current() {
        return current(null);
    }

    /** يقرأ من كاش Remote Control إن وُجد. */
    public static UpdateInfo current(android.content.Context c) {
        UpdateInfo i = new UpdateInfo();
        if (c == null) {
            i.status = "NOT_CONFIGURED";
            return i;
        }
        try {
            org.json.JSONObject snap = com.myplan.app.supabase.RemoteControlCache.loadSnapshot(c);
            if (snap == null) {
                i.status = "NOT_CONFIGURED";
                return i;
            }
            i.minimumSupportedVersion = com.myplan.app.supabase.RemoteControlCache.minVersion(c);
            i.latestVersion = com.myplan.app.supabase.RemoteControlCache.latestVersion(c);
            i.updateUrl = com.myplan.app.supabase.RemoteControlCache.apkUrl(c);
            i.releaseNotes = com.myplan.app.supabase.RemoteControlCache.releaseNotes(c);
            i.forceUpdate = com.myplan.app.supabase.RemoteControlCache.forceUpdate(c);
            i.recommendedUpdate = !i.forceUpdate && i.latestVersion != null && !i.latestVersion.isEmpty();
            i.status = "OK";
        } catch (Exception e) {
            i.status = "NOT_CONFIGURED";
        }
        return i;
    }
}
