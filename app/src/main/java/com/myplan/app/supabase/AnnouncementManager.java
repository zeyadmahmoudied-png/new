package com.myplan.app.supabase;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;
import com.myplan.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * In-App Announcement Popup — منفصل عن Inbox / Planner / Notifications.
 * Types: NEW_FEATURE, GENERAL, PREMIUM, MAINTENANCE, WARNING, UPDATE
 * Frequency default: once (persisted locally).
 */
public final class AnnouncementManager {
    private AnnouncementManager() {}

    private static final String PREFS = "myplan_announcement_state_v1";
    private static final String KEY_CACHE = "cached_announcements";
    private static final String KEY_CACHE_AT = "cached_at";
    private static boolean showing;

    public enum AnnType {
        POLICY("policy", "⚖️", "سياسة", 0xFF5C6BC0),
        UPDATE("update", "🎊", "تحديث", 0xFF26A69A),
        NEW_FEATURE("new_feature", "✨", "ميزة جديدة", 0xFF7C4DFF),
        SPORTS("sports", "🏆", "تحدي رياضي", 0xFF42A5F5),
        NEWS("news", "📰", "خبر", 0xFF78909C),
        OFFER("offer", "🎁", "عرض", 0xFFFFB300),
        GENERAL("general", "📢", "إعلان عام", 0xFF42A5F5),
        PREMIUM("premium", "⭐", "Premium", 0xFFFFB300),
        MAINTENANCE("maintenance", "🔧", "صيانة", 0xFF78909C),
        WARNING("warning", "⚠️", "تحذير", 0xFFEF5350);

        public final String key;
        public final String emoji;
        public final String labelAr;
        public final int accent;

        AnnType(String key, String emoji, String labelAr, int accent) {
            this.key = key; this.emoji = emoji; this.labelAr = labelAr; this.accent = accent;
        }

        public static AnnType from(String raw) {
            if (raw == null) return GENERAL;
            String s = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
            if (s.contains("policy") || s.contains("privacy") || s.contains("terms")) return POLICY;
            if (s.contains("new_feature") || s.equals("feature") || s.equals("newfeature")) return NEW_FEATURE;
            if (s.contains("sport") || s.contains("challenge")) return SPORTS;
            if (s.contains("news") || s.contains("article")) return NEWS;
            if (s.contains("offer") || s.contains("sale") || s.contains("promo")) return OFFER;
            if (s.contains("premium")) return PREMIUM;
            if (s.contains("maintenance")) return MAINTENANCE;
            if (s.contains("warn") || s.contains("alert") || s.contains("important")) return WARNING;
            if (s.contains("update") || s.equals("force_update")) return UPDATE;
            if (s.equals("announcement") || s.equals("popup") || s.equals("general")) return GENERAL;
            for (AnnType t : values()) if (t.key.equals(s)) return t;
            return GENERAL;
        }
    }

    public static final class Item {
        public String id = "";
        public AnnType type = AnnType.GENERAL;
        public String title = "";
        public String body = "";
        public String imageUrl = "";
        public String icon = "";
        public String primaryButtonText = "";
        public String primaryAction = "CLOSE";
        public String primaryUrl = "";
        public String secondaryButtonText = "";
        public String secondaryAction = "NONE";
        public String secondaryUrl = "";
        public long startAt;
        public long endAt;
        public boolean enabled = true;
        public int priority;
        public String audienceType = "all";
        public String audienceId = "";
        public String frequency = "once";
        public boolean dismissible = true;
        public boolean preview;
    }

    public static void maybeShow(Activity activity) {
        if (activity == null || activity.isFinishing() || showing) return;
        try {
            Item next = pickNext(activity);
            if (next == null) return;
            showPopup(activity, next);
        } catch (Exception ignored) {}
    }

    public static void showPreview(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Item it = new Item();
        it.id = "preview_local_new_feature";
        it.type = AnnType.NEW_FEATURE;
        it.title = "ميزة جديدة وصلت!";
        it.body = "أصبح بإمكانك تجربة تحسينات جديدة تساعدك على تنظيم خطتك الدراسية بطريقة أسهل وأكثر وضوحًا.";
        it.primaryButtonText = "اكتشف الميزة";
        it.primaryAction = "CLOSE";
        it.secondaryButtonText = "إغلاق";
        it.secondaryAction = "CLOSE";
        it.dismissible = true;
        it.preview = true;
        it.frequency = "once";
        it.audienceType = "all";
        try {
            showPopup(activity, it);
        } catch (Exception ignored) {}
    }

    public static void fetchAndCache(Context c) {
        if (c == null || !SupabaseConfig.isConfigured(c)) return;
        try {
            SupabaseRepository repo = new SupabaseRepository(c);
            JSONArray out = new JSONArray();
            ApiResult<String> r = repo.httpGet(
                    "/rest/v1/messages?select=*&is_active=eq.true"
                            + "&or=(message_type.eq.announcement,message_type.eq.new_feature,message_type.eq.warning,message_type.eq.premium,message_type.eq.maintenance)"
                            + "&order=created_at.desc&limit=40");
            if (r != null && r.isSuccess() && r.data != null) {
                JSONArray arr = new JSONArray(r.data);
                for (int i = 0; i < arr.length(); i++) out.put(arr.getJSONObject(i));
            }
            JSONObject snap = RemoteControlCache.loadSnapshot(c);
            if (snap != null) {
                Object a = snap.opt("announcements");
                if (a instanceof JSONArray) {
                    JSONArray arr = (JSONArray) a;
                    for (int i = 0; i < arr.length(); i++) out.put(arr.get(i));
                }
            }
            sp(c).edit()
                    .putString(KEY_CACHE, out.toString())
                    .putLong(KEY_CACHE_AT, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    private static Item pickNext(Context c) {
        JSONArray arr;
        try {
            arr = new JSONArray(sp(c).getString(KEY_CACHE, "[]"));
        } catch (Exception e) {
            return null;
        }
        long now = System.currentTimeMillis();
        Item best = null;
        int bestPri = Integer.MIN_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                Item it = fromJson(o);
                if (it.id.isEmpty()) continue;
                if (!it.enabled) continue;
                if (it.startAt > 0 && now < it.startAt) continue;
                if (it.endAt > 0 && now > it.endAt) continue;
                if (!audienceMatches(c, it)) continue;
                if (isSeen(c, it.id)) continue;
                if (!frequencyAllows(c, it)) continue;
                if (best == null || it.priority > bestPri
                        || (it.priority == bestPri && it.id.compareTo(best.id) < 0)) {
                    bestPri = it.priority;
                    best = it;
                }
            } catch (Exception ignored) {}
        }
        return best;
    }

    private static boolean audienceMatches(Context c, Item it) {
        String a = it.audienceType == null ? "all" : it.audienceType.trim().toLowerCase();
        if (a.isEmpty() || a.equals("all") || a.equals("everyone")) return true;
        boolean prem = AppInfrastructure.isPremiumActive(c);
        if (a.equals("premium")) return prem;
        if (a.equals("free")) return !prem;
        if (a.equals("user") || a.equals("specific_user")) {
            String uid = AccountAuth.getSessionUserId(c);
            String remote = uid == null ? "" : SupabaseRepository.remoteUserUuid(uid);
            String aid = it.audienceId == null ? "" : it.audienceId.trim();
            if (aid.isEmpty()) return false;
            return aid.equalsIgnoreCase(uid) || aid.equalsIgnoreCase(remote);
        }
        if (a.equals("group")) {
            String groupKey = it.audienceId == null ? "" : it.audienceId.trim();
            if (groupKey.isEmpty() || !SupabaseConfig.isConfigured(c)) return false;
            try {
                String inst = AppInfrastructure.getInstallationId(c);
                SupabaseRepository repo = new SupabaseRepository(c);
                ApiResult<String> g = repo.httpGet("/rest/v1/target_groups?select=id&group_key=eq." + java.net.URLEncoder.encode(groupKey, "UTF-8") + "&enabled=eq.true&limit=1");
                if (g == null || !g.isSuccess() || g.data == null) return false;
                JSONArray ga = new JSONArray(g.data);
                if (ga.length() == 0) return false;
                String gid = ga.getJSONObject(0).optString("id", "");
                if (gid.isEmpty()) return false;
                ApiResult<String> m = repo.httpGet("/rest/v1/target_group_members?select=installation_id&group_id=eq." + gid + "&installation_id=eq." + java.net.URLEncoder.encode(inst, "UTF-8") + "&limit=1");
                return m != null && m.isSuccess() && m.data != null && new JSONArray(m.data).length() > 0;
            } catch (Exception e) { return false; }
        }
        if (a.equals("version")) {
            String wanted = it.audienceId == null ? "" : it.audienceId.trim();
            return !wanted.isEmpty() && (wanted.equals(BuildConfig.VERSION_NAME) || wanted.equals(String.valueOf(BuildConfig.VERSION_CODE)));
        }
        if (a.equals("android")) {
            String wanted = it.audienceId == null ? "" : it.audienceId.trim();
            if (wanted.isEmpty()) return false;
            return wanted.equalsIgnoreCase(android.os.Build.VERSION.RELEASE)
                    || wanted.equals(String.valueOf(android.os.Build.VERSION.SDK_INT));
        }
        if (a.equals("device") || a.equals("specific_device")) {
            String inst = AppInfrastructure.getInstallationId(c);
            String aid = it.audienceId == null ? "" : it.audienceId.trim();
            return !aid.isEmpty() && aid.equalsIgnoreCase(inst);
        }
        return false;
    }

    private static boolean frequencyAllows(Context c, Item it) {
        String f = it.frequency == null ? "once" : it.frequency.toLowerCase();
        if ("mandatory".equals(f)) return true;
        return !isSeen(c, it.id);
    }

    private static boolean isSeen(Context c, String id) {
        if (id == null || id.isEmpty()) return true;
        return sp(c).getBoolean("announcement_seen_" + id, false);
    }

    private static void markSeen(Context c, Item it) {
        if (it == null || it.preview || it.id == null || it.id.isEmpty()) return;
        sp(c).edit().putBoolean("announcement_seen_" + it.id, true).apply();
    }

    private static Item fromJson(JSONObject o) {
        Item it = new Item();
        it.id = firstNonEmpty(o, "id", "announcement_id");
        if (it.id.isEmpty() && o.has("id")) it.id = String.valueOf(o.opt("id"));

        String typeRaw = firstNonEmpty(o, "type", "message_type", "announcement_type");
        it.type = AnnType.from(typeRaw);

        it.title = firstNonEmpty(o, "title");
        it.body = firstNonEmpty(o, "body", "message", "content");
        it.imageUrl = firstNonEmpty(o, "image_url", "image", "imageUrl");
        it.icon = firstNonEmpty(o, "icon");

        it.primaryButtonText = firstNonEmpty(o, "primary_button_text", "primary_label", "primaryLabel", "cta");
        it.primaryAction = normalizeAction(firstNonEmpty(o, "primary_action", "action", "primaryAction"));
        it.primaryUrl = firstNonEmpty(o, "primary_url", "external_url", "url", "primaryUrl");

        it.secondaryButtonText = firstNonEmpty(o, "secondary_button_text", "secondary_label", "secondaryLabel");
        it.secondaryAction = normalizeAction(firstNonEmpty(o, "secondary_action", "secondaryAction"));
        it.secondaryUrl = firstNonEmpty(o, "secondary_url", "secondaryUrl");

        it.startAt = parseTime(o, "start_at", "starts_at", "startAt");
        it.endAt = parseTime(o, "end_at", "ends_at", "endAt");

        if (o.has("enabled")) it.enabled = o.optBoolean("enabled", true);
        if (o.has("is_active")) it.enabled = o.optBoolean("is_active", it.enabled);

        it.priority = o.optInt("priority", 0);
        it.audienceType = firstNonEmpty(o, "audience_type", "audience", "target_type");
        if (it.audienceType.isEmpty()) it.audienceType = "all";
        if ("everyone".equalsIgnoreCase(it.audienceType)) it.audienceType = "all";
        it.audienceId = firstNonEmpty(o, "audience_id", "target_id", "user_id");

        it.frequency = firstNonEmpty(o, "frequency");
        if (it.frequency.isEmpty()) it.frequency = "once";
        it.dismissible = o.optBoolean("dismissible", true);

        if (it.primaryButtonText.isEmpty()) {
            switch (it.type) {
                case POLICY: it.primaryButtonText = "قراءة السياسة"; break;
                case UPDATE: it.primaryButtonText = "عرض التحديث"; break;
                case NEW_FEATURE: it.primaryButtonText = "اكتشف الميزة"; break;
                case SPORTS: it.primaryButtonText = "ابدأ التحدي"; break;
                case NEWS: it.primaryButtonText = "اقرأ الخبر"; break;
                case OFFER: it.primaryButtonText = "شاهد العرض"; break;
                case PREMIUM: it.primaryButtonText = "معرفة المزيد"; break;
                case WARNING: it.primaryButtonText = "فهمت"; break;
                default: it.primaryButtonText = "حسنًا"; break;
            }
        }
        if (it.title.isEmpty()) it.title = it.type.labelAr;
        return it;
    }

    private static String normalizeAction(String a) {
        if (a == null || a.trim().isEmpty()) return "CLOSE";
        String s = a.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (s.equals("NONE") || s.equals("CLOSE") || s.equals("OPEN_URL")
                || s.equals("OPEN_UPDATE") || s.equals("OPEN_SCREEN")) return s;
        if (s.contains("URL") || s.contains("LINK")) return "OPEN_URL";
        if (s.contains("UPDATE")) return "OPEN_UPDATE";
        if (s.contains("SCREEN") || s.contains("TAB")) return "OPEN_SCREEN";
        if (s.contains("DISMISS") || s.contains("OK")) return "CLOSE";
        return "NONE";
    }

    private static String firstNonEmpty(JSONObject o, String... keys) {
        for (String k : keys) {
            if (!o.has(k) || o.isNull(k)) continue;
            String v = o.optString(k, "").trim();
            if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return "";
    }

    private static long parseTime(JSONObject o, String... keys) {
        for (String k : keys) {
            if (!o.has(k) || o.isNull(k)) continue;
            long n = o.optLong(k, 0);
            if (n > 1_000_000_000_000L) return n;
            if (n > 1_000_000_000L) return n * 1000L;
            String s = o.optString(k, "");
            if (s.length() >= 10) {
                try {
                    return java.time.Instant.parse(s).toEpochMilli();
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static void showPopup(Activity activity, Item it) {
        if (showing) return;
        showing = true;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(it.dismissible);
        dialog.setCanceledOnTouchOutside(it.dismissible);

        FrameLayout root = new FrameLayout(activity);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setClickable(true);

        View dim = new View(activity);
        dim.setBackgroundColor(0xCC0B1220);
        root.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        applyBlurIfPossible(activity, true);

        LinearLayout card = buildCard(activity, it, dialog);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        int side = dp(activity, 28);
        cp.setMargins(side, side, side, side);
        root.addView(card, cp);

        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0f);
        }

        dialog.setOnDismissListener(d -> {
            showing = false;
            clearBlurIfPossible(activity);
            if (!it.preview) {
                try {
                    activity.getWindow().getDecorView().postDelayed(
                            () -> maybeShow(activity), 350);
                } catch (Exception ignored) {}
            }
        });

        try {
            dialog.show();
            if (!it.preview) markSeen(activity, it);
        } catch (Exception e) {
            showing = false;
            clearBlurIfPossible(activity);
        }
    }

    private static LinearLayout buildCard(Activity activity, Item it, Dialog dialog) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = dp(activity, 20);
        card.setPadding(pad, pad, pad, dp(activity, 16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151C29);
        bg.setCornerRadius(dp(activity, 20));
        bg.setStroke(dp(activity, 1), (it.type.accent & 0x66FFFFFF));
        card.setBackground(bg);
        card.setElevation(dp(activity, 14));

        // هوية بصرية مختلفة لكل نوع، مع الحفاظ على ألوان My Plan.
        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable heroBg = new GradientDrawable();
        int heroColor;
        switch (it.type) {
            case POLICY: heroColor = 0x335C6BC0; break;
            case UPDATE: heroColor = 0x3326A69A; break;
            case NEW_FEATURE: heroColor = 0x337C4DFF; break;
            case SPORTS: heroColor = 0x3342A5F5; break;
            case NEWS: heroColor = 0x3378909C; break;
            case OFFER: heroColor = 0x33FFB300; break;
            default: heroColor = 0x334B6DFF; break;
        }
        heroBg.setColor(heroColor);
        heroBg.setCornerRadius(dp(activity, 16));
        hero.setBackground(heroBg);
        hero.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

        TextView icon = new TextView(activity);
        String visual = it.icon == null || it.icon.isEmpty() ? it.type.emoji : it.icon;
        icon.setText(visual);
        icon.setTextSize(it.type == AnnType.SPORTS ? 30 : 28);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(it.type.accent & 0x44FFFFFF);
        iconBg.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBg);
        hero.addView(icon, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));

        LinearLayout heroText = new LinearLayout(activity);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        TextView type = new TextView(activity);
        type.setText(it.type.labelAr);
        type.setTextColor(it.type.accent);
        type.setTextSize(12);
        type.setTypeface(Typeface.DEFAULT_BOLD);
        TextView title = new TextView(activity);
        title.setText(it.title);
        title.setTextColor(0xFFF4F6FA);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.RIGHT);
        heroText.addView(type);
        heroText.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams htp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htp.setMargins(dp(activity, 12), 0, 0, 0);
        hero.addView(heroText, htp);
        card.addView(hero);

        if (it.type == AnnType.POLICY) {
            TextView note = new TextView(activity);
            note.setText("🛡️  معلومات مهمة تخص استخدامك للتطبيق");
            note.setTextColor(0xFFB8C4E0);
            note.setTextSize(12);
            note.setPadding(0, dp(activity, 12), 0, 0);
            card.addView(note);
        } else if (it.type == AnnType.UPDATE) {
            TextView note = new TextView(activity);
            note.setText("🎉  إصدار جديد وتحسينات جاهزة");
            note.setTextColor(0xFF9ADFD3);
            note.setTextSize(12);
            note.setPadding(0, dp(activity, 12), 0, 0);
            card.addView(note);
        } else if (it.type == AnnType.SPORTS) {
            TextView note = new TextView(activity);
            note.setText("🏃  جاهز للتحدي؟ خلّي إنجازك يتكلم!");
            note.setTextColor(0xFF9CCBFF);
            note.setTextSize(12);
            note.setPadding(0, dp(activity, 12), 0, 0);
            card.addView(note);
        } else if (it.type == AnnType.OFFER) {
            TextView note = new TextView(activity);
            note.setText("🎁  فرصة محدودة");
            note.setTextColor(0xFFFFD36A);
            note.setTextSize(12);
            note.setTypeface(Typeface.DEFAULT_BOLD);
            note.setPadding(0, dp(activity, 12), 0, 0);
            card.addView(note);
        }

        if (it.body != null && !it.body.isEmpty()) {
            TextView body = new TextView(activity);
            body.setText(it.body);
            body.setTextColor(0xFFD6DCE8);
            body.setTextSize(14);
            body.setLineSpacing(0, 1.15f);
            body.setGravity(Gravity.RIGHT);
            body.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
            card.addView(body);
        }

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (it.primaryButtonText != null && !it.primaryButtonText.isEmpty()) {
            TextView primary = new TextView(activity);
            primary.setText(it.primaryButtonText);
            primary.setTextColor(Color.WHITE);
            primary.setGravity(Gravity.CENTER);
            primary.setTypeface(Typeface.DEFAULT_BOLD);
            primary.setTextSize(13);
            GradientDrawable pbg = new GradientDrawable();
            pbg.setColor(it.type.accent);
            pbg.setCornerRadius(dp(activity, 12));
            primary.setBackground(pbg);
            primary.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
            primary.setOnClickListener(v -> runAction(activity, dialog, it.primaryAction, it.primaryUrl));
            buttons.addView(primary, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        }

        if (it.secondaryButtonText != null && !it.secondaryButtonText.isEmpty()) {
            TextView secondary = new TextView(activity);
            secondary.setText(it.secondaryButtonText);
            secondary.setTextColor(0xFFD6DCE8);
            secondary.setGravity(Gravity.CENTER);
            secondary.setTextSize(13);
            GradientDrawable sbg = new GradientDrawable();
            sbg.setColor(0x22FFFFFF);
            sbg.setCornerRadius(dp(activity, 12));
            secondary.setBackground(sbg);
            secondary.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
            secondary.setOnClickListener(v -> runAction(activity, dialog, it.secondaryAction, it.secondaryUrl));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
            slp.setMargins(dp(activity, 8), 0, 0, 0);
            buttons.addView(secondary, slp);
        }
        card.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static void handleAction(Activity activity, Dialog dialog, String action, String url) {
        String a = action == null ? "NONE" : action.trim().toUpperCase();
        try {
            switch (a) {
                case "OPEN_URL":
                    if (url != null && url.startsWith("https://")) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                    break;
                case "OPEN_UPDATE": {
                    String apk = RemoteControlCache.apkUrl(activity);
                    if (apk != null && apk.startsWith("https://")) activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apk)));
                    else if (url != null && url.startsWith("https://")) activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    break;
                }
                case "OPEN_SCREEN":
                    // Screen routing is intentionally left to the caller; dismiss the announcement safely.
                    break;
                case "CLOSE":
                case "NONE":
                default:
                    break;
            }
        } catch (Exception ignored) {
        } finally {
            if (dialog != null) dialog.dismiss();
        }
    }

    private static TextView makeButton(Context c, String text, int bgColor, boolean filled) {
        TextView b = new TextView(c);
        b.setText(text == null || text.isEmpty() ? "حسنًا" : text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 12));
        if (filled) {
            g.setColor(bgColor);
            b.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(c, 1), 0x55FFFFFF);
            b.setTextColor(0xFFCFD8DC);
        }
        b.setBackground(g);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private static void runAction(Activity activity, Dialog dialog, String action, String url) {
        if (dialog != null && (action == null || action.trim().isEmpty()
                || "CLOSE".equalsIgnoreCase(action) || "NONE".equalsIgnoreCase(action))) {
            dialog.dismiss();
            return;
        }
        runAction(activity, action, url);
        if (dialog != null) dialog.dismiss();
    }

    private static void runAction(Activity activity, String action, String url) {
        String a = action == null ? "NONE" : action.trim().toUpperCase();
        try {
            switch (a) {
                case "OPEN_URL":
                    if (url != null && url.startsWith("https://")) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                    break;
                case "OPEN_UPDATE": {
                    String apk = RemoteControlCache.apkUrl(activity);
                    if (apk != null && apk.startsWith("https://")) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apk)));
                    } else if (url != null && url.startsWith("https://")) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                    break;
                }
                case "OPEN_SCREEN":
                case "CLOSE":
                case "NONE":
                default:
                    break;
            }
        } catch (Exception ignored) {}
    }

    private static void applyBlurIfPossible(Activity activity, boolean on) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            View decor = activity.getWindow().getDecorView();
            if (on) {
                decor.setRenderEffect(
                        RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP));
            } else {
                decor.setRenderEffect(null);
            }
        } catch (Exception ignored) {}
    }

    private static void clearBlurIfPossible(Activity activity) {
        applyBlurIfPossible(activity, false);
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int dp(Context c, int v) {
        float d = c.getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
