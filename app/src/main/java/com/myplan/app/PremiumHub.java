package com.myplan.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Premium features المحليّة — بيانات حقيقية فقط، Offline، بدون Backend.
 */
public final class PremiumHub {
    private static final String PREFS = "myplan_premium_v1";
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private PremiumHub() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Goals ──
    public static class Goal {
        public String id;
        public String title;
        public String type; // tasks | hours | subject | exam | backlog
        public String subject;
        public int targetValue;
        public long createdMs;
        public boolean done;

        public Goal() {
            id = UUID.randomUUID().toString();
            createdMs = System.currentTimeMillis();
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title == null ? "" : title);
            o.put("type", type == null ? "tasks" : type);
            o.put("subject", subject == null ? "" : subject);
            o.put("targetValue", targetValue);
            o.put("createdMs", createdMs);
            o.put("done", done);
            return o;
        }

        static Goal fromJson(JSONObject o) {
            Goal g = new Goal();
            g.id = o.optString("id", UUID.randomUUID().toString());
            g.title = o.optString("title", "");
            g.type = o.optString("type", "tasks");
            g.subject = o.optString("subject", "");
            g.targetValue = o.optInt("targetValue", 0);
            g.createdMs = o.optLong("createdMs", System.currentTimeMillis());
            g.done = o.optBoolean("done", false);
            return g;
        }
    }

    public static List<Goal> loadGoals(Context c) {
        List<Goal> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("goals", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Goal.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveGoals(Context c, List<Goal> list) {
        try {
            JSONArray a = new JSONArray();
            for (Goal g : list) a.put(g.toJson());
            sp(c).edit().putString("goals", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static int goalProgress(Planner p, Goal g) {
        if (p == null || g == null) return 0;
        if ("hours".equals(g.type)) {
            int min = 0;
            for (Planner.Session s : p.sessions) if (s.done) min += Math.max(0, s.durationMin);
            return min / 60;
        }
        if ("backlog".equals(g.type)) {
            int done = 0;
            for (Planner.Task t : p.tasks) if (t.backlog && t.done) done++;
            return done;
        }
        if ("subject".equals(g.type) && g.subject != null && g.subject.length() > 0) {
            int min = 0;
            for (Planner.Session s : p.sessions)
                if (s.done && g.subject.equals(s.subject)) min += Math.max(0, s.durationMin);
            return min / 60;
        }
        if ("exam".equals(g.type) && g.subject != null) {
            int open = 0, done = 0;
            for (Planner.Task t : p.tasks) {
                if (!g.subject.equals(t.subject)) continue;
                if (t.done) done++; else open++;
            }
            return done;
        }
        // tasks completed
        int n = 0;
        for (Planner.Task t : p.tasks) if (t.done) n++;
        return n;
    }

    // ── Journal ──
    public static class JournalEntry {
        public String id;
        public String day;
        public String text;
        public String subject;
        public int rating; // 1..5
        public long createdMs;

        public JournalEntry() {
            id = UUID.randomUUID().toString();
            createdMs = System.currentTimeMillis();
            day = DAY.format(Calendar.getInstance().getTime());
            rating = 3;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("day", day == null ? "" : day);
            o.put("text", text == null ? "" : text);
            o.put("subject", subject == null ? "" : subject);
            o.put("rating", rating);
            o.put("createdMs", createdMs);
            return o;
        }

        static JournalEntry fromJson(JSONObject o) {
            JournalEntry e = new JournalEntry();
            e.id = o.optString("id", UUID.randomUUID().toString());
            e.day = o.optString("day", "");
            e.text = o.optString("text", "");
            e.subject = o.optString("subject", "");
            e.rating = o.optInt("rating", 3);
            e.createdMs = o.optLong("createdMs", System.currentTimeMillis());
            return e;
        }
    }

    public static List<JournalEntry> loadJournal(Context c) {
        List<JournalEntry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("journal", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(JournalEntry.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> Long.compare(b.createdMs, a.createdMs));
        return out;
    }

    public static void saveJournal(Context c, List<JournalEntry> list) {
        try {
            JSONArray a = new JSONArray();
            for (JournalEntry e : list) a.put(e.toJson());
            sp(c).edit().putString("journal", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ── Customization ──
    public static boolean compactCards(Context c) {
        return sp(c).getBoolean("ui_compact_cards", false);
    }

    public static void setCompactCards(Context c, boolean v) {
        sp(c).edit().putBoolean("ui_compact_cards", v).apply();
    }

    public static boolean showSubjectColors(Context c) {
        return sp(c).getBoolean("ui_subject_colors", true);
    }

    public static void setShowSubjectColors(Context c, boolean v) {
        sp(c).edit().putBoolean("ui_subject_colors", v).apply();
    }

    /** دمج بيانات Premium في Backup root (additive) */
    public static void putIntoBackup(Context c, JSONObject root) throws Exception {
        JSONArray ga = new JSONArray();
        for (Goal g : loadGoals(c)) ga.put(g.toJson());
        root.put("premiumGoals", ga);
        JSONArray ja = new JSONArray();
        for (JournalEntry e : loadJournal(c)) ja.put(e.toJson());
        root.put("premiumJournal", ja);
        JSONObject ui = new JSONObject();
        ui.put("compactCards", compactCards(c));
        ui.put("subjectColors", showSubjectColors(c));
        root.put("premiumUi", ui);
    }

    public static void loadFromBackup(Context c, JSONObject root) {
        if (root == null) return;
        try {
            if (root.has("premiumGoals")) {
                List<Goal> list = new ArrayList<>();
                JSONArray a = root.getJSONArray("premiumGoals");
                for (int i = 0; i < a.length(); i++) list.add(Goal.fromJson(a.getJSONObject(i)));
                saveGoals(c, list);
            }
            if (root.has("premiumJournal")) {
                List<JournalEntry> list = new ArrayList<>();
                JSONArray a = root.getJSONArray("premiumJournal");
                for (int i = 0; i < a.length(); i++) list.add(JournalEntry.fromJson(a.getJSONObject(i)));
                saveJournal(c, list);
            }
            if (root.has("premiumUi")) {
                JSONObject ui = root.getJSONObject("premiumUi");
                setCompactCards(c, ui.optBoolean("compactCards", false));
                setShowSubjectColors(c, ui.optBoolean("subjectColors", true));
            }
        } catch (Exception ignored) {}
    }

    // ── Smart Review ──
    public static List<String> reviewSuggestions(Planner p) {
        List<String> out = new ArrayList<>();
        if (p == null) {
            out.add("لا توجد بيانات كافية للتوصية.");
            return out;
        }
        String today = Planner.todayStr();
        Map<String, Long> lastStudied = new HashMap<>();
        Map<String, Integer> doneMin = new HashMap<>();
        Map<String, Integer> missCount = new HashMap<>();
        for (Planner.Session s : p.sessions) {
            if (s.subject == null || s.subject.isEmpty()) continue;
            if (s.done) {
                doneMin.put(s.subject, doneMin.getOrDefault(s.subject, 0) + Math.max(0, s.durationMin));
                try {
                    long ms = DAY.parse(s.day).getTime();
                    Long prev = lastStudied.get(s.subject);
                    if (prev == null || ms > prev) lastStudied.put(s.subject, ms);
                } catch (Exception ignored) {}
            } else if (s.missed || (s.day != null && s.day.compareTo(today) < 0)) {
                missCount.put(s.subject, missCount.getOrDefault(s.subject, 0) + 1);
            }
        }
        long now = System.currentTimeMillis();
        Set<String> subjects = new HashSet<>();
        for (Planner.Task t : p.tasks) if (t.subject != null) subjects.add(t.subject);
        for (Planner.Exam e : p.exams) if (e.subject != null) subjects.add(e.subject);

        for (Planner.Exam e : p.exams) {
            if (e.done || e.subject == null || e.day == null) continue;
            try {
                long examMs = DAY.parse(e.day).getTime();
                long days = (examMs - now) / (24L * 60 * 60 * 1000);
                if (days >= 0 && days <= 14) {
                    out.add("امتحان " + e.subject + " خلال " + days + " يوم — يحتاج مراجعة مركّزة.");
                }
            } catch (Exception ignored) {}
        }
        for (String sub : subjects) {
            Long last = lastStudied.get(sub);
            if (last == null) {
                boolean hasOpen = false;
                for (Planner.Task t : p.tasks)
                    if (sub.equals(t.subject) && !t.done && t.remainingMin > 0) hasOpen = true;
                if (hasOpen) out.add(sub + ": لم تُسجَّل جلسات مكتملة بعد — ابدأ بمراجعة خفيفة.");
            } else {
                long days = (now - last) / (24L * 60 * 60 * 1000);
                if (days >= 5) out.add(sub + ": آخر دراسة منذ " + days + " يوم — يُفضّل مراجعة.");
            }
            int miss = missCount.getOrDefault(sub, 0);
            if (miss >= 2) out.add(sub + ": " + miss + " جلسات فائتة — أعد جدولة المراجعة.");
        }
        int backlogOpen = 0;
        for (Planner.Task t : p.tasks) if (t.backlog && !t.done && t.remainingMin > 0) backlogOpen++;
        if (backlogOpen > 0) out.add("يوجد " + backlogOpen + " مهام backlog مفتوحة تحتاج توزيع مراجعة.");

        if (out.isEmpty()) out.add("لا توجد توصيات مراجعة الآن — البيانات غير كافية أو الجدول منتظم.");
        if (out.size() > 12) out = out.subList(0, 12);
        return out;
    }

    // ── Forecast ──
    public static List<String> studyForecast(Planner p) {
        List<String> out = new ArrayList<>();
        if (p == null) {
            out.add("لا توجد بيانات.");
            return out;
        }
        int remainingMin = 0;
        int backlogMin = 0;
        for (Planner.Task t : p.tasks) {
            if (t.done) continue;
            int r = Math.max(0, t.remainingMin > 0 ? t.remainingMin : t.durationMin);
            remainingMin += r;
            if (t.backlog) backlogMin += r;
        }
        // معدل الإنجاز: متوسط دقائق مكتملة لآخر 7 أيام
        String today = Planner.todayStr();
        Calendar cal = Calendar.getInstance();
        Map<String, Integer> byDay = new HashMap<>();
        for (int i = 0; i < 14; i++) {
            byDay.put(DAY.format(cal.getTime()), 0);
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        int completedMin14 = 0;
        int daysWithStudy = 0;
        for (Planner.Session s : p.sessions) {
            if (!s.done || s.day == null) continue;
            if (byDay.containsKey(s.day)) {
                byDay.put(s.day, byDay.get(s.day) + Math.max(0, s.durationMin));
            }
            completedMin14 += Math.max(0, s.durationMin);
        }
        for (int v : byDay.values()) if (v > 0) daysWithStudy++;
        double avgPerActiveDay = daysWithStudy > 0 ? (completedMin14 / (double) Math.max(1, daysWithStudy)) : 0;
        // تقريب معدل يومي على 7 أيام
        cal = Calendar.getInstance();
        int last7 = 0;
        for (int i = 0; i < 7; i++) {
            String d = DAY.format(cal.getTime());
            last7 += byDay.getOrDefault(d, 0);
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        double dailyRate = last7 / 7.0;

        out.add("المتبقي من المهام: " + (remainingMin / 60) + " ساعة و " + (remainingMin % 60) + " د.");
        out.add("من ذلك backlog: " + (backlogMin / 60) + "س " + (backlogMin % 60) + "د.");
        out.add(String.format(Locale.US, "معدل آخر 7 أيام: %.0f دقيقة/يوم.", dailyRate));
        if (dailyRate < 1) {
            out.add("لا يمكن تقدير موعد إنهاء الـbacklog — معدل الإنجاز منخفض أو لا توجد جلسات مكتملة.");
        } else {
            int daysToClear = (int) Math.ceil(backlogMin / dailyRate);
            out.add("بنفس المعدل: إنهاء الـbacklog تقريبًا خلال " + daysToClear + " يوم.");
            int daysAll = (int) Math.ceil(remainingMin / dailyRate);
            out.add("إنهاء كل المتبقي تقريبًا خلال " + daysAll + " يوم.");
            out.add("لو زدت ساعة يوميًا: إنهاء الـbacklog خلال ≈ "
                    + (int) Math.ceil(backlogMin / (dailyRate + 60)) + " يوم.");
            out.add("لو قلّلت لـ30 د/يوم: إنهاء الـbacklog خلال ≈ "
                    + (int) Math.ceil(backlogMin / 30.0) + " يوم.");
        }
        out.add("ملاحظة: تقدير حسابي محلي من جلساتك المكتملة — ليس تنبؤًا ذكيًا خارجيًا.");
        return out;
    }

    // ── What-If (read-only simulation) ──
    public static List<String> whatIf(Planner p, String scenario, int param) {
        List<String> out = new ArrayList<>();
        if (p == null) {
            out.add("لا توجد خطة.");
            return out;
        }
        int remaining = 0;
        for (Planner.Task t : p.tasks) if (!t.done) remaining += Math.max(0, t.remainingMin);
        int sessionMin = Math.max(10, p.settings.sessionMin);
        int wake = p.settings.wakeMin + Planner.normalizeMorningPrep(p.settings.morningPrepMin);
        int sleep = p.settings.sleepMin;
        int window = sleep > wake ? (sleep - wake) : (24 * 60 - wake + sleep);
        int breakMin = Math.max(0, p.settings.breakMin);
        int slotsNow = window > 0 ? Math.max(1, window / (sessionMin + breakMin)) : 0;

        out.add("الوضع الحالي: متبقي " + remaining + " د · نافذة يوم ≈ " + window + " د · جلسات تقريبية/يوم " + slotsNow);

        if ("more_study".equals(scenario)) {
            int extra = param > 0 ? param : 60;
            int newWindow = window + extra;
            int slots = Math.max(1, newWindow / (sessionMin + breakMin));
            out.add("لو زدت " + extra + " د يوميًا: جلسات/يوم ≈ " + slots);
            out.add("الوقت اللازم لإنهاء المتبقي ≈ " + (remaining / Math.max(1, slots * sessionMin)) + " يوم.");
        } else if ("shorter_session".equals(scenario)) {
            int sm = param > 0 ? param : 30;
            int slots = Math.max(1, window / (sm + breakMin));
            out.add("لو مدة الجلسة " + sm + " د: عدد الجلسات/يوم ≈ " + slots);
            out.add("مرونة أعلى لكن تقطيع أكثر للمادة الواحدة.");
        } else if ("rest_day".equals(scenario)) {
            out.add("إضافة يوم راحة يقلل أيام العمل الأسبوعية.");
            int workDays = 7 - (p.settings.restDays != null ? p.settings.restDays.size() : 0) - 1;
            if (workDays < 1) workDays = 1;
            out.add("أيام عمل تقريبية بعد الراحة الإضافية: " + workDays + "/أسبوع.");
            out.add("قد يتأخر إنهاء المتبقي — لا يُطبَّق على خطتك إلا عند التأكيد.");
        } else if ("priority_boost".equals(scenario)) {
            out.add("رفع أولوية مادة يعيد ترتيب الجلسات عند إعادة التخطيط.");
            out.add("لا يغيّر إجمالي الوقت المطلوب — يغيّر من يحصل على السعة أولًا.");
        } else {
            out.add("اختر سيناريو: زيادة وقت · تقصير جلسة · يوم راحة · أولوية مادة.");
        }
        out.add("هذه تجربة فقط — الخطة الحقيقية لم تتغير.");
        return out;
    }

    // ── Advanced analytics lines ──
    public static List<String> advancedAnalytics(Planner p) {
        List<String> out = new ArrayList<>();
        if (p == null) return out;
        int planned = 0, completed = 0, missed = 0;
        Map<String, int[]> sub = new HashMap<>(); // [doneMin, plannedMin]
        Calendar cal = Calendar.getInstance();
        String[] last7 = new String[7];
        int[] done7 = new int[7];
        for (int i = 0; i < 7; i++) {
            last7[i] = DAY.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        for (Planner.Session s : p.sessions) {
            planned += Math.max(0, s.durationMin);
            String subj = s.subject != null ? s.subject : "?";
            int[] a = sub.getOrDefault(subj, new int[]{0, 0});
            a[1] += Math.max(0, s.durationMin);
            if (s.done) {
                completed += Math.max(0, s.durationMin);
                a[0] += Math.max(0, s.durationMin);
                for (int i = 0; i < 7; i++) if (last7[i].equals(s.day)) done7[i] += Math.max(0, s.durationMin);
            } else if (s.missed) missed++;
            sub.put(subj, a);
        }
        out.add("مخطط vs منجز: " + (planned / 60) + "س مخطط · " + (completed / 60) + "س منجز.");
        out.add("جلسات فائتة (معلّمة): " + missed);
        StringBuilder trend = new StringBuilder("اتجاه 7 أيام (د): ");
        for (int i = 6; i >= 0; i--) trend.append(done7[i]).append(i == 0 ? "" : " ← ");
        out.add(trend.toString());
        int backlogOpen = 0, backlogDone = 0;
        for (Planner.Task t : p.tasks) {
            if (!t.backlog) continue;
            if (t.done) backlogDone++; else backlogOpen++;
        }
        out.add("Backlog: مفتوح " + backlogOpen + " · مكتمل " + backlogDone);
        double adherence = planned > 0 ? (100.0 * completed / planned) : 0;
        out.add(String.format(Locale.US, "الالتزام بالخطة (منجز/مخطط): %.0f%%", adherence));
        List<Map.Entry<String, int[]>> entries = new ArrayList<>(sub.entrySet());
        Collections.sort(entries, (a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        int n = 0;
        for (Map.Entry<String, int[]> e : entries) {
            out.add("مادة " + e.getKey() + ": " + (e.getValue()[0] / 60) + "س منجز / " + (e.getValue()[1] / 60) + "س مخطط");
            if (++n >= 8) break;
        }
        for (Planner.Exam e : p.exams) {
            if (e.done) continue;
            int rem = 0;
            for (Planner.Task t : p.tasks)
                if (e.subject != null && e.subject.equals(t.subject) && !t.done)
                    rem += Math.max(0, t.remainingMin);
            out.add("تحضير " + e.subject + " (" + e.day + "): متبقي " + (rem / 60) + "س");
        }
        return out;
    }

    // ── Personal records ──
    public static List<String> personalRecords(Planner p) {
        List<String> out = new ArrayList<>();
        if (p == null) {
            out.add("لا سجلات بعد.");
            return out;
        }
        int longest = 0;
        Map<String, Integer> dayMin = new HashMap<>();
        Map<String, Integer> dayTasks = new HashMap<>();
        Set<String> daysDone = new HashSet<>();
        for (Planner.Session s : p.sessions) {
            if (!s.done) continue;
            longest = Math.max(longest, s.durationMin);
            if (s.day != null) {
                dayMin.put(s.day, dayMin.getOrDefault(s.day, 0) + Math.max(0, s.durationMin));
                daysDone.add(s.day);
            }
        }
        for (Planner.Task t : p.tasks) {
            if (!t.done) continue;
            // approx completion day unknown — skip
        }
        int bestDayMin = 0;
        String bestDay = "—";
        for (Map.Entry<String, Integer> e : dayMin.entrySet()) {
            if (e.getValue() > bestDayMin) {
                bestDayMin = e.getValue();
                bestDay = e.getKey();
            }
        }
        out.add("أطول جلسة مكتملة: " + longest + " دقيقة.");
        out.add("أكبر وقت دراسة في يوم: " + bestDayMin + " د (" + bestDay + ").");
        int doneTasks = 0;
        for (Planner.Task t : p.tasks) if (t.done) doneTasks++;
        out.add("إجمالي مهام مكتملة: " + doneTasks);
        out.add("أيام فيها دراسة مسجّلة: " + daysDone.size());
        // streak: consecutive days ending today with study
        int streak = 0;
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 60; i++) {
            String d = DAY.format(cal.getTime());
            if (dayMin.getOrDefault(d, 0) > 0) streak++;
            else break;
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        out.add("سلسلة التزام حالية: " + streak + " يوم.");
        return out;
    }

    // ── Reports ──
    public static String buildReport(Planner p, String range) {
        StringBuilder sb = new StringBuilder();
        sb.append("تقرير My Plan (").append(range).append(")\n");
        sb.append("========================\n");
        if (p == null) {
            sb.append("لا بيانات.\n");
            return sb.toString();
        }
        Calendar cal = Calendar.getInstance();
        Set<String> window = new HashSet<>();
        int days = "week".equals(range) ? 7 : ("month".equals(range) ? 30 : 1);
        for (int i = 0; i < days; i++) {
            window.add(DAY.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        int doneMin = 0, plannedMin = 0, sessionsDone = 0;
        for (Planner.Session s : p.sessions) {
            if (s.day == null || !window.contains(s.day)) continue;
            plannedMin += Math.max(0, s.durationMin);
            if (s.done) {
                doneMin += Math.max(0, s.durationMin);
                sessionsDone++;
            }
        }
        sb.append("جلسات مكتملة: ").append(sessionsDone).append("\n");
        sb.append("وقت منجز: ").append(doneMin).append(" د\n");
        sb.append("وقت مخطط في الفترة: ").append(plannedMin).append(" د\n");
        Map<String, int[]> prog = p.progressBySubject();
        sb.append("--- المواد ---\n");
        for (Map.Entry<String, int[]> e : prog.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()[0]).append("/")
                    .append(e.getValue()[1]).append(" د\n");
        }
        int bl = 0;
        for (Planner.Task t : p.tasks) if (t.backlog && !t.done) bl += Math.max(0, t.remainingMin);
        sb.append("Backlog متبقي: ").append(bl).append(" د\n");
        return sb.toString();
    }

    public static boolean canAccessPremium(Context c) {
        return AppInfrastructure.isPremiumActive(c);
    }
}
