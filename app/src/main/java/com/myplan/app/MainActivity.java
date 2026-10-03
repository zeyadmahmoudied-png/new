package com.myplan.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SweepGradient;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    // Premium design tokens — أسود + Accent من شعار My Plan
    private static final int BG = 0xFF07090E;
    private static final int BG_ELEV = 0xFF0E121A;
    private static final int CARD = 0xFF141A24;
    private static final int CARD2 = 0xFF1A2130;
    private static final int TEXT = 0xFFF4F6FA;
    private static final int MUTED = 0xFF8E98AB;
    private static final int MUTED2 = 0xFF5C6678;
    private static final int ACCENT = 0xFF4B6DFF; // لون شعار My Plan
    private static final int ACCENT_SOFT = 0x334B6DFF;
    private static final int ACCENT_DARK = 0xFFFFFFFF;
    private static final int OK = 0xFF3DCF8E;       // أولوية منخفضة
    private static final int WARN = 0xFFE6B84D;     // أولوية متوسطة
    private static final int DANGER = 0xFFE25563;   // أولوية عالية
    private static final int OVERLAY = 0xCC07090E;
    private static final int RADIUS = 16;
    private static final int RADIUS_SM = 12;

    private static final int REQ_BACKUP_SAVE = 1001;
    private static final int REQ_BACKUP_OPEN = 1002;
    private static final int REQ_LOCATION = 1003;
    /** بعد حفظ Backup من حوار تسجيل الخروج */
    private boolean logoutAfterBackup = false;

    private Planner planner;
    private FrameLayout content;
    private TextView navSchedule, navTasks, navExams, navStats, navRoutine;
    private int tab = 0;
    private boolean weekView = false;
    private int scheduleViewMode = 0; // 0 اليوم 1 أسبوع 2 مخصص
    private View floatingOverlay;
    private int weekSelectedDow = -1; // -1 = show all week, else Calendar.DAY_OF_WEEK

    // Timer state survives tab switches
    private CountDownTimer timer;
    private String timerSessionId;
    private String extraTaskId;
    private long extraStartedMs;
    private int extraDurationMin;
    private long timerLeftMs;
    private long timerTotalMs;
    private boolean timerPaused;
    private boolean timerRunning;
    private PlanningIntentParser.SessionState chatState = new PlanningIntentParser.SessionState();
    private PlanningIntentParser.Intent pendingIntent;
    private final java.util.List<String[]> chatLog = new java.util.ArrayList<>();
    private Planner.Task draggingTask;
    private float dragAnchorY;
    // Free-drag للجلسات (عرض الأسبوع/اليوم)
    private Planner.Session draggingSession;
    private View sessionDragGhost;
    private TextView sessionDropHint;
    private String sessionDropDay;
    private int sessionDropStartMin;
    private boolean sessionDropValid;
    private View draggingCardView;
    private final java.util.HashMap<String, View> dayDropZones = new java.util.HashMap<>();
    private final android.os.Handler dragLongPressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable dragLongPressRunnable;
    private float dragDownRawX, dragDownRawY;
    private boolean dragMoved;
    /** false = قائمة عادية · true = بلوكات حسب المادة */
    private boolean tasksGroupBySubject = false;
    /** وضع العرض: 0 نوع · 1 مادة · 2 مدة · 3 أولوية — عرض فقط */
    private int taskKindFilter = -1;
    private int taskSortKey = 0;
    /** true = تصاعدي (↑) · false = تنازلي (↓) */
    private boolean taskSortAsc = true;
    /** دليل المستخدم: 0 مغلق · 1 قائمة مواضيع · 2 شرح موضوع */
    private int guideMode = 0;
    private int guideTopic = -1;

    private AlertDialog timerDialog;
    private TextView timerClockView;
    private CircularProgressView timerRing;
    private Button timerPauseBtn;

    private final String[] DAY_NAMES = {"", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    // Arabic week order starting Saturday
    private final int[] WEEK_ORDER = {
            Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppInfrastructure.onLaunch(this);
        // بوابة: لا واجهة My Plan بدون جلسة صالحة
        if (!AccountAuth.isLoggedIn(this)) {
            Intent login = new Intent(this, LoginActivity.class);
            if (getIntent() != null && getIntent().getExtras() != null) {
                login.putExtras(getIntent().getExtras());
            }
            startActivity(login);
            finish();
            return;
        }
        planner = new Planner(this);
        setContentView(buildRoot());
        ensureAlertPermissions();
        SessionAlarmScheduler.resync(this, planner);
        if (planner.lastLoadFailed) {
            Toast.makeText(this,
                    "تعذّر قراءة بعض البيانات المحفوظة. لم يتم حذف أي شيء. جرّب الاسترجاع من نسخة احتياطية.",
                    Toast.LENGTH_LONG).show();
        }
        showTab(0);
        // بعد بناء الواجهة: فتح Smart Recovery من إشعار فائتة إن وُجد
        content.post(() -> handleOpenMissedFromIntent(getIntent()));
        // Remote Control (Supabase) — لا يمس Planner
        content.post(this::applyRemoteControlGates);
        content.post(this::applyAdminBanGates);
        com.myplan.app.supabase.RemoteSyncCoordinator.syncAsync(this);
        // تحديث الحظر + الرسائل + Announcements في الخلفية بعد الدخول
        new Thread(() -> {
            try {
                com.myplan.app.supabase.AdminBanGate.refresh(getApplicationContext());
            } catch (Exception ignored) {}
            runOnUiThread(this::applyAdminBanGates);
            try {
                syncInboxAsync(false);
            } catch (Exception ignored) {}
            try {
                com.myplan.app.supabase.AnnouncementManager.fetchAndCache(getApplicationContext());
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                try {
                    content.postDelayed(
                            () -> com.myplan.app.supabase.AnnouncementManager.maybeShow(MainActivity.this),
                            600);
                } catch (Exception ignored) {}
            });
        }, "ban-refresh").start();
    }

    private void syncInboxAsync(boolean refreshUi) {
        final android.content.Context appCtx = getApplicationContext();
        new Thread(() -> {
            int unreadAfter = 0;
            try {
                if (com.myplan.app.supabase.SupabaseConfig.isConfigured(appCtx)) {
                    com.myplan.app.api.ApiResult<org.json.JSONArray> r =
                            new com.myplan.app.supabase.SupabaseRepository(appCtx).fetchInboxMessages();
                    if (r != null && r.isSuccess() && r.data != null) {
                        InboxStore.saveCache(appCtx, r.data);
                    }
                }
                unreadAfter = InboxStore.unreadCount(appCtx);
            } catch (Exception ignored) {}
            final int unread = unreadAfter;
            runOnUiThread(() -> {
                updateMoreNavBadge();
                // لا Toast متكرر لرسائل المطور — Inbox فقط
                lastNotifiedUnread = unread;
                if (refreshUi && showingContactUs) showTab(4);
                else if (tab == 4 && !showingContactUs) showTab(4);
            });
        }, "inbox-sync").start();
    }

    private void updateMoreNavBadge() {
        if (navRoutine == null) return;
        int unread = InboxStore.unreadCount(this);
        if (unread > 0) {
            navRoutine.setText("المزيد ●");
            navRoutine.setTextColor(tab == 4 ? ACCENT : 0xFFE53935);
        } else {
            navRoutine.setText("المزيد");
            navRoutine.setTextColor(tab == 4 ? ACCENT : MUTED);
        }
    }

    /** حظر حساب/جهاز من Control Center — بدون حذف بيانات محلية. */
    private void applyAdminBanGates() {
        try {
            com.myplan.app.supabase.AdminBanGate.BanStatus ban =
                    com.myplan.app.supabase.AdminBanGate.cached(this);
            if (ban.deviceBanned) {
                myDialog().setTitle("الجهاز محظور")
                        .setMessage((ban.deviceMessage != null && !ban.deviceMessage.isEmpty())
                                ? ban.deviceMessage
                                : "تم حظر هذا الجهاز من لوحة التحكم. بياناتك محفوظة محليًا.")
                        .setCancelable(false)
                        .setPositiveButton("خروج", (d, w) -> {
                            AccountAuth.logout(this);
                            startActivity(new Intent(this, LoginActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            finish();
                        })
                        .show();
                return;
            }
            if (ban.accountBanned) {
                myDialog().setTitle("الحساب محظور")
                        .setMessage((ban.accountMessage != null && !ban.accountMessage.isEmpty())
                                ? ban.accountMessage
                                : "تم حظر هذا الحساب من لوحة التحكم. بيانات الدراسة لم تُحذف.")
                        .setCancelable(false)
                        .setPositiveButton("تسجيل الخروج", (d, w) -> {
                            AccountAuth.logout(this);
                            startActivity(new Intent(this, LoginActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            finish();
                        })
                        .show();
            }
        } catch (Exception ignored) {}
    }

    private void showLogoutWithBackupPrompt() {
        myDialog().setTitle("تسجيل الخروج")
                .setMessage("هل تريد الاحتفاظ بنسخة احتياطية من بياناتك قبل تسجيل الخروج؟")
                .setPositiveButton("تخزين نسخة احتياطية", (d, w) -> {
                    try {
                        String json = planner.exportJson();
                        boolean ok = LocalBackupHelper.saveBackupJson(this, json);
                        if (ok) {
                            Toast.makeText(this, "تم حفظ النسخة الاحتياطية محليًا", Toast.LENGTH_SHORT).show();
                            performLogoutToLogin();
                        } else {
                            Toast.makeText(this, "فشل حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "فشل الحفظ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("تخطي", (d, w) -> performLogoutToLogin())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void performLogoutToLogin() {
        logoutAfterBackup = false;
        // لا نحذف بيانات Planner — فقط جلسة الحساب
        AccountAuth.logout(this);
        Toast.makeText(this, "تم تسجيل الخروج. بيانات الدراسة لم تُحذف.", Toast.LENGTH_SHORT).show();
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(login);
        finish();
    }

    /** Maintenance / Kill Switch / Force Update من الكاش — بدون حذف بيانات. */
    private void applyRemoteControlGates() {
        try {
            if (com.myplan.app.supabase.RemoteControlCache.killSwitch(this)) {
                myDialog().setTitle("التطبيق متوقف مؤقتًا")
                        .setMessage("تم تفعيل إيقاف الطوارئ من لوحة التحكم. بياناتك محفوظة على الجهاز.")
                        .setCancelable(false)
                        .setPositiveButton("خروج", (d, w) -> finish())
                        .show();
                return;
            }
            if (com.myplan.app.supabase.RemoteControlCache.maintenance(this)) {
                myDialog().setTitle("صيانة")
                        .setMessage("التطبيق في وضع الصيانة. يمكنك المحاولة لاحقًا. بياناتك لم تُحذف.")
                        .setCancelable(true)
                        .setPositiveButton("حسناً", null)
                        .show();
            }
            com.myplan.app.security.UpdateControlContract.UpdateInfo ui =
                    com.myplan.app.security.UpdateControlContract.current(this);
            if (ui != null && "OK".equals(ui.status)) {
                String cur = "1.0";
                try { cur = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
                if (ui.forceUpdate || needsForceUpdate(cur, ui.minimumSupportedVersion)) {
                    String msg = ui.releaseNotes != null && !ui.releaseNotes.isEmpty()
                            ? ui.releaseNotes : "يجب تحديث التطبيق للمتابعة.";
                    if (ui.updateUrl != null && !ui.updateUrl.isEmpty()) {
                        final String url = ui.updateUrl;
                        myDialog().setTitle("تحديث إجباري")
                                .setMessage(msg)
                                .setCancelable(false)
                                .setPositiveButton("تحديث", (d, w) -> {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                                    } catch (Exception e) {
                                        Toast.makeText(this, "تعذّر فتح رابط التحديث", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .show();
                    } else {
                        myDialog().setTitle("تحديث إجباري")
                                .setMessage(msg)
                                .setCancelable(false)
                                .setPositiveButton("حسناً", (d, w) -> finish())
                                .show();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean needsForceUpdate(String current, String minimum) {
        if (minimum == null || minimum.trim().isEmpty()) return false;
        if (current == null || current.trim().isEmpty()) return false;
        try {
            String[] a = current.trim().split("\\.");
            String[] b = minimum.trim().split("\\.");
            int n = Math.max(a.length, b.length);
            for (int i = 0; i < n; i++) {
                int x = i < a.length ? Integer.parseInt(a[i].replaceAll("[^0-9]", "0")) : 0;
                int y = i < b.length ? Integer.parseInt(b[i].replaceAll("[^0-9]", "0")) : 0;
                if (x < y) return true;
                if (x > y) return false;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenMissedFromIntent(intent);
        if (intent != null && intent.getStringExtra("openSessionId") != null) {
            showTab(0);
        }
    }

    /** من إشعار «محاضرة فائتة» — يفتح مسار Smart Recovery للجلسة */
    private void handleOpenMissedFromIntent(Intent intent) {
        if (intent == null) return;
        String sid = intent.getStringExtra("openMissedSessionId");
        boolean openSr = intent.getBooleanExtra("openSmartRecovery", false);
        if (sid == null || !openSr) return;
        try {
            intent.removeExtra("openMissedSessionId");
            intent.removeExtra("openSmartRecovery");
        } catch (Exception ignored) {}
        showTab(0);
        content.post(() -> showMissedSessionChoicesById(sid));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timerSessionId != null) {
            Planner.Session ts = planner.findSessionById(timerSessionId);
            if (ts != null && !ts.done) persistTimerPartial(ts);
        }
    }

    @Override
    protected void onDestroy() {
        if (timerSessionId != null) {
            Planner.Session ts = planner.findSessionById(timerSessionId);
            if (ts != null && !ts.done) persistTimerPartial(ts);
        }
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    /** يحفظ الدقائق المنفذة من الـTimer الحالي على Session (Partial). */
    private void persistTimerPartial(Planner.Session s) {
        if (s == null || s.done || timerTotalMs <= 0) return;
        long elapsedMs = Math.max(0, timerTotalMs - Math.max(0, timerLeftMs));
        int extra = (int) (elapsedMs / 60000L);
        if (extra < 1 && elapsedMs >= 25_000L) extra = 1;
        if (extra < 1) return;
        int already = Math.max(0, s.executedMin);
        planner.persistExecutedMin(s.id, already + extra);
        // منع العدّ المزدوج بعد الحفظ
        timerTotalMs = Math.max(1000L, timerLeftMs);
        PlanWidgetProvider.updateAll(this);
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout navWrap = new LinearLayout(this);
        navWrap.setOrientation(LinearLayout.VERTICAL);
        navWrap.setBackgroundColor(BG);
        navWrap.setPadding(dp(12), dp(6), dp(12), dp(12));
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(CARD);
        navBg.setCornerRadius(dp(RADIUS));
        nav.setBackground(navBg);
        nav.setPadding(dp(6), dp(8), dp(6), dp(8));
        nav.setElevation(dp(8));
        navWrap.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        root.addView(navWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // نفس الترتيب الحالي تمامًا؛ فقط أيقونة معبرة + اختيار أوضح وأشيك.
        navSchedule = navItem("📅  الجدول");
        navTasks = navItem("✓  المهام");
        navExams = navItem("📝  الامتحانات");
        navStats = navItem("◉  الإحصائيات");
        navRoutine = navItem("☰  المزيد");
        nav.addView(navSchedule, navLp());
        nav.addView(navTasks, navLp());
        nav.addView(navExams, navLp());
        nav.addView(navStats, navLp());
        nav.addView(navRoutine, navLp());
        navSchedule.setOnClickListener(v -> showTab(0));
        navTasks.setOnClickListener(v -> showTab(1));
        navExams.setOnClickListener(v -> showTab(3));
        navStats.setOnClickListener(v -> showTab(2));
        navRoutine.setOnClickListener(v -> showTab(4));
        return root;
    }

    private TextView navItem(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(11f);
        t.setTextColor(MUTED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(2), dp(8), dp(2), dp(8));
        t.setSingleLine(true);
        return t;
    }

    private void styleNavItem(TextView t, boolean selected) {
        if (t == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (selected) {
            bg.setColor(0x334B6DFF);
            bg.setStroke(dp(1), 0x664B6DFF);
            t.setTextColor(0xFFFFFFFF);
        } else {
            bg.setColor(0x00141A24);
            t.setTextColor(MUTED);
        }
        t.setBackground(bg);
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private void showTab(int t) {
        if (t != 4) {
            showingContactUs = false;
            contactUsPage = 0;
        }
        // Leaving Developer Center always locks Developer Mode (password required again)
        if (tab == 7 && t != 7) {
            AppInfrastructure.setDeveloperUnlocked(this, false);
        }
        tab = t;
        styleNavItem(navSchedule, t == 0);
        styleNavItem(navTasks, t == 1);
        styleNavItem(navExams, t == 3);
        styleNavItem(navStats, t == 2);
        styleNavItem(navRoutine, t == 4);
        updateMoreNavBadge();
        content.removeAllViews();
        if (t == 0) { weekView = false; content.addView(buildScheduleScreen()); maybeOfferMissedSessions(); }
        else if (t == 1) content.addView(buildTasksScreen());
        else if (t == 2) content.addView(buildStatsScreen());
        else if (t == 3) content.addView(buildExamsScreen());
        else if (t == 5) {
            // AI Planning — Premium (Test أو Entitlement) + flag
            if (AppInfrastructure.isFeatureEnabled(this, "ai") || AppInfrastructure.isPremiumActive(this)) {
                content.addView(buildChatScreen());
            } else {
                content.addView(guideMode != 0 ? buildUserGuideContent() : buildRoutineScreen());
            }
        }
        else if (t == 6) { weekView = true; content.addView(buildScheduleScreen()); }
        else if (t == 7) content.addView(buildDeveloperCenter());
        else if (t == 8) {
            content.addView(guideMode != 0 ? buildUserGuideContent() : buildRoutineScreen());
        }
        else content.addView(guideMode != 0 ? buildUserGuideContent() : buildRoutineScreen());
    }

    // ═══════════════ جدولي ═══════════════
    private boolean missedOfferShown = false;
    /** وضع تعديل جدول اليوم (قلم) — تأجيل بالدقائق بدون Drag */
    private boolean dayEditMode = false;
    /** شاشة تواصل معنا داخل المزيد */
    private boolean showingContactUs = false;
    /** أقسام «المزيد» المفتوحة */
    private final java.util.HashSet<String> moreOpenSections = new java.util.HashSet<>();
    /** 0=قائمة الأزرار · 1=رسائل المطور · 2=إرسال رسالة */
    private int contactUsPage = 0;
    private int lastNotifiedUnread = -1;

    private void maybeOfferMissedSessions() {
        // فحص خفيف للفائتة بدون buildSchedule كامل
        planner.refreshMissedFlags();
        if (missedOfferShown) return;
        java.util.List<Planner.Session> missed = planner.listMissedSessions();
        if (missed == null || missed.isEmpty()) return;
        missedOfferShown = true;
        Planner.Session first = missed.get(0);
        int dur = first != null ? Math.max(0, first.durationMin) : 0;
        String name = first != null && first.taskName != null ? first.taskName : "محاضرة";
        final String firstId = first != null ? first.id : null;
        myDialog().setTitle("جلسة فائتة")
                .setMessage("فاتتك «" + name + "»\nمدتها " + dur + " دقيقة.\n\n"
                        + "في " + missed.size() + " جلسة فائتة.\nأقدر أتعامل معاها بعدة طرق — اختار الأنسب.")
                .setPositiveButton("عرض الاقتراحات", (d, w) -> {
                    // بعد إغلاق الحوار — على الـUI thread في الدورة التالية
                    if (content != null) {
                        content.post(() -> {
                            if (firstId != null) showMissedSessionChoicesById(firstId);
                            else showMissedSessionChoices(first);
                        });
                    } else {
                        if (firstId != null) showMissedSessionChoicesById(firstId);
                        else showMissedSessionChoices(first);
                    }
                })
                .setNegativeButton("لاحقاً", null)
                .show();
    }

    private void setScheduleModeTabStyle(TextView tv, boolean selected) {
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setTextColor(selected ? Color.WHITE : MUTED);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0,dp(8),0,dp(7));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(dp(8));
        if(selected){
            bg.setStroke(dp(1),0x00000000);
            android.graphics.drawable.LayerDrawable layer=new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{bg,
                            new android.graphics.drawable.InsetDrawable(
                                    new android.graphics.drawable.ColorDrawable(ACCENT),0,dp(40),0,0)});
            tv.setBackground(layer);
        }else{
            tv.setBackground(bg);
        }
    }

    private TextView scheduleModeTab(String text, boolean selected) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(16);
        tv.setPadding(0, dp(8), 0, dp(7));
        setScheduleModeTabStyle(tv, selected);
        return tv;
    }

    private LinearLayout.LayoutParams scheduleModeTabLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        return lp;
    }

    private void styleScheduleModeTabs(LinearLayout tabs, int selected) {
        if (tabs == null) return;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View child = tabs.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView tv = (TextView) child;
            boolean on = i == selected;
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setTextColor(on ? Color.WHITE : MUTED);
            tv.setBackgroundColor(0x00000000);
            tv.setTag(on ? "selected" : "normal");
        }
    }

    private View buildScheduleScreen() {
        dayDropZones.clear();
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(28)); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); sc.addView(box);

        Calendar hc=Calendar.getInstance();
        String[] mons={"يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"};
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tt=new LinearLayout(this); tt.setOrientation(LinearLayout.VERTICAL);
        TextView dt=muted(hc.get(Calendar.DAY_OF_MONTH)+" "+mons[hc.get(Calendar.MONTH)]); dt.setTextSize(13); tt.addView(dt);
        TextView h=title("جدولي"); h.setTextSize(32); tt.addView(h);
        top.addView(tt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView edit=primaryBtn("عدل خطتي"); edit.setTextSize(15);
        edit.setOnClickListener(v->{requestExactAlarmIfNeeded(); if(planner.tasks.isEmpty()) Toast.makeText(this,"ضيف مهام الأول من تبويب المهام",Toast.LENGTH_SHORT).show(); else askPlanDaysAndBuild();});
        top.addView(edit,new LinearLayout.LayoutParams(dp(118),dp(48)));
        top.addView(space(dp(6)));
        TextView more=ghostBtn("•••"); more.setOnClickListener(v->showScheduleMoreMenu(v));
        top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
        box.addView(top); box.addView(space(dp(10)));

        // تبويبات الجدول: ثلاث اختيارات واضحة في نصف الشاشة الأيمن.
        LinearLayout tabsWrap=new LinearLayout(this);
        tabsWrap.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tabsWrap.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView d=scheduleModeTab("اليوم",scheduleViewMode==0);
        TextView w=scheduleModeTab("الأسبوع",scheduleViewMode==1);
        TextView cu=scheduleModeTab("مخصص",scheduleViewMode==2);
        d.setOnClickListener(v->{scheduleViewMode=0;weekView=false;showTab(0);});
        w.setOnClickListener(v->{scheduleViewMode=1;weekView=true;showTab(6);});
        cu.setOnClickListener(v->{scheduleViewMode=2;weekView=true;showTab(6);});
        tabs.addView(d,scheduleModeTabLp());
        tabs.addView(w,scheduleModeTabLp());
        tabs.addView(cu,scheduleModeTabLp());
        int halfScreen=Math.max(dp(210), getResources().getDisplayMetrics().widthPixels/2);
        tabsWrap.addView(tabs,new LinearLayout.LayoutParams(halfScreen,dp(44)));
        box.addView(tabsWrap);
        box.addView(space(dp(16)));

        if(scheduleViewMode==0){
            List<Planner.Session> today=planner.sessionsForDay(Planner.todayStr());
            addSchedulePeriodHeader(box,"",Planner.todayStr(),today);
            addScheduleDayBlock(box,Planner.todayStr(),true);
        } else {
            Calendar c=Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
            if(scheduleViewMode==1){int diff=c.get(Calendar.DAY_OF_WEEK)-Calendar.SATURDAY;if(diff<0)diff+=7;c.add(Calendar.DAY_OF_YEAR,-diff);}
            int days=scheduleViewMode==1?7:Math.max(1,planner.settings.planDays);
            Calendar start=(Calendar)c.clone();
            List<Planner.Session> periodSessions=new ArrayList<>();
            boolean any=false;
            for(int k=0;k<days;k++){
                String day=String.format(Locale.US,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));
                List<Planner.Session> dl=planner.sessionsForDay(day);
                if(!dl.isEmpty())any=true;
                periodSessions.addAll(dl);
                c.add(Calendar.DAY_OF_YEAR,1);
            }
            String periodTitle="";
            Calendar end=(Calendar)c.clone(); end.add(Calendar.DAY_OF_YEAR,-1);
            String periodDate=String.format(Locale.US,"%02d/%02d → %02d/%02d",
                    start.get(Calendar.DAY_OF_MONTH),start.get(Calendar.MONTH)+1,
                    end.get(Calendar.DAY_OF_MONTH),end.get(Calendar.MONTH)+1);
            addSchedulePeriodHeader(box,periodTitle,periodDate,periodSessions);
            c=(Calendar)start.clone();
            for(int k=0;k<days;k++){
                String day=String.format(Locale.US,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));
                if(scheduleViewMode==1&&weekSelectedDow>0&&c.get(Calendar.DAY_OF_WEEK)!=weekSelectedDow){c.add(Calendar.DAY_OF_YEAR,1);continue;}
                addScheduleDayBlock(box,day,false);
                c.add(Calendar.DAY_OF_YEAR,1);
            }
            if(!any&&planner.sessions.isEmpty())box.addView(emptyState("مفيش خطة لسه.\nضيف مهام واعمل خطتك."));
        }
        return sc;
    }

    private void addSchedulePeriodHeader(LinearLayout box, String titleText, String dateText, List<Planner.Session> sessions) {
        int total=sessions==null?0:sessions.size(), done=0, totalMin=0, doneMin=0;
        if(sessions!=null) for(Planner.Session s:sessions){
            if(s==null) continue;
            totalMin+=Math.max(0,s.durationMin);
            if(s.done){done++;doneMin+=Math.max(0,s.durationMin);}
        }
        float pct=total==0?0f:(float)done/(float)total;

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0,dp(2),0,dp(2));

        DayGaugeView gauge=new DayGaugeView(this);
        gauge.setProgress(pct);
        head.addView(gauge,new LinearLayout.LayoutParams(dp(64),dp(64)));

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        info.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView summary=new TextView(this);
        summary.setText(done+" من "+total+" محاضرات");
        summary.setTextColor(TEXT);
        summary.setTextSize(19);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setGravity(Gravity.RIGHT);
        info.addView(summary);

        int remain=Math.max(0,totalMin-doneMin);
        TextView remainTv=muted("فاضل "+formatStudyDuration(remain));
        remainTv.setTextSize(12.5f);
        remainTv.setTextColor(MUTED);
        remainTv.setGravity(Gravity.RIGHT);
        info.addView(remainTv);

        if(dateText!=null&&!dateText.isEmpty()&&!dateText.equals(Planner.todayStr())){
            TextView range=muted(dateText);
            range.setTextSize(11.5f);
            range.setTextColor(MUTED2);
            range.setGravity(Gravity.RIGHT);
            info.addView(range);
        }
        head.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        box.addView(head);
        box.addView(space(dp(10)));
    }

    private String formatStudyDuration(int min) {
        int h=min/60, m=min%60;
        if(h>0 && m>0) return h+" ساعات و "+m+" دقيقة";
        if(h>0) return h+" ساعات";
        return m+" دقيقة";
    }

    private void addScheduleDayBlock(LinearLayout box, String day, boolean showMainCard) {
        if (box == null || day == null) return;
        List<Planner.Session> list = planner.sessionsForDay(day);
        boolean isToday = day.equals(Planner.todayStr());

        if (!showMainCard) {
            LinearLayout dayHead=new LinearLayout(this);
            dayHead.setOrientation(LinearLayout.HORIZONTAL);
            dayHead.setGravity(Gravity.CENTER_VERTICAL);
            TextView dayTitle=new TextView(this);
            dayTitle.setText(Planner.dayLabelAr(day));
            dayTitle.setTextColor(TEXT);
            dayTitle.setTextSize(18);
            dayTitle.setTypeface(Typeface.DEFAULT_BOLD);
            dayHead.addView(dayTitle,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            dayHead.addView(muted(day),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(dayHead);
            box.addView(space(dp(8)));
        } else {
            // كارت الجلسة الحالية لليوم فقط — الدائرة أعلاه هي دائرة الفترة كلها.
            Planner.Session current=null,next=null;
            int nowM=Planner.nowMinOfDay();
            for(Planner.Session s:list) if(s!=null&&!s.done){
                int end=s.endMin<=s.startMin?s.endMin+24*60:s.endMin;
                if(s.startMin<=nowM&&nowM<end) current=s;
                else if(s.startMin>nowM&&next==null) next=s;
            }
            if(current==null) for(Planner.Session s:list) if(s!=null&&!s.done){next=s;break;}
            final Planner.Session currentS=current, nextS=next;
            LinearLayout nowCard=card();
            nowCard.setOrientation(LinearLayout.VERTICAL);
            nowCard.setPadding(dp(16),dp(14),dp(16),dp(14));
            GradientDrawable nowBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF243D78,0xFF5B43C9});
            nowBg.setCornerRadius(dp(20));
            nowCard.setBackground(nowBg);
            if(currentS!=null){
                TextView tag=muted("الآن");
                tag.setTextColor(0xFFE4EAFF);
                tag.setTextSize(13);
                tag.setTypeface(Typeface.DEFAULT_BOLD);
                nowCard.addView(tag);

                String subjectText=currentS.subject==null?"":currentS.subject.trim();
                String lectureText=currentS.taskName==null?"":currentS.taskName.trim();
                TextView n=new TextView(this);
                String mainLine;
                if(!subjectText.isEmpty()&&!lectureText.isEmpty()) mainLine=subjectText+" — "+lectureText;
                else mainLine=!subjectText.isEmpty()?subjectText:lectureText;
                n.setText(mainLine);
                n.setTextColor(Color.WHITE);
                n.setTextSize(22);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                n.setMaxLines(2);
                n.setGravity(Gravity.RIGHT);
                n.setPadding(0,dp(4),0,0);
                nowCard.addView(n);

                int remaining=Math.max(0,(currentS.endMin<=currentS.startMin?currentS.endMin+24*60:currentS.endMin)-nowM);
                TextView duration=muted("المدة: "+Math.max(1,currentS.durationMin)+" د · متبقي "+remaining+" د");
                duration.setTextColor(0xDFFFFFFF);
                duration.setTextSize(13);
                nowCard.addView(duration);
                nowCard.addView(space(dp(12)));

                LinearLayout actions=new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER);
                actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView startBtn=primaryBtn("ابدأ");
                startBtn.setTextSize(16);
                startBtn.setOnClickListener(v->openTimer(currentS));
                actions.addView(startBtn,new LinearLayout.LayoutParams(0,dp(46),1f));

                actions.addView(space(dp(7)));
                TextView doneBtn=ghostBtn("✓");
                doneBtn.setTextSize(20);
                doneBtn.setTextColor(Color.WHITE);
                doneBtn.setOnClickListener(v->finishSessionWithChoice(currentS));
                actions.addView(doneBtn,new LinearLayout.LayoutParams(0,dp(46),.55f));

                actions.addView(space(dp(7)));
                TextView moreBtn=ghostBtn("•••");
                moreBtn.setTextSize(18);
                moreBtn.setTextColor(Color.WHITE);
                moreBtn.setOnClickListener(v->showAdjustDurationDialog(currentS));
                actions.addView(moreBtn,new LinearLayout.LayoutParams(0,dp(46),.55f));
                nowCard.addView(actions);
            } else if(nextS!=null){
                nowCard.addView(muted("القادمة"));
                TextView n=new TextView(this);
                String subj=nextS.subject==null?"":nextS.subject.trim();
                String lecture=nextS.taskName==null?"":nextS.taskName.trim();
                n.setText((!subj.isEmpty()&&!lecture.isEmpty()?subj+" — "+lecture:(!subj.isEmpty()?subj:lecture)));
                n.setTextColor(TEXT);
                n.setTextSize(20);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                nowCard.addView(n);
                nowCard.addView(muted(nextS.timeLabel()));
                Button startBtn=primaryBtn("ابدأ");
                startBtn.setOnClickListener(v->openTimer(nextS));
                nowCard.addView(startBtn,fullBtnLp());
            } else {
                nowCard.addView(muted(list.isEmpty()?"مفيش جلسات لليوم":"خلصت جلسات اليوم ✓"));
            }
            box.addView(nowCard);
            box.addView(space(dp(10)));
            box.addView(sectionHeader("لاحقًا"));
            box.addView(space(dp(6)));
        }

        LinearLayout dayZone=new LinearLayout(this);
        dayZone.setOrientation(LinearLayout.VERTICAL);
        dayZone.setTag(day);
        if(list.isEmpty()) dayZone.addView(emptyState("مفيش جلسات في اليوم ده"));
        else {
            List<Planner.Session> ordered=new ArrayList<>(list);
            Collections.sort(ordered,(a,b)->Integer.compare(a.startMin,b.startMin));
            for(Planner.Session s:ordered){
                if(showMainCard && s!=null){
                    // الجلسة الحالية تُعرض مرة واحدة في الكارت الكبير؛ الباقي يظل قابلًا للفتح.
                    int nowM=Planner.nowMinOfDay();
                    boolean current=!s.done&&isToday&&s.startMin<=nowM&&nowM<(s.endMin<=s.startMin?s.endMin+24*60:s.endMin);
                    if(current) continue;
                }
                dayZone.addView(sessionCard(s));
            }
        }
        box.addView(dayZone);
        dayDropZones.put(day,dayZone);
        attachDayDropListener(dayZone,day);
        box.addView(space(dp(14)));
    }

    /** simple horizontal scroll container */
    private static class HorizontalScrollWrap extends android.widget.HorizontalScrollView {
        HorizontalScrollWrap(android.content.Context ctx) {
            super(ctx);
            setHorizontalScrollBarEnabled(false);
            setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View prayerCard(int[] block) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A2438);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xFF3A4A6A);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        String name = "صلاة";
        if (block != null && block.length > 2 && block[2] >= 0 && block[2] < Planner.PRAYER_NAMES_AR.length) {
            name = "صلاة " + Planner.PRAYER_NAMES_AR[block[2]];
        }
        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(0xFFB8C4E0);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        int a = block != null && block.length > 0 ? block[0] : 0;
        int b = block != null && block.length > 1 ? block[1] : a;
        TextView time = new TextView(this);
        time.setText(Planner.minToTime(a) + " → " + Planner.minToTime(b));
        time.setTextColor(MUTED);
        time.setTextSize(13);
        card.addView(space(dp(2)));
        card.addView(time);

        TextView tag = muted("Busy · مش وقت مذاكرة");
        tag.setTextSize(11);
        card.addView(space(dp(2)));
        card.addView(tag);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private View sessionCard(Planner.Session s) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        // ملخص مضغوط: اسم · مادة · وقت
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        View priDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(priorityColor(s.priority));
        priDot.setBackground(dotBg);
        titleRow.addView(priDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        titleRow.addView(space(dp(8)));

        TextView name = new TextView(this);
        name.setText(s.taskName);
        name.setTextColor(s.done ? OK : TEXT);
        name.setTextSize(15.5f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        if (s.done) name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (s.done) {
            TextView done = new TextView(this);
            done.setText("تم ✓");
            done.setTextColor(OK);
            done.setTextSize(12);
            done.setTypeface(Typeface.DEFAULT_BOLD);
            titleRow.addView(done);
        }
        summary.addView(titleRow);

        String subj = (s.subject == null || s.subject.isEmpty()) ? "—" : s.subject;
        TextView subLine = muted(subj + "  ·  " + s.durationMin + " د");
        subLine.setTextSize(13);
        summary.addView(subLine);
        TextView timeLine = new TextView(this);
        timeLine.setText(s.timeLabel());
        timeLine.setTextColor(MUTED);
        timeLine.setTextSize(13);
        timeLine.setTypeface(Typeface.DEFAULT_BOLD);
        summary.addView(timeLine);
        card.addView(summary);

        // حالة بصرية حسب حالة الجلسة
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(RADIUS));
        if (s.done) {
            cardBg.setColor(0xFF101820);
            cardBg.setStroke(dp(1), 0x553DCF8E);
        } else {
            int nowM = Planner.nowMinOfDay();
            boolean isToday = s.day != null && s.day.equals(Planner.todayStr());
            boolean current = isToday && s.startMin <= nowM && nowM < (s.endMin <= s.startMin ? s.endMin + 24 * 60 : s.endMin);
            if (current) {
                cardBg.setColor(0xFF152038);
                cardBg.setStroke(dp(2), ACCENT);
            } else {
                cardBg.setColor(CARD);
            }
        }
        card.setBackground(cardBg);

        // تفاصيل تُوسَّع عند الضغط (نفس البطاقة)
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.setPadding(0, dp(10), 0, 0);

        String pri = Planner.PRIORITY_LABELS[Math.max(0, Math.min(2, s.priority))];
        String type = s.backlog ? "قديمة" : "جديدة";
        String flags = "";
        if (s.userPinned) flags += " · مثبت";
        if (s.missed && !s.done) flags += " · فائتة";
        details.addView(muted(s.durationMin + " د · جلسة " + s.sessionIndex + "/" + s.sessionTotal
                + " · " + pri + " · " + type + flags));

        if (!s.done) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(8), 0, 0);
            TextView start = link("ابدأ");
            start.setOnClickListener(v -> openTimer(s));
            actions.addView(start);
            actions.addView(space(dp(12)));
            TextView mark = link("تم الإنجاز");
            mark.setOnClickListener(v -> finishSessionWithChoice(s));
            actions.addView(mark);
            actions.addView(space(dp(12)));
            // «نقل» أُزيل من الواجهة — التعديل عبر قلم اليوم (تأجيل بالدقائق)
            TextView dur = link("المدة");
            dur.setOnClickListener(v -> showAdjustDurationDialog(s));
            actions.addView(dur);
            if (s.missed) {
                actions.addView(space(dp(14)));
                TextView rec = link("إعادة جدولة");
                rec.setOnClickListener(v -> showMissedSessionChoices(s));
                actions.addView(rec);
            }
            details.addView(actions);
        }
        card.addView(details);

        final boolean[] open = {false};
        View.OnClickListener toggleOrEdit = v -> {
            if (dayEditMode && !s.done) {
                showPostponeSessionDialog(s);
                return;
            }
            open[0] = !open[0];
            details.setVisibility(open[0] ? View.VISIBLE : View.GONE);
        };
        summary.setOnClickListener(toggleOrEdit);
        card.setOnClickListener(toggleOrEdit);

        // Free Drag: ضغط مطوّل ثم سحب بالإصبع (خاصة الأسبوع)
        if (!s.done) {
            enableSessionFreeDrag(card, s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }


    /**
     * Free Drag مبني على Android Drag & Drop الرسمي.
     * Long-press يبدأ السحب؛ النظام يحرّك الظل مع الإصبع؛
     * مناطق الأيام تستقبل DROP وتتحقق من الصلاحية.
     */
    private void enableSessionFreeDrag(View card, Planner.Session s) {
        if (s == null || s.done) return;
        card.setLongClickable(true);
        card.setOnLongClickListener(v -> {
            try {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Exception ignored) {}
            draggingSession = s;
            draggingCardView = v;
            v.setAlpha(0.4f);
            android.content.ClipData data = android.content.ClipData.newPlainText("sessionId", s.id);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            boolean started;
            if (Build.VERSION.SDK_INT >= 24) {
                started = v.startDragAndDrop(data, shadow, s, 0);
            } else {
                started = v.startDrag(data, shadow, s, 0);
            }
            if (!started) {
                v.setAlpha(1f);
                draggingSession = null;
                draggingCardView = null;
            }
            return true;
        });
    }

    /** مستمع السحب لمنطقة يوم — يحدّث التمييز ويحفظ عند DROP صالح. */
    private void attachDayDropListener(View dayZone, String day) {
        dayZone.setOnDragListener((v, event) -> {
            Object local = event.getLocalState();
            Planner.Session s = null;
            if (local instanceof Planner.Session) s = (Planner.Session) local;
            else if (draggingSession != null) s = draggingSession;
            if (s == null) return false;

            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                case android.view.DragEvent.ACTION_DRAG_LOCATION: {
                    int preferred = estimateDropStartMin(v, event.getY(), s);
                    int nearest = planner.nearestValidStart(s.id, day, preferred);
                    boolean ok = nearest >= 0;
                    sessionDropDay = day;
                    sessionDropStartMin = ok ? nearest : preferred;
                    sessionDropValid = ok;
                    styleDropZone(v, ok ? 1 : 2);
                    return true;
                }
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    styleDropZone(v, 0);
                    return true;
                case android.view.DragEvent.ACTION_DROP: {
                    int preferred = estimateDropStartMin(v, event.getY(), s);
                    int nearest = planner.nearestValidStart(s.id, day, preferred);
                    styleDropZone(v, 0);
                    if (nearest < 0) {
                        Toast.makeText(this, "مكان غير صالح — الجلسة مكانها زي ما هي", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    String err = planner.moveSessionManual(s.id, day, nearest);
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    SessionAlarmScheduler.resync(this, planner);
                    Toast.makeText(this, "اتنقلت لـ " + Planner.dayLabelAr(day) + " "
                            + Planner.minToTime(nearest) + " ✓", Toast.LENGTH_SHORT).show();
                    showTab(weekView ? 6 : 0);
                    return true;
                }
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    styleDropZone(v, 0);
                    if (draggingCardView != null) draggingCardView.setAlpha(1f);
                    draggingSession = null;
                    draggingCardView = null;
                    sessionDropValid = false;
                    // لو فشل الـdrop في أي مكان، أعد رسم الشاشة بهدوء
                    if (!event.getResult() && weekView) {
                        // لا تعِد البناء إلا لو احتجنا — الجلسة لم تتغير
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    /** 0=عادي · 1=صالح · 2=غير صالح */
    private void styleDropZone(View z, int mode) {
        if (z == null) return;
        if (!(z.getBackground() instanceof GradientDrawable)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            z.setBackground(bg);
        }
        GradientDrawable bg = (GradientDrawable) z.getBackground();
        if (mode == 1) {
            bg.setStroke(dp(2), ACCENT);
            bg.setColor(0x223DDC97);
        } else if (mode == 2) {
            bg.setStroke(dp(2), DANGER);
            bg.setColor(0x22FF5252);
        } else {
            bg.setStroke(dp(1), 0x00000000);
            bg.setColor(0x00000000);
        }
    }

    private int estimateDropStartMin(View zone, float localY, Planner.Session s) {
        float h = Math.max(1f, zone.getHeight());
        float rel = Math.max(0f, Math.min(1f, localY / h));
        // نافذة تقريبية من 6:00 إلى 23:00
        return (int) (6 * 60 + rel * (17 * 60));
    }

    private void showMoveSessionDialog(Planner.Session s) {
        if (s == null || s.done) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.addView(muted(s.taskName + " · " + s.durationMin + " د"));
        form.addView(space(dp(8)));
        // أيام قادمة
        final String[] dayHold = {s.day != null ? s.day : Planner.todayStr()};
        final int[] startHold = {s.startMin};
        java.util.ArrayList<String> dayOpts = new java.util.ArrayList<>();
        java.util.ArrayList<String> dayKeys = new java.util.ArrayList<>();
        java.text.SimpleDateFormat dayFmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < 14; i++) {
            String key = dayFmt.format(cal.getTime());
            dayKeys.add(key);
            dayOpts.add(Planner.dayLabelAr(key) + (i == 0 ? " (اليوم)" : ""));
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        TextView dayTv = chip(Planner.dayLabelAr(dayHold[0]), true);
        dayTv.setOnClickListener(v -> myDialog().setItems(dayOpts.toArray(new String[0]), (d, which) -> {
            dayHold[0] = dayKeys.get(which);
            dayTv.setText(dayOpts.get(which));
        }).show());
        form.addView(label("اليوم"));
        form.addView(dayTv);
        TextView timeTv = chip(Planner.minToTime(startHold[0]), true);
        timeTv.setOnClickListener(v -> pickTime(startHold[0], m -> {
            startHold[0] = m;
            timeTv.setText(Planner.minToTime(m));
        }));
        form.addView(label("وقت البداية"));
        form.addView(timeTv);
        form.addView(space(dp(6)));
        form.addView(muted("بعد النقل هتتثبت الجلسة يدويًا ومش هيرجعها الـScheduler لمكان قديم."));
        myDialog().setTitle("نقل الجلسة")
                .setView(form)
                .setPositiveButton("نقل", (d, w) -> {
                    String err = planner.moveSessionManual(s.id, dayHold[0], startHold[0]);
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    } else {
                        SessionAlarmScheduler.resync(this, planner);
                        Toast.makeText(this, "اتنقلت واتثبتت ✓", Toast.LENGTH_SHORT).show();
                        showTab(tab == 6 ? 6 : 0);
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showAdjustDurationDialog(Planner.Session s) {
        if (s == null || s.done) return;
        // المتبقي الكلي للمحاضرة = مجموع جلساتها غير المكتملة
        int lectureLeft = 0;
        for (Planner.Session x : planner.sessions) {
            if (s.taskId != null && s.taskId.equals(x.taskId) && !x.done) {
                lectureLeft += Math.max(0, x.durationMin);
            }
        }
        if (lectureLeft < 5) lectureLeft = Math.max(5, s.durationMin);
        // الوقت المتاح حاليًا في نفس اليوم عند موضع الجلسة (من free slots بدون الجلسة نفسها)
        int slotAvail = planner.availableSlotMinutesAt(s.id, s.day, s.startMin);
        if (slotAvail < s.durationMin) slotAvail = s.durationMin;

        final int maxPick = Math.max(s.durationMin, lectureLeft);
        final int[] val = {Math.max(5, s.durationMin)};
        final int slotAvailF = slotAvail;
        SeekBar sb = new SeekBar(this);
        sb.setMax(maxPick);
        if (android.os.Build.VERSION.SDK_INT >= 26) sb.setMin(5);
        sb.setProgress(val[0]);
        TextView availLab = muted("المتاح حاليًا: " + slotAvail + " د");
        availLab.setTextColor(ACCENT);
        TextView lab = muted("المدة: " + val[0] + " د · أقصى اختيار: " + maxPick + " د (متبقي المحاضرة)");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                val[0] = Math.max(5, progress);
                String extra = val[0] > slotAvailF ? " · سيتطلب تعديل الجدول" : "";
                lab.setText("المدة: " + val[0] + " د · أقصى اختيار: " + maxPick + " د" + extra);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), dp(8));
        f.addView(availLab);
        f.addView(lab);
        f.addView(sb);
        myDialog().setTitle("تعديل مدة الجلسة")
                .setView(f)
                .setPositiveButton("تطبيق", (d, w) -> {
                    int chosen = val[0];
                    Runnable apply = () -> {
                        String err = planner.adjustSessionDuration(s.id, chosen);
                        if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                        else {
                            SessionAlarmScheduler.resync(this, planner);
                            Toast.makeText(this, "تم تعديل المدة", Toast.LENGTH_SHORT).show();
                            showTab(tab == 6 ? 6 : 0);
                        }
                    };
                    if (chosen > slotAvailF) {
                        myDialog().setTitle("تأكيد تعديل المدة")
                                .setMessage("المدة المختارة (" + chosen + " د) أكبر من المتاح حاليًا ("
                                        + slotAvailF + " د).\nسيتم تعديل/إعادة توزيع الجدول حسب القيود.\nهل تريد المتابعة؟")
                                .setPositiveButton("تأكيد", (d2, w2) -> apply.run())
                                .setNegativeButton("إلغاء", null)
                                .show();
                    } else {
                        apply.run();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showMissedSessionChoices(Planner.Session s) {
        if (s == null || s.id == null) {
            Toast.makeText(this, "الجلسة غير متاحة", Toast.LENGTH_SHORT).show();
            return;
        }
        showMissedSessionChoicesById(s.id);
    }

    /**
     * Smart Recovery بالمعرّف الحي من Planner — يتجنب مرجع Session قديم
     * ويؤجّل عرض الحوار لدورة UI التالية (بعد إغلاق أي Dialog سابق).
     */
    private void showMissedSessionChoicesById(String sessionId) {
        Runnable work = () -> {
            try {
                if (sessionId == null) {
                    Toast.makeText(this, "الجلسة غير متاحة", Toast.LENGTH_SHORT).show();
                    return;
                }
                planner.refreshMissedFlags();
                Planner.Session live = planner.findSessionById(sessionId);
                if (live == null) {
                    Toast.makeText(this, "الجلسة غير موجودة في الجدول", Toast.LENGTH_LONG).show();
                    return;
                }
                if (live.done) {
                    Toast.makeText(this, "الجلسة مكتملة بالفعل", Toast.LENGTH_SHORT).show();
                    return;
                }
                java.util.List<Planner.MissedSuggestion> list = planner.suggestMissedPlacements(live, 5);
                if (list == null || list.isEmpty()) {
                    Toast.makeText(this, "مفيش وقت متاح قريب — جرّب «إنشاء خطتي»", Toast.LENGTH_LONG).show();
                    return;
                }
                final String sid = live.id;
                String name = live.taskName != null ? live.taskName : "محاضرة";
                int rem = Math.max(1, live.remainingDurationMin());
                int executed = Math.max(0, live.executedMin);
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(8), dp(4), dp(8), dp(4));
                TextView intro = muted("فاتتك «" + name + "»"
                        + (executed > 0
                        ? ("\nاستكمال — متبقي " + rem + " د (نُفِّذ " + executed + " د)")
                        : ("\nمدتها " + rem + " دقيقة"))
                        + "\nاختار موعدًا:");
                box.addView(intro);
                box.addView(space(dp(8)));
                final java.util.List<Planner.MissedSuggestion> finalList = list;
                AlertDialog[] hold = new AlertDialog[1];
                for (int i = 0; i < finalList.size(); i++) {
                    final int idx = i;
                    Planner.MissedSuggestion ms = finalList.get(i);
                    TextView row = chip(ms.label != null ? ms.label : ("اقتراح " + (i + 1)), true);
                    row.setOnClickListener(v -> {
                        try {
                            if (hold[0] != null) hold[0].dismiss();
                        } catch (Exception ignored) {}
                        String err = planner.placeMissedSession(sid, ms.day, ms.startMin);
                        if (err != null) {
                            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                        } else {
                            SessionAlarmScheduler.resync(this, planner);
                            PlanWidgetProvider.updateAll(this);
                            Toast.makeText(this, "تم التطبيق ✓", Toast.LENGTH_SHORT).show();
                            showTab(0);
                        }
                    });
                    box.addView(row);
                    box.addView(space(dp(6)));
                }
                hold[0] = myDialog()
                        .setTitle("Smart Recovery")
                        .setView(box)
                        .setNegativeButton("لاحقاً", null)
                        .create();
                hold[0].show();
            } catch (Exception e) {
                AppInfrastructure.log(this, "Error", "SR show failed: " + e.getMessage());
                Toast.makeText(this, "تعذر عرض الاقتراحات. جرّب مرة أخرى.", Toast.LENGTH_LONG).show();
            }
        };
        if (content != null) content.post(work);
        else work.run();
    }

    /** حوار تعديل موعد جلسة: تأجيل أو أعملها بدري — وضع تعديل اليوم */
    private void showPostponeSessionDialog(Planner.Session s) {
        if (s == null || s.done) {
            Toast.makeText(this, "لا يمكن تعديل جلسة مكتملة", Toast.LENGTH_SHORT).show();
            return;
        }
        final String sid = s.id;
        final String labelTxt = (s.taskName != null ? s.taskName : "") + " · " + s.timeLabel();
        myDialog().setTitle("تعديل موعد المحاضرة")
                .setMessage(labelTxt + "\n\nاختار:")
                .setPositiveButton("تأجيل", (d, w) -> content.post(() -> showShiftMinutesDialog(sid, labelTxt, false)))
                .setNeutralButton("أعملها بدري", (d, w) -> content.post(() -> showShiftMinutesDialog(sid, labelTxt, true)))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /**
     * @param earlier true = تقديم (أعملها بدري)، false = تأجيل
     */
    private void showShiftMinutesDialog(String sessionId, String labelTxt, boolean earlier) {
        Planner.Session s = planner.findSessionById(sessionId);
        if (s == null || s.done) {
            Toast.makeText(this, "الجلسة غير متاحة", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.addView(muted(labelTxt));
        form.addView(space(dp(10)));
        form.addView(label(earlier ? "تقديم المحاضرة (بالدقائق قبل الموعد)" : "تأجيل المحاضرة (بالدقائق)"));
        EditText input = field();
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(earlier ? "مثال: 120 (ساعتين بدري)" : "مثال: 37");
        form.addView(input);
        form.addView(space(dp(6)));
        form.addView(muted(earlier
                ? "هتتنقل لوقت أبكر في نفس اليوم مع احترام الالتزام والنوم."
                : "الجلسات التالية هتتزحلق تلقائيًا مع احترام الالتزام والنوم."));
        myDialog().setTitle(earlier ? "أعملها بدري" : "تأجيل المحاضرة")
                .setView(form)
                .setPositiveButton("تأكيد", (d, w) -> {
                    String t = input.getText() != null ? input.getText().toString().trim() : "";
                    int mins;
                    try {
                        mins = Integer.parseInt(t);
                    } catch (Exception e) {
                        Toast.makeText(this, "أدخل رقمًا صحيحًا", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String err = earlier
                            ? planner.advanceSessionInDay(sessionId, mins)
                            : planner.postponeSessionInDay(sessionId, mins);
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    } else {
                        SessionAlarmScheduler.resync(this, planner);
                        Toast.makeText(this, earlier ? "تم التقديم ✓" : "تم التأجيل ✓", Toast.LENGTH_SHORT).show();
                        showTab(0);
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ═══════════════ Timer ═══════════════

    /** إنهاء جلسة: لو بدري يعرض إعادة جدولة اليوم / إبقاء كما هو — اليوم فقط */
    private void finishSessionWithChoice(Planner.Session s) {
        if (s == null) return;
        int now = Planner.nowMinOfDay();
        boolean early = !s.done && s.day != null && s.day.equals(Planner.todayStr())
                && now < s.endMin;
        if (timerSessionId != null && s.id != null && timerSessionId.equals(s.id) && timerLeftMs > 30_000L) {
            early = true;
        }
        Runnable markOnly = () -> {
            planner.markSessionDone(s.id, true);
            SessionAlarmScheduler.resync(this, planner);
            timerSessionId = null;
            if (timer != null) { timer.cancel(); timer = null; }
            timerRunning = false;
            Toast.makeText(this, "تم إنهاء الجلسة ✓", Toast.LENGTH_SHORT).show();
            if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
            restoreScheduleView();
        };
        Runnable markReflow = () -> {
            planner.markSessionDone(s.id, true);
            if (s.day != null) {
                planner.reflowTodayAfterEarlyFinish(s.day, Planner.nowMinOfDay());
            }
            SessionAlarmScheduler.resync(this, planner);
            timerSessionId = null;
            if (timer != null) { timer.cancel(); timer = null; }
            timerRunning = false;
            Toast.makeText(this, "تم إنهاء الجلسة ✓ · اتعملت إعادة جدولة لليوم فقط", Toast.LENGTH_SHORT).show();
            if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
            restoreScheduleView();
        };
        if (!early) {
            markOnly.run();
            return;
        }
        myDialog()
                .setTitle("خلّصت بدري")
                .setMessage("عايز تعمل إيه في باقي جدول النهاردة؟\n(التغيير على اليوم ده بس)")
                .setPositiveButton("جدولة اليوم", (d, w) -> markReflow.run())
                .setNeutralButton("إبقاء كما هو", (d, w) -> markOnly.run())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void openTimerWithDuration(Planner.Session s, int durationMin) {
        int d = Math.max(1, durationMin);
        if (timer != null && timerSessionId != null && !timerSessionId.equals(s.id)) {
            timer.cancel();
            timer = null;
            timerRunning = false;
        }
        timerSessionId = s.id;
        timerTotalMs = d * 60L * 1000L;
        timerLeftMs = timerTotalMs;
        timerPaused = false;
        timerRunning = false;
        openTimer(s); // يعيد استخدام نفس الـUI؛ المدة مضبوطة أعلاه
    }

    private void openTimer(Planner.Session s) {
        if (timer != null && timerSessionId != null && !timerSessionId.equals(s.id)) {
            timer.cancel();
            timer = null;
            timerRunning = false;
        }
        int remMin = Math.max(1, s.remainingDurationMin());
        if (timerSessionId == null || !timerSessionId.equals(s.id) || timerLeftMs <= 0) {
            timerSessionId = s.id;
            timerTotalMs = remMin * 60L * 1000L;
            timerLeftMs = timerTotalMs;
            timerPaused = false;
            timerRunning = false;
        }

        if (timerDialog != null && timerDialog.isShowing()) {
            timerDialog.dismiss();
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(26), dp(24), dp(20));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0xFF10151F);
        boxBg.setCornerRadius(dp(16));
        boxBg.setStroke(dp(1), 0xFF2A3344);
        box.setBackground(boxBg);

        TextView focusLab = new TextView(this);
        focusLab.setText(s.executedMin > 0 ? "استكمال الجلسة" : "جلسة تركيز");
        focusLab.setGravity(Gravity.CENTER);
        focusLab.setTextColor(ACCENT);
        focusLab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        focusLab.setLetterSpacing(0.08f);
        box.addView(focusLab);
        box.addView(space(dp(10)));

        TextView title = new TextView(this);
        title.setText(s.taskName != null ? s.taskName : "");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        String subTxt = (s.subject != null ? s.subject : "")
                + "  ·  جلسة " + s.sessionIndex + "/" + s.sessionTotal
                + (s.executedMin > 0 ? ("  ·  متبقي " + remMin + " د") : "");
        TextView sub = muted(subTxt.trim());
        sub.setGravity(Gravity.CENTER);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        box.addView(sub);
        box.addView(space(dp(22)));

        FrameLayout ringWrap = new FrameLayout(this);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(236), dp(236));
        ringLp.gravity = Gravity.CENTER_HORIZONTAL;
        ringWrap.setLayoutParams(ringLp);
        timerRing = new CircularProgressView(this);
        timerRing.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        timerClockView = new TextView(this);
        timerClockView.setTextColor(0xFFFFFFFF);
        timerClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        timerClockView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        timerClockView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ringWrap.addView(timerRing);
        ringWrap.addView(timerClockView, clp);
        box.addView(ringWrap);
        box.addView(space(dp(22)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        timerPauseBtn = primaryBtn(timerPaused || !timerRunning ? "بدء" : "إيقاف");
        Button resetBtn = ghostBtn("إعادة");
        Button finishBtn = ghostBtn("إنهاء");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        row.addView(timerPauseBtn, bp);
        row.addView(space(dp(10)));
        row.addView(resetBtn, bp);
        row.addView(space(dp(10)));
        row.addView(finishBtn, bp);
        box.addView(row);

        updateTimerUi();

        AlertDialog.Builder b = myDialog();
        timerDialog = b.setView(box).setCancelable(true).create();
        timerDialog.setOnShowListener(d -> {
            try {
                if (timerDialog.getWindow() != null) {
                    timerDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }
            } catch (Exception ignored) {}
        });

        timerPauseBtn.setOnClickListener(v -> {
            if (!timerRunning || timerPaused) {
                startTimerCountdown(s);
            } else {
                if (timer != null) timer.cancel();
                timerPaused = true;
                timerRunning = false;
                persistTimerPartial(s);
                timerPauseBtn.setText("استئناف");
            }
        });
        resetBtn.setOnClickListener(v -> {
            if (timer != null) timer.cancel();
            timerLeftMs = timerTotalMs;
            timerPaused = false;
            timerRunning = false;
            timerPauseBtn.setText("بدء");
            updateTimerUi();
        });
        finishBtn.setOnClickListener(v -> {
            if (timer != null) timer.cancel();
            timerRunning = false;
            if (extraTaskId != null && extraTaskId.equals(s.taskId)) {
                long elapsed = System.currentTimeMillis() - extraStartedMs;
                int studied = (int) Math.max(1, Math.min(extraDurationMin, (elapsed + 59999) / 60000));
                // لو خلّص التايمر كامل استخدم المدة المطلوبة
                if (timerLeftMs <= 0) studied = extraDurationMin;
                else studied = Math.max(1, extraDurationMin - (int) ((timerLeftMs + 59999) / 60000));
                planner.applyExtraSameTask(extraTaskId, studied);
                extraTaskId = null;
                SessionAlarmScheduler.resync(this, planner);
                timerSessionId = null;
                timerDialog.dismiss();
                Toast.makeText(this, "إضافي: +" + studied + " د لنفس المحاضرة", Toast.LENGTH_SHORT).show();
                restoreScheduleView();
                return;
            }
            // خيار: إعادة جدولة اليوم أو إبقاء كما هو (اليوم فقط)
            finishSessionWithChoice(s);
        });

        timerDialog.setOnDismissListener(d -> {
            persistTimerPartial(s);
            timerDialog = null;
            timerClockView = null;
            timerRing = null;
            timerPauseBtn = null;
        });
        timerDialog.show();

        if (timerRunning && !timerPaused) {
            startTimerCountdown(s);
        }
    }

    private void startTimerCountdown(Planner.Session s) {
        if (timer != null) timer.cancel();
        if (timerLeftMs <= 0) {
            timerLeftMs = timerTotalMs;
        }
        timerPaused = false;
        timerRunning = true;
        if (timerPauseBtn != null) timerPauseBtn.setText("إيقاف");
        timer = new CountDownTimer(timerLeftMs, 200) {
            @Override public void onTick(long ms) {
                timerLeftMs = ms;
                updateTimerUi();
            }
            @Override public void onFinish() {
                timerLeftMs = 0;
                timerRunning = false;
                updateTimerUi();
                if (extraTaskId != null && extraTaskId.equals(s.taskId)) {
                    planner.applyExtraSameTask(extraTaskId, extraDurationMin);
                    extraTaskId = null;
                    SessionAlarmScheduler.resync(MainActivity.this, planner);
                    timerSessionId = null;
                    Toast.makeText(MainActivity.this, "إضافي اكتمل لنفس المحاضرة ✓", Toast.LENGTH_LONG).show();
                    if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
                    showTab(0);
                    return;
                }
                planner.markSessionDone(s.id, true);
                planner.reflowTodayAfterEarlyFinish(s.day, Planner.nowMinOfDay());
                SessionAlarmScheduler.resync(MainActivity.this, planner);
                timerSessionId = null;
                Toast.makeText(MainActivity.this, "انتهى الوقت! ✓", Toast.LENGTH_LONG).show();
                if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
                restoreScheduleView();
            }
        }.start();
    }

    private void updateTimerUi() {
        if (timerClockView != null) timerClockView.setText(formatMs(timerLeftMs));
        if (timerRing != null && timerTotalMs > 0) {
            float p = (float) timerLeftMs / (float) timerTotalMs;
            timerRing.setProgress(p);
        }
    }

    private String formatMs(long ms) {
        long total = Math.max(0, ms / 1000);
        long m = total / 60;
        long s = total % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    /** Circular progress ring for timer */
    private class CircularProgressView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 1f;
        private final RectF oval = new RectF();

        CircularProgressView(android.content.Context ctx) {
            super(ctx);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(dp(10));
            bgPaint.setColor(0xFF2A3142);
            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeWidth(dp(10));
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setColor(ACCENT);
        }

        void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float pad = dp(12);
            oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawArc(oval, 0, 360, false, bgPaint);
            canvas.drawArc(oval, -90, 360f * progress, false, fgPaint);
        }
    }

    /** عداد إنجاز اليوم — Gauge تقني؛ اللون حسب نسبة الإنجاز فقط */
    private class DayGaugeView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;
        private final RectF oval = new RectF();

        DayGaugeView(android.content.Context ctx) {
            super(ctx);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(dp(11));
            bgPaint.setColor(0xFF252B3A);
            bgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeWidth(dp(11));
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setColor(gaugeColorFor(0f));
        }

        private int gaugeColorFor(float p) {
            int pct = Math.round(Math.max(0f, Math.min(1f, p)) * 100f);
            if (pct <= 24) return 0xFFE25563;      // أحمر
            if (pct <= 49) return 0xFFF0883A;      // برتقالي
            if (pct <= 74) return 0xFFE6B84D;      // أصفر
            if (pct <= 89) return 0xFF7DCF6E;      // أخضر فاتح
            return 0xFF3DCF8E;                     // أخضر قوي
        }

        void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            fgPaint.setColor(gaugeColorFor(progress));
            invalidate();
        }

        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        {
            textPaint.setColor(TEXT);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float pad = dp(12);
            oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
            float start = 135f;
            float sweep = 270f;
            canvas.drawArc(oval, start, sweep, false, bgPaint);
            canvas.drawArc(oval, start, sweep * progress, false, fgPaint);
            // النسبة في منتصف العداد
            int pct = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
            String label = pct + "%";
            textPaint.setTextSize(dp(18));
            textPaint.setColor(TEXT);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, cx, cy, textPaint);
        }
    }

    private void ensureAlertPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
            }
        }
        SessionAlarmReceiver.ensureChannel(this);
    }

    private void requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }
    }

    /** العودة لنفس عرض الجدول الحالي (يوم / أسبوع / مخصص) دون فرض وضع آخر. */
    private void restoreScheduleView() {
        if (scheduleViewMode == 1) {
            weekView = true;
            showTab(6);
        } else if (scheduleViewMode == 2) {
            weekView = true;
            showTab(6);
        } else {
            weekView = false;
            scheduleViewMode = 0;
            showTab(0);
        }
    }


    /**
     * قبل البناء: مجموعة تعادل (نفس الأولوية + طبقة امتحان) → Dialog ترتيب مؤقت لهذه العملية فقط.
     */
    private void resolveLectureTiesThenBuild(int mode, int days, String until) {
        resolveLectureTiesThenBuild(mode, days, until, true);
    }

    private void resolveLectureTiesThenBuild(int mode, int days, String until, boolean topLevel) {
        if (topLevel) planner.clearPlanSessionTieState();
        List<Planner.Task> group = planner.findFirstScheduleTieGroup();
        if (group == null || group.size() < 2) {
            showScheduleResult(planner.buildSchedule(mode, days, until));
            return;
        }
        showScheduleTieOrderDialog(group, mode, days, until);
    }

    /**
     * Dialog ترتيب مجموعة متعادلة — ترتيب العملية الحالية فقط (لا يُحفظ على Task).
     * ضغط المهمة = اختيار في الترتيب. «أي ترتيب» = إكمال بالعوامل الطبيعية.
     */
    private void showScheduleTieOrderDialog(List<Planner.Task> group, int mode, int days, String until) {
        final List<Planner.Task> remaining = new ArrayList<>(group);
        final List<Planner.Task> chosen = new ArrayList<>();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView msg = new TextView(this);
        msg.setTextColor(TEXT);
        msg.setTextSize(14f);
        if (group.size() == 2) {
            msg.setText("مهمتان متساويتان في أولوية الجدولة.\n"
                    + "اضغط المهمة التي تريدها أولًا، أو «أي ترتيب».");
        } else {
            msg.setText("يوجد " + group.size() + " مهام متساوية في أولوية الجدولة.\n"
                    + "اضغط المهام بالترتيب المطلوب (الأولى أولًا)، أو «أي ترتيب».");
        }
        root.addView(msg);

        TextView orderTv = new TextView(this);
        orderTv.setTextColor(ACCENT);
        orderTv.setTextSize(15f);
        orderTv.setTypeface(Typeface.DEFAULT_BOLD);
        orderTv.setPadding(0, dp(12), 0, dp(8));
        orderTv.setText("الترتيب: (لم يُختر بعد)");
        root.addView(orderTv);

        LinearLayout picks = new LinearLayout(this);
        picks.setOrientation(LinearLayout.VERTICAL);
        root.addView(picks);

        Button anyBtn = primaryBtn("أي ترتيب");
        LinearLayout.LayoutParams anyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        anyLp.topMargin = dp(12);
        anyBtn.setLayoutParams(anyLp);
        root.addView(anyBtn);

        final AlertDialog[] dlgHolder = new AlertDialog[1];

        Runnable applyChosen = () -> {
            if (dlgHolder[0] != null) dlgHolder[0].dismiss();
            planner.setPlanSessionOrder(new ArrayList<>(chosen));
            planner.markPlanSessionGroupResolved(group);
            resolveLectureTiesThenBuild(mode, days, until, false);
        };

        Runnable refreshPicks = new Runnable() {
            @Override public void run() {
                picks.removeAllViews();
                StringBuilder ob = new StringBuilder("الترتيب: ");
                if (chosen.isEmpty()) {
                    ob.append("(لم يُختر بعد)");
                } else {
                    for (int i = 0; i < chosen.size(); i++) {
                        if (i > 0) ob.append(" → ");
                        Planner.Task t = chosen.get(i);
                        String n = t.name == null || t.name.trim().isEmpty() ? ("مهمة " + (i + 1)) : t.name.trim();
                        ob.append(n);
                    }
                    if (!remaining.isEmpty()) ob.append(" → …");
                }
                orderTv.setText(ob.toString());

                for (Planner.Task t : remaining) {
                    String label = t.name == null || t.name.trim().isEmpty() ? "مهمة" : t.name.trim();
                    if (t.subject != null && !t.subject.isEmpty()) label = label + " (" + t.subject + ")";
                    Button b = primaryBtn(label);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(6);
                    b.setLayoutParams(lp);
                    final Planner.Task task = t;
                    b.setOnClickListener(v -> {
                        remaining.remove(task);
                        chosen.add(task);
                        if (remaining.size() == 1) {
                            chosen.add(remaining.remove(0));
                        }
                        if (remaining.isEmpty()) {
                            applyChosen.run();
                        } else {
                            run();
                        }
                    });
                    picks.addView(b);
                }
            }
        };
        refreshPicks.run();

        anyBtn.setOnClickListener(v -> {
            if (dlgHolder[0] != null) dlgHolder[0].dismiss();
            planner.markPlanSessionGroupResolved(group);
            resolveLectureTiesThenBuild(mode, days, until, false);
        });

        AlertDialog dlg = myDialog()
                .setTitle("ترتيب مهام متشابهة")
                .setView(root)
                .setCancelable(false)
                .create();
        dlgHolder[0] = dlg;
        dlg.show();
    }

    private void showScheduleResult(String msg) {
        if (msg != null && (msg.contains("تعارض") || msg.length() > 60)) {
            myDialog().setTitle("نتيجة الجدول").setMessage(msg)
                    .setPositiveButton("تمام", (d, w) -> restoreScheduleView()).show();
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            restoreScheduleView();
        }
    }





    private String resolveLocationName(double lat, double lng) {
        try {
            if (Geocoder.isPresent()) {
                Geocoder g = new Geocoder(this, new Locale("ar"));
                java.util.List<android.location.Address> list = g.getFromLocation(lat, lng, 1);
                if (list != null && !list.isEmpty()) {
                    android.location.Address a = list.get(0);
                    String city = a.getLocality();
                    if (city == null || city.isEmpty()) city = a.getSubAdminArea();
                    if (city == null || city.isEmpty()) city = a.getAdminArea();
                    if (city != null && !city.isEmpty()) {
                        String country = a.getCountryName();
                        if (country != null && !country.isEmpty() && !city.contains(country))
                            return city + "، " + country;
                        return city;
                    }
                }
            }
        } catch (Exception ignored) {}
        double[][] cities = {
                {30.0444, 31.2357}, {31.2001, 29.9187}, {30.0131, 31.1801},
                {30.7885, 31.0019}, {26.5591, 31.6956}, {25.6872, 32.6396},
                {24.0889, 32.8998}, {31.0409, 31.3785}, {30.8418, 30.5135},
                {29.3084, 30.8428}, {31.5084, 31.7400}, {27.1800, 31.1837}
        };
        String[] names = {"القاهرة", "الإسكندرية", "الجيزة", "المنصورة", "سوهاج",
                "الأقصر", "أسوان", "دمياط", "طنطا", "الفيوم", "بورسعيد", "أسيوط"};
        int best = 0;
        double bestD = 1e9;
        for (int i = 0; i < cities.length; i++) {
            double dlat = cities[i][0] - lat;
            double dlng = cities[i][1] - lng;
            double d = dlat * dlat + dlng * dlng;
            if (d < bestD) { bestD = d; best = i; }
        }
        if (bestD < 0.25) return names[best];
        return names[best] + " (تقريبي)";
    }

    private void requestPhoneLocation() {
        if (Build.VERSION.SDK_INT >= 23) {
            boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!fine && !coarse) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
                return;
            }
        }
        applyPhoneLocation();
    }

    private void applyPhoneLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) {
                Toast.makeText(this, "خدمة الموقع غير متاحة", Toast.LENGTH_SHORT).show();
                return;
            }
            Location loc = null;
            try {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            } catch (SecurityException ignored) {}
            if (loc == null) {
                try {
                    loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } catch (SecurityException ignored) {}
            }
            if (loc == null) {
                try {
                    loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                } catch (SecurityException ignored) {}
            }
            if (loc == null) {
                planner.settings.hasLocation = false;
                planner.save();
                Toast.makeText(this, "تعذّر قراءة الموقع. تأكد أن الموقع مفعّل على الجهاز.", Toast.LENGTH_LONG).show();
                return;
            }
            planner.settings.latitude = loc.getLatitude();
            planner.settings.longitude = loc.getLongitude();
            planner.settings.hasLocation = true;
            planner.settings.locationName = resolveLocationName(loc.getLatitude(), loc.getLongitude());
            planner.save();
            String shown = (planner.settings.locationName != null && !planner.settings.locationName.isEmpty())
                    ? planner.settings.locationName : "موقع محفوظ";
            Toast.makeText(this, "تم حفظ الموقع: " + shown, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            planner.settings.hasLocation = false;
            planner.save();
            Toast.makeText(this, "خطأ في قراءة الموقع", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false;
            if (grantResults != null) {
                for (int g : grantResults) {
                    if (g == PackageManager.PERMISSION_GRANTED) { ok = true; break; }
                }
            }
            if (ok) {
                applyPhoneLocation();
                showTab(4);
            } else {
                planner.settings.hasLocation = false;
                planner.save();
                Toast.makeText(this, "تم رفض إذن الموقع. لن تُحسب مواقيت الصلاة بدون موقع.", Toast.LENGTH_LONG).show();
                showTab(4);
            }
        }
    }

    private void showPrayerSettingsDialog() {
        final boolean[] en = {planner.settings.prayersEnabled};
        final int[] val = {planner.settings.prayerDurationMin};

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(12), dp(20), dp(8));
        f.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Switch sw = new Switch(this);
        sw.setText("تفعيل مواقيت الصلاة (Busy Blocks)");
        sw.setChecked(en[0]);
        sw.setOnCheckedChangeListener((b, c) -> en[0] = c);
        f.addView(sw);
        f.addView(space(dp(10)));

        f.addView(label("الموقع من الهاتف"));
        if (planner.settings.hasLocation) {
            String nm = planner.settings.locationName;
            if (nm == null || nm.isEmpty()) {
                nm = resolveLocationName(planner.settings.latitude, planner.settings.longitude);
                planner.settings.locationName = nm;
            }
            TextView locTv = new TextView(this);
            locTv.setTextColor(TEXT);
            locTv.setTextSize(15);
            locTv.setTypeface(Typeface.DEFAULT_BOLD);
            locTv.setText(nm);
            f.addView(locTv);
        } else {
            f.addView(muted("الموقع غير متاح — لم يتم منح الإذن أو تعذّرت القراءة."));
        }
        f.addView(space(dp(6)));
        TextView locBtn = chip("استخدم موقعي الحالي", true);
        locBtn.setOnClickListener(v -> requestPhoneLocation());
        f.addView(locBtn);
        f.addView(space(dp(10)));

        f.addView(label("توقيت مصر"));
        LinearLayout tzRow = new LinearLayout(this);
        tzRow.setOrientation(LinearLayout.HORIZONTAL);
        final boolean[] summer = {planner.settings.egyptSummerTime};
        TextView summerChip = chip("صيفي (+3)", summer[0]);
        TextView winterChip = chip("شتوي (+2)", !summer[0]);
        summerChip.setOnClickListener(v2 -> {
            summer[0] = true;
            styleChip(summerChip, true);
            styleChip(winterChip, false);
        });
        winterChip.setOnClickListener(v2 -> {
            summer[0] = false;
            styleChip(summerChip, false);
            styleChip(winterChip, true);
        });
        tzRow.addView(summerChip, chipLp());
        tzRow.addView(space(dp(8)));
        tzRow.addView(winterChip, chipLp());
        f.addView(tzRow);
        f.addView(space(dp(6)));
        f.addView(muted("الصيفي = +3. لو المواقيت متقدمة ساعة اختار شتوي (+2)."));
        f.addView(space(dp(10)));

        f.addView(label("مواقيت اليوم (فلكيًا)"));
        if (planner.settings.hasLocation && planner.settings.prayersEnabled) {
            java.util.List<int[]> blocks = planner.prayerBlocksToday();
            if (blocks.isEmpty()) {
                f.addView(muted("تعذّر حساب المواقيت."));
            } else {
                for (int[] b : blocks) {
                    String name = (b[2] >= 0 && b[2] < Planner.PRAYER_NAMES_AR.length)
                            ? Planner.PRAYER_NAMES_AR[b[2]] : "صلاة";
                    TextView row = new TextView(this);
                    row.setTextColor(TEXT);
                    row.setTextSize(14);
                    row.setText("صلاة " + name + "  ·  " + Planner.minToTime(b[0]) + " → " + Planner.minToTime(b[1]));
                    f.addView(row);
                    f.addView(space(dp(4)));
                }
            }
        } else if (!planner.settings.hasLocation) {
            f.addView(muted("فعّل الموقع أولًا لعرض المواقيت."));
        } else {
            f.addView(muted("الصلاة متوقفة — لن تظهر مواقيت."));
        }
        f.addView(space(dp(10)));

        f.addView(label("مدة كل صلاة (Busy)"));
        SeekBar sb = new SeekBar(this);
        sb.setMax(115);
        sb.setProgress(Math.max(0, val[0] - 5));
        TextView lab = muted(val[0] + " دقيقة");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                val[0] = p + 5;
                lab.setText(val[0] + " دقيقة");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        f.addView(lab);
        f.addView(sb);

        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        myDialog().setTitle("إعدادات الصلاة")
                .setView(sv)
                .setPositiveButton("حفظ", (d, w) -> {
                    planner.settings.prayersEnabled = en[0];
                    planner.settings.prayerDurationMin = Math.max(5, Math.min(120, val[0]));
                    planner.settings.egyptSummerTime = summer[0];
                    if (en[0] && !planner.settings.hasLocation) {
                        Toast.makeText(this, "فعّلت الصلاة لكن مفيش موقع. اضغط «استخدم موقعي».", Toast.LENGTH_LONG).show();
                    }
                    planner.save();
                    Toast.makeText(this, "تم حفظ إعدادات الصلاة", Toast.LENGTH_SHORT).show();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }


    private void showScheduleMoreMenu(View anchor) {
        String[] items = {
                "إضافي",
                "يوم بدون محاضرات",
                "حذف الجدول"
        };
        showFloatingChoices(anchor, items, -1, idx -> {
            if (idx == 0) showExtraDialog();
            else if (idx == 1) showNoLectureDayDialog();
            else if (idx == 2) {
                myDialog()
                        .setTitle("حذف الجدول")
                        .setMessage("هيتحذف الجدول المُولَّد فقط.\nالمهام والامتحانات والمواد والإعدادات مش هتتأثر.")
                        .setPositiveButton("حذف", (d, w) -> {
                            planner.clearGeneratedSchedule();
                            SessionAlarmScheduler.resync(this, planner);
                            Toast.makeText(this, "اتحذف الجدول · المهام لسه موجودة", Toast.LENGTH_SHORT).show();
                            showTab(0);
                        })
                        .setNegativeButton("إلغاء", null)
                        .show();
            }
        });
    }

    private void showExtraDialog() {
        // آخر محاضرة ظهرت النهاردة ولسه لها متبقي (حتى لو باقي الجلسات بكرة)
        Planner.Session cur = planner.findExtraTargetSession();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (cur == null) {
            form.addView(muted("مفيش محاضرة ظهرت النهاردة ولسه متبقية.\n«إضافي» يكمل نفس المحاضرة فقط — مش بيفتح محاضرة جديدة."));
            myDialog().setTitle("إضافي").setView(form)
                    .setPositiveButton("تمام", null).show();
            return;
        }

        Planner.Task t = planner.findTask(cur.taskId);
        int rem = planner.taskRemainingMinutes(t);
        if (rem < 1) {
            form.addView(muted("المحاضرة دي خلصت."));
            myDialog().setTitle("إضافي").setView(form)
                    .setPositiveButton("تمام", null).show();
            return;
        }

        int maxExtra = Math.max(1, Math.min(120, planner.settings.extraTimeMaxMin));
        if (rem >= maxExtra) {
            form.addView(label("المحاضرة"));
            TextView nameTv = new TextView(this);
            int lecNum = Planner.lectureNumber(t);
            String title = cur.taskName == null ? "" : cur.taskName;
            if (lecNum >= 0) title = "محاضرة " + lecNum + " — " + title;
            if (cur.subject != null && !cur.subject.isEmpty()) title += " · " + cur.subject;
            nameTv.setText(title);
            nameTv.setTextColor(TEXT);
            nameTv.setTextSize(17);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            form.addView(nameTv);
            form.addView(space(dp(8)));
            form.addView(muted("المتبقي: " + rem + " د\nالحد الأقصى لـ«إضافي»: أقل من " + maxExtra + " د.\nالمتبقي أكبر من الحد — كمّل من الجدول العادي."));
            myDialog().setTitle("إضافي").setView(form)
                    .setPositiveButton("تمام", null).show();
            return;
        }

        form.addView(label("المحاضرة (نفسها — مش جديدة)"));
        TextView nameTv = new TextView(this);
        int lecNum = Planner.lectureNumber(t);
        String title = cur.taskName == null ? "" : cur.taskName;
        if (lecNum >= 0) title = "محاضرة " + lecNum + " — " + title;
        if (cur.subject != null && !cur.subject.isEmpty()) title += " · " + cur.subject;
        nameTv.setText(title);
        nameTv.setTextColor(TEXT);
        nameTv.setTextSize(17);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(nameTv);
        form.addView(space(dp(8)));
        form.addView(muted("المتبقي: " + rem + " دقيقة\nلو فيه جلسات بكرة لنفس المحاضرة هتتشال/هتتقلص بعد ما تخلّص."));
        form.addView(space(dp(12)));
        form.addView(muted("أكمل النهارده = تايمر بالمتبقي.\nترحيل لبكرة = بدون تايمر."));

        final int remFinal = rem;
        final Planner.Session curFinal = cur;
        myDialog()
                .setTitle("إضافي")
                .setView(form)
                .setPositiveButton("أكمل النهارده", (d, w) -> {
                    int dur = Math.max(1, remFinal);
                    extraTaskId = curFinal.taskId;
                    extraStartedMs = System.currentTimeMillis();
                    extraDurationMin = dur;
                    openTimerWithDuration(curFinal, dur);
                })
                .setNeutralButton("ترحيل لبكرة", (d, w) -> {
                    planner.rolloverRemainingToNextDay(curFinal.taskId);
                    Toast.makeText(this, "المتبقي اترحّل لبكرة كأول حاجة", Toast.LENGTH_SHORT).show();
                    showTab(0);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }


    private void sendFeedbackEmail() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:"));
            intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"myplan.feedback@gmail.com"});
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "اقتراح / مشكلة — My Plan");
            String ver = "?";
            try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
            intent.putExtra(android.content.Intent.EXTRA_TEXT, "اكتب رسالتك هنا:\n\n---\nإصدار التطبيق: " + ver + "\n");
            startActivity(android.content.Intent.createChooser(intent, "ابعت رسالة"));
        } catch (Exception e) {
            Toast.makeText(this, "مفيش تطبيق بريد على الجهاز", Toast.LENGTH_LONG).show();
        }
    }

    private void openContactUsScreen() {
        showingContactUs = true;
        contactUsPage = 0;
        showTab(4);
        syncInboxAsync(true);
    }

    /** صفحة تواصل معنا: قائمة زرين أو رسائل المطور أو نموذج الإرسال */
    private View buildContactUsScreen() {
        if (contactUsPage == 1) return buildDeveloperMessagesPage();
        if (contactUsPage == 2) return buildComposeMessagePage();
        return buildContactUsHub();
    }

    private View buildContactUsHub() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView back = link("← المزيد");
        back.setOnClickListener(v -> {
            showingContactUs = false;
            contactUsPage = 0;
            showTab(4);
        });
        box.addView(back);
        box.addView(title("تواصل معنا"));
        box.addView(space(dp(18)));

        int unread = InboxStore.unreadCount(this);
        LinearLayout devCard = card();
        devCard.setOrientation(LinearLayout.VERTICAL);
        devCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView devTitle = new TextView(this);
        devTitle.setText(unread > 0
                ? ("📨 رسائل من المطور  ● " + unread)
                : "📨 رسائل من المطور");
        devTitle.setTextColor(TEXT);
        devTitle.setTextSize(16);
        devTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        devCard.addView(devTitle);
        devCard.setOnClickListener(v -> {
            contactUsPage = 1;
            showTab(4);
            syncInboxAsync(true);
        });
        box.addView(devCard);
        box.addView(space(dp(12)));

        LinearLayout sendCard = card();
        sendCard.setOrientation(LinearLayout.VERTICAL);
        sendCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView sendTitle = new TextView(this);
        sendTitle.setText("✉️ إرسال رسالة لنا");
        sendTitle.setTextColor(TEXT);
        sendTitle.setTextSize(16);
        sendTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sendCard.addView(sendTitle);
        sendCard.setOnClickListener(v -> {
            contactUsPage = 2;
            showTab(4);
        });
        box.addView(sendCard);
        return sc;
    }

    private View buildDeveloperMessagesPage() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView back = link("← تواصل معنا");
        back.setOnClickListener(v -> {
            contactUsPage = 0;
            showTab(4);
        });
        box.addView(back);
        box.addView(title("رسائل من المطور"));
        box.addView(space(dp(12)));

        TextView refresh = chip("تحديث", false);
        refresh.setOnClickListener(v -> {
            Toast.makeText(this, "جاري التحديث…", Toast.LENGTH_SHORT).show();
            syncInboxAsync(true);
        });
        box.addView(refresh);
        box.addView(space(dp(12)));

        java.util.List<InboxStore.Msg> list = InboxStore.list(this);
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(16), dp(20), dp(16), dp(20));
            empty.addView(muted("لا توجد رسائل من المطور"));
            box.addView(empty);
            return sc;
        }
        for (InboxStore.Msg m : list) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView t = new TextView(this);
            String titleTxt = (m.title == null || m.title.isEmpty()) ? "(بدون عنوان)" : m.title;
            if (!m.read) titleTxt = "● " + titleTxt;
            t.setText(titleTxt);
            t.setTextColor(TEXT);
            t.setTextSize(15);
            t.setTypeface(null, m.read ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
            row.addView(t);
            String bodyPreview = m.body == null ? "" : m.body;
            TextView bodyTv = new TextView(this);
            bodyTv.setText(bodyPreview);
            bodyTv.setTextColor(MUTED);
            bodyTv.setTextSize(13);
            bodyTv.setPadding(0, dp(4), 0, dp(2));
            row.addView(bodyTv);
            if (m.createdAt != null && !m.createdAt.isEmpty()) {
                row.addView(muted(m.createdAt));
            }
            if (!m.read) {
                TextView badge = new TextView(this);
                badge.setText("غير مقروءة");
                badge.setTextColor(0xFFE53935);
                badge.setTextSize(11);
                row.addView(badge);
            }
            final InboxStore.Msg fm = m;
            row.setOnClickListener(v -> {
                InboxStore.markRead(this, fm.id);
                updateMoreNavBadge();
                myDialog().setTitle(fm.title == null || fm.title.isEmpty() ? "رسالة" : fm.title)
                        .setMessage((fm.body == null ? "" : fm.body)
                                + (fm.createdAt != null && !fm.createdAt.isEmpty()
                                ? "\n\n—\n" + fm.createdAt : ""))
                        .setPositiveButton("حسنًا", (d, w) -> showTab(4))
                        .show();
            });
            // زر حذف — تأكيد ثم DELETE من remote_messages
            TextView delBtn = chip("🗑️ حذف", false);
            delBtn.setOnClickListener(v -> {
                myDialog().setTitle("حذف الرسالة")
                        .setMessage("هل تريد حذف هذه الرسالة نهائيًا؟")
                        .setPositiveButton("حذف", (d, w) -> deleteDeveloperMessage(fm.id))
                        .setNegativeButton("إلغاء", null)
                        .show();
            });
            row.addView(space(dp(6)));
            row.addView(delBtn);
            box.addView(row);
            box.addView(space(dp(8)));
        }
        return sc;
    }

    private void deleteDeveloperMessage(long id) {
        if (id <= 0) {
            Toast.makeText(this, "معرّف غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "جاري الحذف…", Toast.LENGTH_SHORT).show();
        final android.content.Context appCtx = getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            String err = "";
            try {
                com.myplan.app.api.ApiResult<Void> r =
                        new com.myplan.app.supabase.SupabaseRepository(appCtx).deleteMessage(id);
                ok = r != null && r.isSuccess();
                if (!ok && r != null) err = r.message != null ? r.message : "";
            } catch (Exception e) {
                ok = false;
                err = "خطأ شبكة";
            }
            final boolean success = ok;
            final String errMsg = err;
            runOnUiThread(() -> {
                if (success) {
                    InboxStore.removeFromCache(MainActivity.this, id);
                    updateMoreNavBadge();
                    Toast.makeText(MainActivity.this, "تم حذف الرسالة", Toast.LENGTH_SHORT).show();
                    if (showingContactUs && contactUsPage == 1) showTab(4);
                } else {
                    Toast.makeText(MainActivity.this,
                            "تعذّر الحذف" + (errMsg.isEmpty() ? "" : ": " + errMsg),
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "msg-delete").start();
    }

    private View buildComposeMessagePage() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView back = link("← تواصل معنا");
        back.setOnClickListener(v -> {
            contactUsPage = 0;
            showTab(4);
        });
        box.addView(back);
        box.addView(title("إرسال رسالة لنا"));
        box.addView(space(dp(14)));

        box.addView(label("نوع الرسالة"));
        box.addView(space(dp(6)));
        final String[] types = {"مشكلة", "اقتراح", "استفسار", "أخرى"};
        final int[] selected = {0};
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final TextView[] chips = new TextView[types.length];
        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            chips[i] = chip(types[i], i == 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            typeRow.addView(chips[i], lp);
            chips[i].setOnClickListener(v -> {
                selected[0] = idx;
                for (int j = 0; j < chips.length; j++) {
                    styleChip(chips[j], j == idx);
                }
            });
        }
        box.addView(typeRow);
        box.addView(space(dp(14)));
        box.addView(label("الرسالة"));
        EditText body = field();
        body.setMinLines(5);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setHint("اكتب رسالتك هنا…");
        box.addView(body);
        box.addView(space(dp(14)));
        TextView sendBtn = chip("إرسال", true);
        sendBtn.setOnClickListener(v -> {
            String msg = body.getText() != null ? body.getText().toString().trim() : "";
            if (msg.isEmpty()) {
                Toast.makeText(this, "اكتب الرسالة أولًا", Toast.LENGTH_SHORT).show();
                return;
            }
            submitContactMessage(types[selected[0]], msg);
        });
        box.addView(sendBtn);
        return sc;
    }

    /**
     * يحفظ الرسالة محليًا، ويرسل إلى Supabase على background thread.
     * عند النجاح: لا يفتح البريد. عند الفشل: pending + fallback بريد.
     */
    private void submitContactMessage(String type, String message) {
        final String installId = AppInfrastructure.getInstallationId(this);
        String uid = "";
        AccountAuth.Account acc = AccountAuth.getCurrentAccount(this);
        if (acc != null && acc.userId != null) uid = acc.userId;
        final String userId = uid;
        final long now = System.currentTimeMillis();

        // حفظ محلي فوري كـ pending
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("type", type);
            o.put("message", message);
            o.put("userId", userId);
            o.put("installationId", installId);
            o.put("sentAt", now);
            o.put("status", "pending");
            android.content.SharedPreferences sp = getSharedPreferences("myplan_contact_v1", MODE_PRIVATE);
            String raw = sp.getString("messages", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            arr.put(o);
            sp.edit().putString("messages", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر حفظ الرسالة محليًا", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!com.myplan.app.supabase.SupabaseConfig.isConfigured(this)) {
            openContactEmailFallback(type, message, userId, installId, now);
            return;
        }

        Toast.makeText(this, "جاري الإرسال…", Toast.LENGTH_SHORT).show();
        final android.content.Context appCtx = getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                com.myplan.app.api.ApiResult<Void> remote =
                        new com.myplan.app.supabase.SupabaseRepository(appCtx)
                                .submitMessage(type, message, userId, installId);
                ok = remote != null && remote.isSuccess();
            } catch (Exception ignored) {
                ok = false;
            }
            final boolean sent = ok;
            runOnUiThread(() -> {
                if (sent) {
                    markLastContactMessageSent(type, message, now);
                    Toast.makeText(MainActivity.this, "تم إرسال الرسالة", Toast.LENGTH_SHORT).show();
                    contactUsPage = 0;
                    if (showingContactUs) showTab(4);
                } else {
                    openContactEmailFallback(type, message, userId, installId, now);
                }
            });
        }, "contact-send").start();
    }

    private void markLastContactMessageSent(String type, String message, long sentAt) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("myplan_contact_v1", MODE_PRIVATE);
            String raw = sp.getString("messages", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = arr.length() - 1; i >= 0; i--) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (o.optLong("sentAt", 0) == sentAt
                        && type.equals(o.optString("type", ""))
                        && message.equals(o.optString("message", ""))) {
                    o.put("status", "sent");
                    break;
                }
            }
            sp.edit().putString("messages", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void openContactEmailFallback(String type, String message, String userId,
                                          String installId, long now) {
        try {
            String ver = "?";
            try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"myplan.feedback@gmail.com"});
            intent.putExtra(Intent.EXTRA_SUBJECT, "تواصل معنا — " + type + " — My Plan");
            intent.putExtra(Intent.EXTRA_TEXT,
                    "النوع: " + type + "\n\n" + message
                            + "\n\n---\nuserId=" + userId
                            + "\ninstallationId=" + installId
                            + "\nsentAt=" + now
                            + "\nversion=" + ver + "\n");
            startActivity(Intent.createChooser(intent, "إرسال الرسالة"));
            Toast.makeText(this, "تم الحفظ محليًا (في الانتظار للإرسال)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "تم الحفظ محليًا. ستُرسل عند توفر الاتصال.", Toast.LENGTH_LONG).show();
        }
    }

    private void showNoLectureDayDialog() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (vv, y, m, d) -> {
            String day = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            boolean exists = planner.settings.noLectureDays.contains(day);
            if (exists) {
                myDialog().setTitle("يوم بدون محاضرات")
                        .setMessage("اليوم " + day + " ممنوع فيه المحاضرات.")
                        .setPositiveButton("إلغاء المنع", (d2, w) -> {
                            planner.settings.noLectureDays.remove(day);
                            planner.save();
                            Toast.makeText(this, "اتشال المنع عن " + day, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("إغلاق", null).show();
            } else {
                myDialog().setTitle("يوم بدون محاضرات")
                        .setMessage("تمنع المحاضرات يوم " + day + "؟\nينفع تستخدمه للمذاكرة لامتحان.")
                        .setPositiveButton("منع المحاضرات", (d2, w) -> {
                            planner.settings.noLectureDays.add(day);
                            planner.save();
                            Toast.makeText(this, "اتمنع المحاضرات يوم " + day + " · اضغط عدّل خطتي", Toast.LENGTH_LONG).show();
                        })
                        .setNeutralButton("الأيام الممنوعة", (d2, w) -> {
                            if (planner.settings.noLectureDays.isEmpty()) {
                                Toast.makeText(this, "مفيش أيام ممنوعة", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            java.util.ArrayList<String> list = new java.util.ArrayList<>(planner.settings.noLectureDays);
                            java.util.Collections.sort(list);
                            myDialog().setTitle("أيام بدون محاضرات")
                                    .setItems(list.toArray(new String[0]), (d3, w3) -> {
                                        String rem = list.get(w3);
                                        planner.settings.noLectureDays.remove(rem);
                                        planner.save();
                                        Toast.makeText(this, "اتشال المنع عن " + rem, Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("إغلاق", null).show();
                        })
                        .setNegativeButton("إلغاء", null).show();
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void askPlanDaysAndBuild() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        form.addView(label("عدد الأيام"));
        EditText et = field();
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("مثال: 3 أو 7 أو 14 أو 30");
        int def = Math.max(1, planner.settings.planDays);
        et.setText(String.valueOf(def));
        form.addView(et);
        form.addView(space(dp(12)));

        form.addView(label("طريقة توزيع المحاضرات"));
        final int[] mode = {Math.max(0, Math.min(2, planner.settings.lectureDistMode))};
        TextView chip0 = chip("كل يوم جزء من كل محاضرة", mode[0] == 0);
        TextView chip1 = chip("جلسات المحاضرة ورا بعض", mode[0] == 1);
        TextView chip2 = chip("محاضرة واحدة في اليوم", mode[0] == 2);
        Runnable sync = () -> {
            chip0.setBackgroundColor(mode[0] == 0 ? ACCENT : CARD);
            chip1.setBackgroundColor(mode[0] == 1 ? ACCENT : CARD);
            chip2.setBackgroundColor(mode[0] == 2 ? ACCENT : CARD);
            chip0.setTextColor(mode[0] == 0 ? Color.WHITE : TEXT);
            chip1.setTextColor(mode[0] == 1 ? Color.WHITE : TEXT);
            chip2.setTextColor(mode[0] == 2 ? Color.WHITE : TEXT);
        };
        chip0.setOnClickListener(v -> { mode[0] = 0; sync.run(); });
        chip1.setOnClickListener(v -> { mode[0] = 1; sync.run(); });
        chip2.setOnClickListener(v -> { mode[0] = 2; sync.run(); });
        sync.run();
        form.addView(chip0);
        form.addView(space(dp(6)));
        form.addView(chip1);
        form.addView(space(dp(6)));
        form.addView(chip2);

        myDialog()
                .setTitle("عدّل خطتي")
                .setView(form)
                .setPositiveButton("عدّل خطتي", (d, w) -> {
                    int n = def;
                    try { n = Integer.parseInt(et.getText().toString().trim()); } catch (Exception ignored) {}
                    if (n < 1) n = 1;
                    if (n > 90) n = 90;
                    planner.settings.lectureDistMode = mode[0];
                    planner.save();
                    resolveLectureTiesThenBuild(Planner.DIST_DAYS, n, null);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showCreateScheduleDialog() {
        if (planner.tasks.isEmpty()) {
            Toast.makeText(this, "ضيف مهام الأول من تبويب المهام", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] modes = {
                "خلال أسبوع",
                "خلال عدد معيّن من الأيام",
                "حتى موعد نهائي",
                "بدون موعد محدد (١٤ يوم)"
        };
        myDialog()
                .setTitle("طريقة توزيع المهام")
                .setItems(modes, (d, which) -> {
                    if (which == Planner.DIST_DAYS) {
                        askDaysCount();
                    } else if (which == Planner.DIST_DEADLINE) {
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_YEAR, 7);
                        new DatePickerDialog(this, (v, y, m, day) -> {
                            String until = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day);
                            resolveLectureTiesThenBuild(Planner.DIST_DEADLINE, 0, until);
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                    } else {
                        resolveLectureTiesThenBuild(which, 0, null);
                    }
                })
                .show();
    }

    private void askDaysCount() {
        EditText et = field();
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("عدد الأيام");
        et.setText("7");
        myDialog()
                .setTitle("خلال كام يوم؟")
                .setView(pad(et))
                .setPositiveButton("إنشاء", (d, w) -> {
                    int n = 7;
                    try { n = Integer.parseInt(et.getText().toString().trim()); } catch (Exception ignored) {}
                    resolveLectureTiesThenBuild(Planner.DIST_DAYS, n, null);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ═══════════════ Tasks ═══════════════
    private View buildTasksScreen() {
        FrameLayout root=new FrameLayout(this);
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        sc.setPadding(0,0,0,dp(92));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(28)); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); sc.addView(box);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView h=title("المهام");h.setTextSize(32);head.addView(h,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView filter=chip("العرض ▾",false);filter.setOnClickListener(v->showTaskViewChoices(v));head.addView(filter);box.addView(head);box.addView(space(dp(10)));
        LinearLayout tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);
        TextView all=chip("الكل",taskKindFilter==-1),lec=chip("محاضرات",taskKindFilter==Planner.Task.KIND_LECTURE),study=chip("مذاكرة",taskKindFilter==Planner.Task.KIND_STUDY);
        all.setOnClickListener(v->{taskKindFilter=-1;showTab(1);});lec.setOnClickListener(v->{taskKindFilter=Planner.Task.KIND_LECTURE;showTab(1);});study.setOnClickListener(v->{taskKindFilter=Planner.Task.KIND_STUDY;showTab(1);});
        tabs.addView(all,chipLp());tabs.addView(space(dp(6)));tabs.addView(lec,chipLp());tabs.addView(space(dp(6)));tabs.addView(study,chipLp());box.addView(tabs);box.addView(space(dp(14)));
        List<Planner.Task> list=new ArrayList<>(planner.tasks);if(taskKindFilter!=-1){List<Planner.Task>x=new ArrayList<>();for(Planner.Task t:list)if(t.kind==taskKindFilter)x.add(t);list=x;}list.sort(this::compareTasksForDisplay);
        if(list.isEmpty())box.addView(emptyState("مفيش مهام في القسم ده."));else for(Planner.Task t:list)box.addView(taskCard(t));
        box.addView(space(dp(110)));
        root.addView(sc,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(fixedAddBar("إضافة مهمة",()->showAddTaskDialog(null)));
        return root;
    }

    private View fixedAddBar(String labelText, final Runnable action) {
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(12),dp(8),dp(12),dp(8));
        bar.setBackgroundColor(BG);

        TextView btn=primaryBtn("+ "+labelText);
        btn.setTextSize(16);
        btn.setOnClickListener(v->action.run());
        bar.addView(btn,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(64));
        lp.gravity=Gravity.BOTTOM;
        lp.leftMargin=0;
        lp.rightMargin=0;
        lp.bottomMargin=0;
        return bar;
    }

    private void appendTaskGroup(LinearLayout box, String title, List<Planner.Task> list) {
        box.addView(sectionHeader(title + "  (" + list.size() + ")"));
        if (list.isEmpty()) box.addView(muted("—"));
        else for (Planner.Task tk : list) box.addView(taskCard(tk));
        box.addView(space(dp(8)));
    }

    private int compareTasksForDisplay(Planner.Task a, Planner.Task b) {
        // المفتاح الأساسي حسب وضع العرض
        int primary = 0;
        if (taskSortKey == 0) {
            // نوع: جديد(0) → متراكم(1) → مكتمل(2)
            int ra = a.done ? 2 : (a.backlog ? 1 : 0);
            int rb = b.done ? 2 : (b.backlog ? 1 : 0);
            primary = Integer.compare(ra, rb);
        } else if (taskSortKey == 1) {
            String sa = a.subject == null ? "" : a.subject.trim();
            String sb = b.subject == null ? "" : b.subject.trim();
            primary = sa.compareToIgnoreCase(sb);
        } else if (taskSortKey == 3) {
            // أولوية رقمية: 0 منخفضة · 1 متوسطة · 2 عالية
            primary = Integer.compare(
                    Math.max(0, Math.min(2, a.priority)),
                    Math.max(0, Math.min(2, b.priority)));
        } else {
            // مدة
            primary = Integer.compare(a.durationMin, b.durationMin);
        }
        if (primary != 0) {
            // ↑ taskSortAsc=true → تصاعدي (الأصغر أولًا)
            // ↓ taskSortAsc=false → تنازلي (الأكبر أولًا)
            return taskSortAsc ? primary : -primary;
        }
        // ثانوي: المدة (ليظهر أثر ↑/↓ داخل نفس المجموعة)
        if (taskSortKey != 2) {
            int sec = Integer.compare(a.durationMin, b.durationMin);
            if (sec != 0) return taskSortAsc ? sec : -sec;
        }
        // ثانوي للأولوية عند تساوي المدة
        if (taskSortKey != 3) {
            int secP = Integer.compare(
                    Math.max(0, Math.min(2, a.priority)),
                    Math.max(0, Math.min(2, b.priority)));
            if (secP != 0) return taskSortAsc ? secP : -secP;
        }
        // Tie-breaker ثابت — لا يُعكس مع الاتجاه
        int tie = Integer.compare(a.sortOrder, b.sortOrder);
        if (tie != 0) return tie;
        String ida = a.id == null ? "" : a.id;
        String idb = b.id == null ? "" : b.id;
        return ida.compareTo(idb);
    }

    private void loadTaskSortPrefs() {
        try {
            android.content.SharedPreferences p = getSharedPreferences("myplan_ui", MODE_PRIVATE);
            taskSortKey = p.getInt("taskSortKey", 0);
            taskSortAsc = p.getBoolean("taskSortAsc", true);
        } catch (Exception ignored) {}
    }

    private void saveTaskSortPrefs() {
        try {
            getSharedPreferences("myplan_ui", MODE_PRIVATE).edit()
                    .putInt("taskSortKey", taskSortKey)
                    .putBoolean("taskSortAsc", taskSortAsc)
                    .apply();
        } catch (Exception ignored) {}
    }


    /** اختيارات عائمة خفيفة فوق الشاشة مع تعتيم — بدون Card ضخم. */
    private void showFloatingChoices(View anchor, String[] labels, int selectedIndex, IntPick onPick) {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        // تعتيم أقوى لوضوح الاختيارات
        View dim = new View(this);
        dim.setBackgroundColor(0xCC05070C);
        root.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(20), dp(12), dp(20), dp(12));
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        // كل اختيار زر مستقل — بدون كارت واحد يضمّ الكل
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView btn = new TextView(this);
            btn.setText(labels[i]);
            btn.setTextSize(15);
            btn.setTypeface(selectedIndex == i ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            btn.setTextColor(selectedIndex == i ? 0xFFFFFFFF : TEXT);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(22), dp(14), dp(22), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            if (selectedIndex == i) {
                bg.setColor(ACCENT);
            } else {
                bg.setColor(0xF01A2130);
                bg.setStroke(dp(1), 0x44FFFFFF);
            }
            btn.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            panel.addView(btn, lp);
            btn.setOnClickListener(v -> {
                dismissFloatingOverlay();
                onPick.pick(idx);
            });
        }
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        plp.leftMargin = dp(28);
        plp.rightMargin = dp(28);
        root.addView(panel, plp);

        dim.setOnClickListener(v -> dismissFloatingOverlay());

        dismissFloatingOverlay();
        floatingOverlay = root;
        ((ViewGroup) findViewById(android.R.id.content)).addView(root,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void dismissFloatingOverlay() {
        if (floatingOverlay != null) {
            try { ((ViewGroup) findViewById(android.R.id.content)).removeView(floatingOverlay); } catch (Exception ignored) {}
            floatingOverlay = null;
        }
    }

    private void showTaskViewChoices(View anchor) {
        String[] labs = {
                "حسب النوع",
                "حسب المادة",
                "حسب المدة",
                "حسب الأولوية"
        };
        showFloatingChoices(anchor, labs, Math.max(0, Math.min(3, taskSortKey)), idx -> {
            taskSortKey = idx;
            saveTaskSortPrefs();
            showTab(1);
        });
    }

    private void showTaskSortPopup(View anchor) {
        // توافق: الفرز مستقل عبر زر ↑/↓ في شاشة المهام
        showTaskViewChoices(anchor);
    }

    /** بطاقة إضافة موحّدة: مهمة واحدة | أكثر من مهمة */
    private void showUnifiedAddDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        final int[] mode = {0}; // 0 واحدة · 1 أكثر
        TextView tabOne = chip("مهمة واحدة", true);
        TextView tabMulti = chip("أكثر من مهمة", false);
        FrameLayout body = new FrameLayout(this);
        body.setMinimumHeight(dp(120));

        Runnable render = () -> {
            body.removeAllViews();
            TextView hint = muted(mode[0] == 0
                    ? "هتفتح نموذج إضافة مهمة واحدة بالكامل."
                    : "كل مهمة لها اسم ومادة ومدة وأولوية مستقلة.");
            body.addView(hint);
        };
        tabOne.setOnClickListener(v -> {
            mode[0] = 0;
            styleChip(tabOne, true);
            styleChip(tabMulti, false);
            render.run();
        });
        tabMulti.setOnClickListener(v -> {
            mode[0] = 1;
            styleChip(tabOne, false);
            styleChip(tabMulti, true);
            render.run();
        });
        tabs.addView(tabOne, chipLp());
        tabs.addView(space(dp(8)));
        tabs.addView(tabMulti, chipLp());
        root.addView(tabs);
        root.addView(space(dp(12)));
        root.addView(body);
        render.run();

        myDialog().setTitle("إضافة")
                .setView(root)
                .setPositiveButton("متابعة", (d, w) -> {
                    if (mode[0] == 0) showAddTaskDialog(null);
                    else showIndependentBatchAdd();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** إضافة عدة مهام؛ كل مهمة مستقلة (اسم/مادة/مدة/أولوية). */
    private void showIndependentBatchAdd() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(8), dp(14), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.addView(muted("كل صف = مهمة مستقلة. المادة من قائمتك."));
        form.addView(space(dp(8)));

        LinearLayout rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        form.addView(rowsBox);

        java.util.ArrayList<EditText> nameFields = new java.util.ArrayList<>();
        java.util.ArrayList<String[]> subjectHolds = new java.util.ArrayList<>();
        java.util.ArrayList<TextView> subjectViews = new java.util.ArrayList<>();
        java.util.ArrayList<EditText> durFields = new java.util.ArrayList<>();
        java.util.ArrayList<int[]> priHolds = new java.util.ArrayList<>();

        Runnable addRow = new Runnable() {
            @Override public void run() {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(CARD2);
                bg.setCornerRadius(dp(12));
                row.setBackground(bg);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = dp(8);
                row.setLayoutParams(rlp);

                EditText nm = field();
                nm.setHint("اسم المهمة");
                row.addView(label("الاسم"));
                row.addView(nm);
                nameFields.add(nm);

                final String[] subH = {""};
                subjectHolds.add(subH);
                TextView subV = chip("اختار المادة ▾", true);
                subV.setOnClickListener(v -> pickSubject(subH[0], n -> {
                    subH[0] = n;
                    subV.setText(n);
                }));
                row.addView(label("المادة"));
                row.addView(subV);
                subjectViews.add(subV);

                EditText dur = field();
                dur.setInputType(InputType.TYPE_CLASS_NUMBER);
                dur.setHint("المدة بالدقيقة");
                row.addView(label("المدة"));
                row.addView(dur);
                durFields.add(dur);

                final int[] pri = {1};
                priHolds.add(pri);
                LinearLayout priRow = new LinearLayout(MainActivity.this);
                priRow.setOrientation(LinearLayout.HORIZONTAL);
                priRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView[] pcs = new TextView[3];
                String[] pl = {"منخفضة", "متوسطة", "عالية"};
                for (int i = 0; i < 3; i++) {
                    final int idx = i;
                    pcs[i] = chip(pl[i], pri[0] == i);
                    pcs[i].setOnClickListener(v -> {
                        pri[0] = idx;
                        for (int j = 0; j < 3; j++) styleChip(pcs[j], j == idx);
                    });
                    priRow.addView(pcs[i], chipLp());
                    if (i < 2) priRow.addView(space(dp(4)));
                }
                row.addView(label("الأولوية"));
                row.addView(priRow);

                rowsBox.addView(row);
            }
        };

        // صفّان ابتدائيان
        addRow.run();
        addRow.run();

        TextView addMore = link("+ صف آخر");
        addMore.setOnClickListener(v -> addRow.run());
        form.addView(space(dp(4)));
        form.addView(addMore);

        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        myDialog().setTitle("أكثر من مهمة")
                .setView(sv)
                .setPositiveButton("إضافة", (d, w) -> {
                    int added = 0;
                    int maxOrd = 0;
                    for (Planner.Task x : planner.tasks) if (x.sortOrder > maxOrd) maxOrd = x.sortOrder;
                    for (int i = 0; i < nameFields.size(); i++) {
                        String name = nameFields.get(i).getText() == null ? "" : nameFields.get(i).getText().toString().trim();
                        if (name.isEmpty()) continue;
                        String sub = subjectHolds.get(i)[0];
                        if (sub == null || sub.isEmpty()) sub = "عام";
                        int dur = 60;
                        try {
                            String ds = durFields.get(i).getText() == null ? "" : durFields.get(i).getText().toString().trim();
                            if (!ds.isEmpty()) dur = Integer.parseInt(ds);
                        } catch (Exception ignored) {}
                        if (dur < 5) dur = 5;
                        int pri = priHolds.get(i)[0];
                        Planner.Task tk = new Planner.Task();
                        tk.name = name;
                        tk.subject = sub;
                        tk.durationMin = dur;
                        tk.remainingMin = dur;
                        tk.priority = pri;
                        tk.sortOrder = ++maxOrd;
                        int sm = Math.max(10, planner.settings.sessionMin);
                        tk.applySessionCount(Math.max(1, (int) Math.ceil(dur / (double) sm)));
                        planner.tasks.add(tk);
                        try { planner.addSubject(sub); } catch (Exception ignored) {}
                        added++;
                    }
                    planner.save();
                    Toast.makeText(this, "اتضافت " + added + " مهمة", Toast.LENGTH_SHORT).show();
                    showTab(1);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** بلوك مادة قابل للفتح/الطي */
    private View subjectBlockCard(String subject, List<Planner.Task> list, int openCount, int total) {
        LinearLayout wrap = card();
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(4), 0, dp(4));

        TextView arrow = new TextView(this);
        arrow.setText("▶");
        arrow.setTextColor(MUTED);
        arrow.setTextSize(12);
        arrow.setPadding(dp(4), 0, dp(8), 0);

        TextView titleTv = new TextView(this);
        titleTv.setText(subject);
        titleTv.setTextColor(TEXT);
        titleTv.setTextSize(15);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView countTv = muted(openCount + " مفتوحة · " + total);

        head.addView(arrow);
        head.addView(titleTv);
        head.addView(countTv);
        wrap.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        body.setPadding(0, dp(8), 0, 0);
        for (Planner.Task tk : list) {
            body.addView(taskCard(tk));
        }
        wrap.addView(body);

        final boolean[] open = {false};
        View.OnClickListener toggle = v -> {
            open[0] = !open[0];
            body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            arrow.setText(open[0] ? "▼" : "▶");
        };
        head.setOnClickListener(toggle);
        titleTv.setOnClickListener(toggle);
        arrow.setOnClickListener(toggle);
        return wrap;
    }

    private View taskCard(Planner.Task t) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        View priDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(priorityColor(t.priority));
        priDot.setBackground(dotBg);
        row.addView(priDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        row.addView(space(dp(10)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(t.name == null ? "—" : t.name);
        name.setTextColor(t.done ? MUTED : TEXT);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);
        String subj = (t.subject == null || t.subject.isEmpty()) ? "بدون مادة" : t.subject;
        TextView meta = muted(subj + "  ·  " + t.durationMin + " د");
        meta.setTextSize(12);
        info.addView(meta);
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // يسار البلوك: أزرار بنص مضمون الظهور (ليس Button ثيم قد يخفي النص)
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(4), 0, dp(4), 0);
        actions.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (t.done) {
            actions.addView(makeLabeledTaskBtn("مسح", true, v -> confirmEraseCompletedTask(t)));
        } else {
            actions.addView(makeLabeledTaskBtn("تعديل", false, v -> showAddTaskDialog(t)));
            actions.addView(space(dp(8)));
            actions.addView(makeLabeledTaskBtn("مسح", true, v -> confirmDeleteTask(t)));
        }
        row.addView(actions, actLp);
        card.addView(row);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.setPadding(0, dp(10), 0, 0);
        String pri = Planner.PRIORITY_LABELS[Math.max(0, Math.min(2, t.priority))];
        TextView priLine = new TextView(this);
        priLine.setText("الأولوية: " + pri);
        priLine.setTextColor(priorityColor(t.priority));
        priLine.setTextSize(13);
        priLine.setTypeface(Typeface.DEFAULT_BOLD);
        details.addView(priLine);
        details.addView(muted("المادة: " + subj));
        details.addView(muted("المدة: " + t.durationMin + " د"
                + (t.remainingMin < t.durationMin && !t.done ? " · متبقي " + t.remainingMin + "د" : "")));
        if (t.deadline != null && !t.deadline.isEmpty()) {
            details.addView(muted("الموعد النهائي: " + t.deadline));
        }
        if (!t.done) {
            TextView doneBtn = link("خلصت");
            doneBtn.setPadding(0, dp(8), 0, 0);
            doneBtn.setOnClickListener(v -> {
                planner.completeTask(t.id);
                showTab(1);
            });
            details.addView(doneBtn);
        }
        card.addView(details);

        final boolean[] open = {false};
        View.OnClickListener toggle = v -> {
            open[0] = !open[0];
            details.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            // أزرار تعديل/مسح تبقى ظاهرة دائمًا — لا تُخفى مع الطي
        };
        // الطي فقط من الضغط على معلومات المهمة، وليس على أزرار الإجراءات
        info.setOnClickListener(toggle);
        name.setOnClickListener(toggle);
        meta.setOnClickListener(toggle);
        enableTaskDrag(card, t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    /** حذف نهائي للمهمة + كل جلساتها من الجدول والبيانات. */
    private void permanentlyDeleteTask(Planner.Task t) {
        if (t == null) return;
        String id = t.id;
        planner.tasks.removeIf(x -> id.equals(x.id));
        planner.sessions.removeIf(s -> id.equals(s.taskId));
        planner.save();
        SessionAlarmScheduler.resync(this, planner);
        Toast.makeText(this, "اتمسحت المهمة من القائمة والجدول", Toast.LENGTH_SHORT).show();
        showTab(1);
    }

    private void enableTaskDrag(View card, Planner.Task t) {
        // إعادة ترتيب بالضغط المطوّل + سحب بسيط داخل القائمة فقط (من غير ما تطلع برّه التطبيق)
        card.setOnLongClickListener(v -> {
            draggingTask = t;
            dragAnchorY = 0f;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            v.setAlpha(0.85f);
            if (v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            Toast.makeText(this, "اسحب لفوق أو تحت لإعادة الترتيب", Toast.LENGTH_SHORT).show();
            return true;
        });
        card.setOnTouchListener((v, e) -> {
            if (draggingTask == null || draggingTask != t) return false;
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                dragAnchorY = e.getRawY();
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                if (dragAnchorY == 0f) dragAnchorY = e.getRawY();
                float dy = e.getRawY() - dragAnchorY;
                // حد أقصى بسيط للحركة البصرية — مش هتطلع برّه الكرت/التطبيق
                float clamp = dp(28);
                if (dy > clamp) dy = clamp;
                if (dy < -clamp) dy = -clamp;
                v.setTranslationY(dy);
                if (e.getRawY() - dragAnchorY < -dp(52)) {
                    planner.moveTask(t, -1);
                    dragAnchorY = e.getRawY();
                    v.setTranslationY(0);
                    showTab(1);
                    return true;
                } else if (e.getRawY() - dragAnchorY > dp(52)) {
                    planner.moveTask(t, 1);
                    dragAnchorY = e.getRawY();
                    v.setTranslationY(0);
                    showTab(1);
                    return true;
                }
                return true;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                draggingTask = null;
                v.setTranslationY(0f);
                v.setAlpha(1f);
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }
            return true;
        });
    }

    private void confirmEraseCompletedTask(Planner.Task t) {
        String nm = t.name == null ? "المهمة" : t.name;
        myDialog().setTitle("مسح المهمة المكتملة")
                .setMessage("هتتمسح «" + nm + "» نهائيًا مع كل جلساتها من الجدول والبيانات.\nمتأكد؟")
                .setPositiveButton("مسح", (d, w) -> permanentlyDeleteTask(t))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void confirmDeleteTask(Planner.Task t) {
        String nm = t == null || t.name == null ? "المهمة" : t.name;
        myDialog().setTitle("مسح المهمة")
                .setMessage("هتتمسح «" + nm + "» وكل جلساتها من الجدول والبيانات.\nمتأكد؟")
                .setPositiveButton("مسح", (d, w) -> permanentlyDeleteTask(t))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showAddTaskDialog(Planner.Task existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (existing == null) {
            TextView multiLink = link("إضافة أكثر من مهمة");
            multiLink.setTextSize(12);
            multiLink.setPadding(0, 0, 0, dp(8));
            multiLink.setOnClickListener(v -> {
                try {
                    // أغلق الحوار الحالي عبر الضغط خارجيًا لاحقًا؛ افتح النموذج المتعدد
                    showIndependentBatchAdd();
                } catch (Exception ignored) {}
            });
            form.addView(multiLink);
        }

        EditText name = dialogField();
        name.setHint("اسم المهمة / المحاضرة");
        form.addView(label("الاسم"));
        form.addView(name);

        final String[] subjectHold = {existing != null && existing.subject != null ? existing.subject : ""};
        form.addView(label("المادة"));
        TextView subject = chip(subjectHold[0].isEmpty() ? "اختار المادة ▾" : subjectHold[0], true);
        subject.setOnClickListener(v -> pickSubject(subjectHold[0], name0 -> {
            subjectHold[0] = name0;
            subject.setText(name0);
        }));
        form.addView(subject);

        form.addView(label("مدة المهمة كاملة (دقيقة)"));
        EditText durationEt = dialogField();
        durationEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        durationEt.setHint("مثلاً 180");
        form.addView(durationEt);


        form.addView(label("الأولوية"));
        final int[] pri = {existing != null ? Math.min(2, existing.priority) : 1};
        LinearLayout priRow = new LinearLayout(this);
        priRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] priChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            priChips[i] = chip(Planner.PRIORITY_LABELS[i], pri[0] == i);
            priChips[i].setOnClickListener(v -> {
                pri[0] = idx;
                for (int j = 0; j < 3; j++) styleChip(priChips[j], pri[0] == j);
            });
            priRow.addView(priChips[i], chipLp());
            if (i < 2) priRow.addView(space(dp(6)));
        }
        form.addView(priRow);

        form.addView(label("التصنيف"));
        final int[] kind = {existing != null ? existing.kind : Planner.Task.KIND_LECTURE};
        LinearLayout kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView chipLec = chip("محاضرة 📖", kind[0] != Planner.Task.KIND_STUDY);
        TextView chipStu = chip("مذاكرة 📝", kind[0] == Planner.Task.KIND_STUDY);
        chipLec.setOnClickListener(v -> {
            kind[0] = Planner.Task.KIND_LECTURE;
            styleChip(chipLec, true);
            styleChip(chipStu, false);
        });
        chipStu.setOnClickListener(v -> {
            kind[0] = Planner.Task.KIND_STUDY;
            styleChip(chipLec, false);
            styleChip(chipStu, true);
        });
        kindRow.addView(chipLec, chipLp());
        kindRow.addView(space(dp(6)));
        kindRow.addView(chipStu, chipLp());
        form.addView(kindRow);
        form.addView(muted("المذاكرة لا تدخل في ترتيب المحاضرات (محاضرة 1، 2، 3…)."));
        form.addView(space(dp(6)));

        form.addView(label("نوع المهمة"));
        final boolean[] backlog = {existing != null && existing.backlog};
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView chipNew = chip("جديدة 🆕", !backlog[0]);
        TextView chipBack = chip("قديمة 📚", backlog[0]);
        chipNew.setOnClickListener(v -> {
            backlog[0] = false;
            styleChip(chipNew, true);
            styleChip(chipBack, false);
        });
        chipBack.setOnClickListener(v -> {
            backlog[0] = true;
            styleChip(chipNew, false);
            styleChip(chipBack, true);
        });
        typeRow.addView(chipNew, chipLp());
        typeRow.addView(space(dp(8)));
        typeRow.addView(chipBack, chipLp());
        form.addView(typeRow);

        TextView moreToggle = link("خيارات إضافية ▾");
        form.addView(space(dp(8)));
        form.addView(moreToggle);
        LinearLayout moreBox = new LinearLayout(this);
        moreBox.setOrientation(LinearLayout.VERTICAL);
        moreBox.setVisibility(View.GONE);
        moreBox.setPadding(0, dp(6), 0, 0);
        final boolean[] moreOpen = {false};
        moreToggle.setOnClickListener(v -> {
            moreOpen[0] = !moreOpen[0];
            moreBox.setVisibility(moreOpen[0] ? View.VISIBLE : View.GONE);
            moreToggle.setText(moreOpen[0] ? "خيارات إضافية ▴" : "خيارات إضافية ▾");
        });

        moreBox.addView(label("وقت البدء"));
        final boolean[] pin = {existing != null && existing.pinStart};
        final String[] pinDay = {existing != null && existing.pinDay != null ? existing.pinDay : ""};
        final int[] pinMin = {existing != null ? existing.pinStartMin : 16 * 60 + 30};
        LinearLayout pinRow = new LinearLayout(this);
        pinRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView chipAuto = chip("تلقائي", !pin[0]);
        TextView chipManual = chip("أحدده بنفسي", pin[0]);
        TextView pinWhen = chip(
                pin[0] && pinDay[0].length() > 0
                        ? (pinDay[0] + "  " + Planner.minToTime(pinMin[0]))
                        : "اختار التاريخ والوقت",
                pin[0]);
        pinWhen.setVisibility(pin[0] ? View.VISIBLE : View.GONE);
        chipAuto.setOnClickListener(v -> {
            pin[0] = false;
            styleChip(chipAuto, true);
            styleChip(chipManual, false);
            pinWhen.setVisibility(View.GONE);
        });
        Runnable pickPin = () -> {
            Calendar cal = Calendar.getInstance();
            if (pinDay[0].length() >= 8) {
                try {
                    String[] p = pinDay[0].split("-");
                    cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
            }
            new DatePickerDialog(this, (vv, y, m, day) -> {
                pinDay[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day);
                pickTime(pinMin[0], min -> {
                    pinMin[0] = min;
                    pinWhen.setText(pinDay[0] + "  " + Planner.minToTime(pinMin[0]));
                    styleChip(pinWhen, true);
                });
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        };
        chipManual.setOnClickListener(v -> {
            pin[0] = true;
            styleChip(chipAuto, false);
            styleChip(chipManual, true);
            pinWhen.setVisibility(View.VISIBLE);
            pickPin.run();
        });
        pinWhen.setOnClickListener(v -> pickPin.run());
        pinRow.addView(chipAuto, chipLp());
        pinRow.addView(space(dp(8)));
        pinRow.addView(chipManual, chipLp());
        moreBox.addView(pinRow);
        moreBox.addView(space(dp(6)));
        moreBox.addView(pinWhen);

        moreBox.addView(label("اليوم المفضل للمحاضرة (اختياري)"));
        final int[] prefDow = {existing != null ? existing.preferredDow : 0};
        LinearLayout prefRow = new LinearLayout(this);
        prefRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView prefNone = chip("بدون", prefDow[0] == 0);
        prefNone.setOnClickListener(v -> {
            prefDow[0] = 0;
            styleChip(prefNone, true);
            for (int i = 0; i < prefRow.getChildCount(); i++) {
                View ch = prefRow.getChildAt(i);
                if (ch instanceof TextView && ch != prefNone) styleChip((TextView) ch, false);
            }
        });
        prefRow.addView(prefNone, chipLp());
        // ترتيب My Plan: السبت أولًا
        for (int d : WEEK_ORDER) {
            final int dow = d;
            TextView c = chip(DAY_NAMES[d], prefDow[0] == d);
            c.setOnClickListener(v -> {
                prefDow[0] = dow;
                styleChip(prefNone, false);
                for (int i = 0; i < prefRow.getChildCount(); i++) {
                    View ch = prefRow.getChildAt(i);
                    if (ch instanceof TextView) styleChip((TextView) ch, ch == c);
                }
            });
            prefRow.addView(space(dp(4)));
            prefRow.addView(c, chipLp());
        }
        HorizontalScrollView prefScroll = new HorizontalScrollView(this);
        prefScroll.setHorizontalScrollBarEnabled(false);
        prefScroll.addView(prefRow);
        moreBox.addView(prefScroll);
        form.addView(space(dp(6)));
        moreBox.addView(muted("لو محدد: كل جلسات المحاضرة في اليوم ده فقط (قيد إلزامي)."));

        moreBox.addView(label("الموعد النهائي (اختياري)"));
        TextView deadlineTv = chip("بدون موعد", true);
        final String[] deadline = {""};
        deadlineTv.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (vv, y, m, day) -> {
                deadline[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day);
                deadlineTv.setText(deadline[0]);
                styleChip(deadlineTv, true);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        moreBox.addView(deadlineTv);

        if (existing != null) {
            name.setText(existing.name);
            subject.setText(existing.subject);
            durationEt.setText(String.valueOf(existing.durationMin));
            if (existing.deadline != null && !existing.deadline.isEmpty()) {
                deadline[0] = existing.deadline;
                deadlineTv.setText(existing.deadline);
            }
        } else {
            durationEt.setText("60");
        }

        form.addView(moreBox);
        ScrollView sv = new ScrollView(this);
        sv.addView(form);

        AlertDialog taskDlg = myDialog()
                .setTitle(existing == null ? "إضافة مهمة" : "تعديل مهمة")
                .setView(sv)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        taskDlg.setOnShowListener(di -> {
            taskDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                    Toast.makeText(this, "اكتب اسم المهمة", Toast.LENGTH_SHORT).show();
                    return;
                }
                String sub = subjectHold[0] == null ? "" : subjectHold[0].trim();
                if (sub.isEmpty()) sub = "عام";
                int total = 60;
                try { total = Integer.parseInt(durationEt.getText().toString().trim()); } catch (Exception ignored) {}
                if (total < 5) total = 5;
                if (total > 20000) total = 20000;
                int sm = Math.max(10, planner.settings.sessionMin);
                int scount = Math.max(1, (int) Math.ceil(total / (double) sm));

                Planner.Task t = existing != null ? existing : new Planner.Task();
                boolean isNew = existing == null;
                int prevDuration = isNew ? 0 : existing.durationMin;
                t.name = n;
                t.subject = sub;
                t.durationMin = total;
                t.applySessionCount(scount);
                if (isNew) t.remainingMin = t.durationMin;
                else if (t.remainingMin > t.durationMin) t.remainingMin = t.durationMin;
                t.priority = pri[0];
                t.kind = kind[0] == Planner.Task.KIND_STUDY ? Planner.Task.KIND_STUDY : Planner.Task.KIND_LECTURE;
                t.backlog = backlog[0];
                t.deadline = deadline[0];
                t.pinStart = pin[0];
                t.pinDay = pin[0] ? pinDay[0] : "";
                t.pinStartMin = pinMin[0];
                t.preferredDow = prefDow[0];
                if (pin[0] && (t.pinDay == null || t.pinDay.isEmpty())) {
                    Toast.makeText(this, "اختار تاريخ ووقت البداية", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isNew) {
                    int maxOrd = 0;
                    for (Planner.Task x : planner.tasks) if (x.sortOrder > maxOrd) maxOrd = x.sortOrder;
                    t.sortOrder = maxOrd + 1;
                    planner.tasks.add(t);
                } else if (total != prevDuration) {
                    planner.syncTaskSessions(t);
                } else {
                    planner.updateTaskSessionMetadata(t);
                }
                planner.save();
                // لا نعيد بناء الجدول تلقائيًا — المستخدم يضغط «عدّل خطتي» لما يكون جاهز
                taskDlg.dismiss();
                showTab(1);
                Toast.makeText(this, isNew ? "تمت إضافة المهمة" : "تم الحفظ", Toast.LENGTH_SHORT).show();
            });
        });
        taskDlg.show();
    }


    private void showBatchAddTasks() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final String[] subjectHold = {planner.subjects.isEmpty() ? "عام" : planner.subjects.get(0).name};
        form.addView(label("المادة"));
        TextView subject = chip(subjectHold[0], true);
        subject.setOnClickListener(v -> pickSubject(subjectHold[0], name0 -> {
            subjectHold[0] = name0; subject.setText(name0);
        }));
        form.addView(subject);
        form.addView(label("الأسماء (سطر لكل مهمة)"));
        EditText namesEt = field();
        namesEt.setMinLines(5);
        namesEt.setHint("إنجليزي 1\nإنجليزي 2\nإنجليزي 3");
        form.addView(namesEt);
        form.addView(label("مدة كل مهمة (دقيقة)"));
        EditText durEt = field();
        durEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        durEt.setText("60");
        form.addView(durEt);
        final int[] pri = {1};
        final boolean[] backlog = {false};
        form.addView(label("النوع"));
        LinearLayout typeRow = new LinearLayout(this);
        TextView chipNew = chip("جديدة 🆕", true);
        TextView chipBack = chip("متراكمة 📚", false);
        chipNew.setOnClickListener(v2 -> { backlog[0] = false; styleChip(chipNew, true); styleChip(chipBack, false); });
        chipBack.setOnClickListener(v2 -> { backlog[0] = true; styleChip(chipNew, false); styleChip(chipBack, true); });
        typeRow.addView(chipNew, chipLp()); typeRow.addView(space(dp(6))); typeRow.addView(chipBack, chipLp());
        form.addView(typeRow);
        ScrollView sv = new ScrollView(this); sv.addView(form);
        AlertDialog dlg = myDialog()
                .setTitle("إضافة عدة مهام")
                .setView(sv)
                .setPositiveButton("حفظ الكل", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] lines = namesEt.getText().toString().split("\n");
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String line : lines) { String n = line.trim(); if (!n.isEmpty()) names.add(n); }
            if (names.isEmpty()) { Toast.makeText(this, "اكتب اسم مهمة واحد على الأقل", Toast.LENGTH_SHORT).show(); return; }
            int total = 60;
            try { total = Integer.parseInt(durEt.getText().toString().trim()); } catch (Exception ignored) {}
            if (total < 5) total = 5;
            int sm = Math.max(10, planner.settings.sessionMin);
            int scount = Math.max(1, (int) Math.ceil(total / (double) sm));
            String sub = subjectHold[0] == null || subjectHold[0].isEmpty() ? "عام" : subjectHold[0].trim();
            int maxOrd = 0;
            for (Planner.Task x : planner.tasks) if (x.sortOrder > maxOrd) maxOrd = x.sortOrder;
            int added = 0;
            for (String n : names) {
                Planner.Task t = new Planner.Task();
                t.name = n; t.subject = sub; t.durationMin = total;
                t.applySessionCount(scount); t.remainingMin = t.durationMin;
                t.priority = pri[0]; t.backlog = backlog[0]; t.sortOrder = ++maxOrd;
                planner.tasks.add(t); added++;
            }
            planner.save();
            // لا نعيد بناء الجدول تلقائيًا بعد الإضافة الجماعية
            dlg.dismiss();
            showTab(1);
            Toast.makeText(this, "تمت إضافة " + added + " مهام · عدّل خطتي لما تجهز", Toast.LENGTH_SHORT).show();
        }));
        dlg.show();
    }

    // ═══════════════ Stats ═══════════════

    private float sessionCompletionRatio(String fromDay, String toDay) {
        int tot = 0, done = 0;
        for (Planner.Session s : planner.sessions) {
            if (s.day == null) continue;
            if (fromDay != null && s.day.compareTo(fromDay) < 0) continue;
            if (toDay != null && s.day.compareTo(toDay) > 0) continue;
            tot++;
            if (s.done) done++;
        }
        return tot <= 0 ? 0f : (float) done / (float) tot;
    }

    private String[] currentWeekRangeInclusive() {
        Calendar c = Calendar.getInstance();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int daysFromSat;
        if (dow == Calendar.SATURDAY) daysFromSat = 0;
        else daysFromSat = dow; // Sun=1 .. Fri=6
        Calendar start = (Calendar) c.clone();
        start.add(Calendar.DAY_OF_MONTH, -daysFromSat);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return new String[]{ f.format(start.getTime()), f.format(end.getTime()) };
    }

    /** نطاق مخصص فعلي: planDays > 7 أو وجود جلسات خارج أسبوع السبت→الجمعة الحالي. */
    private boolean hasCustomPlanSessions() {
        if (planner.settings != null && planner.settings.planDays > 7) {
            for (Planner.Session s : planner.sessions) {
                if (s.day != null) return true;
            }
        }
        String[] wr = currentWeekRangeInclusive();
        for (Planner.Session s : planner.sessions) {
            if (s.day == null) continue;
            if (s.day.compareTo(wr[0]) < 0 || s.day.compareTo(wr[1]) > 0) return true;
        }
        return false;
    }

    /** إنجاز جلسات النطاق المخصص: من اليوم لمدة planDays (أو خارج الأسبوع إن لزم). */
    private float customPlanCompletionRatio() {
        String today = Planner.todayStr();
        int n = Math.max(1, planner.settings == null ? 1 : planner.settings.planDays);
        String endDay = today;
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(Planner.DAY.parse(today));
            c.add(Calendar.DAY_OF_MONTH, n - 1);
            endDay = Planner.DAY.format(c.getTime());
        } catch (Exception ignored) {}
        int tot = 0, done = 0;
        for (Planner.Session s : planner.sessions) {
            if (s.day == null) continue;
            if (s.day.compareTo(today) < 0 || s.day.compareTo(endDay) > 0) continue;
            tot++;
            if (s.done) done++;
        }
        if (tot == 0) {
            // fallback: جلسات خارج الأسبوع الحالي
            String[] wr = currentWeekRangeInclusive();
            for (Planner.Session s : planner.sessions) {
                if (s.day == null) continue;
                if (s.day.compareTo(wr[0]) >= 0 && s.day.compareTo(wr[1]) <= 0) continue;
                tot++;
                if (s.done) done++;
            }
        }
        return tot <= 0 ? 0f : (float) done / (float) tot;
    }

    private View miniGaugeBlock(String label, float progress) {
        // عمود واحد: Gauge ثم الاسم مباشرة تحته — هذا هو الـLayout الظاهر في الإحصائيات
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.setVisibility(View.VISIBLE);

        DayGaugeView g = new DayGaugeView(this);
        g.setProgress(progress);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(100), dp(100));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(g, glp);

        TextView lab = new TextView(this);
        lab.setText(label); // «اليوم» / «الأسبوع» / «مخصص»
        lab.setTextColor(0xFFFFFFFF);
        lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setGravity(Gravity.CENTER_HORIZONTAL);
        lab.setVisibility(View.VISIBLE);
        lab.setAlpha(1f);
        lab.setPadding(0, dp(8), 0, dp(4));
        lab.setSingleLine(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        col.addView(lab, llp);
        return col;
    }

    private View buildStatsScreen() {
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(28));box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);sc.addView(box);
        TextView h=title("الإحصائيات");h.setTextSize(32);box.addView(h);box.addView(space(dp(14)));
        planner.refreshRemainingFromSessions();int total=planner.totalMinutes();int done=Math.max(0,Math.min(total,planner.doneMinutes()));int pct=total>0?Math.max(0,Math.min(100,Math.round(100f*done/total))):0;
        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(18),dp(12),dp(18),dp(12));
        GradientDrawable hb=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF405FEA,0xFF7146E8});hb.setCornerRadius(dp(24));hero.setBackground(hb);
        ProgressHeroGauge gauge=new ProgressHeroGauge(this);gauge.setProgress(pct);hero.addView(gauge,new LinearLayout.LayoutParams(dp(104),dp(104)));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);ht.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        TextView s=muted("نسبة الإنجاز الكلية");s.setTextColor(0xCCFFFFFF);s.setGravity(Gravity.RIGHT);s.setTextSize(13);ht.addView(s);
        TextView msg=new TextView(this);msg.setText(progressMessage(pct));msg.setTextColor(Color.WHITE);msg.setTextSize(22);msg.setTypeface(Typeface.DEFAULT_BOLD);msg.setGravity(Gravity.RIGHT);ht.addView(msg);
        hero.addView(ht,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));box.addView(hero);box.addView(space(dp(12)));
        LinearLayout metrics=new LinearLayout(this);metrics.setGravity(Gravity.CENTER);
        int hours=Math.max(0,planner.doneMinutes())/60,finished=0;for(Planner.Session ss:planner.sessions)if(ss!=null&&ss.done)finished++;
        int remainingTasks=0; for(Planner.Task tt:planner.tasks) if(tt!=null&&!tt.done) remainingTasks++;
        metrics.addView(statMetricCard("المذاكرة",String.valueOf(hours)));metrics.addView(space(dp(8)));metrics.addView(statMetricCard("المهام المكتملة",String.valueOf(finished)));metrics.addView(space(dp(8)));metrics.addView(statMetricCard("المهام المتبقية",String.valueOf(remainingTasks)));box.addView(metrics);
        box.addView(space(dp(14)));LinearLayout chart=card();TextView ct=muted("آخر 7 أيام");ct.setTextColor(TEXT);ct.setTextSize(16);ct.setTypeface(Typeface.DEFAULT_BOLD);ct.setGravity(Gravity.RIGHT);chart.addView(ct);chart.addView(space(dp(8)));
        SevenDayProgressChart ch=new SevenDayProgressChart(this);ch.setDays(lastSevenDayProgress(),lastSevenDayLabels());chart.addView(ch,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(180)));box.addView(chart);
        return sc;
    }

    private String progressMessage(int pct) {
        if (pct <= 10) return "لسه أول الطريق.";
        if (pct <= 20) return "البداية بدأت تؤتي ثمارها.";
        if (pct <= 30) return "مستواك بيتحسن.";
        if (pct <= 40) return "خطواتك بتجيب نتيجة.";
        if (pct <= 50) return "شغلك واضح.";
        if (pct <= 60) return "تقدّم قوي.";
        if (pct <= 70) return "أداء ممتاز.";
        if (pct <= 80) return "الهدف قريب.";
        if (pct <= 90) return "فاضل القليل.";
        return "وصلت لهدفك 🏆";
    }

    private View statMetricCard(String labelText, String value) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8), dp(10), dp(8), dp(10));
        c.setLayoutParams(new LinearLayout.LayoutParams(0, dp(82), 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(ACCENT);
        v.setTextSize(25);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        c.addView(v);
        TextView l = new TextView(this);
        l.setText(labelText);
        l.setTextColor(TEXT);
        l.setTextSize(12);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        return c;
    }

    private int[] lastSevenDayProgress() {
        int[] values = new int[7];
        Calendar base = Calendar.getInstance();
        base.set(Calendar.HOUR_OF_DAY, 0);
        base.set(Calendar.MINUTE, 0);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);
        for (int i = 6; i >= 0; i--) {
            Calendar d = (Calendar)base.clone();
            d.add(Calendar.DAY_OF_MONTH, -i);
            String day = Planner.DAY.format(d.getTime());
            int total = 0, done = 0;
            for (Planner.Session ss : planner.sessions) {
                if (ss == null || ss.day == null || !day.equals(ss.day)) continue;
                total++;
                if (ss.done) done++;
            }
            values[6-i] = total == 0 ? 0 : Math.max(0, Math.min(100, Math.round(done * 100f / total)));
        }
        return values;
    }

    private static class ProgressHeroGauge extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int percent;
        ProgressHeroGauge(android.content.Context c) { super(c); }
        void setProgress(int v) { percent = Math.max(0, Math.min(100, v)); invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(getWidth(),getHeight())*.38f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(getContext(),10));
            p.setStrokeCap(Paint.Cap.ROUND);
            rect.set(cx-r,cy-r,cx+r,cy+r);

            p.setColor(0x664B6DFF);
            p.setShader(null);
            canvas.drawArc(rect,-90,360,false,p);

            // 10 درجات ثابتة: لون واضح لكل شريحة من 10%، والرقم في المنتصف يأخذ نفس لون مستواه.
            int[] colors = new int[]{
                    0xFFFF1744, 0xFFFF3D00, 0xFFFF6D00, 0xFFFFAB00, 0xFFFFD600,
                    0xFFFFFF00, 0xFFB2FF00, 0xFF64DD17, 0xFF00E676, 0xFF00FF87
            };
            float[] pos = new float[]{0f,.1f,.2f,.3f,.4f,.5f,.6f,.7f,.8f,.9f};
            p.setShader(new SweepGradient(cx,cy,colors,pos));
            canvas.drawArc(rect,-90,Math.max(1f,percent*3.6f),false,p);
            p.setShader(null);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(getContext(),23));
            Paint.FontMetrics fm=p.getFontMetrics();
            canvas.drawText(percent+"%",cx,cy-(fm.ascent+fm.descent)/2f,p);
        }
        private static float dp(android.content.Context c,float v){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,c.getResources().getDisplayMetrics());}
    }

    private String[] lastSevenDayLabels() {
        String[] labels = new String[7];
        String[] names = {"الأحد","الإثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"};
        Calendar base = Calendar.getInstance();
        base.set(Calendar.HOUR_OF_DAY,0); base.set(Calendar.MINUTE,0); base.set(Calendar.SECOND,0); base.set(Calendar.MILLISECOND,0);
        for (int i=6;i>=0;i--) {
            Calendar d=(Calendar)base.clone();
            d.add(Calendar.DAY_OF_MONTH,-i);
            labels[6-i]=names[Math.max(1,d.get(Calendar.DAY_OF_WEEK))-1];
        }
        return labels;
    }

    private static class SevenDayProgressChart extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] values=new int[7];
        private String[] names={"","","","","","",""};
        SevenDayProgressChart(android.content.Context c){super(c);}
        void setDays(int[] v,String[] labels){
            if(v!=null&&v.length==7)values=v.clone();
            if(labels!=null&&labels.length==7)names=labels.clone();
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight();
            float left=dp(getContext(),8),right=w-left,top=dp(getContext(),6),bottom=h-dp(getContext(),28);
            float gap=dp(getContext(),9),barW=(right-left-gap*6)/7f,barH=bottom-top;

            for(int d=0;d<7;d++){
                float x=left+d*(barW+gap);
                // الامتلاء يقفز على درجات 10% لكن يظل عمودًا واحدًا متصلًا.
                int level=Math.max(0,Math.min(10,(values[d]+5)/10));
                float filled=barH*(level/10f);

                p.setStyle(Paint.Style.FILL);
                p.setColor(0x224B6DFF);
                canvas.drawRoundRect(x,top,x+barW,bottom,dp(getContext(),5),dp(getContext(),5),p);

                if(filled>0){
                    p.setColor(ACCENT);
                    canvas.drawRoundRect(x,bottom-filled,x+barW,bottom,dp(getContext(),5),dp(getContext(),5),p);
                }

                p.setColor(TEXT);
                p.setTextSize(dp(getContext(),9));
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(names[d],x+barW/2f,h-dp(getContext(),8),p);
            }
        }
        private static float dp(android.content.Context c,float v){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,c.getResources().getDisplayMetrics());}
    }

    private View statRow(String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView left = muted(k);
        TextView right = new TextView(this);
        right.setText(v);
        right.setTextColor(TEXT);
        right.setTextSize(13);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(right);
        return row;
    }

    // ═══════════════ Routine ═══════════════

    private void showUserGuide() {
        guideMode = 1;
        guideTopic = -1;
        showTab(4);
    }

    private void openGuideTopic(int index) {
        guideMode = 2;
        guideTopic = index;
        showTab(4);
    }

    private void closeUserGuide() {
        guideMode = 0;
        guideTopic = -1;
        showTab(4);
    }

    private String[] guideSections() {
        return new String[] {
                "كيف يعمل الجدول؟",
                "إنشاء خطتي",
                "اليوم · الأسبوع · مخصص",
                "طريقة التوزيع: كل يوم جزء من كل محاضرة",
                "طريقة التوزيع: جلسات المحاضرة ورا بعض",
                "طريقة التوزيع: محاضرة واحدة كل يوم",
                "القيود التي يحترمها الجدول",
                "الجلسات بعد إنشاء الجدول",
                "ساعات المذاكرة المرنة",
                "مواعيد نزول المحاضرات"
        };
    }

    private String[] guideBodies() {
        return new String[] {
                "الجدول يُبنى من مهامك (المحاضرات) + الإعدادات (النوم، التجهيز، الراحة، أيام الراحة، الالتزامات) عند الضغط على «إنشاء خطتي».\n\nالتطبيق يحسب المدة المتبقية لكل مهمة، يرتّب المهام حسب الأولوية وقرب الامتحان والقواعد الحالية، ثم يوزّع الجلسات على عدد الأيام الذي تختاره.\n\nعرض «اليوم / الأسبوع / مخصص» يعرض الجلسات فقط ولا يعيد حساب الخطة.",
                "من صفحة الجدول اضغط «إنشاء خطتي».\nتختار:\n1) عدد الأيام التي توزَّع عليها المهام.\n2) طريقة توزيع المحاضرات (واحدة من الثلاث أدناه).\n\nبعد التأكيد يُعاد بناء الجلسات غير المكتملة/غير المثبتة وفق المنطق الحالي، مع احترام النوم والالتزامات وأيام الراحة والأيام المفضلة/المثبتة إن وُجدت.",
                "• اليوم: كل جلسات تاريخ اليوم فقط.\n• الأسبوع: من السبت إلى الجمعة للأسابيع الحالية، بأسماء الأيام كاملة.\n• مخصص: نافذة بعدد أيام الخطة التي اخترتها في «إنشاء خطتي» (planDays).\nالتبديل بين العروض لا يغيّر طريقة التوزيع ولا يعيد إنشاء الجدول.",
                "الاسم داخل التطبيق: «كل يوم جزء من كل محاضرة» (وضع التوزيع 0).\n\nالمعنى الفعلي:\n• يُحسب المتبقي لكل محاضرة.\n• تُقسَّم مدة كل محاضرة على أيام العمل المتاحة قدر الإمكان (توزيع متوازي).\n• في نفس اليوم قد تظهر أجزاء من محاضرات مختلفة.\n• لا يُشترط إنهاء محاضرة كاملة قبل بدء أخرى.\n• يظل يحترم سعة اليوم (وقت متاح بعد النوم/الالتزامات) والقيود الأقوى.",
                "الاسم داخل التطبيق: «جلسات المحاضرة ورا بعض» (وضع التوزيع 1).\n\nالمعنى الفعلي في الكود الحالي:\n• يُحسب إجمالي المدة المتبقية لكل المحاضرات ÷ عدد أيام العمل ≈ هدف يومي بالدقائق.\n• تُملأ الأيام بالتتابع: تُنهى محاضرة A (كل حصصها المتبقية بالترتيب) قبل الانتقال إلى B، وهكذا.\n• ممنوع نمط تداخل مثل A→B→A لنفس التوزيع.\n• إن لم تكتمل A في يوم، اليوم التالي يكمل A أولًا.\n• الـGap الطبيعي (التزام/صلاة/جلسة أخرى) مسموح؛ التتابع منطقي وليس لصقًا بلا فجوات.\n• أي متبقٍ بسبب قيد يوم يُكمَل في أيام لاحقة بنفس الترتيب.",
                "الاسم داخل التطبيق: «محاضرة واحدة في اليوم» (وضع التوزيع 2).\n\nالمعنى الفعلي:\n• يُفضَّل تخصيص يوم عمل لمحاضرة واحدة حتى تنتهي مدتها المتبقية في ذلك اليوم (ضمن السعة).\n• لا تُخلط محاضرتان في نفس اليوم طالما ما زالت المحاضرة الحالية تحتاج وقتًا واليوم ما زال مخصصًا لها.\n• إن احتاجت المحاضرة أكثر من يوم، تُكمَل في أيام لاحقة قبل فتح يوم لمحاضرة جديدة حسب القواعد الحالية.\n• يحترم سعة اليوم والقيود الثابتة كباقي الأوضاع.",
                "مهما كانت طريقة التوزيع، الجدول لا يكسر:\n• وقت الاستيقاظ + التجهيز الصباحي → أول دراسة.\n• وقت النوم → نهاية نافذة الدراسة.\n• أيام الراحة.\n• الالتزامات والأوقات غير المتاحة.\n• الجلسات المكتملة والمثبتة يدويًا (مثل بعد السحب الحر).\n• قيود اليوم المفضل / أيام المادة عند تفعيلها.\n\nطريقة التوزيع تغيّر ترتيب وتعبئة الحصص فقط داخل هذه الحدود.",
                "بعد إنشاء الجدول تظهر الجلسات في اليوم/الأسبوع/مخصص.\nمن بطاقة الجلسة (بدون تعديل/حذف المهمة من هنا):\n• ابدأ · تم الإنجاز · نقل · المدة · وإعادة جدولة إن كانت فائتة.\nتعديل بيانات المهمة أو حذفها يتم من تبويب «المهام» فقط.",
                "من المزيد → التخطيط → ساعات المذاكرة يمكنك (اختياريًا) تحديد حد أدنى ومستهدف وحد أقصى لساعات المذاكرة اليومية.\n\n• إن لم تفعّل الميزة: التخطيط يعمل كالمعتاد دون أي سقف إضافي.\n• إن فعّلتها: يُطبَّق سقف ناعم عند الحد الأقصى فقط حتى لا يُحمَّل اليوم بأكثر مما اخترت، مع احترام النوم والالتزامات والراحة.\n• الهدف والحد الأدنى توجيهان مرنان حسب ازدحام اليوم وليسا رقمًا إجباريًا كل يوم.\n• لا تُكسر مواعيد النوم أو الالتزامات بسبب هذه الإعدادات.",
                "من المزيد → التخطيط → مواعيد نزول المحاضرات تربط المادة بأيام نزول (يمكن أكثر من يوم) ووقت اختياري.\n\n• موعد النزول هدف مرن (Soft) وليس Deadline صارمًا.\n• عند التفعيل فقط: ترتفع أولوية المحاضرات تدريجيًا قبل يوم النزول لاستغلال الأيام الأفرغ.\n• إن لم يُضبط شيء: ترتيب المحاضرات يبقى كما هو في النظام الحالي.\n• عدم إنهاء المحاضرة قبل النزول لا يُفشل الخطة؛ تبقى في المتبقي/Backlog وتُوزَّع لاحقًا.\n• المحاضرات الجديدة لا تلغي القديمة."
        };
    }

    private View buildUserGuideContent() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        String[] sections = guideSections();
        String[] bodies = guideBodies();

        if (guideMode == 2 && guideTopic >= 0 && guideTopic < sections.length) {
            TextView back = link("→ رجوع لقائمة الدليل");
            back.setOnClickListener(v -> {
                guideMode = 1;
                guideTopic = -1;
                showTab(4);
            });
            box.addView(back);
            box.addView(space(dp(12)));
            TextView h = title(sections[guideTopic]);
            box.addView(h);
            box.addView(space(dp(8)));
            TextView body = new TextView(this);
            body.setText(bodies[guideTopic]);
            body.setTextColor(TEXT);
            body.setTextSize(14);
            body.setLineSpacing(dp(4), 1.15f);
            box.addView(body);
            box.addView(space(dp(20)));
            TextView close = chip("إغلاق الدليل", false);
            close.setOnClickListener(v -> closeUserGuide());
            box.addView(close);
            return sc;
        }

        // قائمة المواضيع
        TextView backMore = link("→ رجوع للمزيد");
        backMore.setOnClickListener(v -> closeUserGuide());
        box.addView(backMore);
        box.addView(space(dp(8)));
        box.addView(title("دليل المستخدم"));
        box.addView(muted("اختر موضوعًا لمعرفة معناه ومتى تستخدمه."));
        box.addView(space(dp(12)));
        for (int i = 0; i < sections.length; i++) {
            final int idx = i;
            TextView row = chip(sections[i], false);
            row.setOnClickListener(v -> openGuideTopic(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            box.addView(row, lp);
        }
        return sc;
    }

    private View buildRoutineScreen() {
        if(showingContactUs)return buildContactUsScreen();

        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView h=title("المزيد");
        h.setTextSize(32);
        box.addView(h);
        box.addView(space(dp(14)));

        // الإعدادات الأساسية — كل قسم مستقل وواضح.
        String[] keys={"subjects","prayer","sleep","planning","sessions","alarms","guide"};
        String[] labels={"المواد","الصلاة","النوم","التخطيط","الجلسات","التنبيهات","دليل المستخدم"};

        for(int i=0;i<keys.length;i++){
            final String key=keys[i];
            final String rowLabel=labels[i];
            LinearLayout section=card();
            section.setOrientation(LinearLayout.VERTICAL);

            boolean open=moreOpenSections.contains(key);
            TextView row=new TextView(this);
            row.setText((open?"▾  ":"‹  ")+rowLabel);
            row.setTextColor(open?ACCENT:TEXT);
            row.setTextSize(17);
            row.setTypeface(open?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
            row.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
            row.setPadding(dp(10),dp(16),dp(10),dp(16));
            section.addView(row);

            if(open){
                View body=null;
                if("subjects".equals(key)) body=moreSubjectsBody();
                else if("prayer".equals(key)) body=morePrayerBody();
                else if("sleep".equals(key)) body=moreSleepBody();
                else if("planning".equals(key)) body=morePlanBody();
                else if("sessions".equals(key)) body=moreSessionsBody();
                else if("alarms".equals(key)) body=moreAlarmsBody();
                else if("guide".equals(key)) body=moreGuideBody();

                if(body!=null){
                    View divider=new View(this);
                    divider.setBackgroundColor(0x142B3545);
                    section.addView(divider,new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,1));
                    section.addView(space(dp(6)));
                    section.addView(body);
                    section.addView(space(dp(10)));
                }
            }

            row.setOnClickListener(v->{
                if(moreOpenSections.contains(key)) moreOpenSections.remove(key);
                else {
                    moreOpenSections.clear();
                    moreOpenSections.add(key);
                }
                showTab(4);
            });

            box.addView(section);
            box.addView(space(dp(9)));
        }

        // تواصل معنا في خانة مستقلة.
        LinearLayout contact=card();
        contact.setOrientation(LinearLayout.HORIZONTAL);
        contact.setGravity(Gravity.CENTER_VERTICAL);
        TextView ct=new TextView(this);
        ct.setText("تواصل معنا");
        ct.setTextColor(TEXT);
        ct.setTextSize(17);
        ct.setTypeface(Typeface.DEFAULT_BOLD);
        contact.addView(ct,new LinearLayout.LayoutParams(0,dp(60),1f));
        TextView ca=muted("‹");
        ca.setTextSize(24);
        contact.addView(ca,new LinearLayout.LayoutParams(dp(34),dp(60)));
        contact.setOnClickListener(v->openContactUsScreen());
        box.addView(contact);
        box.addView(space(dp(9)));

        // الحساب — عند فتحه يظهر زر تسجيل الخروج بوضوح.
        LinearLayout account=card();
        account.setOrientation(LinearLayout.VERTICAL);
        account.setPadding(dp(14),dp(10),dp(14),dp(10));

        LinearLayout accountHead=new LinearLayout(this);
        accountHead.setOrientation(LinearLayout.HORIZONTAL);
        accountHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView ac=new TextView(this);
        ac.setText("الحساب");
        ac.setTextColor(TEXT);
        ac.setTextSize(17);
        ac.setTypeface(Typeface.DEFAULT_BOLD);
        accountHead.addView(ac,new LinearLayout.LayoutParams(0,dp(54),1f));
        TextView accountState=muted(AccountAuth.getCurrentAccount(this)==null?"تسجيل الدخول":"الحساب مفتوح");
        accountHead.addView(accountState);
        account.addView(accountHead);

        account.setOnClickListener(v->{
            AccountAuth.Account acc=AccountAuth.getCurrentAccount(this);
            if(acc==null){
                showLocalLoginDialog();
                return;
            }
            String name=(acc.displayName!=null&&!acc.displayName.trim().isEmpty())?acc.displayName.trim():"—";
            String email=(acc.email!=null&&!acc.email.isEmpty())?acc.email:"—";
            String uid=(acc.userId!=null&&!acc.userId.isEmpty())?acc.userId:"—";
            myDialog().setTitle("الحساب")
                    .setMessage("الاسم: "+name+"\nالبريد: "+email+"\nمعرّف الحساب: "+uid)
                    .setPositiveButton("تسجيل الخروج", (d,w)->showLogoutWithBackupPrompt())
                    .setNegativeButton("إغلاق",null)
                    .show();
        });
        box.addView(account);
        box.addView(space(dp(14)));

        // الإصدار ظاهر دائمًا في أسفل الصفحة؛ الضغط المطوّل فقط يفتح مركز المطور.
        TextView ver=muted("My Plan · الإصدار "+getAppVersionName());
        ver.setGravity(Gravity.CENTER);
        ver.setTextSize(12);
        ver.setTextColor(MUTED);
        ver.setPadding(0,dp(8),0,dp(8));
        ver.setSingleLine(false);
        ver.setOnLongClickListener(v->{promptDeveloperPassword();return true;});
        box.addView(ver);

        return sc;
    }

    private String getAppVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(Exception e){ return "1.0"; }
    }



    private LinearLayout moreExpandableCard(String key, String titleAr, View body) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        boolean open = moreOpenSections.contains(key);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView t = new TextView(this);
        t.setText((open ? "▾  " : "▸  ") + titleAr);
        t.setTextColor(TEXT);
        t.setTextSize(16);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(t, tp);
        card.addView(header);

        if (open && body != null) {
            card.addView(space(dp(10)));
            card.addView(body);
        }
        header.setClickable(true);
        header.setOnClickListener(v -> {
            if (moreOpenSections.contains(key)) moreOpenSections.remove(key);
            else moreOpenSections.add(key);
            showTab(4);
        });
        card.setClickable(true);
        card.setOnClickListener(v -> header.performClick());
        return card;
    }

    private View moreSubjectsBody() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.addView(subjectsCard());
        return b;
    }

    private View morePrayerBody() {
        LinearLayout prayCard = new LinearLayout(this);
        prayCard.setOrientation(LinearLayout.VERTICAL);
        String prayStatus = planner.settings.prayersEnabled ? "تشغيل" : "إيقاف";
        TextView praySummary = new TextView(this);
        praySummary.setTextColor(TEXT);
        praySummary.setTextSize(14);
        praySummary.setText("الحالة: " + prayStatus + " · المدة: " + planner.settings.prayerDurationMin + " د");
        prayCard.addView(praySummary);
        prayCard.addView(space(dp(4)));
        if (planner.settings.hasLocation) {
            String nmCard = planner.settings.locationName;
            if (nmCard == null || nmCard.isEmpty())
                nmCard = resolveLocationName(planner.settings.latitude, planner.settings.longitude);
            prayCard.addView(muted("الموقع: " + nmCard));
            prayCard.addView(muted(planner.settings.egyptSummerTime ? "التوقيت: صيفي (+3)" : "التوقيت: شتوي (+2)"));
        } else {
            prayCard.addView(muted("الموقع غير متاح — افتح الإعدادات واختر «استخدم موقعي»."));
        }
        prayCard.addView(space(dp(8)));
        TextView prayOpen = chip("إعدادات الصلاة", true);
        prayOpen.setOnClickListener(v -> showPrayerSettingsDialog());
        prayCard.addView(prayOpen);
        return prayCard;
    }

    private View moreSleepBody() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.addView(muted("مواعيد النوم"));
        b.addView(space(dp(6)));
        TextView wakeTv = chip("استيقاظ: " + Planner.minToTime(planner.settings.wakeMin), true);
        wakeTv.setOnClickListener(v -> pickTime(planner.settings.wakeMin, m -> {
            planner.settings.wakeMin = m;
            planner.save();
            showTab(4);
        }));
        b.addView(wakeTv);
        b.addView(space(dp(6)));
        TextView sleepTv = chip("نوم: " + Planner.minToTime(planner.settings.sleepMin), true);
        sleepTv.setOnClickListener(v -> pickTime(planner.settings.sleepMin, m -> {
            planner.settings.sleepMin = m;
            planner.save();
            showTab(4);
        }));
        b.addView(sleepTv);
        b.addView(space(dp(12)));
        b.addView(muted("التجهيز بعد الاستيقاظ"));
        int prepNow = Planner.normalizeMorningPrep(planner.settings.morningPrepMin);
        TextView prepTv = chip("التجهيز: " + prepNow + " د", true);
        prepTv.setOnClickListener(v -> showMorningPrepDialog());
        b.addView(prepTv);
        b.addView(space(dp(12)));
        b.addView(muted("استثناءات النوم"));
        TextView exBtn = chip("إدارة استثناءات النوم", false);
        exBtn.setOnClickListener(v -> pickExceptionDays());
        b.addView(exBtn);
        if (!planner.settings.sleepExceptions.isEmpty()) {
            b.addView(space(dp(4)));
            b.addView(muted("عدد الاستثناءات: " + planner.settings.sleepExceptions.size()));
        }
        return b;
    }

    private View morePlanBody() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);

        b.addView(muted("أيام الراحة"));
        TextView restBtn = chip("تحديد أيام الراحة", false);
        restBtn.setOnClickListener(v -> pickRestDays());
        b.addView(restBtn);
        if (!planner.settings.restDays.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String[] names = {"", "أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت"};
            for (int d = 1; d <= 7; d++) {
                if (planner.settings.restDays.contains(d)) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(names[d]);
                }
            }
            b.addView(muted(sb.toString()));
        }
        b.addView(space(dp(12)));

        b.addView(muted("ساعات المذاكرة (اختياري)"));
        String st = planner.settings.studyTimeEnabled
                ? ("مفعّل · أدنى " + (planner.settings.studyMinMin / 60f) + "س · هدف "
                + (planner.settings.studyTargetMin / 60f) + "س · أقصى "
                + (planner.settings.studyMaxMin / 60f) + "س")
                : "غير مفعّل — التخطيط يعمل كالمعتاد";
        b.addView(muted(st));
        TextView studyBtn = chip("ضبط ساعات المذاكرة", true);
        studyBtn.setOnClickListener(v -> showStudyTimeSettingsDialog());
        b.addView(studyBtn);
        b.addView(space(dp(12)));

        b.addView(muted("مواعيد نزول المحاضرات (اختياري)"));
        b.addView(muted(planner.settings.lectureReleaseEnabled
                ? "مفعّل — فترة مشغولة (Busy) في أيام/أوقات النزول"
                : "غير مفعّل — ترتيب المحاضرات كالمعتاد"));
        TextView relBtn = chip("ضبط مواعيد النزول", true);
        relBtn.setOnClickListener(v -> showLectureReleaseSettingsDialog());
        b.addView(relBtn);
        return b;
    }

    private View moreSessionsBody() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        TextView sessLabel = new TextView(this);
        sessLabel.setTextColor(TEXT);
        sessLabel.setTextSize(14);
        sessLabel.setText("مدة الجلسة: " + planner.settings.sessionMin + " د · الراحة: " + planner.settings.breakMin + " د");
        b.addView(sessLabel);
        b.addView(space(dp(8)));
        TextView sessBtn = chip("تعديل مدة الجلسة", true);
        sessBtn.setOnClickListener(v -> showDefaultSessionDurationDialog());
        b.addView(sessBtn);
        b.addView(space(dp(6)));
        TextView brBtn = chip("تعديل مدة الراحة", false);
        brBtn.setOnClickListener(v -> showDefaultBreakDurationDialog());
        b.addView(brBtn);
        return b;
    }

    private View moreAlarmsBody() {
        LinearLayout alCard = new LinearLayout(this);
        alCard.setOrientation(LinearLayout.VERTICAL);
        TextView onOff = chip(planner.settings.alarmEnabled ? "التنبيهات: تشغيل" : "التنبيهات: إيقاف", planner.settings.alarmEnabled);
        onOff.setOnClickListener(v -> {
            planner.settings.alarmEnabled = !planner.settings.alarmEnabled;
            planner.save();
            showTab(4);
        });
        alCard.addView(onOff);
        alCard.addView(space(dp(6)));
        TextView vib = chip(planner.settings.alarmVibrate ? "اهتزاز: تشغيل" : "اهتزاز: إيقاف", planner.settings.alarmVibrate);
        vib.setOnClickListener(v -> {
            planner.settings.alarmVibrate = !planner.settings.alarmVibrate;
            planner.save();
            showTab(4);
        });
        alCard.addView(vib);
        alCard.addView(space(dp(6)));
        TextView sn = chip(planner.settings.alarmSnooze ? ("غفوة: " + planner.settings.snoozeMin + " د") : "غفوة: إيقاف", planner.settings.alarmSnooze);
        sn.setOnClickListener(v -> {
            planner.settings.alarmSnooze = !planner.settings.alarmSnooze;
            planner.save();
            showTab(4);
        });
        alCard.addView(sn);
        if (!planner.settings.alarmEnabled) {
            alCard.addView(space(dp(6)));
            alCard.addView(muted("التنبيهات متوقفة. الجدول نفسه لا يتأثر."));
        }
        return alCard;
    }

    private View moreAccountBody() {
        LinearLayout accBox = new LinearLayout(this);
        accBox.setOrientation(LinearLayout.VERTICAL);
        AccountAuth.Account curAcc = AccountAuth.getCurrentAccount(this);
        if (curAcc != null) {
            String dn = curAcc.displayName != null && !curAcc.displayName.isEmpty() ? curAcc.displayName : "—";
            accBox.addView(muted("الاسم: " + dn));
            accBox.addView(muted("البريد: " + (curAcc.email != null ? curAcc.email : "—")));
            accBox.addView(muted("المعرّف: " + curAcc.userId));
            accBox.addView(space(dp(6)));
            accBox.addView(muted("بيانات الدراسة تبقى على الجهاز."));
            accBox.addView(space(dp(8)));
            TextView logoutBtn = chip("تسجيل الخروج", false);
            logoutBtn.setOnClickListener(v -> showLogoutWithBackupPrompt());
            accBox.addView(logoutBtn);
        } else {
            accBox.addView(muted("حساب محلي على هذا الجهاز فقط."));
            accBox.addView(space(dp(8)));
            TextView reg = chip("إنشاء حساب", true);
            reg.setOnClickListener(v -> showLocalRegisterDialog());
            accBox.addView(reg);
            accBox.addView(space(dp(6)));
            TextView login = chip("تسجيل الدخول", false);
            login.setOnClickListener(v -> showLocalLoginDialog());
            accBox.addView(login);
        }
        return accBox;
    }

    private View morePremiumBody() {
        LinearLayout premBox = new LinearLayout(this);
        premBox.setOrientation(LinearLayout.VERTICAL);
        if (AppInfrastructure.isPremiumActive(this)) {
            premBox.addView(muted("Premium نشط — الميزات المحلية متاحة"));
            premBox.addView(space(dp(8)));
            TextView openPrem = chip("فتح مركز Premium", true);
            openPrem.setOnClickListener(v -> showTab(8));
            premBox.addView(openPrem);
        } else {
            premBox.addView(muted("استعرض ميزات Premium."));
            premBox.addView(space(dp(8)));
            TextView openPremFree = chip("فتح مركز Premium", true);
            openPremFree.setOnClickListener(v -> showTab(8));
            premBox.addView(openPremFree);
        }
        return premBox;
    }

    private View moreGuideBody() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.addView(muted("شرح عملي لكيف يخطّط My Plan يومك — وليس مجرد أماكن الأزرار."));
        b.addView(space(dp(8)));
        TextView open = chip("فتح الدليل", true);
        open.setOnClickListener(v -> showUserGuide());
        b.addView(open);
        return b;
    }

    private void showStudyTimeSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        final boolean[] en = {planner.settings.studyTimeEnabled};
        final android.widget.EditText minH = new android.widget.EditText(this);
        final android.widget.EditText tgtH = new android.widget.EditText(this);
        final android.widget.EditText maxH = new android.widget.EditText(this);
        minH.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        tgtH.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        maxH.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minH.setText(String.valueOf(Math.max(0, planner.settings.studyMinMin / 60)));
        tgtH.setText(String.valueOf(Math.max(0, planner.settings.studyTargetMin / 60)));
        maxH.setText(String.valueOf(Math.max(0, planner.settings.studyMaxMin / 60)));
        TextView enChip = chip(en[0] ? "تشغيل" : "إيقاف", en[0]);
        enChip.setOnClickListener(v -> {
            en[0] = !en[0];
            enChip.setText(en[0] ? "تشغيل" : "إيقاف");
            styleChip(enChip, en[0]);
        });
        form.addView(enChip);
        form.addView(space(dp(8)));
        form.addView(muted("الحد الأدنى (ساعات)"));
        form.addView(minH);
        form.addView(muted("المستهدف اليومي (ساعات)"));
        form.addView(tgtH);
        form.addView(muted("الحد الأقصى (ساعات)"));
        form.addView(maxH);
        form.addView(space(dp(6)));
        form.addView(muted("مرن حسب التزامات اليوم. لا يكسر النوم أو الالتزامات. إن أوقفت الميزة يعود التخطيط كالمعتاد."));
        myDialog().setTitle("ساعات المذاكرة")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        int min = Integer.parseInt(minH.getText().toString().trim()) * 60;
                        int tgt = Integer.parseInt(tgtH.getText().toString().trim()) * 60;
                        int max = Integer.parseInt(maxH.getText().toString().trim()) * 60;
                        if (min < 0) min = 0;
                        if (max < min) max = min;
                        if (tgt < min) tgt = min;
                        if (tgt > max) tgt = max;
                        planner.settings.studyTimeEnabled = en[0];
                        planner.settings.studyMinMin = min;
                        planner.settings.studyTargetMin = tgt;
                        planner.settings.studyMaxMin = max;
                        planner.settings.studyFlexible = true;
                        planner.save();
                        Toast.makeText(this, en[0] ? "تم حفظ ساعات المذاكرة" : "تم إيقاف ساعات المذاكرة", Toast.LENGTH_SHORT).show();
                        showTab(4);
                    } catch (Exception e) {
                        Toast.makeText(this, "أدخل أرقامًا صحيحة", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showLectureReleaseSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        final boolean[] en = {planner.settings.lectureReleaseEnabled};
        TextView enChip = chip(en[0] ? "تشغيل" : "إيقاف", en[0]);
        enChip.setOnClickListener(v -> {
            en[0] = !en[0];
            enChip.setText(en[0] ? "تشغيل" : "إيقاف");
            styleChip(enChip, en[0]);
        });
        form.addView(enChip);
        form.addView(space(dp(8)));
        form.addView(muted("اختر مادة ثم أيام النزول (يمكن أكثر من يوم). الوقت اختياري."));

        String[] subs = planner.subjectNames();
        if (subs.length == 0) {
            form.addView(muted("أضف مواد أولًا من قسم المواد."));
        }
        final String[] subHold = {subs.length > 0 ? subs[0] : ""};
        TextView subChip = chip(subHold[0].isEmpty() ? "لا توجد مواد" : subHold[0], true);
        if (subs.length > 0) {
            subChip.setOnClickListener(v -> myDialog().setItems(subs, (d, which) -> {
                subHold[0] = subs[which];
                subChip.setText(subHold[0]);
            }).setTitle("المادة").show());
        }
        form.addView(subChip);
        form.addView(space(dp(8)));

        // أيام الأسبوع: Calendar 1=أحد ... 7=سبت
        final boolean[] days = new boolean[8];
        // حمّل إن وُجدت
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    planner.settings.lectureReleaseJson == null ? "[]" : planner.settings.lectureReleaseJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (subHold[0].equalsIgnoreCase(o.optString("subject", ""))) {
                    org.json.JSONArray ds = o.optJSONArray("days");
                    if (ds != null) for (int j = 0; j < ds.length(); j++) {
                        int d = ds.getInt(j);
                        if (d >= 1 && d <= 7) days[d] = true;
                    }
                }
            }
        } catch (Exception ignored) {}

        String[] dayNames = {"", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.VERTICAL);
        for (int d = 1; d <= 7; d++) {
            final int dow = d;
            TextView ch = chip((days[dow] ? "☑ " : "☐ ") + dayNames[dow], days[dow]);
            ch.setOnClickListener(v2 -> {
                days[dow] = !days[dow];
                ch.setText((days[dow] ? "☑ " : "☐ ") + dayNames[dow]);
            });
            dayRow.addView(ch);
            dayRow.addView(space(dp(4)));
        }
        form.addView(dayRow);
        form.addView(space(dp(6)));
        final int[] timeMin = {-1};
        final int[] endTimeMin = {-1};
        TextView timeChip = chip("وقت البداية: اختياري (غير محدد)", false);
        TextView endChip = chip("وقت النهاية: +ساعتان افتراضيًا", false);
        timeChip.setOnClickListener(v -> pickTime(14 * 60, m -> {
            timeMin[0] = m;
            timeChip.setText("وقت البداية: " + Planner.minToTime(m));
            if (endTimeMin[0] < 0 || endTimeMin[0] <= m) {
                endTimeMin[0] = Math.min(m + 120, 24 * 60 - 1);
                endChip.setText("وقت النهاية: " + Planner.minToTime(endTimeMin[0]));
            }
        }));
        endChip.setOnClickListener(v -> {
            int def = timeMin[0] >= 0 ? Math.min(timeMin[0] + 120, 24 * 60 - 1) : 16 * 60;
            pickTime(def, m -> {
                endTimeMin[0] = m;
                endChip.setText("وقت النهاية: " + Planner.minToTime(m));
            });
        });
        form.addView(timeChip);
        form.addView(space(dp(4)));
        form.addView(endChip);
        form.addView(space(dp(6)));
        form.addView(muted("عند تحديد الوقت يُعامل كفترة مشغولة فقط (Busy) — الجلسات تُمنع داخل الفترة وتبقى متاحة قبلها وبعدها."));

        ScrollView sc = new ScrollView(this);
        sc.addView(form);
        myDialog().setTitle("مواعيد نزول المحاضرات")
                .setView(sc)
                .setPositiveButton("حفظ", (d, w) -> {
                    if (subHold[0].isEmpty()) {
                        Toast.makeText(this, "لا توجد مادة", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        int dayCount = 0;
                        for (int di = 1; di <= 7; di++) if (days[di]) dayCount++;
                        // إيقاف الميزة فقط — لا تلمس JSON القديم
                        if (!en[0]) {
                            planner.settings.lectureReleaseEnabled = false;
                            planner.save();
                            Toast.makeText(this, "تم إيقاف مواعيد النزول", Toast.LENGTH_SHORT).show();
                            showTab(4);
                            return;
                        }
                        // تشغيل + حفظ مادة: يلزم يوم واحد على الأقل
                        if (dayCount == 0) {
                            Toast.makeText(this, "اختر يومًا واحدًا على الأقل للمادة.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        org.json.JSONArray arr = new org.json.JSONArray(
                                planner.settings.lectureReleaseJson == null ? "[]" : planner.settings.lectureReleaseJson);
                        org.json.JSONArray next = new org.json.JSONArray();
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject o = arr.getJSONObject(i);
                            if (!subHold[0].equalsIgnoreCase(o.optString("subject", ""))) next.put(o);
                        }
                        org.json.JSONObject row = new org.json.JSONObject();
                        row.put("subject", subHold[0]);
                        row.put("enabled", true);
                        org.json.JSONArray ds = new org.json.JSONArray();
                        for (int di = 1; di <= 7; di++) if (days[di]) ds.put(di);
                        row.put("days", ds);
                        row.put("releaseTimeMin", timeMin[0]);
                        if (timeMin[0] >= 0) {
                            int end = endTimeMin[0] >= 0 && endTimeMin[0] > timeMin[0]
                                    ? endTimeMin[0] : timeMin[0] + 120;
                            row.put("endTimeMin", end);
                        }
                        next.put(row);
                        planner.settings.lectureReleaseJson = next.toString();
                        planner.settings.lectureReleaseEnabled = true;
                        planner.save();
                        Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show();
                        showTab(4);
                    } catch (Exception e) {
                        Toast.makeText(this, "تعذّر الحفظ", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }


    private void showMorningPrepDialog() {
        final int[] val = {Planner.normalizeMorningPrep(planner.settings.morningPrepMin)};
        final TextView lab = muted(val[0] + " دقيقة بعد الاستيقاظ");
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(60);
        sb.setProgress(val[0]);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean u) {
                val[0] = p;
                lab.setText(p + " دقيقة بعد الاستيقاظ");
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) {}
            public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.addView(lab);
        form.addView(sb);
        myDialog().setTitle("التجهيز الصباحي")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    planner.settings.morningPrepMin = Planner.normalizeMorningPrep(val[0]);
                    planner.save();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showDefaultSessionDurationDialog() {
        final int[] val = {Math.max(10, Math.min(200, planner.settings.sessionMin))};
        final TextView lab = muted(val[0] + " دقيقة");
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(190);
        sb.setProgress(val[0] - 10);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean u) {
                val[0] = p + 10;
                lab.setText(val[0] + " دقيقة");
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) {}
            public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.addView(lab);
        form.addView(sb);
        myDialog().setTitle("مدة الجلسة الافتراضية")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    planner.settings.sessionMin = val[0];
                    planner.save();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showDefaultBreakDurationDialog() {
        final int[] val = {Math.max(1, Math.min(200, planner.settings.breakMin))};
        final TextView lab = muted(val[0] + " دقيقة");
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(199);
        sb.setProgress(val[0] - 1);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean u) {
                val[0] = p + 1;
                lab.setText(val[0] + " دقيقة");
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) {}
            public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.addView(lab);
        form.addView(sb);
        myDialog().setTitle("مدة الراحة")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    planner.settings.breakMin = val[0];
                    planner.save();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    interface BoolTap { void apply(boolean on); }

    private View toggleRow(String label, boolean on, BoolTap cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(TEXT);
        lab.setTextSize(14);
        row.addView(lab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chip = chip(on ? "تشغيل" : "إيقاف", on);
        chip.setOnClickListener(v -> cb.apply(!on));
        row.addView(chip);
        return row;
    }


    private View planningCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(muted("الميول اليدوية. الكلام الحر من المخطط الذكي فوق."));
        card.addView(space(dp(8)));
        card.addView(muted("أسلوب التخطيط"));
        LinearLayout styles = new LinearLayout(this);
        styles.setOrientation(LinearLayout.HORIZONTAL);
        String[] keys = {"balanced", "exam", "new", "backlog"};
        String[] labs = {"متوازن", "امتحانات", "الجديد", "تراكم"};
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            TextView c = chip(labs[i], k.equals(planner.settings.planningStyle));
            c.setOnClickListener(v -> { planner.settings.planningStyle = k; planner.save(); showTab(4); });
            styles.addView(c, chipLp());
            if (i < 3) styles.addView(space(dp(4)));
        }
        card.addView(styles);
        card.addView(space(dp(8)));
        card.addView(muted("مستوى الضغط"));
        LinearLayout load = new LinearLayout(this);
        load.setOrientation(LinearLayout.HORIZONTAL);
        String[] ll = {"خفيف", "عادي", "أعلى"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView c = chip(ll[i], planner.settings.workload == i);
            c.setOnClickListener(v -> { planner.settings.workload = idx; planner.save(); showTab(4); });
            load.addView(c, chipLp());
            if (i < 2) load.addView(space(dp(6)));
        }
        card.addView(load);
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Planner.Focus f : planner.focuses) {
            if (!f.isActive(now)) continue;
            if (!any) { card.addView(space(dp(10))); card.addView(muted("أولويات شغالة دلوقتي")); any = true; }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView lab = muted(f.subject + "  ·  " + ("temporary".equals(f.scope) ? "مؤقت" : "حالي"));
            row.addView(lab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView rm = link("إلغاء");
            rm.setTextColor(DANGER);
            final Planner.Focus ff = f;
            rm.setOnClickListener(v -> {
                ff.scope = "historical";
                ff.expiresAt = System.currentTimeMillis();
                planner.save();
                showTab(4);
            });
            row.addView(rm);
            card.addView(row);
        }
        return card;
    }

    private View buildExamsScreen() {
        FrameLayout root=new FrameLayout(this);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setPadding(0,0,0,dp(92));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(28));box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);sc.addView(box);
        TextView h=title("الامتحانات");h.setTextSize(32);box.addView(h);box.addView(space(dp(14)));
        if(planner.exams.isEmpty())box.addView(emptyState("لا توجد امتحانات مضافة."));
        else{
            Calendar td=Calendar.getInstance();td.set(Calendar.HOUR_OF_DAY,0);td.set(Calendar.MINUTE,0);td.set(Calendar.SECOND,0);td.set(Calendar.MILLISECOND,0);
            for(Planner.Exam e:planner.exams){
                LinearLayout ex=card();ex.setPadding(dp(14),dp(14),dp(14),dp(14));ex.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);ex.setGravity(Gravity.CENTER_VERTICAL);
                int days=999;try{Calendar d=Planner.dayCal(e.day);days=(int)((d.getTimeInMillis()-td.getTimeInMillis())/86400000L);}catch(Exception ignored){}
                TextView cd=muted(days<0?"عدّى":days==0?"النهاردة":"بعد "+days+" يوم");cd.setTextColor(TEXT);cd.setGravity(Gravity.CENTER);cd.setTextSize(13);GradientDrawable cb=new GradientDrawable();cb.setColor(0xFF1B2637);cb.setCornerRadius(dp(14));cd.setBackground(cb);cd.setPadding(dp(8),dp(8),dp(8),dp(8));ex.addView(cd,new LinearLayout.LayoutParams(dp(88),dp(44)));ex.addView(space(dp(10)));
                LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);info.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView sub=new TextView(this);sub.setText(e.subject==null?"":e.subject);sub.setTextColor(TEXT);sub.setTextSize(19);sub.setTypeface(Typeface.DEFAULT_BOLD);sub.setGravity(Gravity.RIGHT);
                TextView nm=new TextView(this);nm.setText(e.title==null||e.title.isEmpty()?"امتحان":e.title);nm.setTextColor(TEXT);nm.setTextSize(16);nm.setTypeface(Typeface.DEFAULT_BOLD);nm.setGravity(Gravity.RIGHT);
                info.addView(sub);info.addView(space(dp(2)));info.addView(nm);ex.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                SubjectIconView ic=new SubjectIconView(this);ic.setSubject(e.subject);ex.addView(ic,new LinearLayout.LayoutParams(dp(44),dp(44)));
                ex.setOnClickListener(v->showExamDialog(e));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);ep.bottomMargin=dp(10);ex.setLayoutParams(ep);box.addView(ex);
            }
        }
        box.addView(space(dp(110)));
        root.addView(sc,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(fixedAddBar("إضافة امتحان",()->showExamDialog(null)));
        return root;
    }

    private void showExamDialog(Planner.Exam existing) {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(12),dp(18),dp(10));form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final String[] subjectHold={existing!=null&&existing.subject!=null?existing.subject:""};
        TextView subjectLabel=label("المادة");subjectLabel.setTextColor(TEXT);form.addView(subjectLabel);
        TextView subject=chip(subjectHold[0].isEmpty()?"اختار مادة":subjectHold[0],true);form.addView(subject);
        form.addView(space(dp(8)));
        TextView titleLabel=label("اسم الامتحان");titleLabel.setTextColor(TEXT);form.addView(titleLabel);
        EditText title=dialogField();title.setHint("اسم الامتحان");if(existing!=null&&existing.title!=null)title.setText(existing.title);form.addView(title);
        form.addView(space(dp(8)));
        TextView dateLabel=label("تاريخ الامتحان");dateLabel.setTextColor(TEXT);form.addView(dateLabel);
        final String[] day={existing!=null&&existing.day!=null?existing.day:""};
        TextView dayTv=chip(day[0].isEmpty()?"اختار التاريخ":day[0],true);
        dayTv.setOnClickListener(v->{Calendar cal=Calendar.getInstance();DatePickerDialog dpd=new DatePickerDialog(this,(vv,y,m,d)->{day[0]=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d);dayTv.setText(day[0]);dayTv.setTextColor(ACCENT);},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));dpd.setOnShowListener(x->styleBlueDialog(dpd));dpd.show();});form.addView(dayTv);
        form.addView(space(dp(12)));TextView moreToggle=link("خيارات إضافية ▾");moreToggle.setTextColor(ACCENT);form.addView(moreToggle);
        LinearLayout moreBox=new LinearLayout(this);moreBox.setOrientation(LinearLayout.VERTICAL);moreBox.setVisibility(View.GONE);moreBox.setPadding(0,dp(8),0,0);
        TextView topicsLabel=label("الفصول أو الأجزاء");topicsLabel.setTextColor(TEXT);moreBox.addView(topicsLabel);
        EditText topics=dialogField();topics.setHint("اختياري");if(existing!=null&&existing.topics!=null)topics.setText(existing.topics);moreBox.addView(topics);
        moreBox.addView(space(dp(8)));final int[] prep={existing!=null?existing.prepLevel:40};TextView prepTv=new TextView(this);prepTv.setText("مستوى التحضير: "+prep[0]+"%");prepTv.setTextColor(TEXT);prepTv.setTextSize(12);SeekBar prepBar=new SeekBar(this);prepBar.setMax(100);prepBar.setProgress(prep[0]);prepBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){prep[0]=p;prepTv.setText("مستوى التحضير: "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});moreBox.addView(prepTv);moreBox.addView(prepBar);
        moreBox.addView(space(dp(8)));TextView lastLabel=label("آخر محاضرة داخلة في الامتحان");lastLabel.setTextColor(TEXT);moreBox.addView(lastLabel);TextView lastHint=muted("مثال: محاضرة 7 → الامتحان يشمل 1…7.");lastHint.setTextColor(MUTED);moreBox.addView(lastHint);
        final String[] lastLecId={existing!=null&&existing.lastLectureTaskId!=null?existing.lastLectureTaskId:""};String lastLecLabel="بدون تحديد";if(!lastLecId[0].isEmpty()){Planner.Task lt=planner.findTask(lastLecId[0]);if(lt!=null){int n=Planner.lectureNumber(lt);lastLecLabel=(n>=0?("محاضرة "+n+" — "):"")+(lt.name==null?"?":lt.name);}}
        TextView lastLecTv=chip(lastLecLabel,true);
        lastLecTv.setOnClickListener(v->{String sub=subjectHold[0]==null?"":subjectHold[0].trim();if(sub.isEmpty()){Toast.makeText(this,"اختار المادة أولًا",Toast.LENGTH_SHORT).show();return;}java.util.ArrayList<Planner.Task> lecs=new java.util.ArrayList<>();for(Planner.Task tk:planner.tasks)if(tk.subject!=null&&tk.subject.equals(sub))lecs.add(tk);lecs.sort((a,b)->Integer.compare(Planner.lectureNumber(a),Planner.lectureNumber(b)));String[] labels=new String[lecs.size()+1];labels[0]="بدون تحديد";for(int i=0;i<lecs.size();i++){Planner.Task tk=lecs.get(i);int n=Planner.lectureNumber(tk);labels[i+1]=(n>=0?("محاضرة "+n+" — "):"")+(tk.name==null?"?":tk.name)+(tk.done?" ✓":"");}AlertDialog ld=myDialog().setTitle("آخر محاضرة — "+sub).setItems(labels,(dd,w)->{if(w==0){lastLecId[0]="";lastLecTv.setText("بدون تحديد");}else{Planner.Task tk=lecs.get(w-1);lastLecId[0]=tk.id;int n=Planner.lectureNumber(tk);lastLecTv.setText((n>=0?("محاضرة "+n+" — "):"")+(tk.name==null?"?":tk.name));}lastLecTv.setTextColor(ACCENT);}).show();styleBlueDialog(ld);});
        moreBox.addView(lastLecTv);form.addView(moreBox);
        final boolean[] moreOpen={false};moreToggle.setOnClickListener(v->{moreOpen[0]=!moreOpen[0];moreBox.setVisibility(moreOpen[0]?View.VISIBLE:View.GONE);moreToggle.setText(moreOpen[0]?"خيارات إضافية ▴":"خيارات إضافية ▾");});
        subject.setOnClickListener(v->pickSubject(subjectHold[0],name0->{if(!name0.equals(subjectHold[0])){lastLecId[0]="";lastLecTv.setText("بدون تحديد");}subjectHold[0]=name0;subject.setText(name0);subject.setTextColor(ACCENT);}));
        ScrollView sv=new ScrollView(this);sv.addView(form);
        AlertDialog dialog=myDialog().setTitle(existing==null?"امتحان جديد":"تعديل امتحان").setView(sv).setPositiveButton("حفظ",(d,w)->{String sub=subjectHold[0]==null?"":subjectHold[0].trim();if(sub.isEmpty()){Toast.makeText(this,"اختار المادة",Toast.LENGTH_SHORT).show();return;}if(day[0].isEmpty()){Toast.makeText(this,"اختار التاريخ",Toast.LENGTH_SHORT).show();return;}Planner.Exam e=existing!=null?existing:new Planner.Exam();e.subject=sub;e.title=title.getText().toString().trim();e.topics=topics.getText().toString().trim();e.day=day[0];e.prepLevel=prep[0];e.importance=2;e.lastLectureTaskId=lastLecId[0]==null?"":lastLecId[0];if(existing==null)planner.exams.add(e);planner.save();showTab(3);}).setNegativeButton("إلغاء",null).show();styleBlueDialog(dialog);
    }

    private void styleBlueDialog(AlertDialog dialog) {
        if(dialog==null)return;
        try{
            int titleId=getResources().getIdentifier("alertTitle","id","android");
            TextView titleView=titleId!=0?dialog.findViewById(titleId):null;
            if(titleView!=null)titleView.setTextColor(TEXT);
            TextView messageView=dialog.findViewById(android.R.id.message);if(messageView!=null)messageView.setTextColor(TEXT);
            Button pos=dialog.getButton(AlertDialog.BUTTON_POSITIVE),neg=dialog.getButton(AlertDialog.BUTTON_NEGATIVE),neu=dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if(pos!=null)pos.setTextColor(ACCENT);if(neg!=null)neg.setTextColor(ACCENT);if(neu!=null)neu.setTextColor(ACCENT);
            View root=dialog.getWindow()==null?null:dialog.getWindow().getDecorView();if(root!=null)colorDialogText(root);
        }catch(Exception ignored){}
    }

    private void colorDialogText(View v) {
        // لا نلوّن كل الـTextViews بالأزرق؛ الـLabels في نماذج الإضافة تظل بيضاء.
        if(v instanceof EditText){
            ((EditText)v).setTextColor(TEXT);
            ((EditText)v).setHintTextColor(0xB8FFFFFF);
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) colorDialogText(g.getChildAt(i));
        }
    }

    interface NameTap { void apply(String name); }
    interface IntPick { void pick(int index); }

    private void pickSubject(String current, NameTap cb) {
        java.util.List<String> names=new java.util.ArrayList<>();for(Planner.Subject ss:planner.subjects)names.add(ss.name);names.add("+ مادة جديدة");
        AlertDialog dialog=myDialog().setTitle("المادة").setItems(names.toArray(new String[0]),(d,w)->{
            if(w==names.size()-1){
                EditText et=field();et.setTextColor(ACCENT);et.setHintTextColor(0xAA4B6DFF);et.setHint("اسم المادة");
                AlertDialog add=myDialog().setTitle("مادة جديدة").setView(pad(et)).setPositiveButton("إضافة",(dd,ww)->{String n=et.getText().toString().trim();if(n.isEmpty())return;planner.addSubject(n);cb.apply(n);}).setNegativeButton("إلغاء",null).show();styleBlueDialog(add);
            }else cb.apply(names.get(w));
        }).show();styleBlueDialog(dialog);
    }


    private void showSubjectOptionsDialog(Planner.Subject s) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        form.addView(label("اسم المادة"));
        EditText et = field();
        et.setText(s.name);
        form.addView(et);
        form.addView(space(dp(12)));
        form.addView(label("تثبيت يوم المحاضرة (بعد انتهاء التراكم)"));
        form.addView(muted("أيام متعددة · للمحاضرات الجديدة فقط لما مفيش backlog."));
        form.addView(space(dp(6)));
        final java.util.Set<Integer> fixed = new java.util.HashSet<>(s.fixedDows);
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.VERTICAL);
        for (int d : WEEK_ORDER) {
            final int dow = d;
            TextView chip = chip(DAY_NAMES[d], fixed.contains(d));
            chip.setOnClickListener(v -> {
                if (fixed.contains(dow)) fixed.remove(dow); else fixed.add(dow);
                styleChip(chip, fixed.contains(dow));
            });
            days.addView(chip);
            days.addView(space(dp(4)));
        }
        form.addView(days);
        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        myDialog().setTitle("خيارات المادة")
                .setView(sv)
                .setPositiveButton("حفظ", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty() && !n.equals(s.name)) planner.renameSubject(s.name, n);
                    String targetName = !n.isEmpty() ? n : s.name;
                    for (Planner.Subject x : planner.subjects) {
                        if (x.id.equals(s.id) || x.name.equals(targetName)) {
                            x.fixedDows.clear();
                            x.fixedDows.addAll(fixed);
                        }
                    }
                    planner.save();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showLectureOrderDialog() {
        java.util.ArrayList<Planner.Task> open = new java.util.ArrayList<>();
        for (Planner.Task t : planner.tasks) if (!t.done) open.add(t);
        open.sort((a, b) -> {
            int oa = a.sortOrder;
            int ob = b.sortOrder;
            if (oa != ob) return Integer.compare(oa, ob);
            return (a.name == null ? "" : a.name).compareTo(b.name == null ? "" : b.name);
        });
        if (open.isEmpty()) {
            Toast.makeText(this, "مفيش محاضرات مفتوحة", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(8));
        form.addView(muted("رتّب المحاضرات. كل محاضرة تتحرك بكل جلساتها."));
        final java.util.ArrayList<Planner.Task> list = open;
        final Runnable[] refresh = new Runnable[1];
        LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        refresh[0] = () -> {
            listBox.removeAllViews();
            for (int i = 0; i < list.size(); i++) {
                final int idx = i;
                Planner.Task t = list.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView lab = new TextView(this);
                lab.setText((i + 1) + ". " + t.name + " · " + t.subject);
                lab.setTextColor(TEXT);
                lab.setTextSize(14);
                row.addView(lab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView up = chip("↑", false);
                TextView down = chip("↓", false);
                up.setOnClickListener(v -> {
                    if (idx <= 0) return;
                    java.util.Collections.swap(list, idx, idx - 1);
                    refresh[0].run();
                });
                down.setOnClickListener(v -> {
                    if (idx >= list.size() - 1) return;
                    java.util.Collections.swap(list, idx, idx + 1);
                    refresh[0].run();
                });
                row.addView(up);
                row.addView(space(dp(4)));
                row.addView(down);
                listBox.addView(row);
                listBox.addView(space(dp(6)));
            }
        };
        refresh[0].run();
        form.addView(listBox);
        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        myDialog().setTitle("ترتيب المحاضرات")
                .setView(sv)
                .setPositiveButton("حفظ", (d, w) -> {
                    for (int i = 0; i < list.size(); i++) {
                        list.get(i).sortOrder = i;
                    }
                    planner.save();
                    Toast.makeText(this, "تم حفظ الترتيب اليدوي · اضغط عدّل خطتي عشان يتطبق على اليوم والأسبوع", Toast.LENGTH_LONG).show();
                    showTab(1);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static class SubjectIconView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private String subject="";
        SubjectIconView(android.content.Context c){super(c);}
        void setSubject(String s){subject=s==null?"":s;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f,stroke=Math.max(2f,w*.055f);
            p.setColor(ACCENT);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            String s=subject.toLowerCase(Locale.ROOT);p.setTextAlign(Paint.Align.CENTER);
            if(s.contains("عرب")||s.equals("arabic")){p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD_ITALIC));p.setTextSize(w*.62f);canvas.drawText("ع",cx,cy+w*.22f,p);}
            else if(s.contains("english")||s.contains("إنج")||s.contains("انج")){p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));p.setTextSize(w*.42f);canvas.drawText("Aa",cx,cy+w*.15f,p);}
            else if(s.contains("كيم")||s.contains("chem")){p.setStyle(Paint.Style.STROKE);Path path=new Path();path.moveTo(cx-w*.18f,cy-w*.22f);path.lineTo(cx-w*.12f,cy-w*.02f);path.lineTo(cx-w*.30f,cy+w*.27f);path.quadTo(cx,cy+w*.45f,cx+w*.30f,cy+w*.27f);path.lineTo(cx+w*.12f,cy-w*.02f);path.lineTo(cx+w*.18f,cy-w*.22f);canvas.drawPath(path,p);canvas.drawLine(cx-w*.20f,cy-w*.22f,cx+w*.20f,cy-w*.22f,p);}
            else if(s.contains("أحيا")||s.contains("احيا")||s.contains("bio")){p.setStyle(Paint.Style.STROKE);canvas.drawOval(new RectF(cx-w*.30f,cy-w*.30f,cx+w*.30f,cy+w*.30f),p);canvas.drawOval(new RectF(cx-w*.18f,cy-w*.14f,cx+w*.18f,cy+w*.14f),p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx+w*.08f,cy-w*.04f,w*.07f,p);canvas.drawCircle(cx-w*.10f,cy+w*.10f,w*.045f,p);}
            else if(s.contains("فيز")||s.contains("phys")){p.setStyle(Paint.Style.STROKE);canvas.drawLine(cx,cy-w*.34f,cx,cy+w*.06f,p);canvas.drawCircle(cx,cy+w*.12f,w*.075f,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy+w*.22f,w*.10f,p);}
            else{p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD_ITALIC));p.setTextSize(w*.68f);canvas.drawText("π",cx,cy+w*.23f,p);}
        }
    }

    private View subjectsCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        if (planner.subjects.isEmpty()) card.addView(muted("ضيف موادك الأساسية من هنا."));
        for (Planner.Subject s : new java.util.ArrayList<>(planner.subjects)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView lab = new TextView(this);
            lab.setText(s.name);
            lab.setTextColor(TEXT);
            lab.setTextSize(15);
            row.addView(lab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView ed = link("تعديل");
            ed.setOnClickListener(v -> showSubjectOptionsDialog(s));
            TextView del = link("حذف");
            del.setTextColor(DANGER);
            del.setOnClickListener(v -> {
                planner.deleteSubject(s.name);
                showTab(4);
            });
            row.addView(ed);
            row.addView(space(dp(10)));
            row.addView(del);
            card.addView(row);
            card.addView(space(dp(6)));
        }
        TextView add = chip("+ إضافة مادة", true);
        add.setOnClickListener(v -> {
            EditText et = field();
            et.setHint("مثال: الإنجليزي");
            myDialog().setTitle("مادة جديدة").setView(pad(et))
                    .setPositiveButton("إضافة", (d, w) -> {
                        planner.addSubject(et.getText().toString());
                        showTab(4);
                    }).setNegativeButton("إلغاء", null).show();
        });
        card.addView(add);
        return card;
    }


    // ═══════════════ Premium Hub (محلي · خلف isPremiumActive) ═══════════════
    private String premiumSubScreen = "home";
    private String devSubScreen = "home";

    private View buildPremiumHub() {
        final boolean premiumOn = AppInfrastructure.isPremiumActive(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView back = link("← المزيد");
        back.setOnClickListener(v -> {
            if (!"home".equals(premiumSubScreen)) {
                premiumSubScreen = "home";
                showTab(8);
            } else showTab(4);
        });
        box.addView(back);
        box.addView(title("مركز Premium"));
        if (premiumOn) {
            box.addView(muted("محلي بالكامل · Offline · بياناتك الحقيقية"));
        } else {
            box.addView(muted("استعرض الميزات أدناه. التشغيل يتطلب اشتراك Premium."));
        }
        box.addView(space(dp(10)));

        if ("home".equals(premiumSubScreen)) {
            String[][] items = {
                    {"review", "مخطط المراجعة الذكي", "اقتراحات مراجعة من مهامك وجلساتك"},
                    {"forecast", "توقع الدراسة", "تقدير الحمل والأيام القادمة"},
                    {"whatif", "مختبر ماذا لو", "محاكاة تغييرات بدون تعديل خطتك"},
                    {"analytics", "تحليلات متقدمة", "إحصاءات أعمق لتقدمك"},
                    {"goals", "الأهداف", "أهداف دراسية ومتابعة التقدم"},
                    {"journal", "دفتر الدراسة", "ملاحظات يومية مرتبطة بالمذاكرة"},
                    {"records", "الأرقام القياسية", "أفضل إنجازاتك المحفوظة"},
                    {"reports", "تقارير", "تقارير ملخصة لفترات الدراسة"},
                    {"voice", "تخطيط صوتي", "أوامر تخطيط صوتية محلية"},
                    {"custom", "تخصيص العرض", "خيارات عرض إضافية"},
                    {"ai", "✨ عدّل خطتي", "مساعد تخطيط محلي"}
            };
            for (String[] it : items) {
                LinearLayout row = card();
                row.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(this);
                name.setText(it[1] + (premiumOn ? "" : "  🔒"));
                name.setTextColor(TEXT);
                name.setTextSize(15);
                row.addView(name);
                row.addView(muted(it[2]));
                final String key = it[0];
                row.setOnClickListener(v -> {
                    if (!AppInfrastructure.isPremiumActive(this)) {
                        Toast.makeText(this, "هذه الميزة متاحة مع Premium.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if ("ai".equals(key)) { showTab(5); return; }
                    premiumSubScreen = key;
                    showTab(8);
                });
                box.addView(row);
                box.addView(space(dp(8)));
            }
            return sc;
        }

        if (!premiumOn) {
            box.addView(muted("هذه الميزة متاحة مع Premium."));
            TextView goHome = chip("العودة للقائمة", true);
            goHome.setOnClickListener(v -> { premiumSubScreen = "home"; showTab(8); });
            box.addView(goHome);
            return sc;
        }

        if ("review".equals(premiumSubScreen)) {
            box.addView(sectionHeader("مخطط المراجعة"));
            for (String line : PremiumHub.reviewSuggestions(planner)) {
                LinearLayout c = card();
                c.setOrientation(LinearLayout.VERTICAL);
                c.addView(muted(line));
                box.addView(c);
                box.addView(space(dp(6)));
            }
            return sc;
        }
        if ("forecast".equals(premiumSubScreen)) {
            box.addView(sectionHeader("توقع الدراسة"));
            for (String line : PremiumHub.studyForecast(planner)) box.addView(muted(line));
            return sc;
        }
        if ("whatif".equals(premiumSubScreen)) {
            box.addView(sectionHeader("ماذا لو؟ (بدون تعديل خطتك)"));
            String[][] scenarios = {
                    {"more_study", "زيادة 60 د يوميًا"},
                    {"shorter_session", "جلسة 30 دقيقة"},
                    {"rest_day", "يوم راحة إضافي"},
                    {"priority_boost", "رفع أولوية مادة"}
            };
            for (String[] scn : scenarios) {
                TextView ch = chip(scn[1], false);
                final String sid = scn[0];
                ch.setOnClickListener(v -> {
                    StringBuilder msg = new StringBuilder();
                    for (String line : PremiumHub.whatIf(planner, sid, 60)) msg.append(line).append("\n");
                    myDialog().setTitle("نتيجة التجربة").setMessage(msg.toString())
                            .setPositiveButton("حسنًا", null).show();
                });
                box.addView(ch);
                box.addView(space(dp(6)));
            }
            box.addView(muted("Apply على الخطة الحقيقية يتم فقط من «عدّل خطتي» أو إعدادات الراحة يدويًا."));
            return sc;
        }
        if ("analytics".equals(premiumSubScreen)) {
            box.addView(sectionHeader("تحليلات متقدمة"));
            for (String line : PremiumHub.advancedAnalytics(planner)) box.addView(muted(line));
            return sc;
        }
        if ("goals".equals(premiumSubScreen)) {
            box.addView(sectionHeader("الأهداف"));
            TextView add = chip("إضافة هدف", true);
            add.setOnClickListener(v -> showAddGoalDialog());
            box.addView(add);
            box.addView(space(dp(8)));
            for (PremiumHub.Goal g : PremiumHub.loadGoals(this)) {
                int prog = PremiumHub.goalProgress(planner, g);
                LinearLayout c = card();
                c.setOrientation(LinearLayout.VERTICAL);
                TextView t = new TextView(this);
                t.setText(g.title);
                t.setTextColor(TEXT);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                c.addView(t);
                c.addView(muted("التقدم: " + prog + " / " + g.targetValue + (g.done ? " · مكتمل" : "")));
                TextView del = link("حذف");
                del.setTextColor(DANGER);
                del.setOnClickListener(v2 -> {
                    List<PremiumHub.Goal> list = PremiumHub.loadGoals(this);
                    list.removeIf(x -> g.id.equals(x.id));
                    PremiumHub.saveGoals(this, list);
                    showTab(8);
                });
                c.addView(del);
                box.addView(c);
                box.addView(space(dp(6)));
            }
            return sc;
        }
        if ("journal".equals(premiumSubScreen)) {
            box.addView(sectionHeader("دفتر الدراسة"));
            TextView add = chip("ملاحظة جديدة", true);
            add.setOnClickListener(v -> showAddJournalDialog());
            box.addView(add);
            box.addView(space(dp(8)));
            for (PremiumHub.JournalEntry e : PremiumHub.loadJournal(this)) {
                LinearLayout c = card();
                c.setOrientation(LinearLayout.VERTICAL);
                c.addView(muted(e.day + (e.subject != null && e.subject.length() > 0 ? " · " + e.subject : "") + " · ★" + e.rating));
                TextView tx = new TextView(this);
                tx.setText(e.text != null ? e.text : "");
                tx.setTextColor(TEXT);
                tx.setTextSize(14);
                c.addView(tx);
                box.addView(c);
                box.addView(space(dp(6)));
            }
            return sc;
        }
        if ("records".equals(premiumSubScreen)) {
            box.addView(sectionHeader("أرقامك القياسية"));
            for (String line : PremiumHub.personalRecords(planner)) box.addView(muted(line));
            return sc;
        }
        if ("reports".equals(premiumSubScreen)) {
            box.addView(sectionHeader("تقارير"));
            for (String range : new String[]{"day", "week", "month"}) {
                TextView ch = chip(range.equals("day") ? "يومي" : range.equals("week") ? "أسبوعي" : "30 يوم", false);
                final String r = range;
                ch.setOnClickListener(v -> {
                    String report = PremiumHub.buildReport(planner, r);
                    myDialog().setTitle("تقرير").setMessage(report)
                            .setPositiveButton("نسخ", (d, w) -> {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("report", report));
                                Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("إغلاق", null).show();
                });
                box.addView(ch);
                box.addView(space(dp(6)));
            }
            return sc;
        }
        if ("voice".equals(premiumSubScreen)) {
            box.addView(sectionHeader("تخطيط صوتي"));
            box.addView(muted("يستخدم التعرف على الكلام في النظام إن وُجد، ثم نفس محرك التخطيط المحلي."));
            TextView start = chip("استمع للأمر", true);
            start.setOnClickListener(v -> startVoicePlanning());
            box.addView(space(dp(8)));
            box.addView(start);
            return sc;
        }
        if ("custom".equals(premiumSubScreen)) {
            box.addView(sectionHeader("تخصيص العرض"));
            boolean compact = PremiumHub.compactCards(this);
            TextView c1 = chip(compact ? "بطاقات مدمجة: تشغيل" : "بطاقات مدمجة: إيقاف", compact);
            c1.setOnClickListener(v -> {
                PremiumHub.setCompactCards(this, !PremiumHub.compactCards(this));
                showTab(8);
            });
            box.addView(c1);
            box.addView(space(dp(6)));
            boolean colors = PremiumHub.showSubjectColors(this);
            TextView c2 = chip(colors ? "تمييز المواد: تشغيل" : "تمييز المواد: إيقاف", colors);
            c2.setOnClickListener(v -> {
                PremiumHub.setShowSubjectColors(this, !PremiumHub.showSubjectColors(this));
                showTab(8);
            });
            box.addView(c2);
            box.addView(muted("التخصيص محلي ولا يغيّر منطق الجدولة."));
            return sc;
        }
        premiumSubScreen = "home";
        showTab(8);
        return sc;
    }

    private void showAddGoalDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText title = field();
        title.setHint("عنوان الهدف");
        EditText target = field();
        target.setHint("الهدف الرقمي (مثال: 10)");
        target.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(title);
        form.addView(space(dp(8)));
        form.addView(target);
        myDialog().setTitle("هدف جديد").setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    PremiumHub.Goal g = new PremiumHub.Goal();
                    g.title = title.getText() != null ? title.getText().toString().trim() : "";
                    if (g.title.isEmpty()) g.title = "هدف";
                    try { g.targetValue = Integer.parseInt(target.getText().toString().trim()); }
                    catch (Exception e) { g.targetValue = 1; }
                    g.type = "tasks";
                    List<PremiumHub.Goal> list = PremiumHub.loadGoals(this);
                    list.add(g);
                    PremiumHub.saveGoals(this, list);
                    premiumSubScreen = "goals";
                    showTab(8);
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showAddJournalDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText body = field();
        body.setHint("ماذا درست؟ ملاحظات؟");
        body.setMinLines(3);
        EditText sub = field();
        sub.setHint("المادة (اختياري)");
        form.addView(body);
        form.addView(space(dp(8)));
        form.addView(sub);
        myDialog().setTitle("دفتر الدراسة").setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    PremiumHub.JournalEntry e = new PremiumHub.JournalEntry();
                    e.text = body.getText() != null ? body.getText().toString().trim() : "";
                    e.subject = sub.getText() != null ? sub.getText().toString().trim() : "";
                    List<PremiumHub.JournalEntry> list = PremiumHub.loadJournal(this);
                    list.add(0, e);
                    PremiumHub.saveJournal(this, list);
                    premiumSubScreen = "journal";
                    showTab(8);
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void startVoicePlanning() {
        try {
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar");
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "قل أمر التخطيط...");
            startActivityForResult(intent, 4401);
        } catch (Exception e) {
            Toast.makeText(this, "التعرف على الكلام غير متاح على هذا الجهاز", Toast.LENGTH_LONG).show();
        }
    }

    private View buildChatScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));

        TextView back = link("← المزيد");
        back.setOnClickListener(v -> showTab(4));
        root.addView(back);
        TextView h = title("المخطط الذكي");
        h.setTextSize(22);
        root.addView(h);
        root.addView(muted("يقرأ جدولك الحالي ويقدر يعدّله."));
        root.addView(space(dp(8)));

        ScrollView sc = new ScrollView(this);
        LinearLayout log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        sc.addView(log);
        root.addView(sc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (chatLog.isEmpty()) {
            chatLog.add(new String[]{"bot", "قولّي عايز الخطة تتعدل إزاي. مثال: عايز أهتم بالإنجليزي."});
        }
        for (String[] m : chatLog) log.addView(bubble(m[0], m[1]));

        if (pendingIntent != null && pendingIntent.proposal != null && pendingIntent.proposal.length() > 0) {
            LinearLayout card = card();
            card.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText("التعديل المقترح");
            t1.setTextColor(ACCENT);
            t1.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(t1);
            card.addView(muted(pendingIntent.proposal));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView ok = chip("تطبيق التعديل", true);
            TextView no = chip("إلغاء", false);
            ok.setOnClickListener(v -> {
                String res = planner.applyChatIntent(pendingIntent);
                chatState.lastType = pendingIntent.type;
                chatState.lastSubject = pendingIntent.subject;
                chatState.lastTemp = pendingIntent.temporary;
                chatLog.add(new String[]{"bot", res});
                pendingIntent = null;
                showTab(5);
            });
            no.setOnClickListener(v -> {
                chatLog.add(new String[]{"bot", "تمام، ملغي."});
                pendingIntent = null;
                showTab(5);
            });
            row.addView(ok, chipLp());
            row.addView(space(dp(8)));
            row.addView(no, chipLp());
            card.addView(space(dp(8)));
            card.addView(row);
            log.addView(space(dp(8)));
            log.addView(card);
        }

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] qs = {"عايز أهتم بمادة", "عندي امتحان", "الجدول تقيل", "عايز يوم راحة"};
        for (String q : qs) {
            TextView c = chip(q, false);
            c.setOnClickListener(v -> sendChat(q));
            chips.addView(c);
            chips.addView(space(dp(6)));
        }
        HorizontalScrollWrap hs = new HorizontalScrollWrap(this);
        hs.addView(chips);
        root.addView(hs);

        LinearLayout input = new LinearLayout(this);
        input.setOrientation(LinearLayout.HORIZONTAL);
        input.setGravity(Gravity.CENTER_VERTICAL);
        EditText et = field();
        et.setHint("اكتب طلبك...");
        input.addView(et, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView send = chip("إرسال", true);
        send.setOnClickListener(v -> {
            String msg = et.getText().toString().trim();
            if (msg.isEmpty()) return;
            sendChat(msg);
        });
        input.addView(space(dp(6)));
        input.addView(send);
        root.addView(space(dp(6)));
        root.addView(input);
        return root;
    }

    private void sendChat(String msg) {
        chatLog.add(new String[]{"me", msg});
        PlanningIntentParser.Intent in = PlanningIntentParser.parse(planner, msg, chatState);
        pendingIntent = in;
        if ("UNKNOWN".equals(in.type) || in.proposal == null || in.proposal.isEmpty()) {
            chatLog.add(new String[]{"bot", in.reply});
            pendingIntent = null;
        } else {
            chatLog.add(new String[]{"bot", "بحلل جدولك الحالي...\n" + in.reply});
        }
        showTab(5);
    }

    private View bubble(String who, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(dp(16));
        boolean me = "me".equals(who);
        d.setColor(me ? ACCENT : CARD);
        tv.setTextColor(TEXT);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(me ? dp(40) : 0, dp(4), me ? 0 : dp(40), dp(4));
        wrap.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setBackground(d);
        return wrap;
    }

    private void startBackupSave() {
        String name = "MyPlan_Backup_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, REQ_BACKUP_SAVE);
        } catch (Exception e) {
            Toast.makeText(this, "لا يمكن فتح حفظ الملفات", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBackupOpen() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_BACKUP_OPEN);
        } catch (Exception e) {
            Toast.makeText(this, "لا يمكن فتح اختيار الملفات", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 4401 && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> matches = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spoken = matches.get(0);
                if (AppInfrastructure.isPremiumActive(this)) {
                    chatLog.add(new String[]{"me", spoken});
                    PlanningIntentParser.Intent intent = PlanningIntentParser.parse(planner, spoken, chatState);
                    pendingIntent = intent;
                    chatLog.add(new String[]{"bot", intent.reply != null ? intent.reply : ""});
                    showTab(5);
                } else {
                    Toast.makeText(this, "التخطيط الصوتي ضمن Premium", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (requestCode == REQ_BACKUP_SAVE && (resultCode != RESULT_OK || data == null || data.getData() == null)) {
            // ألغى المستخدم حفظ الملف — لا نسجّل خروجًا تلقائيًا
            logoutAfterBackup = false;
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_BACKUP_SAVE) {
            try {
                String json = planner.exportJson();
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new Exception("لا يمكن الكتابة");
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.close();
                Toast.makeText(this, "تم حفظ النسخة الاحتياطية ✓", Toast.LENGTH_LONG).show();
                if (logoutAfterBackup) {
                    performLogoutToLogin();
                }
            } catch (Exception e) {
                logoutAfterBackup = false;
                Toast.makeText(this, "فشل الحفظ: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_BACKUP_OPEN) {
            myDialog()
                    .setTitle("استرجاع النسخة؟")
                    .setMessage("بعد التحقق من صلاحية الملف، ستُستبدل البيانات الحالية. لو الملف تالف لن يتغير شيء.")
                    .setPositiveButton("استرجاع", (d, w) -> {
                        try {
                            InputStream is = getContentResolver().openInputStream(uri);
                            if (is == null) throw new Exception("لا يمكن القراءة");
                            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line).append('\n');
                            br.close();
                            // importJson يتحقق ويبني بيانات مؤقتة قبل أي استبدال
                            planner.importJson(sb.toString());
                            SessionAlarmScheduler.resync(this, planner);
                            Toast.makeText(this, "تم الاسترجاع ✓", Toast.LENGTH_LONG).show();
                            showTab(4);
                        } catch (Exception e) {
                            String msg = e.getMessage() != null ? e.getMessage() : "ملف غير صالح";
                            Toast.makeText(this, "فشل الاسترجاع — البيانات الحالية لم تتغير. " + msg, Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
        }
    }

    private void pickRestDays() {
        boolean[] checked = new boolean[7];
        String[] labels = new String[7];
        for (int i = 0; i < 7; i++) {
            int dow = WEEK_ORDER[i];
            labels[i] = DAY_NAMES[dow];
            checked[i] = planner.settings.restDays.contains(dow);
        }
        myDialog()
                .setTitle("أيام الراحة")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("حفظ", (d, w) -> {
                    planner.settings.restDays.clear();
                    for (int i = 0; i < 7; i++) if (checked[i]) planner.settings.restDays.add(WEEK_ORDER[i]);
                    planner.save();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void pickExceptionDays() {
        boolean[] checked = new boolean[7];
        String[] labels = new String[7];
        for (int i = 0; i < 7; i++) {
            int dow = WEEK_ORDER[i];
            labels[i] = DAY_NAMES[dow];
            checked[i] = planner.settings.sleepExceptions.containsKey(dow);
        }
        myDialog()
                .setTitle("اختر يوم استثناء")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("التالي", (d, w) -> {
                    List<Integer> selected = new ArrayList<>();
                    for (int i = 0; i < 7; i++) if (checked[i]) selected.add(WEEK_ORDER[i]);
                    if (selected.isEmpty()) {
                        planner.settings.sleepExceptions.clear();
                        planner.save();
                        showTab(4);
                        return;
                    }
                    pickTime(planner.settings.wakeMin, wake -> pickTime(planner.settings.sleepMin, sleep -> {
                        List<Integer> toRemove = new ArrayList<>();
                        for (Integer k : planner.settings.sleepExceptions.keySet()) {
                            if (!selected.contains(k)) toRemove.add(k);
                        }
                        for (Integer k : toRemove) planner.settings.sleepExceptions.remove(k);
                        for (int dow : selected) {
                            planner.settings.sleepExceptions.put(dow, new int[]{wake, sleep});
                        }
                        planner.save();
                        showTab(4);
                    }));
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showAddCommitment() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        EditText title = field();
        title.setHint("مثلاً: مدرسة / سنتر رياضيات");
        form.addView(label("العنوان"));
        form.addView(title);

        form.addView(label("الأيام (يمكن اختيار أكثر من يوم)"));
        final boolean[] everyDay = {false};
        final java.util.LinkedHashSet<Integer> selectedDays = new java.util.LinkedHashSet<>();
        TextView daySummary = muted("لم يُختر يوم — اضغط لاختيار الأيام");
        // أزرار أيام الأسبوع — عرض طبيعي بدون ضغط العرض (يمنع تكسر الحروف العربية)
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final TextView[] dayChips = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int dow = WEEK_ORDER[i];
            dayChips[i] = chip(DAY_NAMES[dow], false);
            dayChips[i].setSingleLine(true);
            dayChips[i].setMaxLines(1);
            dayChips[i].setEllipsize(null);
            dayChips[i].setOnClickListener(v -> {
                everyDay[0] = false;
                if (selectedDays.contains(dow)) selectedDays.remove(dow);
                else selectedDays.add(dow);
                for (int j = 0; j < 7; j++) {
                    int d2 = WEEK_ORDER[j];
                    boolean on = selectedDays.contains(d2);
                    styleChip(dayChips[j], on);
                    dayChips[j].setTextColor(on ? ACCENT : MUTED);
                }
                if (selectedDays.isEmpty()) daySummary.setText("لم يُختر يوم");
                else {
                    StringBuilder sb = new StringBuilder();
                    for (int d : WEEK_ORDER) {
                        if (selectedDays.contains(d)) {
                            if (sb.length() > 0) sb.append(" · ");
                            sb.append(DAY_NAMES[d]);
                        }
                    }
                    daySummary.setText(sb.toString());
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(3));
            lp.setMarginEnd(dp(3));
            chips.addView(dayChips[i], lp);
        }
        HorizontalScrollView dayScroll = new HorizontalScrollView(this);
        dayScroll.setHorizontalScrollBarEnabled(false);
        dayScroll.setFillViewport(false);
        dayScroll.addView(chips);
        form.addView(dayScroll);
        form.addView(daySummary);
        TextView everyChip = chip("كل يوم", false);
        everyChip.setOnClickListener(v -> {
            everyDay[0] = !everyDay[0];
            if (everyDay[0]) {
                selectedDays.clear();
                for (int j = 0; j < 7; j++) {
                    styleChip(dayChips[j], false);
                    dayChips[j].setTextColor(MUTED);
                }
                daySummary.setText("كل يوم");
            } else {
                daySummary.setText("لم يُختر يوم");
            }
            styleChip(everyChip, everyDay[0]);
        });
        form.addView(space(dp(4)));
        form.addView(everyChip);

        form.addView(label("من – إلى"));
        final int[] start = {8 * 60};
        final int[] end = {14 * 60};
        TextView range = chip(Planner.minToTime(start[0]) + " – " + Planner.minToTime(end[0]), true);
        range.setOnClickListener(v -> pickTime(start[0], sMin -> {
            start[0] = sMin;
            pickTime(end[0], eMin -> {
                end[0] = eMin;
                range.setText(Planner.minToTime(start[0]) + " – " + Planner.minToTime(end[0]));
            });
        }));
        form.addView(range);

        myDialog()
                .setTitle("التزام ثابت")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    String titleStr = title.getText().toString().trim();
                    if (titleStr.isEmpty()) titleStr = "التزام";
                    if (!everyDay[0] && selectedDays.isEmpty()) {
                        Toast.makeText(this, "اختار يوم واحد على الأقل", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Planner.Commitment c = new Planner.Commitment();
                    c.title = titleStr;
                    c.startMin = start[0];
                    c.endMin = end[0];
                    c.daysOfWeek = new java.util.LinkedHashSet<>();
                    if (everyDay[0]) {
                        c.dayOfWeek = 0;
                    } else {
                        c.daysOfWeek.addAll(selectedDays);
                        // توافق: dayOfWeek = أول يوم مختار
                        c.dayOfWeek = selectedDays.iterator().next();
                    }
                    planner.commitments.add(c);
                    planner.save();
                    showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private interface MinCallback { void onMin(int min); }

    /**
     * Time Picker مخصص: عجلتا ساعة ودقيقة أفقيًا [ HH ] : [ MM ]
     * 24 ساعة، دائريتان، مستقلتان، بدون AM/PM.
     * التحويل عبر Planner.timeToMin فقط (نفس المنطق الحالي).
     */
    private void pickTime(int initialMin, MinCallback cb) {
        int total = ((initialMin % (24 * 60)) + (24 * 60)) % (24 * 60);
        final int initH = total / 60;
        final int initM = total % 60;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        final NumberPicker hourPicker = buildTimeWheel(0, 23, initH);
        final NumberPicker minPicker = buildTimeWheel(0, 59, initM);

        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wheelLp.gravity = Gravity.CENTER;
        row.addView(hourPicker, wheelLp);

        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextColor(TEXT);
        colon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        colon.setTypeface(Typeface.DEFAULT_BOLD);
        colon.setGravity(Gravity.CENTER);
        colon.setPadding(dp(10), 0, dp(10), 0);
        row.addView(colon);

        row.addView(minPicker, wheelLp);
        root.addView(row);

        TextView hint = muted("ساعة  :  دقيقة   ·   24 ساعة");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, 0);
        root.addView(hint);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("اختر الوقت")
                .setView(root)
                .setPositiveButton("تم", (d, w) -> {
                    int hour = hourPicker.getValue();
                    int minute = minPicker.getValue();
                    // مستقلتان: لا ربط بينهما
                    cb.onMin(Planner.timeToMin(hour, minute));
                })
                .setNegativeButton("إلغاء", null)
                .create();
        dlg.setOnShowListener(d -> {
            try {
                if (dlg.getWindow() != null) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(CARD);
                    bg.setCornerRadius(dp(16));
                    dlg.getWindow().setBackgroundDrawable(bg);
                }
                Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
                Button neg = dlg.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (pos != null) pos.setTextColor(ACCENT);
                if (neg != null) neg.setTextColor(MUTED);
            } catch (Exception ignored) {}
        });
        dlg.show();
    }

    /** عجلة رقمية دائرية (0..max) بقيم ثابتة العرض %02d */
    private NumberPicker buildTimeWheel(int min, int max, int value) {
        NumberPicker np = new NumberPicker(this);
        np.setMinValue(min);
        np.setMaxValue(max);
        int count = max - min + 1;
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            labels[i] = String.format(Locale.US, "%02d", min + i);
        }
        np.setDisplayedValues(labels);
        np.setWrapSelectorWheel(true);
        np.setValue(Math.max(min, Math.min(max, value)));
        np.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        // مظهر أنظف: تعطيل لوحة المفاتيح على العجلة
        np.setFocusable(false);
        np.setFocusableInTouchMode(false);
        return np;
    }


    /** قائمة إجراءات قرب العنصر + تعتيم خفيف لباقي الشاشة (بدون full-screen). */
    private void showContextActions(View anchor, String[] labels, Runnable[] actions) {
        if (anchor == null || labels == null || actions == null) return;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(OVERLAY);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> {
            if (content.indexOfChild(overlay) >= 0) content.removeView(overlay);
        });
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable mbg = new GradientDrawable();
        mbg.setColor(CARD);
        mbg.setCornerRadius(dp(14));
        mbg.setStroke(dp(1), 0x334B6DFF);
        menu.setBackground(mbg);
        menu.setElevation(dp(10));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(labels[i]);
            row.setTextColor(TEXT);
            row.setTextSize(15);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setOnClickListener(v -> {
                if (content.indexOfChild(overlay) >= 0) content.removeView(overlay);
                if (idx < actions.length && actions[idx] != null) actions[idx].run();
            });
            menu.addView(row);
        }
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.gravity = Gravity.CENTER;
        mlp.setMargins(dp(24), dp(24), dp(24), dp(24));
        overlay.addView(menu, mlp);
        content.addView(overlay);
    }

    // ═══════════════ UI helpers ═══════════════
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(h, h));
        return v;
    }

        private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.01f);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

        private TextView sectionHeader(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(10));
        return t;
    }

    private TextView muted(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(11f);
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(12);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView link(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ACCENT);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(RADIUS));
        c.setBackground(bg);
        c.setElevation(dp(2));
        return c;
    }

    private EditText field() {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(14);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD2);
        bg.setCornerRadius(dp(RADIUS_SM));
        e.setBackground(bg);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        e.setTextDirection(View.TEXT_DIRECTION_RTL);
        return e;
    }

    private EditText dialogField() {
        EditText e = field();
        e.setTextColor(TEXT);
        e.setHintTextColor(0xB8FFFFFF);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF193A86);
        bg.setCornerRadius(dp(RADIUS_SM));
        bg.setStroke(dp(1), 0x664B6DFF);
        e.setBackground(bg);
        return e;
    }

    private View pad(View v) {
        LinearLayout p = new LinearLayout(this);
        p.setPadding(dp(16), dp(8), dp(16), dp(8));
        p.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return p;
    }

    private TextView chip(String text, boolean on) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        // منع تكسر الحروف العربية عموديًا عند ضيق العرض
        t.setSingleLine(true);
        t.setMaxLines(1);
        styleChip(t, on);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(RADIUS_SM));
        if (on) {
            bg.setColor(ACCENT);
            t.setTextColor(ACCENT_DARK);
            t.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            bg.setColor(CARD2);
            t.setTextColor(MUTED);
            t.setTypeface(Typeface.DEFAULT);
        }
        t.setBackground(bg);
    }

    private int priorityColor(int p) {
        int i = Math.max(0, Math.min(2, p));
        if (i == 2) return DANGER;
        if (i == 1) return WARN;
        return OK;
    }

    private LinearLayout.LayoutParams chipLp() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * زر مهمة بنص ظاهر حتمًا: TextView + خلفية صلبة + أبعاد دنيا.
     * لا نستخدم Widget.Button لأن ثيم Material قد يجعل لون النص غير مرئي مع setBackground.
     */
    private TextView makeLabeledTaskBtn(String label, boolean danger, View.OnClickListener onClick) {
        TextView btn = new TextView(this);
        btn.setText(label); // النص جزء من نفس الـView
        btn.setTextColor(0xFFFFFFFF); // أبيض صريح — أقصى تباين
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setIncludeFontPadding(true);
        btn.setSingleLine(true);
        btn.setPadding(dp(16), dp(12), dp(16), dp(12));
        btn.setMinWidth(dp(88));
        btn.setMinHeight(dp(44));
        btn.setVisibility(View.VISIBLE);
        btn.setAlpha(1f);
        GradientDrawable bg = new GradientDrawable();
        // خلفية معتمة صريحة مختلفة عن لون النص
        bg.setColor(danger ? 0xFF6B1F2A : 0xFF2E3A52);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(onClick);
        btn.setContentDescription(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btn.setLayoutParams(lp);
        return btn;
    }

    private TextView actionPill(String label, boolean accent) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent ? ACCENT : CARD2);
        bg.setCornerRadius(dp(16));
        t.setBackground(bg);
        return t;
    }

    private TextView circularAiButton() {
        TextView t = new TextView(this);
        t.setText("AI");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setShape(GradientDrawable.OVAL);
        t.setBackground(bg);
        t.setElevation(dp(4));
        return t;
    }

    private AlertDialog.Builder myDialog() {
        return new AlertDialog.Builder(this, R.style.MyPlanDialog);
    }

    private Button primaryBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(15f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setCornerRadius(dp(RADIUS_SM));
        b.setBackground(bg);
        b.setPadding(dp(18), dp(13), dp(18), dp(13));
        b.setElevation(dp(2));
        b.setStateListAnimator(null);
        return b;
    }

    private Button ghostBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD2);
        bg.setCornerRadius(dp(RADIUS_SM));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setStateListAnimator(null);
        return b;
    }

    private Button smallBtn(String text) {
        Button b = primaryBtn(text);
        b.setTextSize(12);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        return b;
    }

    private LinearLayout.LayoutParams fullBtnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(4);
        return lp;
    }

    private TextView emptyState(String msg) {
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(MUTED);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(40), dp(24), dp(40));
        return t;
    }

    // ═══════════════ Local Account (foundation · offline) ═══════════════

    private void showLocalRegisterDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText email = new EditText(this);
        email.setHint("البريد الإلكتروني");
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setTextColor(TEXT);
        email.setHintTextColor(MUTED);
        EditText name = new EditText(this);
        name.setHint("الاسم المعروض (اختياري)");
        name.setTextColor(TEXT);
        name.setHintTextColor(MUTED);
        EditText pass = new EditText(this);
        pass.setHint("كلمة المرور (8+)");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTextColor(TEXT);
        pass.setHintTextColor(MUTED);
        EditText conf = new EditText(this);
        conf.setHint("تأكيد كلمة المرور");
        conf.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        conf.setTextColor(TEXT);
        conf.setHintTextColor(MUTED);
        TextView note = muted("حساب محلي على هذا الجهاز فقط. لا يمكن تسجيل الدخول منه على جهاز آخر حاليًا.");
        form.addView(note);
        form.addView(space(dp(8)));
        form.addView(email);
        form.addView(name);
        form.addView(pass);
        form.addView(conf);
        new AlertDialog.Builder(this)
                .setTitle("إنشاء حساب محلي")
                .setView(form)
                .setPositiveButton("إنشاء", (d, w) -> {
                    AccountAuth.AuthResult r = AccountAuth.registerLocal(this,
                            email.getText().toString(),
                            pass.getText().toString(),
                            conf.getText().toString(),
                            name.getText().toString());
                    Toast.makeText(this, r.messageAr, Toast.LENGTH_LONG).show();
                    if (r.ok) showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showLocalLoginDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText email = new EditText(this);
        email.setHint("البريد الإلكتروني");
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setTextColor(TEXT);
        email.setHintTextColor(MUTED);
        EditText pass = new EditText(this);
        pass.setHint("كلمة المرور");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTextColor(TEXT);
        pass.setHintTextColor(MUTED);
        TextView note = muted("تسجيل دخول محلي لهذا الجهاز فقط.");
        form.addView(note);
        form.addView(space(dp(8)));
        form.addView(email);
        form.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("تسجيل الدخول")
                .setView(form)
                .setPositiveButton("دخول", (d, w) -> {
                    AccountAuth.AuthResult r = AccountAuth.loginLocal(this,
                            email.getText().toString(),
                            pass.getText().toString());
                    Toast.makeText(this, r.messageAr, Toast.LENGTH_LONG).show();
                    if (r.ok) showTab(4);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ═══════════════ Developer Center (Local only) ═══════════════
    private void promptDeveloperPassword() {
        final android.widget.EditText et = field();
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setHint("كلمة مرور المطور");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        form.addView(muted("Developer Mode — محلي على هذا الجهاز فقط"));
        form.addView(space(dp(8)));
        form.addView(et);
        myDialog()
                .setTitle("أدخل كلمة مرور المطور")
                .setView(form)
                .setPositiveButton("دخول", (d, w) -> {
                    String pass = et.getText() != null ? et.getText().toString() : "";
                    if (AppInfrastructure.checkLocalDevPassword(pass)) {
                        AppInfrastructure.setDeveloperUnlocked(this, true);
                        AppInfrastructure.log(this, "Error", "Developer Mode unlocked");
                        Toast.makeText(this, "Developer Mode مفعّل", Toast.LENGTH_SHORT).show();
                        showTab(7);
                    } else {
                        AppInfrastructure.log(this, "Error", "Developer password rejected");
                        Toast.makeText(this, "كلمة المرور غير صحيحة", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private View buildDeveloperCenter() {
        if (!AppInfrastructure.isDeveloperUnlocked(this)) {
            ScrollView sc = new ScrollView(this);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(18), dp(18), dp(18), dp(28));
            sc.addView(box);
            box.addView(title("غير مصرح"));
            box.addView(muted("Developer Mode مقفول."));
            TextView back = link("← المزيد");
            back.setOnClickListener(v -> showTab(4));
            box.addView(back);
            return sc;
        }
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView back = link("← المزيد");
        back.setOnClickListener(v -> {
            if (!"home".equals(devSubScreen)) {
                devSubScreen = "home";
                showTab(7);
            } else {
                showTab(4);
            }
        });
        box.addView(back);

        if (!"home".equals(devSubScreen)) {
            box.addView(title(devSectionTitle(devSubScreen)));
            box.addView(space(dp(8)));
            buildDevSectionBody(box, devSubScreen);
            return sc;
        }

        box.addView(title("Developer Center"));
        box.addView(muted("مركز تحكم محلي · اختبار فقط · ليس لوحة سيرفر"));
        box.addView(space(dp(10)));

        // ملخص الهوية من Session الحالية (AccountAuth) — بدون اختراع بيانات
        {
            AccountAuth.Account cur = AccountAuth.getCurrentAccount(this);
            LinearLayout idCard = card();
            idCard.setOrientation(LinearLayout.VERTICAL);
            idCard.addView(muted("الحساب الحالي"));
            if (cur != null) {
                String dn = (cur.displayName != null && !cur.displayName.trim().isEmpty())
                        ? cur.displayName.trim() : "—";
                String em = (cur.email != null && !cur.email.isEmpty()) ? cur.email : "—";
                String uid = (cur.userId != null && !cur.userId.isEmpty()) ? cur.userId : "—";
                idCard.addView(devRow("Display Name", dn));
                idCard.addView(devRow("Email", em));
                idCard.addView(devRow("User ID", uid));
            } else {
                idCard.addView(devRow("Auth state", AccountAuth.isLoggedIn(this) ? "LOGGED_IN" : "LOGGED_OUT"));
                String infraUid = AppInfrastructure.getUserId(this);
                idCard.addView(devRow("User ID", infraUid != null && !infraUid.isEmpty() ? infraUid : "—"));
                idCard.addView(muted("لا يوجد سجل حساب محلي مرتبط بالجلسة."));
            }
            idCard.addView(devRow("Installation ID", AppInfrastructure.getInstallationId(this)));
            box.addView(idCard);
            box.addView(space(dp(12)));
        }

        String[][] cards = {
                {"premium", "Premium", "Entitlement · Test · Visibility"},
                {"flags", "Feature Flags", "حالة كل ميزة"},
                {"accounts", "الحساب", "هوية محلية · Session"},
                {"ai", "AI / Planning", "PlanningBrain · IntentParser"},
                {"premfeat", "Premium Features", "قائمة الميزات المحلية"},
                {"testlab", "Premium Feature Test Lab", "تحقق UI→Logic→Data→Gate"},
                {"cloud", "Cloud Sync", "Backend · Not Configured"},
                {"notif", "Notifications", "Local · Push Not Configured"},
                {"parent", "Parent Monitoring", "Future · Backend"},
                {"pay", "Payments / Subscriptions", "Billing · Not Configured"},
                {"ads", "Ads", "Provider · Not Configured"},
                {"api", "API / Backend", "Foundation · Not Connected"},
                {"diag", "Diagnostics", "Planner · Storage · Alarms"},
                {"data", "Data & Backup", "نسخ احتياطي · سلامة"},
                {"alarm", "Alarm Lab", "اختبار المنبّه"},
                {"logs", "Logs", "سجلات المطور"},
                {"sys", "System Status", "إصدار · توقيع · حالة"},
                {"sec", "Security", "Keystore · Hardening · Self-Test"}
        };
        for (String[] c : cards) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.VERTICAL);
            TextView n = new TextView(this);
            n.setText(c[1]);
            n.setTextColor(TEXT);
            n.setTextSize(15);
            row.addView(n);
            row.addView(muted(c[2]));
            final String key = c[0];
            row.setOnClickListener(v -> { devSubScreen = key; showTab(7); });
            box.addView(row);
            box.addView(space(dp(8)));
        }

        TextView lock = chip("قفل Developer Mode", false);
        lock.setOnClickListener(v -> {
            AppInfrastructure.setDeveloperUnlocked(this, false);
            devSubScreen = "home";
            Toast.makeText(this, "اتقفل", Toast.LENGTH_SHORT).show();
            showTab(4);
        });
        box.addView(space(dp(8)));
        box.addView(lock);
        return sc;
    }

    private String devSectionTitle(String key) {
        switch (key) {
            case "premium": return "Premium";
            case "ads": return "Ads";
            case "flags": return "Feature Flags";
            case "accounts": return "الحساب";
            case "ai": return "AI / Planning";
            case "premfeat": return "Premium Features";
            case "testlab": return "Premium Feature Test Lab";
            case "cloud": return "Cloud Sync";
            case "notif": return "Notifications";
            case "parent": return "Parent Monitoring";
            case "pay": return "Payments / Subscriptions";
            case "diag": return "Diagnostics";
            case "data": return "Data & Backup";
            case "alarm": return "Alarm Lab";
            case "logs": return "Logs";
            case "sys": return "System Status";
            case "sec": return "Security";
            case "api": return "API / Backend";
            default: return key;
        }
    }

    private void buildDevSectionBody(LinearLayout box, String key) {
        switch (key) {
            case "premium":
                buildDevPremiumSection(box);
                break;
            case "ads":
                buildDevAdsSection(box);
                break;
            case "flags":
                buildDevFlagsSection(box);
                break;
            case "accounts":
                buildDevAccountsSection(box);
                break;
            case "ai":
                buildDevAiSection(box);
                break;
            case "premfeat":
                buildDevPremiumFeaturesSection(box);
                break;
            case "testlab":
                buildDevTestLabSection(box);
                break;
            case "cloud":
                buildDevCloudSyncSection(box);
                break;
            case "notif":
                buildDevNotificationsSection(box);
                break;
            case "parent":
                box.addView(devRow("Status", "Future"));
                box.addView(devRow("Backend", "Required"));
                box.addView(devRow("Enabled", "No"));
                break;
            case "pay":
                buildDevPaymentsSection(box);
                break;
            case "diag":
                buildDevDiagnosticsSection(box);
                break;
            case "data":
                buildDevDataSection(box);
                break;
            case "alarm":
                buildDevAlarmLab(box);
                break;
            case "logs":
                buildDevLogsSection(box);
                break;
            case "sys":
                buildDevSystemStatus(box);
                break;
            case "api":
                buildDevApiSection(box);
                break;
            case "sec":
                buildDevSecuritySection(box);
                break;
            default:
                box.addView(muted("قسم غير معروف"));
        }
    }

    private void buildDevPremiumSection(LinearLayout box) {
        AppInfrastructure.Entitlement e = AppInfrastructure.getCurrentPremiumEntitlement(this);
        boolean test = AppInfrastructure.isPremiumTestMode(this);
        boolean active = AppInfrastructure.isPremiumActive(this);
        boolean visible = AppInfrastructure.isPremiumUserVisible(this);
        box.addView(devRow("User-facing visibility", visible ? "VISIBLE" : "HIDDEN"));
        box.addView(devRow("Premium Active", active ? "YES" : "NO"));
        box.addView(devRow("Test Mode", test ? "ON" : "OFF"));
        box.addView(devRow("Entitlement", e != null ? e.source + " · " + e.id : "none"));
        box.addView(space(dp(8)));
        TextView vis = chip(visible ? "إخفاء Premium عن المستخدم" : "إظهار Premium للمستخدم", visible);
        vis.setOnClickListener(v -> {
            AppInfrastructure.setPremiumUserVisible(this, !visible);
            Toast.makeText(this, !visible ? "Premium ظاهر للمستخدم" : "Premium مخفي عن المستخدم", Toast.LENGTH_SHORT).show();
            showTab(7);
        });
        box.addView(vis);
        box.addView(space(dp(6)));
        TextView tog = chip(test ? "إيقاف Premium Test" : "تشغيل Premium Test", test);
        tog.setOnClickListener(v -> {
            boolean next = !test;
            AppInfrastructure.setPremiumTestMode(this, next);
            Toast.makeText(this, next ? "Premium Test ON — الميزات ظاهرة" : "Premium Test OFF — الميزات مخفية", Toast.LENGTH_SHORT).show();
            // أعد بناء Developer Center لتعكس الحالة فورًا
            showTab(7);
        });
        box.addView(tog);
        box.addView(space(dp(6)));
        TextView openHub = chip("فتح Premium Hub (اختبار)", true);
        openHub.setOnClickListener(v -> { premiumSubScreen = "home"; showTab(8); });
        box.addView(openHub);
        box.addView(space(dp(6)));
        box.addView(muted("إخفاء المستخدم لا يحذف Entitlement أو البيانات."));
    }

    private void buildDevFlagsSection(LinearLayout box) {
        AppInfrastructure.Flags f = AppInfrastructure.getFlags(this);
        String[][] rows = {
                {"AI Planning", String.valueOf(f.ai), "User+Premium", "Yes", "No"},
                {"Voice", String.valueOf(f.voice), "User+Premium", "Yes", "No"},
                {"Premium (flag)", String.valueOf(f.premium), "User", "—", "No"},
                {"Ads", String.valueOf(f.ads), "User", "No", "SDK"},
                {"Accounts", String.valueOf(f.accounts), "User", "No", "Partial"},
                {"Cloud", String.valueOf(f.cloud), "Hidden", "No", "Yes"},
                {"Reports", String.valueOf(f.reports), "Premium", "Yes", "No"},
                {"Goals", String.valueOf(f.goals), "Premium", "Yes", "No"},
                {"Advanced Analytics", String.valueOf(f.advancedAnalytics), "Premium", "Yes", "No"},
                {"Connected Accounts", String.valueOf(f.connectedAccounts), "Future", "No", "Yes"}
        };
        for (String[] r : rows) {
            box.addView(devRow(r[0], "flag=" + r[1] + " · " + r[2] + " · prem=" + r[3] + " · backend=" + r[4]));
        }
        box.addView(muted("القيم من Feature Flags المحلية · الافتراضي OFF."));
    }

    private void buildDevAccountsSection(LinearLayout box) {
        AccountAuth.Account a = AccountAuth.getCurrentAccount(this);
        box.addView(devRow("Auth state", AccountAuth.isLoggedIn(this) ? "LOGGED_IN" : "LOGGED_OUT"));
        String uidShow = "—";
        if (a != null && a.userId != null && !a.userId.isEmpty()) uidShow = a.userId;
        else {
            String infra = AppInfrastructure.getUserId(this);
            if (infra != null && !infra.isEmpty()) uidShow = infra;
        }
        box.addView(devRow("User ID", uidShow));
        box.addView(devRow("Email", a != null && a.email != null && !a.email.isEmpty() ? a.email : "—"));
        box.addView(devRow("Display Name",
                a != null && a.displayName != null && !a.displayName.trim().isEmpty()
                        ? a.displayName.trim() : "—"));
        box.addView(devRow("Account state", a != null ? a.accountState : "—"));
        box.addView(devRow("Installation ID", AppInfrastructure.getInstallationId(this)));
        if (a != null && a.lastLoginAt > 0) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
                box.addView(devRow("Last login", sdf.format(new java.util.Date(a.lastLoginAt))));
            } catch (Exception e) {
                box.addView(devRow("Last login", String.valueOf(a.lastLoginAt)));
            }
        } else {
            box.addView(devRow("Last login", "—"));
        }
        box.addView(devRow("Mode", "Local-only (AccountAuth)"));
        box.addView(space(dp(8)));
        TextView clear = chip("Clear local session", false);
        clear.setOnClickListener(v -> {
            AccountAuth.logout(this);
            Toast.makeText(this, "Session cleared · بيانات الدراسة لم تُمس", Toast.LENGTH_SHORT).show();
            showTab(7);
        });
        box.addView(clear);
        box.addView(muted("لا يُعرض password/hash. المصدر: AccountAuth session + myplan_accounts_v1."));
    }

    private void buildDevAiSection(LinearLayout box) {
        AppInfrastructure.Flags f = AppInfrastructure.getFlags(this);
        box.addView(devRow("AI flag", String.valueOf(f.ai)));
        box.addView(devRow("Premium required", "Yes for full hub"));
        box.addView(devRow("PlanningBrain", "Present · local"));
        box.addView(devRow("PlanningIntentParser", "Present · local"));
        box.addView(devRow("Voice Planning", f.voice ? "flag ON" : "flag OFF · needs Premium"));
        box.addView(devRow("External LLM", "No"));
        box.addView(space(dp(8)));
        TextView test = chip("اختبار عدّل خطتي", true);
        test.setOnClickListener(v -> {
            if (AppInfrastructure.isPremiumActive(this) || AppInfrastructure.isDeveloperUnlocked(this)) {
                showTab(5);
            } else {
                Toast.makeText(this, "فعّل Premium Test أولًا للاختبار الكامل", Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(test);
    }

    private void buildDevTestLabSection(LinearLayout box) {
        box.addView(muted("اختبارات حقيقية · لا PASS وهمي · لا تعديل دائم لبيانات المستخدم"));
        box.addView(space(dp(8)));
        TextView runAll = chip("اختبار كل المميزات", true);
        runAll.setOnClickListener(v -> {
            List<PremiumFeatureTestLab.Result> results = PremiumFeatureTestLab.runAll(this, planner);
            String sum = PremiumFeatureTestLab.summaryAr(results);
            AppInfrastructure.log(this, "TestLab", sum);
            myDialog().setTitle("ملخص الاختبار")
                    .setMessage(sum)
                    .setPositiveButton("حسنًا", (d, w) -> showTab(7))
                    .show();
        });
        box.addView(runAll);
        box.addView(space(dp(10)));

        List<PremiumFeatureTestLab.Result> saved = PremiumFeatureTestLab.loadResults(this);
        String[] features = {
                "Premium Entitlement", "Smart Review Planner", "Study Forecast", "What-If Lab",
                "Advanced Analytics", "Goals & Milestones", "Study Journal", "Personal Records",
                "Advanced Reports", "Voice Planning", "Premium Customization", "AI Planning",
                "Smart Recovery", "Feature Gates", "Accounts", "Backup Safety", "Alarms Resync",
                "Ads", "Connected Accounts", "Cloud Sync", "Parent Monitoring", "Payments / Subscriptions"
        };
        for (String feat : features) {
            PremiumFeatureTestLab.Result last = PremiumFeatureTestLab.find(saved, feat);
            LinearLayout row = card();
            row.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(feat);
            name.setTextColor(TEXT);
            name.setTextSize(14);
            row.addView(name);
            String st = last == null ? "—" : last.status.name();
            String det = last == null ? "لم يُختبر بعد" : last.detail;
            row.addView(muted("Status: " + st));
            if (last != null) {
                row.addView(muted("Premium: " + (last.needsPremium ? "Yes" : "No")
                        + " · Flag: " + last.featureFlag
                        + " · Backend: " + (last.needsBackend ? "Yes" : "No")));
                row.addView(muted("Availability: " + last.availability));
                if (last.testedAt > 0) row.addView(muted("آخر اختبار: " + last.testedAt));
            }
            row.addView(muted(det));
            final String fKey = feat;
            TextView btn = chip("اختبار", false);
            btn.setOnClickListener(v -> {
                PremiumFeatureTestLab.Result r = PremiumFeatureTestLab.runOne(this, planner, fKey);
                Toast.makeText(this, fKey + ": " + r.status + " — " + r.detail, Toast.LENGTH_LONG).show();
                showTab(7);
            });
            row.addView(space(dp(4)));
            row.addView(btn);
            // Open real UI when relevant
            if ("Smart Review Planner".equals(feat) || "Study Forecast".equals(feat)
                    || "What-If Lab".equals(feat) || "Advanced Analytics".equals(feat)
                    || "Goals & Milestones".equals(feat) || "Study Journal".equals(feat)
                    || "Personal Records".equals(feat) || "Advanced Reports".equals(feat)
                    || "Premium Customization".equals(feat)) {
                TextView open = chip("فتح", true);
                open.setOnClickListener(v -> {
                    if (!AppInfrastructure.isDeveloperUnlocked(this)) return;
                    premiumSubScreen = "home";
                    showTab(8);
                });
                row.addView(space(dp(4)));
                row.addView(open);
            }
            if ("AI Planning".equals(feat)) {
                TextView openAi = chip("فتح عدّل خطتي", true);
                openAi.setOnClickListener(v -> showTab(5));
                row.addView(space(dp(4)));
                row.addView(openAi);
            }
            box.addView(row);
            box.addView(space(dp(8)));
        }
    }

    private void buildDevPremiumFeaturesSection(LinearLayout box) {
        String[][] feats = {
                {"Smart Review Planner", "Implemented", "review", "Yes", "No"},
                {"Study Forecast", "Implemented", "forecast", "Yes", "No"},
                {"What-If Lab", "Implemented", "whatif", "Yes", "No"},
                {"Advanced Analytics", "Implemented", "analytics", "Yes", "No"},
                {"Goals & Milestones", "Implemented", "goals", "Yes", "No"},
                {"Study Journal", "Implemented", "journal", "Yes", "No"},
                {"Personal Records", "Implemented", "records", "Yes", "No"},
                {"Advanced Reports", "Implemented", "reports", "Yes", "No"},
                {"Voice Planning", "Partial", "voice", "Yes", "No"},
                {"Premium Customization", "Implemented", "custom", "Yes", "No"},
                {"AI Planning", "Implemented", "ai", "Yes", "No"},
                {"Connected Accounts", "Future", "—", "No", "Yes"},
                {"Cloud Sync", "Future", "—", "No", "Yes"},
                {"Parent Monitoring", "Future", "—", "No", "Yes"}
        };
        for (String[] f : feats) {
            box.addView(devRow(f[0], f[1] + " · gate=" + f[2] + " · prem=" + f[3] + " · backend=" + f[4]));
        }
    }


    private void showPlannerDiagnostics() {
        String text;
        if (planner == null || planner.lastPlanningDiag == null) {
            text = "لا توجد نتيجة تخطيط محفوظة بعد.\n\nاضغط «عدّل خطتي» / إنشاء الجدول مرة، ثم افتح التشخيص مرة أخرى.";
        } else {
            text = planner.lastPlanningDiag.asText();
        }
        final String body = text;
        ScrollView sc = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(body);
        tv.setTextIsSelectable(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(0xFFE8EEF7);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        sc.addView(tv);
        myDialog()
                .setTitle("Planner Diagnostics")
                .setView(sc)
                .setPositiveButton("تمام", null)
                .show();
    }

    private void buildDevDiagnosticsSection(LinearLayout box) {
        TextView annPreview = chip("👁️ معاينة إعلان", true);
        annPreview.setOnClickListener(v -> {
            try {
                com.myplan.app.supabase.AnnouncementManager.showPreview(MainActivity.this);
            } catch (Exception e) {
                Toast.makeText(this, "تعذّر فتح المعاينة", Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(annPreview);
        box.addView(space(dp(10)));
        TextView planDiagBtn = chip("Planner Diagnostics", true);
        planDiagBtn.setOnClickListener(v -> showPlannerDiagnostics());
        box.addView(planDiagBtn);
        box.addView(space(dp(10)));
        box.addView(devRow("Storage", "SharedPreferences · myplan_v3 + infra + accounts"));
        box.addView(devRow("Planner", planner != null ? "OK · tasks=" + planner.tasks.size() : "NULL"));
        box.addView(devRow("Sessions", planner != null ? String.valueOf(planner.sessions.size()) : "—"));
        box.addView(devRow("Exams", planner != null ? String.valueOf(planner.exams.size()) : "—"));
        box.addView(devRow("Alarms", "SessionAlarmScheduler"));
        box.addView(devRow("SR diag", planner != null && planner.lastSrDiag != null && planner.lastSrDiag.length() > 0 ? planner.lastSrDiag : "—"));
        box.addView(devRow("Missed sched", SessionAlarmScheduler.lastMissedSchedDiag.length() > 0 ? SessionAlarmScheduler.lastMissedSchedDiag : "—"));
        box.addView(devRow("Missed recv", SessionAlarmReceiver.lastMissedRecvDiag.length() > 0 ? SessionAlarmReceiver.lastMissedRecvDiag : "—"));
        box.addView(devRow("Widget", PlanWidgetProvider.lastUpdateDiag.length() > 0 ? PlanWidgetProvider.lastUpdateDiag : "—"));
        boolean notifOk = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notifOk = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        box.addView(devRow("Notif permission", notifOk ? "GRANTED" : "DENIED"));
        if (planner != null) {
            int partial = 0;
            for (Planner.Session s : planner.sessions) {
                if (s != null && !s.done && s.executedMin > 0) partial++;
            }
            box.addView(devRow("Partial sessions", String.valueOf(partial)));
        }
        box.addView(devRow("Premium", AppInfrastructure.isPremiumActive(this) ? "ACTIVE" : "OFF"));
        box.addView(devRow("Accounts", AccountAuth.isLoggedIn(this) ? "LOGGED_IN" : "LOGGED_OUT"));
        box.addView(devRow("Backup", "local JSON · no credentials"));
        TextView snap = chip("Planner Snapshot", false);
        snap.setOnClickListener(v -> showDevPlannerSnapshot());
        box.addView(space(dp(6)));
        box.addView(snap);
    }

    private void buildDevDataSection(LinearLayout box) {
        box.addView(devRow("DB schema", "myplan_v3"));
        box.addView(devRow("Infra", "myplan_infra_v1"));
        box.addView(devRow("Accounts store", "myplan_accounts_v1 · excluded from study backup"));
        box.addView(devRow("Premium backup", "keys in export when present"));
        box.addView(space(dp(6)));
        TextView exp = chip("Export Backup", true);
        exp.setOnClickListener(v -> startBackupSave());
        box.addView(exp);
        box.addView(space(dp(4)));
        TextView imp = chip("Import Restore", false);
        imp.setOnClickListener(v -> startBackupOpen());
        box.addView(imp);
    }

    private void buildDevAlarmLab(LinearLayout box) {
        box.addView(muted("اختبار عبر مسار المنبّه الحقيقي الموجود."));
        box.addView(space(dp(6)));
        TextView resync = chip("Resync Alarms", true);
        resync.setOnClickListener(v -> {
            try {
                SessionAlarmScheduler.resync(this, planner);
                Toast.makeText(this, "Resync done", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Resync: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(resync);
        box.addView(muted("Sound / Full-screen / Snooze عبر نظام Alarm الحالي."));
    }

    private void buildDevLogsSection(LinearLayout box) {
        TextView refresh = chip("تحديث", false);
        refresh.setOnClickListener(v -> showTab(7));
        box.addView(refresh);
        box.addView(space(dp(6)));
        TextView clear = chip("مسح السجلات", false);
        clear.setOnClickListener(v -> {
            try { AppInfrastructure.clearLogs(this); } catch (Exception ignored) {}
            Toast.makeText(this, "تم المسح", Toast.LENGTH_SHORT).show();
            showTab(7);
        });
        box.addView(clear);
        box.addView(space(dp(8)));
        int n = 0;
        for (String line : AppInfrastructure.getLogLines(this)) {
            box.addView(muted(line));
            if (++n >= 40) break;
        }
        if (n == 0) box.addView(muted("لا سجلات"));
    }




    private void buildDevCloudSyncSection(LinearLayout box) {
        com.myplan.app.sync.SyncEngine engine = com.myplan.app.sync.SyncLayer.engine(this);
        com.myplan.app.sync.SyncState st = engine.currentState();
        box.addView(muted("Cloud Sync + Multi-device Foundation Ready — Backend Not Connected"));
        box.addView(space(dp(8)));
        box.addView(devRow("Cloud Provider", "none"));
        box.addView(devRow("Backend Status", st.status));
        box.addView(devRow("Sync Status", st.status));
        box.addView(devRow("Last Sync Success", st.lastSuccessMs > 0 ? String.valueOf(st.lastSuccessMs) : "—"));
        box.addView(devRow("Pending Operations", String.valueOf(st.pendingOperations)));
        box.addView(devRow("Last Sync Error", emptyDash(st.lastError)));
        box.addView(devRow("User ID", emptyDash(st.userId)));
        box.addView(devRow("Installation ID", emptyDash(st.installationId)));
        box.addView(devRow("Multi-device Status", "NOT_CONFIGURED"));
        box.addView(devRow("CloudSyncAllowed gate", String.valueOf(com.myplan.app.sync.SyncLayer.isCloudSyncAllowed(this))));
        box.addView(devRow("Backup vs Cloud", "Separate — Backup local only"));
        box.addView(space(dp(8)));
        TextView trySync = chip("Run Sync (expects NOT_CONFIGURED)", false);
        trySync.setOnClickListener(v -> {
            com.myplan.app.sync.SyncResult<?> r = engine.runSync();
            Toast.makeText(this, r.kind + " · " + r.message, Toast.LENGTH_LONG).show();
            showTab(7);
        });
        box.addView(trySync);
        box.addView(space(dp(6)));
        TextView self = chip("Cloud Sync Self-Test", true);
        self.setOnClickListener(v -> {
            java.util.List<com.myplan.app.sync.SyncSelfTest.Case> cases =
                    com.myplan.app.sync.SyncSelfTest.run(this);
            int pass = 0, fail = 0;
            StringBuilder sb = new StringBuilder();
            for (com.myplan.app.sync.SyncSelfTest.Case c : cases) {
                if (c.pass) pass++; else fail++;
                sb.append(c.pass ? "✓ " : "✗ ").append(c.name).append(" · ").append(c.detail).append("\n");
            }
            sb.insert(0, "PASS=" + pass + " FAIL=" + fail + "\n\n");
            myDialog().setTitle("Cloud Sync Self-Test")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
        box.addView(self);
        box.addView(space(dp(8)));
        box.addView(sectionHeader("Pending queue (local only)"));
        int n = 0;
        for (String line : com.myplan.app.sync.SyncLayer.queue(this).pendingSummaries()) {
            box.addView(muted(line));
            if (++n >= 10) break;
        }
        if (n == 0) box.addView(muted("لا عمليات معلّقة"));
        box.addView(space(dp(6)));
        box.addView(sectionHeader("Sync Logs"));
        n = 0;
        for (String line : com.myplan.app.sync.SyncLog.lines(this)) {
            box.addView(muted(line));
            if (++n >= 12) break;
        }
        if (n == 0) box.addView(muted("لا سجلات"));
        box.addView(space(dp(6)));
        box.addView(muted("لا Upload حقيقي. لا Fake Sync. البيانات المحلية لا تُحذف بسبب غياب Cloud."));
    }

    private void buildDevNotificationsSection(LinearLayout box) {
        com.myplan.app.notifications.NotificationService svc =
                com.myplan.app.notifications.NotificationsLayer.service(this);
        com.myplan.app.notifications.PushProvider push = svc.pushProvider();
        box.addView(muted("Notifications Foundation Ready — Push Provider Not Connected"));
        box.addView(space(dp(8)));
        box.addView(devRow("Local Notifications", "Contract · Session alarms تبقى محلية"));
        box.addView(devRow("Push Provider", push.providerName()));
        box.addView(devRow("SDK Status", "NOT_INSTALLED"));
        box.addView(devRow("Push Status", svc.pushStatus()));
        box.addView(devRow("Permission State", svc.permissionStateLabel()));
        box.addView(devRow("Session reminders pref",
                String.valueOf(com.myplan.app.notifications.NotificationPreferences.sessionReminders(this))));
        box.addView(devRow("Exam reminders pref",
                String.valueOf(com.myplan.app.notifications.NotificationPreferences.examReminders(this))));
        box.addView(devRow("Cloud events pref",
                String.valueOf(com.myplan.app.notifications.NotificationPreferences.cloudEvents(this))));
        box.addView(space(dp(8)));
        TextView tryPush = chip("Test Push (expects NOT_CONFIGURED)", false);
        tryPush.setOnClickListener(v -> {
            com.myplan.app.notifications.NotificationResult<?> r = push.sendTestPush();
            Toast.makeText(this, r.kind + " · " + r.message, Toast.LENGTH_LONG).show();
            showTab(7);
        });
        box.addView(tryPush);
        box.addView(space(dp(6)));
        TextView self = chip("Notifications Self-Test", true);
        self.setOnClickListener(v -> {
            java.util.List<com.myplan.app.notifications.NotificationsSelfTest.Case> cases =
                    com.myplan.app.notifications.NotificationsSelfTest.run(this);
            int pass = 0, fail = 0;
            StringBuilder sb = new StringBuilder();
            for (com.myplan.app.notifications.NotificationsSelfTest.Case c : cases) {
                if (c.pass) pass++; else fail++;
                sb.append(c.pass ? "✓ " : "✗ ").append(c.name).append(" · ").append(c.detail).append("\n");
            }
            sb.insert(0, "PASS=" + pass + " FAIL=" + fail + "\n\n");
            myDialog().setTitle("Notifications Self-Test")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
        box.addView(self);
        box.addView(space(dp(8)));
        box.addView(sectionHeader("Notification Logs"));
        int n = 0;
        for (String line : com.myplan.app.notifications.NotificationLog.lines(this)) {
            box.addView(muted(line));
            if (++n >= 12) break;
        }
        if (n == 0) box.addView(muted("لا سجلات"));
        box.addView(space(dp(6)));
        box.addView(muted("لا Fake Push. SessionAlarmScheduler لم يُستبدل. Alarms محلية تظل تعمل بدون Backend."));
    }

    private void buildDevPaymentsSection(LinearLayout box) {
        com.myplan.app.payments.SubscriptionRepository repo =
                com.myplan.app.payments.PaymentLayer.subscriptions(this);
        com.myplan.app.payments.PaymentProvider p = repo.provider();
        box.addView(muted("Payments/Subscriptions Foundation Ready — Provider Not Connected"));
        box.addView(space(dp(8)));
        box.addView(devRow("Billing Provider", p.providerName()));
        box.addView(devRow("Connection Status", repo.statusLabel()));
        box.addView(devRow("Configured", String.valueOf(p.isConfigured())));
        box.addView(devRow("Connected", String.valueOf(p.isConnected())));
        box.addView(devRow("Environment", "none"));
        box.addView(devRow("Products Status", "NOT_CONFIGURED (placeholders only)"));
        box.addView(devRow("Local Entitlement",
                com.myplan.app.AppInfrastructure.isPremiumActive(this) ? "ACTIVE" : "FREE"));
        box.addView(devRow("Entitlement source note", "developer_test independent of billing"));
        box.addView(devRow("Subscription State", "none / NOT_CONFIGURED"));
        box.addView(devRow("Restore Status", "NOT_CONFIGURED"));
        box.addView(devRow("Last Billing Error", "—"));
        box.addView(space(dp(8)));
        box.addView(sectionHeader("Catalog (placeholders)"));
        for (com.myplan.app.payments.ProductInfo pi : repo.catalogPlaceholders()) {
            box.addView(muted(pi.productId + " · " + pi.title + " · configured=" + pi.configured));
        }
        box.addView(space(dp(8)));
        TextView tryBuy = chip("Try purchase (expects NOT_CONFIGURED)", false);
        tryBuy.setOnClickListener(v -> {
            com.myplan.app.payments.BillingResult<?> r = repo.purchase("premium_monthly");
            Toast.makeText(this, r.kind + " · " + r.message, Toast.LENGTH_LONG).show();
            showTab(7);
        });
        box.addView(tryBuy);
        box.addView(space(dp(6)));
        TextView restore = chip("Restore purchases (expects NOT_CONFIGURED)", false);
        restore.setOnClickListener(v -> {
            com.myplan.app.payments.BillingResult<?> r = repo.restorePurchases();
            Toast.makeText(this, r.kind + " · " + r.message, Toast.LENGTH_LONG).show();
            showTab(7);
        });
        box.addView(restore);
        box.addView(space(dp(6)));
        TextView self = chip("Payments Self-Test", true);
        self.setOnClickListener(v -> {
            java.util.List<com.myplan.app.payments.PaymentsSelfTest.Case> cases =
                    com.myplan.app.payments.PaymentsSelfTest.run(this);
            int pass = 0, fail = 0;
            StringBuilder sb = new StringBuilder();
            for (com.myplan.app.payments.PaymentsSelfTest.Case c : cases) {
                if (c.pass) pass++; else fail++;
                sb.append(c.pass ? "✓ " : "✗ ").append(c.name).append(" · ").append(c.detail).append("\n");
            }
            sb.insert(0, "PASS=" + pass + " FAIL=" + fail + "\n\n");
            myDialog().setTitle("Payments Self-Test")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
        box.addView(self);
        box.addView(space(dp(8)));
        box.addView(sectionHeader("Billing Logs"));
        int n = 0;
        for (String line : com.myplan.app.payments.PaymentLog.lines(this)) {
            box.addView(muted(line));
            if (++n >= 15) break;
        }
        if (n == 0) box.addView(muted("لا سجلات"));
        box.addView(space(dp(6)));
        box.addView(muted("لا Fake Purchase. Google Play Billing SDK غير مربوط. Developer Test مستقل."));
    }

    private void buildDevAdsSection(LinearLayout box) {
        com.myplan.app.ads.AdService svc = com.myplan.app.ads.AdsLayer.service(this);
        com.myplan.app.ads.AdProvider p = svc.provider();
        box.addView(muted("Ads Foundation Ready — Provider Not Connected"));
        box.addView(space(dp(8)));
        box.addView(devRow("Ads Provider", p.providerName()));
        box.addView(devRow("SDK Status", "NOT_INSTALLED"));
        box.addView(devRow("Initialization", String.valueOf(p.isInitialized())));
        box.addView(devRow("Status", svc.statusLabel()));
        box.addView(devRow("Ads Enabled (logic)", String.valueOf(
                p.isConfigured() && !svc.isPremiumSuppressingAds())));
        box.addView(devRow("Premium Ad Suppression",
                svc.isPremiumSuppressingAds() ? "YES (Premium active)" : "NO (Free)"));
        box.addView(devRow("Revenue", com.myplan.app.ads.AdsLayer.revenueStatus()));
        box.addView(devRow("Test Mode", "N/A — SDK not connected"));
        box.addView(space(dp(6)));
        box.addView(sectionHeader("Placements"));
        for (com.myplan.app.ads.AdPlacement pl : com.myplan.app.ads.AdPlacement.values()) {
            boolean show = svc.shouldShowAds(pl);
            box.addView(muted(pl.name() + " · shouldShow=" + show + " · enabled=" + svc.isPlacementEnabled(pl)));
        }
        box.addView(space(dp(8)));
        TextView tryLoad = chip("Load TODAY ad (expects NOT_CONFIGURED)", false);
        tryLoad.setOnClickListener(v -> {
            com.myplan.app.ads.AdResult<?> r = svc.load(com.myplan.app.ads.AdPlacement.TODAY);
            Toast.makeText(this, r.kind + " · " + r.message, Toast.LENGTH_LONG).show();
            showTab(7);
        });
        box.addView(tryLoad);
        box.addView(space(dp(6)));
        TextView self = chip("Ads Self-Test", true);
        self.setOnClickListener(v -> {
            java.util.List<com.myplan.app.ads.AdsSelfTest.Case> cases =
                    com.myplan.app.ads.AdsSelfTest.run(this);
            int pass = 0, fail = 0;
            StringBuilder sb = new StringBuilder();
            for (com.myplan.app.ads.AdsSelfTest.Case c : cases) {
                if (c.pass) pass++; else fail++;
                sb.append(c.pass ? "✓ " : "✗ ").append(c.name).append(" · ").append(c.detail).append("\n");
            }
            sb.insert(0, "PASS=" + pass + " FAIL=" + fail + "\n\n");
            myDialog().setTitle("Ads Self-Test")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
        box.addView(self);
        box.addView(space(dp(8)));
        box.addView(sectionHeader("Ad Logs"));
        int n = 0;
        for (String line : com.myplan.app.ads.AdLog.lines(this)) {
            box.addView(muted(line));
            if (++n >= 15) break;
        }
        if (n == 0) box.addView(muted("لا سجلات"));
        box.addView(space(dp(6)));
        box.addView(muted("لا Fake Ads / لا إيرادات وهمية. AdMob SDK غير مربوط. Premium يخفي الإعلانات عند التفعيل."));
    }

    private void buildDevApiSection(LinearLayout box) {
        box.addView(muted("API Client/Foundation Ready — Backend Not Connected"));
        box.addView(space(dp(8)));
        box.addView(devRow("Environment", com.myplan.app.api.ApiConfig.getEnvironment(this).name()));
        box.addView(devRow("Backend Status", com.myplan.app.api.ApiLayer.backendStatus(this)));
        box.addView(devRow("Connection", com.myplan.app.api.ApiLayer.connectionLabel(this)));
        box.addView(devRow("API Version path", com.myplan.app.api.ApiConfig.getApiVersionPath(this)));
        String base = com.myplan.app.api.ApiConfig.resolveBaseUrl(this);
        box.addView(devRow("Base URL", base.isEmpty() ? "(empty)" : base));
        box.addView(devRow("Remote enabled", String.valueOf(com.myplan.app.api.ApiConfig.isRemoteEnabled(this))));
        box.addView(devRow("Last request", emptyDash(com.myplan.app.api.ApiConfig.getLastRequest(this))));
        long ls = com.myplan.app.api.ApiConfig.getLastSuccessMs(this);
        box.addView(devRow("Last success", ls > 0 ? String.valueOf(ls) : "—"));
        box.addView(devRow("Last error", emptyDash(com.myplan.app.api.ApiConfig.getLastError(this))));
        box.addView(devRow("Auth (remote)", "NOT_CONFIGURED"));
        box.addView(devRow("Server Entitlement", "NOT_CONNECTED"));
        box.addView(devRow("Remote Config", "NOT_CONNECTED · local flags active"));
        box.addView(space(dp(8)));

        TextView testConn = chip("Test API Connection", true);
        testConn.setOnClickListener(v -> {
            com.myplan.app.api.ApiResult<String> r = com.myplan.app.api.ApiLayer.client(this).health();
            String msg = r.kind + " · " + r.message;
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            AppInfrastructure.log(this, "API", msg);
            showTab(7);
        });
        box.addView(testConn);
        box.addView(space(dp(6)));

        TextView selfTest = chip("API Layer Self-Test (offline)", false);
        selfTest.setOnClickListener(v -> {
            java.util.List<com.myplan.app.api.ApiLayerSelfTest.Case> cases =
                    com.myplan.app.api.ApiLayerSelfTest.run(this);
            int pass = 0, fail = 0;
            StringBuilder sb = new StringBuilder();
            for (com.myplan.app.api.ApiLayerSelfTest.Case c : cases) {
                if (c.pass) pass++; else fail++;
                sb.append(c.pass ? "✓ " : "✗ ").append(c.name).append(" · ").append(c.detail).append("\n");
            }
            sb.insert(0, "PASS=" + pass + " FAIL=" + fail + "\n\n");
            myDialog().setTitle("API Layer Self-Test")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
        box.addView(selfTest);
        box.addView(space(dp(8)));

        box.addView(sectionHeader("API Logs"));
        TextView clearLogs = chip("مسح سجلات API", false);
        clearLogs.setOnClickListener(v -> {
            com.myplan.app.api.ApiLog.clear(this);
            Toast.makeText(this, "تم", Toast.LENGTH_SHORT).show();
            showTab(7);
        });
        box.addView(clearLogs);
        int n = 0;
        for (String line : com.myplan.app.api.ApiLog.lines(this)) {
            box.addView(muted(line));
            if (++n >= 20) break;
        }
        if (n == 0) box.addView(muted("لا سجلات API"));
        box.addView(space(dp(8)));
        box.addView(muted("لا كلمات مرور/توكنات في السجلات. لا Cloud Sync في هذه المرحلة."));
    }

    private String emptyDash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }


    private void buildDevSecuritySection(LinearLayout box) {
        box.addView(muted("Backend Hardening + Security Foundation — Backend Not Connected"));
        box.addView(space(dp(8)));
        box.addView(devRow("TokenStore", "SecureTokenStore · Android Keystore AES/GCM"));
        box.addView(devRow("Legacy plaintext tokens", "wiped on init if present"));
        box.addView(devRow("API HTTPS (Production)", "required"));
        box.addView(devRow("Retry", "finite · exponential · no 401/403/404/422"));
        box.addView(devRow("Request IDs", "X-Request-Id · logged truncated"));
        box.addView(devRow("Update Control", com.myplan.app.security.UpdateControlContract.current().status));
        box.addView(devRow("Remote Config", "NOT_CONFIGURED"));
        box.addView(devRow("Server Premium", "NOT_CONFIGURED"));
        box.addView(space(dp(8)));
        TextView run = chip("Security Self-Test", true);
        run.setOnClickListener(v -> {
            String backupSample = "{}";
            try { backupSample = planner.exportJson(); } catch (Exception ignored) {}
            java.util.List<com.myplan.app.security.SecuritySelfTest.Case> cases =
                    com.myplan.app.security.SecuritySelfTest.run(this, backupSample);
            int pass = 0, fail = 0, other = 0;
            StringBuilder sb = new StringBuilder();
            for (com.myplan.app.security.SecuritySelfTest.Case c : cases) {
                if ("PASS".equals(c.status)) pass++;
                else if ("FAIL".equals(c.status)) fail++;
                else other++;
                sb.append(c.status).append(" · ").append(c.name).append(" · ").append(c.detail).append("\n");
            }
            sb.insert(0, "PASS=" + pass + " FAIL=" + fail + " OTHER=" + other + "\n\n");
            myDialog().setTitle("Security Self-Test")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
        box.addView(run);
        box.addView(space(dp(8)));
        box.addView(muted("لا tokens في Backup. لا Upload. لا Fake Backend. Developer Test ≠ Purchase."));
    }

    private void buildDevSystemStatus(LinearLayout box) {
        String ver = "?";
        int code = 0;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            code = pi.versionCode;
        } catch (Exception ignored) {}
        box.addView(devRow("App", "My Plan"));
        box.addView(devRow("Version", ver + " (" + code + ")"));
        box.addView(devRow("applicationId", getPackageName()));
        box.addView(devRow("Debuggable", "false (release)"));
        box.addView(devRow("Signing", "release keystore · stable"));
        box.addView(devRow("Premium status", AppInfrastructure.isPremiumActive(this) ? "ACTIVE" : "OFF"));
        box.addView(devRow("Premium user visible", String.valueOf(AppInfrastructure.isPremiumUserVisible(this))));
        AccountAuth.Account sysAcc = AccountAuth.getCurrentAccount(this);
        box.addView(devRow("Account", AccountAuth.isLoggedIn(this) ? "LOGGED_IN" : "LOGGED_OUT"));
        box.addView(devRow("Display Name",
                sysAcc != null && sysAcc.displayName != null && !sysAcc.displayName.trim().isEmpty()
                        ? sysAcc.displayName.trim() : "—"));
        box.addView(devRow("Email",
                sysAcc != null && sysAcc.email != null && !sysAcc.email.isEmpty() ? sysAcc.email : "—"));
        String sysUid = "—";
        if (sysAcc != null && sysAcc.userId != null && !sysAcc.userId.isEmpty()) sysUid = sysAcc.userId;
        else if (!AppInfrastructure.getUserId(this).isEmpty()) sysUid = AppInfrastructure.getUserId(this);
        box.addView(devRow("User ID", sysUid));
        box.addView(devRow("Installation ID", AppInfrastructure.getInstallationId(this)));
        box.addView(devRow("Backend", com.myplan.app.supabase.SupabaseConfig.isConfigured(this) ? "Supabase configured" : "None"));
        box.addView(devRow("Ads SDK", "Not Installed"));
    }

    private TextView devRow(String k, String v) {
        TextView t = new TextView(this);
        t.setText(k + ":  " + v);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private void showDevPlannerSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("wake=").append(Planner.minToTime(planner.settings.wakeMin));
        sb.append(" sleep=").append(Planner.minToTime(planner.settings.sleepMin)).append("\n");
        sb.append("sessionMin=").append(planner.settings.sessionMin);
        sb.append(" breakMin=").append(planner.settings.breakMin).append("\n");
        sb.append("restDays=").append(planner.settings.restDays.size()).append("\n");
        int nld = planner.settings.noLectureDays != null ? planner.settings.noLectureDays.size() : 0;
        sb.append("noLectureDays=").append(nld).append("\n");
        int open = 0;
        for (Planner.Task t : planner.tasks) if (!t.done) open++;
        sb.append("tasks open=").append(open).append(" / ").append(planner.tasks.size()).append("\n");
        sb.append("sessions=").append(planner.sessions.size()).append("\n");
        sb.append("exams=").append(planner.exams.size()).append("\n");
        sb.append("lectureDistMode=").append(planner.settings.lectureDistMode).append("\n");
        AppInfrastructure.log(this, "Planner", "Snapshot shown");
        myDialog().setTitle("Planner constraints")
                .setMessage(sb.toString())
                .setPositiveButton("حسنًا", null).show();
    }

}