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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Planner {
    /** ترتيب أيام My Plan: السبت أولًا (قيم Calendar.DAY_OF_WEEK) */
    public static final int[] WEEK_ORDER = {
            Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
    };

    private static final String PREFS = "myplan_v3";
    public static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // 0 منخفضة 🟢, 1 متوسطة 🟡, 2 عالية 🔴
    public static final String[] PRIORITY_LABELS = {"منخفضة 🟢", "متوسطة 🟡", "عالية 🔴"};
    public static final int[] PRIORITY_WEIGHT = {1, 2, 4};

    public static class Task {
        public String id;
        public String name;
        public String subject;
        public int durationMin;
        public int priority; // 0..2
        public String deadline;
        public boolean backlog; // true = قديمة 📚
        public int sessionCount; // عدد الجلسات المطلوبة
        public int sessionTargetMin; // متوسط مدة الجلسة (محسوب)
        public int remainingMin;
        public boolean done;
        public String dependsOn;
        public boolean pinStart;      // المستخدم حدّد وقت بداية
        public String pinDay;         // yyyy-MM-dd
        public int pinStartMin;       // دقائق من منتصف الليل
        public int sortOrder;
        /** 0 = بدون يوم مفضل، 1..7 = Calendar.DAY_OF_WEEK */
        public int preferredDow;
        /** 0 = محاضرة (Lecture) ، 1 = مذاكرة (Study) */
        public int kind;
        /** ربط اختياري بامتحان (Study فقط) — Exam.id */
        public String linkedExamId;

        public static final int KIND_LECTURE = 0;
        public static final int KIND_STUDY = 1;

        public boolean isLecture() { return kind != KIND_STUDY; }
        public boolean isStudy() { return kind == KIND_STUDY; }

        public Task() {
            id = UUID.randomUUID().toString();
            name = "";
            subject = "عام";
            durationMin = 60;
            priority = 1;
            deadline = "";
            backlog = false;
            sessionCount = 1;
            sessionTargetMin = 60;
            remainingMin = 60;
            done = false;
            dependsOn = "";
            pinStart = false;
            pinDay = "";
            pinStartMin = 16 * 60 + 30;
            sortOrder = 0;
            preferredDow = 0;
            kind = KIND_LECTURE;
            linkedExamId = "";
        }

        /** توزيع الدقائق على N جلسة بشكل متساوٍ مع باقي منطقي */
        public static int[] splitDurations(int totalMin, int count) {
            if (count < 1) count = 1;
            if (totalMin < count) totalMin = count;
            int[] parts = new int[count];
            int base = totalMin / count;
            int rem = totalMin % count;
            for (int i = 0; i < count; i++) {
                parts[i] = base + (i < rem ? 1 : 0);
            }
            return parts;
        }

        public void applySessionCount(int count) {
            if (count < 1) count = 1;
            sessionCount = count;
            int[] parts = splitDurations(Math.max(durationMin, count), count);
            int sum = 0;
            for (int p : parts) sum += p;
            durationMin = sum;
            sessionTargetMin = parts[0];
            if (!done) remainingMin = Math.max(remainingMin, durationMin);
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("subject", subject);
            o.put("durationMin", durationMin);
            o.put("priority", priority);
            o.put("deadline", deadline == null ? "" : deadline);
            o.put("backlog", backlog);
            o.put("sessionCount", sessionCount);
            o.put("sessionTargetMin", sessionTargetMin);
            o.put("remainingMin", remainingMin);
            o.put("done", done);
            o.put("dependsOn", dependsOn == null ? "" : dependsOn);
            o.put("pinStart", pinStart);
            o.put("pinDay", pinDay == null ? "" : pinDay);
            o.put("pinStartMin", pinStartMin);
            o.put("sortOrder", sortOrder);
            o.put("preferredDow", preferredDow);
            o.put("kind", kind);
            o.put("linkedExamId", linkedExamId == null ? "" : linkedExamId);
            return o;
        }

        static Task fromJson(JSONObject o) throws Exception {
            Task t = new Task();
            t.id = o.optString("id", UUID.randomUUID().toString());
            t.name = o.optString("name", "");
            t.subject = o.optString("subject", "عام");
            t.durationMin = o.optInt("durationMin", 60);
            // migration: old 0..3 -> clamp to 0..2 (3 عاجلة -> 2 عالية)
            int p = o.optInt("priority", 1);
            if (p > 2) p = 2;
            if (p < 0) p = 0;
            t.priority = p;
            t.deadline = o.optString("deadline", "");
            t.backlog = o.optBoolean("backlog", false);
            t.sessionCount = o.optInt("sessionCount", 0);
            t.sessionTargetMin = o.optInt("sessionTargetMin", 45);
            if (t.sessionCount <= 0) {
                // migrate from sessionTargetMin
                if (t.sessionTargetMin > 0 && t.durationMin > 0) {
                    t.sessionCount = Math.max(1, (int) Math.ceil(t.durationMin / (double) t.sessionTargetMin));
                } else {
                    t.sessionCount = 1;
                }
            }
            t.remainingMin = o.optInt("remainingMin", t.durationMin);
            t.done = o.optBoolean("done", false);
            t.dependsOn = o.optString("dependsOn", "");
            t.pinStart = o.optBoolean("pinStart", false);
            t.pinDay = o.optString("pinDay", "");
            t.pinStartMin = o.optInt("pinStartMin", 16 * 60 + 30);
            t.sortOrder = o.optInt("sortOrder", 0);
            t.preferredDow = o.optInt("preferredDow", 0);
            if (t.preferredDow < 0 || t.preferredDow > 7) t.preferredDow = 0;
            // lectureOrder قُدم — يُتجاهل إن وُجد في JSON قديم
            t.kind = o.optInt("kind", 0);
            if (t.kind != 1) t.kind = 0;
            t.linkedExamId = o.optString("linkedExamId", "");

            return t;
        }
    }

    public static class Commitment {
        public String id;
        public String title;
        /** يوم واحد للتوافق القديم: 0 = كل يوم، أو Calendar.DAY_OF_WEEK */
        public int dayOfWeek;
        /** أيام متعددة (اختياري). لو فارغ يُستخدم dayOfWeek فقط */
        public java.util.LinkedHashSet<Integer> daysOfWeek = new java.util.LinkedHashSet<>();
        public int startMin;
        public int endMin;

        public Commitment() {
            id = UUID.randomUUID().toString();
            title = "";
            dayOfWeek = 0;
            startMin = 8 * 60;
            endMin = 14 * 60;
        }

        /** هل الالتزام ينطبق على يوم الأسبوع المعطى؟ */
        public boolean matchesDay(int dow) {
            if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
                return daysOfWeek.contains(dow);
            }
            return dayOfWeek == 0 || dayOfWeek == dow;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("dayOfWeek", dayOfWeek);
            o.put("startMin", startMin);
            o.put("endMin", endMin);
            if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (Integer d : daysOfWeek) arr.put(d);
                o.put("daysOfWeek", arr);
            }
            return o;
        }

        static Commitment fromJson(JSONObject o) throws Exception {
            Commitment c = new Commitment();
            c.id = o.optString("id", UUID.randomUUID().toString());
            c.title = o.optString("title", "");
            c.dayOfWeek = o.optInt("dayOfWeek", 0);
            c.startMin = readCommitmentMinField(o, "startMin", 8 * 60);
            c.endMin = readCommitmentMinField(o, "endMin", 14 * 60);
            sanitizeCommitmentTimes(c);
            c.daysOfWeek = new java.util.LinkedHashSet<>();
            JSONArray arr = o.optJSONArray("daysOfWeek");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    int d = arr.optInt(i, 0);
                    if (d >= Calendar.SUNDAY && d <= Calendar.SATURDAY) c.daysOfWeek.add(d);
                }
            }
            return c;
        }
    }

    /**
     * قراءة دقيقة التزام من JSON: رقم، أو نص "HH:mm"، مع افتراضي آمن.
     * لا يستبدل أبدًا وقت البداية بوقت الاستيقاظ.
     */
    private static int readCommitmentMinField(JSONObject o, String key, int def) {
        if (o == null || key == null || !o.has(key) || o.isNull(key)) return def;
        Object v = o.opt(key);
        if (v instanceof Number) {
            int n = ((Number) v).intValue();
            if (n < 0) return def;
            return n;
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return def;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
            int parsed = parseTimeToMin(s);
            if (parsed >= 0) return parsed;
        }
        return def;
    }

    /**
     * تطبيع أوقات الالتزام:
     * - قيم قديمة بكِلا الحقلين في 0..23 تُفسَّر كساعات وتُحوَّل لدقائق.
     * - الناتج دائمًا دقائق من منتصف الليل في [0, 24*60).
     * لا يُضبط start على wake.
     */
    private static void sanitizeCommitmentTimes(Commitment c) {
        if (c == null) return;
        int s = c.startMin;
        int e = c.endMin;
        if (s >= 0 && e >= 0 && s <= 23 && e <= 23) {
            s *= 60;
            e *= 60;
        }
        c.startMin = ((s % (24 * 60)) + (24 * 60)) % (24 * 60);
        c.endMin = ((e % (24 * 60)) + (24 * 60)) % (24 * 60);
    }

    public static class Session {
        public String id;
        public String taskId;
        public String day;
        public int startMin;
        public int endMin;
        public int durationMin;
        public boolean done;
        public String taskName;
        public String subject;
        public int sessionIndex;
        public int sessionTotal;
        public int priority;
        public boolean backlog;
        public boolean userPinned;
        public boolean missed;
        /** دقائق نُفِّذت فعليًا من هذه الجلسة (Partial Recovery) */
        public int executedMin;

        public Session() {
            id = UUID.randomUUID().toString();
            done = false;
            userPinned = false;
            missed = false;
            executedMin = 0;
        }

        /** المدة المتبقية لهذه الجلسة (لا تقل عن 1 إن لم تكتمل) */
        public int remainingDurationMin() {
            int rem = Math.max(0, durationMin) - Math.max(0, executedMin);
            if (done) return 0;
            return rem;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("taskId", taskId);
            o.put("day", day);
            o.put("startMin", startMin);
            o.put("endMin", endMin);
            o.put("durationMin", durationMin);
            o.put("done", done);
            o.put("taskName", taskName);
            o.put("subject", subject);
            o.put("sessionIndex", sessionIndex);
            o.put("sessionTotal", sessionTotal);
            o.put("priority", priority);
            o.put("backlog", backlog);
            o.put("userPinned", userPinned);
            o.put("missed", missed);
            o.put("executedMin", Math.max(0, executedMin));
            return o;
        }

        static Session fromJson(JSONObject o) throws Exception {
            Session s = new Session();
            s.id = o.optString("id", UUID.randomUUID().toString());
            s.taskId = o.optString("taskId", "");
            s.day = o.optString("day", "");
            s.startMin = o.optInt("startMin", 0);
            s.endMin = o.optInt("endMin", 0);
            s.durationMin = o.optInt("durationMin", 0);
            s.done = o.optBoolean("done", false);
            s.taskName = o.optString("taskName", "");
            s.subject = o.optString("subject", "");
            s.sessionIndex = o.optInt("sessionIndex", 1);
            s.sessionTotal = o.optInt("sessionTotal", 1);
            s.priority = Math.max(0, Math.min(2, o.optInt("priority", 1)));
            s.backlog = o.optBoolean("backlog", false);
            s.userPinned = o.optBoolean("userPinned", false);
            s.missed = o.optBoolean("missed", false);
            s.executedMin = Math.max(0, o.optInt("executedMin", 0));
            if (s.done) s.executedMin = Math.max(s.executedMin, s.durationMin);
            return s;
        }

        public String timeLabel() {
            int a = ((startMin % (24 * 60)) + (24 * 60)) % (24 * 60);
            int b = ((endMin % (24 * 60)) + (24 * 60)) % (24 * 60);
            return String.format(Locale.US, "%02d:%02d → %02d:%02d",
                    a / 60, a % 60, b / 60, b % 60);
        }
    }

    public static class Subject {
        public String id;
        public String name;
        /** أيام تثبيت المحاضرة للمادة (Calendar.DAY_OF_WEEK)، فارغ = بدون تثبيت */
        public java.util.Set<Integer> fixedDows = new HashSet<>();
        public Subject() { id = UUID.randomUUID().toString(); name = ""; }
        public Subject(String n) { this(); name = n; }
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("name", name);
            JSONArray fd = new JSONArray();
            for (int d : fixedDows) fd.put(d);
            o.put("fixedDows", fd);
            return o;
        }
        static Subject fromJson(JSONObject o) throws Exception {
            Subject s = new Subject();
            s.id = o.optString("id", UUID.randomUUID().toString());
            s.name = o.optString("name", "");
            s.fixedDows = new HashSet<>();
            JSONArray fd = o.optJSONArray("fixedDows");
            if (fd != null) for (int i = 0; i < fd.length(); i++) s.fixedDows.add(fd.getInt(i));
            return s;
        }
    }

    public static class Focus {
        public String id;
        public String subject;
        public String reason;   // USER_REQUEST, UPCOMING_EXAM, BACKLOG, NEW_PREVENT, CORRECTION
        public int strength;    // 1..5
        public String scope;    // current, temporary, historical
        public long createdAt;
        public long expiresAt;  // 0 = none
        public float confidence;
        public String text;

        public Focus() {
            id = UUID.randomUUID().toString();
            subject = "";
            reason = "USER_REQUEST";
            strength = 3;
            scope = "temporary";
            createdAt = System.currentTimeMillis();
            expiresAt = createdAt + 14L * 24 * 60 * 60 * 1000;
            confidence = 0.7f;
            text = "";
        }

        public boolean isActive(long now) {
            if ("historical".equals(scope)) return false;
            if (expiresAt > 0 && now > expiresAt) return false;
            return "current".equals(scope) || "temporary".equals(scope);
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("subject", subject); o.put("reason", reason);
            o.put("strength", strength); o.put("scope", scope);
            o.put("createdAt", createdAt); o.put("expiresAt", expiresAt);
            o.put("confidence", confidence); o.put("text", text == null ? "" : text);
            return o;
        }

        static Focus fromJson(JSONObject o) throws Exception {
            Focus f = new Focus();
            f.id = o.optString("id", UUID.randomUUID().toString());
            f.subject = o.optString("subject", "");
            f.reason = o.optString("reason", "USER_REQUEST");
            f.strength = o.optInt("strength", 3);
            f.scope = o.optString("scope", "temporary");
            f.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            f.expiresAt = o.optLong("expiresAt", 0);
            f.confidence = (float) o.optDouble("confidence", 0.7);
            f.text = o.optString("text", "");
            return f;
        }
    }

    public static class Exam {
        public String id;
        public String subject;
        public String title;
        public String day;
        public String time;
        public String topics;
        public int importance; // 0..2
        public int prepLevel; // 0..100
        public int neededMin;
        public String notes;
        public boolean done;
        /** آخر محاضرة داخلة في نطاق الامتحان (Task.id) */
        public String lastLectureTaskId;

        public Exam() {
            id = UUID.randomUUID().toString();
            subject = "";
            title = "";
            day = "";
            time = "";
            topics = "";
            importance = 2;
            prepLevel = 40;
            neededMin = 180;
            notes = "";
            done = false;
            lastLectureTaskId = "";
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("subject", subject);
            o.put("title", title);
            o.put("day", day);
            o.put("time", time == null ? "" : time);
            o.put("topics", topics == null ? "" : topics);
            o.put("importance", importance);
            o.put("prepLevel", prepLevel);
            o.put("neededMin", neededMin);
            o.put("notes", notes == null ? "" : notes);
            o.put("done", done);
            o.put("lastLectureTaskId", lastLectureTaskId == null ? "" : lastLectureTaskId);
            return o;
        }

        static Exam fromJson(JSONObject o) throws Exception {
            Exam e = new Exam();
            e.id = o.optString("id", UUID.randomUUID().toString());
            e.subject = o.optString("subject", "");
            e.title = o.optString("title", "");
            e.day = o.optString("day", "");
            e.time = o.optString("time", "");
            e.topics = o.optString("topics", "");
            e.importance = Math.max(0, Math.min(2, o.optInt("importance", 2)));
            e.prepLevel = Math.max(0, Math.min(100, o.optInt("prepLevel", 40)));
            e.neededMin = o.optInt("neededMin", 180);
            e.notes = o.optString("notes", "");
            e.done = o.optBoolean("done", false);
            e.lastLectureTaskId = o.optString("lastLectureTaskId", "");
            return e;
        }
    }

    public static class Settings {
        public int wakeMin = 7 * 60;
        public int sleepMin = 23 * 60; // bedtime = نهاية الدراسة
        public int morningPrepMin = 0; // بعد الاستيقاظ — أي قيمة 0..60
        public int prayerDurationMin = 30;
        public boolean prayersEnabled = true;
        public boolean hasLocation = false; // حصلنا على موقع حقيقي من الجهاز
        public String locationName = ""; // اسم المدينة/المنطقة
        public boolean egyptSummerTime = true; // صيفي UTC+3 / شتوي UTC+2
        public double latitude = 30.0444;
        public double longitude = 31.2357;
        public int sessionMin = 45;
        public int breakMin = 10;
        public boolean alarmEnabled = true;
        public boolean alarmVibrate = true;
        public boolean alarmSnooze = true;
        public int snoozeMin = 5;
        public String planningStyle = "balanced";
        public String preferredSubjectsCsv = "";
        public String tempSubjectsCsv = "";
        public String planningNote = "";
        public int workload = 1;
        public String lastInsight = "";
        public int planDays = 7;
        public boolean allowExtraTime = true;
        public int extraTimeMaxMin = 60;
        /** 0=توازي · 1=متتابع متوازن (مدة÷أيام) · 2=محاضرة واحدة في اليوم */
        public int lectureDistMode = 0;
        /** تواريخ بدون محاضرات yyyy-MM-dd */
        public Set<String> noLectureDays = new HashSet<>();
        public Set<Integer> restDays = new HashSet<>();
        public Map<Integer, int[]> sleepExceptions = new HashMap<>();

        // ── ساعات المذاكرة (اختياري — معطّل افتراضيًا) ──
        public boolean studyTimeEnabled = false;
        public int studyMinMin = 3 * 60;   // حد أدنى دقائق
        public int studyTargetMin = 6 * 60;
        public int studyMaxMin = 8 * 60;
        public boolean studyFlexible = true;

        // ── مواعيد نزول المحاضرات (اختياري) ──
        // JSON: [{subject, days:[Calendar.DAY_OF_WEEK...], releaseTimeMin:-1|minutes, enabled}]
        public boolean lectureReleaseEnabled = false;
        public String lectureReleaseJson = "[]";

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("wakeMin", wakeMin);
            o.put("sleepMin", sleepMin);
            o.put("morningPrepMin", normalizeMorningPrep(morningPrepMin));
            o.put("prayerDurationMin", prayerDurationMin);
            o.put("prayersEnabled", prayersEnabled);
            o.put("hasLocation", hasLocation);
            o.put("locationName", locationName == null ? "" : locationName);
            o.put("egyptSummerTime", egyptSummerTime);
            o.put("latitude", latitude);
            o.put("longitude", longitude);
            o.put("sessionMin", sessionMin);
            o.put("breakMin", breakMin);
            o.put("alarmEnabled", alarmEnabled);
            o.put("alarmVibrate", alarmVibrate);
            o.put("alarmSnooze", alarmSnooze);
            o.put("snoozeMin", snoozeMin);
            o.put("planningStyle", planningStyle == null ? "balanced" : planningStyle);
            o.put("preferredSubjectsCsv", preferredSubjectsCsv == null ? "" : preferredSubjectsCsv);
            o.put("tempSubjectsCsv", tempSubjectsCsv == null ? "" : tempSubjectsCsv);
            o.put("planningNote", planningNote == null ? "" : planningNote);
            o.put("workload", workload);
            o.put("lastInsight", lastInsight == null ? "" : lastInsight);
            o.put("planDays", planDays);
            o.put("allowExtraTime", allowExtraTime);
            o.put("extraTimeMaxMin", extraTimeMaxMin);
            o.put("lectureDistMode", lectureDistMode);
            JSONArray nld = new JSONArray();
            for (String d : noLectureDays) nld.put(d);
            o.put("noLectureDays", nld);
            JSONArray rd = new JSONArray();
            for (int d : restDays) rd.put(d);
            o.put("restDays", rd);
            JSONObject ex = new JSONObject();
            for (Map.Entry<Integer, int[]> e : sleepExceptions.entrySet()) {
                JSONArray a = new JSONArray();
                a.put(e.getValue()[0]);
                a.put(e.getValue()[1]);
                ex.put(String.valueOf(e.getKey()), a);
            }
            o.put("sleepExceptions", ex);
            o.put("studyTimeEnabled", studyTimeEnabled);
            o.put("studyMinMin", studyMinMin);
            o.put("studyTargetMin", studyTargetMin);
            o.put("studyMaxMin", studyMaxMin);
            o.put("studyFlexible", studyFlexible);
            o.put("lectureReleaseEnabled", lectureReleaseEnabled);
            o.put("lectureReleaseJson", lectureReleaseJson == null ? "[]" : lectureReleaseJson);
            return o;
        }

        static Settings fromJson(JSONObject o) throws Exception {
            Settings s = new Settings();
            if (o == null) return s;
            s.wakeMin = o.optInt("wakeMin", 7 * 60);
            s.sleepMin = o.optInt("sleepMin", 23 * 60);
            // 0 قيمة صحيحة؛ الافتراضي 30 فقط إذا المفتاح غير موجود
            // قيم مسموحة فقط: 0 / 15 / 30 / 60 — بدون أي +30 مخفي
            if (o.has("morningPrepMin")) {
                s.morningPrepMin = normalizeMorningPrep(o.optInt("morningPrepMin", 0));
            } else {
                s.morningPrepMin = 0;
            }
            s.prayerDurationMin = Math.max(5, Math.min(120, o.optInt("prayerDurationMin", 30)));
            s.prayersEnabled = o.optBoolean("prayersEnabled", true);
            s.hasLocation = o.optBoolean("hasLocation", false);
            s.locationName = o.optString("locationName", "");
            s.egyptSummerTime = o.optBoolean("egyptSummerTime", true);
            s.latitude = o.optDouble("latitude", 30.0444);
            s.longitude = o.optDouble("longitude", 31.2357);
            s.sessionMin = o.optInt("sessionMin", 45);
            s.breakMin = o.optInt("breakMin", 10);
            s.alarmEnabled = o.optBoolean("alarmEnabled", true);
            s.alarmVibrate = o.optBoolean("alarmVibrate", true);
            s.alarmSnooze = o.optBoolean("alarmSnooze", true);
            s.snoozeMin = o.optInt("snoozeMin", 5);
            if (s.snoozeMin != 10) s.snoozeMin = 5;
            s.planningStyle = o.optString("planningStyle", "balanced");
            s.preferredSubjectsCsv = o.optString("preferredSubjectsCsv", "");
            s.tempSubjectsCsv = o.optString("tempSubjectsCsv", "");
            s.planningNote = o.optString("planningNote", "");
            s.workload = o.optInt("workload", 1);
            s.planDays = Math.max(1, Math.min(90, o.optInt("planDays", 7)));
            s.allowExtraTime = o.optBoolean("allowExtraTime", true);
            s.extraTimeMaxMin = o.optInt("extraTimeMaxMin", 60);
            if (s.extraTimeMaxMin < 1) s.extraTimeMaxMin = 1;
            if (s.extraTimeMaxMin > 120) s.extraTimeMaxMin = 120;
            s.lectureDistMode = o.optInt("lectureDistMode", -1);
            if (s.lectureDistMode < 0) {
                s.lectureDistMode = o.optBoolean("finishOneThenNext", false) ? 1 : 0;
            }
            if (s.lectureDistMode > 2) s.lectureDistMode = 0;
            s.noLectureDays = new HashSet<>();
            JSONArray nld = o.optJSONArray("noLectureDays");
            if (nld != null) for (int i = 0; i < nld.length(); i++) s.noLectureDays.add(nld.getString(i));
            s.lastInsight = o.optString("lastInsight", "");
            if (s.sessionMin < 10) s.sessionMin = 10;
            if (s.sessionMin > 200) s.sessionMin = 200;
            if (s.breakMin < 1) s.breakMin = 1;
            if (s.breakMin > 200) s.breakMin = 200;
            s.restDays = new HashSet<>();
            JSONArray rd = o.optJSONArray("restDays");
            if (rd != null) for (int i = 0; i < rd.length(); i++) s.restDays.add(rd.getInt(i));
            s.sleepExceptions = new HashMap<>();
            JSONObject ex = o.optJSONObject("sleepExceptions");
            if (ex != null) {
                JSONArray names = ex.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String k = names.getString(i);
                        JSONArray a = ex.getJSONArray(k);
                        s.sleepExceptions.put(Integer.parseInt(k), new int[]{a.getInt(0), a.getInt(1)});
                    }
                }
            }
            s.studyTimeEnabled = o.optBoolean("studyTimeEnabled", false);
            s.studyMinMin = Math.max(0, Math.min(16 * 60, o.optInt("studyMinMin", 3 * 60)));
            s.studyTargetMin = Math.max(0, Math.min(16 * 60, o.optInt("studyTargetMin", 6 * 60)));
            s.studyMaxMin = Math.max(0, Math.min(16 * 60, o.optInt("studyMaxMin", 8 * 60)));
            s.studyFlexible = o.optBoolean("studyFlexible", true);
            if (s.studyMinMin > s.studyMaxMin && s.studyMaxMin > 0) {
                int t = s.studyMinMin; s.studyMinMin = s.studyMaxMin; s.studyMaxMin = t;
            }
            if (s.studyTargetMin < s.studyMinMin) s.studyTargetMin = s.studyMinMin;
            if (s.studyTargetMin > s.studyMaxMin && s.studyMaxMin > 0) s.studyTargetMin = s.studyMaxMin;
            s.lectureReleaseEnabled = o.optBoolean("lectureReleaseEnabled", false);
            s.lectureReleaseJson = o.optString("lectureReleaseJson", "[]");
            if (s.lectureReleaseJson == null || s.lectureReleaseJson.isEmpty()) s.lectureReleaseJson = "[]";
            return s;
        }
    }

    public static final int DIST_WEEK = 0;
    public static final int DIST_DAYS = 1;
    public static final int DIST_DEADLINE = 2;
    public static final int DIST_NONE = 3;

    private final SharedPreferences prefs;
    private final android.content.Context app;
    public List<Task> tasks = new ArrayList<>();
    public List<Commitment> commitments = new ArrayList<>();
    public List<Session> sessions = new ArrayList<>();
    public Settings settings = new Settings();
    public List<Exam> exams = new ArrayList<>();
    public List<Focus> focuses = new ArrayList<>();
    public List<Subject> subjects = new ArrayList<>();
    public transient PlanningBrain lastBrain;
    /** آخر نتيجة تخطيط للتشخيص — لا تؤثر على السلوك */
    public transient PlanningDiag lastPlanningDiag;

    /**
     * ترتيب مؤقت لعملية التخطيط الحالية فقط (بعد Dialog التشابه).
     * لا يُحفظ في JSON ولا على Task.
     */
    private final Map<String, Integer> planSessionOrder = new HashMap<>();
    /** مفاتيح مجموعات تعادل حُسمت في عملية التخطيط الحالية (منع حلقة Dialog). */
    private final java.util.HashSet<String> planSessionResolvedKeys = new java.util.HashSet<>();

    public void clearPlanSessionTieState() {
        planSessionOrder.clear();
        planSessionResolvedKeys.clear();
    }

    public void setPlanSessionOrder(List<Task> ordered) {
        if (ordered == null) return;
        for (int i = 0; i < ordered.size(); i++) {
            Task t = ordered.get(i);
            if (t != null && t.id != null) planSessionOrder.put(t.id, i);
        }
    }

    public void markPlanSessionGroupResolved(List<Task> group) {
        String key = tieGroupKey(group);
        if (key != null) planSessionResolvedKeys.add(key);
    }

    private static String tieGroupKey(List<Task> group) {
        if (group == null || group.isEmpty()) return null;
        List<String> ids = new ArrayList<>();
        for (Task t : group) if (t != null && t.id != null) ids.add(t.id);
        Collections.sort(ids);
        if (ids.size() < 2) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    /** تشخيص تشغيل تخطيط واحد (للعرض في Developer Center فقط) */
    public static class PlanningDiag {
        public String runAt = "";
        public int wakeMin, sleepMin, morningPrep, sessionMin, breakMin, interGapUsed;
        public int planDays;
        public final java.util.List<String> lines = new ArrayList<>();
        public void line(String s) { if (s != null) lines.add(s); }
        public String asText() {
            StringBuilder sb = new StringBuilder();
            if (runAt != null && !runAt.isEmpty()) sb.append("آخر تخطيط: ").append(runAt).append('\n');
            sb.append("Wake=").append(Planner.minToTime(wakeMin))
              .append("  Sleep=").append(Planner.minToTime(sleepMin))
              .append("  Prep=").append(morningPrep).append("د\n");
            sb.append("Session=").append(sessionMin).append("د  Break=")
              .append(breakMin).append("د  InterGap=").append(interGapUsed).append("د\n");
            sb.append("Horizon أيام=").append(planDays).append('\n');
            for (String l : lines) sb.append(l).append('\n');
            return sb.toString().trim();
        }
    }

    public Planner(Context ctx) {
        app = ctx.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    /** true إذا فشل آخر load من التخزين المحلي (البيانات في الذاكرة لم تُستبدل بفارغ عند الفشل بعد نجاح سابق) */
    public boolean lastLoadFailed = false;
    public String lastLoadError = "";

    public void load() {
        lastLoadFailed = false;
        lastLoadError = "";
        try {
            List<Task> newTasks = new ArrayList<>();
            List<Commitment> newCommitments = new ArrayList<>();
            List<Session> newSessions = new ArrayList<>();
            List<Exam> newExams = new ArrayList<>();
            List<Focus> newFocuses = new ArrayList<>();
            List<Subject> newSubjects = new ArrayList<>();
            Settings newSettings;

            JSONArray ta = new JSONArray(prefs.getString("tasks", "[]"));
            for (int i = 0; i < ta.length(); i++) newTasks.add(Task.fromJson(ta.getJSONObject(i)));
            JSONArray ca = new JSONArray(prefs.getString("commitments", "[]"));
            for (int i = 0; i < ca.length(); i++) newCommitments.add(Commitment.fromJson(ca.getJSONObject(i)));
            JSONArray sa = new JSONArray(prefs.getString("sessions", "[]"));
            for (int i = 0; i < sa.length(); i++) newSessions.add(Session.fromJson(sa.getJSONObject(i)));
            newSettings = Settings.fromJson(new JSONObject(prefs.getString("settings", "{}")));
            JSONArray ea = new JSONArray(prefs.getString("exams", "[]"));
            for (int i = 0; i < ea.length(); i++) newExams.add(Exam.fromJson(ea.getJSONObject(i)));
            JSONArray fa = new JSONArray(prefs.getString("focuses", "[]"));
            for (int i = 0; i < fa.length(); i++) newFocuses.add(Focus.fromJson(fa.getJSONObject(i)));
            JSONArray suba = new JSONArray(prefs.getString("subjects", "[]"));
            for (int i = 0; i < suba.length(); i++) newSubjects.add(Subject.fromJson(suba.getJSONObject(i)));

            // Commit only after full successful parse
            tasks.clear(); tasks.addAll(newTasks);
            commitments.clear(); commitments.addAll(newCommitments);
            sessions.clear(); sessions.addAll(newSessions);
            settings = newSettings != null ? newSettings : new Settings();
            exams.clear(); exams.addAll(newExams);
            focuses.clear(); focuses.addAll(newFocuses);
            subjects.clear(); subjects.addAll(newSubjects);
            seedSubjects();
            migrateCsvFocuses();
            ensureTaskOrder();
        } catch (Exception e) {
            lastLoadFailed = true;
            lastLoadError = e.getMessage() != null ? e.getMessage() : "load_failed";
            e.printStackTrace();
            // لا نمسح البيانات الحالية في الذاكرة ولا نكتب قوائم فارغة على التخزين
        }
    }

    private void seedSubjects() {
        if (!subjects.isEmpty()) return;
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Task x : tasks) if (x.subject != null && x.subject.trim().length() > 0) names.add(x.subject.trim());
        for (Exam e : exams) if (e.subject != null && e.subject.trim().length() > 0) names.add(e.subject.trim());
        String[] def = {"العربي", "الإنجليزي", "الفيزياء", "الكيمياء", "الأحياء"};
        if (names.isEmpty()) for (String d : def) names.add(d);
        for (String n : names) subjects.add(new Subject(n));
    }

    public Subject findSubject(String name) {
        if (name == null) return null;
        String c = PlanningBrain.canon(name);
        for (Subject s : subjects) if (PlanningBrain.canon(s.name).equals(c) || s.name.equals(name)) return s;
        return null;
    }

    public void addSubject(String name) {
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty() || findSubject(name) != null) return;
        subjects.add(new Subject(name));
        save();
    }

    public void renameSubject(String oldName, String newName) {
        if (newName == null || newName.trim().isEmpty()) return;
        newName = newName.trim();
        Subject s = findSubject(oldName);
        if (s != null) s.name = newName;
        for (Task t : tasks) if (t.subject != null && t.subject.equals(oldName)) t.subject = newName;
        for (Exam e : exams) if (e.subject != null && e.subject.equals(oldName)) e.subject = newName;
        for (Session se : sessions) if (se.subject != null && se.subject.equals(oldName)) se.subject = newName;
        for (Focus f : focuses) if (f.subject != null && f.subject.equals(oldName)) f.subject = newName;
        save();
    }

    public void deleteSubject(String name) {
        Subject s = findSubject(name);
        if (s != null) subjects.remove(s);
        save();
    }

    public String[] subjectNames() {
        String[] a = new String[subjects.size()];
        for (int i = 0; i < subjects.size(); i++) a[i] = subjects.get(i).name;
        return a;
    }

    private void migrateCsvFocuses() {
        if (!focuses.isEmpty()) return;
        long now = System.currentTimeMillis();
        java.util.LinkedHashSet<String> subs = new java.util.LinkedHashSet<>();
        if (settings.preferredSubjectsCsv != null)
            for (String s : settings.preferredSubjectsCsv.split("[,،]"))
                if (s.trim().length() > 0) subs.add(PlanningBrain.canon(s.trim()));
        if (settings.tempSubjectsCsv != null)
            for (String s : settings.tempSubjectsCsv.split("[,،]"))
                if (s.trim().length() > 0) subs.add(PlanningBrain.canon(s.trim()));
        for (String s : subs) {
            Focus f = new Focus();
            f.subject = s;
            f.reason = "USER_REQUEST";
            f.scope = "temporary";
            f.strength = 3;
            f.createdAt = now;
            f.expiresAt = now + 14L * 24 * 60 * 60 * 1000;
            focuses.add(f);
        }
    }

    public void save() {
        try {
            // مصدر واحد لمدة الجلسة/الراحة
            if (settings.sessionMin < 10) settings.sessionMin = 10;
            if (settings.sessionMin > 200) settings.sessionMin = 200;
            if (settings.breakMin < 1) settings.breakMin = 1;
            if (settings.breakMin > 200) settings.breakMin = 200;
            JSONArray ta = new JSONArray();
            for (Task t : tasks) ta.put(t.toJson());
            JSONArray ca = new JSONArray();
            for (Commitment c : commitments) ca.put(c.toJson());
            JSONArray sa = new JSONArray();
            for (Session s : sessions) sa.put(s.toJson());
            JSONArray ea = new JSONArray();
            for (Exam e : exams) ea.put(e.toJson());
            JSONArray fa = new JSONArray();
            for (Focus f : focuses) fa.put(f.toJson());
            JSONArray suba = new JSONArray();
            for (Subject s : subjects) suba.put(s.toJson());
            prefs.edit()
                    .putString("tasks", ta.toString())
                    .putString("commitments", ca.toString())
                    .putString("sessions", sa.toString())
                    .putString("settings", settings.toJson().toString())
                    .putString("exams", ea.toString())
                    .putString("focuses", fa.toString())
                    .putString("subjects", suba.toString())
                    .apply();
            SessionAlarmScheduler.resync(app, this);
            PlanWidgetProvider.updateAll(app);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** حفظ تقدم جزئي للجلسة (دقائق منفذة). لا يضبط done. */
    public void persistExecutedMin(String sessionId, int executed) {
        Session s = findSessionById(sessionId);
        if (s == null || s.done) return;
        s.executedMin = Math.max(0, Math.min(s.durationMin, executed));
        if (s.executedMin >= s.durationMin) {
            // لا تُغلق تلقائيًا هنا — الإكمال عبر markSessionDone فقط
            s.executedMin = Math.max(0, s.durationMin - 1);
        }
        save();
    }

    public String exportJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "MyPlan");
            root.put("version", 3); // data schema (myplan_v3 compatible)
            root.put("backupFormat", 1); // backup envelope version; old files without this still load
            JSONArray ta = new JSONArray();
            for (Task t : tasks) ta.put(t.toJson());
            JSONArray ca = new JSONArray();
            for (Commitment c : commitments) ca.put(c.toJson());
            JSONArray sa = new JSONArray();
            for (Session s : sessions) sa.put(s.toJson());
            root.put("tasks", ta);
            root.put("commitments", ca);
            root.put("sessions", sa);
            JSONArray ea = new JSONArray();
            for (Exam e : exams) ea.put(e.toJson());
            root.put("exams", ea);
            JSONArray fa = new JSONArray();
            for (Focus f : focuses) fa.put(f.toJson());
            root.put("focuses", fa);
            JSONArray suba = new JSONArray();
            for (Subject s : subjects) suba.put(s.toJson());
            root.put("subjects", suba);
            root.put("settings", settings.toJson());
            try { PremiumHub.putIntoBackup(app, root); } catch (Exception ignored) {}
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * يستعيد من JSON بأمان: يبني بيانات مؤقتة أولًا، ولا يلمس البيانات الحالية إلا بعد نجاح كامل.
     * متوافق مع Backup قديم بدون backupFormat / بدون app.
     */
    public void importJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            throw new Exception("الملف فارغ");
        }
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            throw new Exception("JSON تالف وغير قابل للقراءة");
        }
        // قبول: MyPlan صريح، أو ملفات قديمة فيها tasks/settings بدون حقل app
        String appName = root.optString("app", "");
        if (appName.length() > 0 && !"MyPlan".equalsIgnoreCase(appName)) {
            throw new Exception("الملف ليس نسخة احتياطية لـ My Plan");
        }
        if (!root.has("tasks") && !root.has("settings") && !root.has("sessions")) {
            throw new Exception("ملف غير صالح — لا توجد بيانات أساسية");
        }

        List<Task> newTasks = new ArrayList<>();
        List<Commitment> newCommitments = new ArrayList<>();
        List<Session> newSessions = new ArrayList<>();
        List<Exam> newExams = new ArrayList<>();
        List<Focus> newFocuses = new ArrayList<>();
        List<Subject> newSubjects = new ArrayList<>();
        Settings newSettings;

        try {
            JSONArray ta = root.optJSONArray("tasks");
            if (ta != null) {
                for (int i = 0; i < ta.length(); i++) newTasks.add(Task.fromJson(ta.getJSONObject(i)));
            }
            JSONArray ca = root.optJSONArray("commitments");
            if (ca != null) {
                for (int i = 0; i < ca.length(); i++) newCommitments.add(Commitment.fromJson(ca.getJSONObject(i)));
            }
            JSONArray sa = root.optJSONArray("sessions");
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) newSessions.add(Session.fromJson(sa.getJSONObject(i)));
            }
            if (root.has("settings") && root.optJSONObject("settings") != null) {
                newSettings = Settings.fromJson(root.optJSONObject("settings"));
            } else {
                newSettings = new Settings();
            }
            if (newSettings == null) newSettings = new Settings();
            JSONArray ea = root.optJSONArray("exams");
            if (ea != null) {
                for (int i = 0; i < ea.length(); i++) newExams.add(Exam.fromJson(ea.getJSONObject(i)));
            }
            JSONArray fa = root.optJSONArray("focuses");
            if (fa != null) {
                for (int i = 0; i < fa.length(); i++) newFocuses.add(Focus.fromJson(fa.getJSONObject(i)));
            }
            JSONArray suba = root.optJSONArray("subjects");
            if (suba != null) {
                for (int i = 0; i < suba.length(); i++) newSubjects.add(Subject.fromJson(suba.getJSONObject(i)));
            }
        } catch (Exception e) {
            throw new Exception("فشل قراءة محتوى النسخة: " + (e.getMessage() != null ? e.getMessage() : "خطأ"));
        }

        // Commit فقط بعد نجاح التحويل الكامل
        tasks.clear(); tasks.addAll(newTasks);
        commitments.clear(); commitments.addAll(newCommitments);
        sessions.clear(); sessions.addAll(newSessions);
        settings = newSettings;
        exams.clear(); exams.addAll(newExams);
        focuses.clear(); focuses.addAll(newFocuses);
        subjects.clear(); subjects.addAll(newSubjects);
        seedSubjects();
        ensureTaskOrder();
        try { PremiumHub.loadFromBackup(app, root); } catch (Exception ignored) {}
        save();
    }


    /** هل للمادة مهام backlog غير مكتملة ما زال لها رصيد؟ */
    private boolean subjectHasOpenBacklog(String subject, Map<String, Integer> remaining) {
        if (subject == null) return false;
        for (Task t : tasks) {
            if (!subject.equals(t.subject)) continue;
            if (!t.backlog || t.done) continue;
            int rem = remaining != null ? remaining.getOrDefault(t.id, t.remainingMin) : t.remainingMin;
            if (rem > 0) return true;
        }
        return false;
    }

    public Task findTask(String id) {
        for (Task t : tasks) if (t.id.equals(id)) return t;
        return null;
    }

    public List<Session> sessionsForDay(String day) {
        List<Session> out = new ArrayList<>();
        if (day == null) return out;
        String d = day.trim();
        for (Session s : sessions) {
            if (s.day != null && d.equals(s.day.trim())) out.add(s);
        }
        Collections.sort(out, Comparator.comparingInt(a -> a.startMin));
        return out;
    }

    public static String todayStr() {
        return DAY.format(Calendar.getInstance().getTime());
    }

    public static Calendar dayCal(String day) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(DAY.parse(day));
            return c;
        } catch (Exception e) {
            return Calendar.getInstance();
        }
    }

    public static String dayLabelAr(String day) {
        Calendar c = dayCal(day);
        String[] names = {"", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
        return names[c.get(Calendar.DAY_OF_WEEK)] + "  " + day;
    }

    public static int parseTimeToMin(String t) {
        if (t == null) return -1;
        t = t.trim();
        try {
            String[] p = t.split(":");
            if (p.length < 2) return -1;
            int h = Integer.parseInt(p[0].trim());
            String mm = p[1].trim().replaceAll("[^0-9].*", "");
            int m = Integer.parseInt(mm);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * دقائق من منتصف الليل → عرض 24 ساعة "HH:mm".
     * بدون ص/م وبدون ±12 — نفس معنى TimePicker 24 ساعة والقيمة الداخلية.
     */
    public static String minToTime(int min) {
        int m = ((min % (24 * 60)) + 24 * 60) % (24 * 60);
        int h24 = m / 60;
        int mm = m % 60;
        return String.format(Locale.US, "%02d:%02d", h24, mm);
    }

    /**
     * من ساعة/دقيقة TimePicker 24 ساعة (hour 0..23) → دقائق من منتصف الليل.
     * ممنوع إضافة أو طرح 12.
     */
    public static int timeToMin(int hour24, int minute) {
        int h = hour24 % 24;
        if (h < 0) h += 24;
        int mm = minute % 60;
        if (mm < 0) mm += 60;
        return h * 60 + mm;
    }

    private int[] wakeSleepFor(Calendar day) {
        int dow = day.get(Calendar.DAY_OF_WEEK);
        if (settings.sleepExceptions.containsKey(dow)) {
            return settings.sleepExceptions.get(dow);
        }
        return new int[]{settings.wakeMin, settings.sleepMin};
    }

    /** تجهيز صباحي: أي دقيقة من 0 إلى 60 كما اختارها المستخدم (بدون تقريب لـ 15/30/60). */
    public static int normalizeMorningPrep(int v) {
        if (v < 0) return 0;
        if (v > 60) return 60;
        return v;
    }

    /**
     * أول دقيقة دراسة في اليوم = استيقاظ + تجهيز صباحي.
     * المصدر الوحيد لبداية نافذة الدراسة — لا يُضاف أي ثابت آخر.
     */
    private int studyStartMin(Calendar day) {
        int[] ws = wakeSleepFor(day);
        int wakeRaw = ((ws[0] % (24 * 60)) + (24 * 60)) % (24 * 60);
        int prep = normalizeMorningPrep(settings.morningPrepMin);
        int start = wakeRaw + prep;
        if (start >= 24 * 60) start = start % (24 * 60);
        return start;
    }

    private boolean isRestDay(Calendar day) {
        return settings.restDays.contains(day.get(Calendar.DAY_OF_WEEK));
    }

    private boolean isNoLectureDay(String dayStr) {
        return dayStr != null && settings.noLectureDays != null && settings.noLectureDays.contains(dayStr);
    }

    private boolean isNoLectureDay(Calendar day) {
        return isNoLectureDay(DAY.format(day.getTime()));
    }

    /** هل يمكن وضع هذه المحاضرة على هذا اليوم؟ (deadline / preferred / fixed) */
    private boolean lectureAllowedOnDay(Task t, String ds,
                                        Map<String, String> taskFinish,
                                        Map<String, Integer> remaining) {
        if (t == null || ds == null) return false;
        String finish = taskFinish != null ? taskFinish.get(t.id) : null;
        if (finish != null && ds.compareTo(finish) > 0) return false;
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(DAY.parse(ds));
            int dow = c.get(Calendar.DAY_OF_WEEK);
            if (t.preferredDow >= 1 && t.preferredDow <= 7 && t.preferredDow != dow) return false;
            Subject subj = findSubject(t.subject);
            boolean useFixed = subj != null && !subj.fixedDows.isEmpty()
                    && t.isLecture() && !t.backlog
                    && !subjectHasOpenBacklog(t.subject, remaining);
            if (useFixed && !subj.fixedDows.contains(dow)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    /**
     * مواقيت تقريبية فلكية لمصر (فجر 19.5 / عشاء 17.5).
     * ترجع: فجر، ظهر، عصر، مغرب، عشاء بالدقائق من منتصف الليل.
     */
    public static final String[] PRAYER_NAMES_AR = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};

    /** مواقيت الصلاة ليوم معيّن: [startMin, endMin, nameIndex] — فارغ لو مفيش موقع */
    public List<int[]> prayerBlocksForDay(Calendar day) {
        List<int[]> out = new ArrayList<>();
        if (!settings.prayersEnabled || !settings.hasLocation) return out;
        int[] starts = prayerStartMinutes(day);
        int pd = Math.max(5, Math.min(120, settings.prayerDurationMin));
        for (int i = 0; i < starts.length; i++) {
            int ps = starts[i];
            if (ps < 0) continue;
            out.add(new int[]{ps, Math.min(ps + pd, 24 * 60), i});
        }
        return out;
    }

    public List<int[]> prayerBlocksToday() {
        return prayerBlocksForDay(Calendar.getInstance());
    }

    private int[] prayerStartMinutes(Calendar day) {
        try {
            int year = day.get(Calendar.YEAR);
            int month = day.get(Calendar.MONTH) + 1;
            int dayOfMonth = day.get(Calendar.DAY_OF_MONTH);
            double lat = settings.latitude;
            double lng = settings.longitude;
            int y = year, m = month;
            if (m <= 2) { y -= 1; m += 12; }
            int A = y / 100;
            int B = 2 - A + A / 4;
            double jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1))
                    + dayOfMonth + B - 1524.5;
            double d = jd - 2451545.0;
            double g = prFixAngle(357.529 + 0.98560028 * d);
            double q = prFixAngle(280.459 + 0.98564736 * d);
            double L = prFixAngle(q + 1.915 * Math.sin(Math.toRadians(g))
                    + 0.020 * Math.sin(Math.toRadians(2 * g)));
            double e = 23.439 - 0.00000036 * d;
            double RA = Math.toDegrees(Math.atan2(
                    Math.cos(Math.toRadians(e)) * Math.sin(Math.toRadians(L)),
                    Math.cos(Math.toRadians(L)))) / 15.0;
            double eqt = q / 15.0 - prFixHour(RA);
            double decl = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(e)) * Math.sin(Math.toRadians(L))));
            double tz = settings.egyptSummerTime ? 3.0 : 2.0; // مصر: صيفي +3 / شتوي +2
            double noon = prFixHour(12 + tz - lng / 15.0 - eqt);
            double fajr = noon - prTAngle(lat, decl, 19.5) / 15.0;
            double asr = noon + prAsr(lat, decl) / 15.0;
            double maghrib = noon + prTAngle(lat, decl, 0.833) / 15.0;
            double isha = noon + prTAngle(lat, decl, 17.5) / 15.0;
            return new int[]{
                    prHourToMin(fajr),
                    prHourToMin(noon),
                    prHourToMin(asr),
                    prHourToMin(maghrib),
                    prHourToMin(isha)
            };
        } catch (Exception ex) {
            return new int[]{-1, -1, -1, -1, -1};
        }
    }

    private static double prFixAngle(double a) {
        a = a % 360;
        if (a < 0) a += 360;
        return a;
    }

    private static double prFixHour(double h) {
        h = h % 24;
        if (h < 0) h += 24;
        return h;
    }

    private static double prTAngle(double lat, double decl, double angle) {
        double x = (-Math.sin(Math.toRadians(angle))
                - Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl)))
                / (Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(decl)));
        x = Math.max(-1, Math.min(1, x));
        return Math.toDegrees(Math.acos(x));
    }

    private static double prAsr(double lat, double decl) {
        double v = (Math.sin(Math.atan(1.0 / (1 + Math.tan(Math.toRadians(Math.abs(lat - decl))))))
                - Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl)))
                / (Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(decl)));
        v = Math.max(-1, Math.min(1, v));
        return Math.toDegrees(Math.acos(v));
    }

    private static int prHourToMin(double h) {
        h = prFixHour(h);
        int m = (int) Math.round(h * 60);
        if (m < 0) m = 0;
        if (m >= 24 * 60) m = 24 * 60 - 1;
        return m;
    }

    /**
     * أوقات الدراسة المتاحة في يوم واحد.
     *
     * Commitment = فترة مشغولة بالكامل (ليست محاضرة وليست Session).
     * لا يُسمح بأي تداخل بين free وبين أي Commitment.
     * الصلاة وامتحان بوقت = مشغول أيضًا (منطقها كما هو؛ لا يُغيَّر).
     *
     * الناتج: فترات [start,end) داخل [studyStart, bedtime) فقط،
     * قبل وبعد الالتزامات، بدون ثغرات وهمية بين التزامين متلامسين.
     */
    private List<int[]> freeSlots(Calendar day) {
        if (isRestDay(day)) return new ArrayList<>();
        int wake = studyStartMin(day);
        int[] ws = wakeSleepFor(day);
        int sleep = normMinOfDay(ws[1]);
        int sleepEnd = sleep;
        if (sleepEnd <= wake) sleepEnd += 24 * 60;

        // مصادر Busy منفصلة للتشخيص فقط — نفس الشروط السابقة، بدون تغيير منطق الجمع
        List<int[]> commitmentBusy = new ArrayList<>();
        collectCommitmentBusy(day, wake, sleepEnd, commitmentBusy);

        List<int[]> prayerBusy = new ArrayList<>();
        if (settings.prayersEnabled && settings.hasLocation) {
            int[] prayers = prayerStartMinutes(day);
            int pd = Math.max(5, Math.min(120, settings.prayerDurationMin));
            for (int ps : prayers) {
                if (ps < 0) continue;
                appendBusyClipped(prayerBusy, ps, ps + pd, wake, sleepEnd);
            }
        }

        List<int[]> examBusy = new ArrayList<>();
        String dayStrEx = DAY.format(day.getTime());
        for (Exam e : exams) {
            if (e == null || e.done) continue;
            if (e.day == null || !e.day.equals(dayStrEx)) continue;
            if (e.time == null || e.time.trim().isEmpty()) continue;
            int em = parseTimeToMin(e.time);
            if (em < 0) continue;
            int dur = e.neededMin > 0 ? Math.min(e.neededMin, 180) : 120;
            appendBusyClipped(examBusy, em, em + dur, wake, sleepEnd);
        }

        // مواعيد النزول: Busy/Unavailable interval فقط عند تحديد وقت (لا من بداية اليوم)
        List<int[]> releaseBusy = new ArrayList<>();
        collectLectureReleaseBusy(day, wake, sleepEnd, releaseBusy);

        List<int[]> busy = new ArrayList<>();
        busy.addAll(commitmentBusy);
        busy.addAll(prayerBusy);
        busy.addAll(examBusy);
        busy.addAll(releaseBusy);

        List<int[]> merged = mergeBusyStrict(busy);
        List<int[]> rawFree = invertBusyToFree(wake, sleepEnd, merged);

        // Diagnostic فقط — لا يؤثر على الناتج
        if (lastPlanningDiag != null) {
            String dayLabel = dayStrEx;
            lastPlanningDiag.line("— Busy sources يوم " + dayLabel
                    + " wake=" + minToTime(wake) + " sleepEnd=" + minToTime(sleepEnd % (24 * 60)) + " —");
            logBusySourceDiag("Commitment Busy", commitmentBusy);
            logBusySourceDiag("Prayer Busy", prayerBusy);
            logBusySourceDiag("Timed Exam Busy", examBusy);
            logBusySourceDiag("Lecture Release Busy", releaseBusy);
            logBusySourceDiag("Merged Busy", merged);
            logBusySourceDiag("Raw Free", rawFree);
        }

        return rawFree;
    }

    /**
     * مواعيد نزول المحاضرات كـ Busy interval فقط.
     * عند وجود وقت محدد ليوم الأسبوع المطابق: يمنع الجلسات داخل [start, end) فقط
     * (لا يُعتبر من بداية اليوم حتى الموعد Busy).
     * end = endTimeMin إن وُجد، وإلا start + 120 دقيقة.
     */
    private void collectLectureReleaseBusy(Calendar day, int wake, int sleepEnd, List<int[]> busy) {
        if (day == null || busy == null) return;
        if (!settings.lectureReleaseEnabled) return;
        String json = settings.lectureReleaseJson;
        if (json == null || json.isEmpty() || "[]".equals(json)) return;
        try {
            int dow = day.get(Calendar.DAY_OF_WEEK);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("enabled", true)) continue;
                JSONArray days = o.optJSONArray("days");
                if (days == null || days.length() == 0) continue;
                boolean match = false;
                for (int j = 0; j < days.length(); j++) {
                    if (days.getInt(j) == dow) { match = true; break; }
                }
                if (!match) continue;
                int start = o.optInt("releaseTimeMin", -1);
                if (start < 0) continue;
                int end = o.optInt("endTimeMin", -1);
                if (end < 0 || end <= start) end = start + 120;
                if (end > start) appendBusyClipped(busy, start, end, wake, sleepEnd);
            }
        } catch (Exception ignored) {}
    }

    /** سطور تشخيص لمصدر Busy/Free — لا تغيّر المنطق. */
    private void logBusySourceDiag(String title, List<int[]> intervals) {
        if (lastPlanningDiag == null) return;
        if (intervals == null || intervals.isEmpty()) {
            lastPlanningDiag.line(title + ": (لا يوجد)");
            return;
        }
        lastPlanningDiag.line(title + ":");
        for (int i = 0; i < intervals.size(); i++) {
            int[] it = intervals.get(i);
            if (it == null || it.length < 2) continue;
            int a = it[0];
            int b = it[1];
            lastPlanningDiag.line("  [" + i + "] " + a + " → " + b
                    + "  (" + minToTime(a % (24 * 60)) + " → " + minToTime(b % (24 * 60))
                    + " ، " + Math.max(0, b - a) + "د)");
        }
    }

    /**
     * يضيف فترات الالتزامات المطبقة على هذا اليوم كـbusy.
     * الالتزام = [startMin, endMin) كما خزّنها المستخدم فقط.
     * التقاطع مع نافذة الدراسة [wake, sleepEnd) فقط — لا يُستبدل start بـ wake
     * إلا إذا كان الالتزام يبدأ فعليًا قبل بداية نافذة الدراسة (قص تقاطع، ليس fallback).
     */
    private void collectCommitmentBusy(Calendar day, int wake, int sleepEnd, List<int[]> busy) {
        if (day == null || busy == null) return;
        int dow = day.get(Calendar.DAY_OF_WEEK);
        for (Commitment c : commitments) {
            if (c == null || !c.matchesDay(dow)) continue;
            sanitizeCommitmentTimes(c);

            // الحدود الحقيقية للالتزام — ليست wake
            int commitStart = c.startMin;
            int commitEnd = c.endMin;

            if (commitStart == commitEnd) continue; // مدة صفر

            if (commitEnd > commitStart) {
                // نفس اليوم: busy = تقاطع [commitStart, commitEnd) ∩ [wake, sleepEnd)
                int bs = commitStart > wake ? commitStart : wake;
                int be = commitEnd < sleepEnd ? commitEnd : sleepEnd;
                if (be > bs) busy.add(new int[]{bs, be});
            } else {
                // عبر منتصف الليل: جزء قبل منتصف الليل + جزء بعده
                // [commitStart, 24:00) ∩ نافذة الدراسة
                int bs1 = commitStart > wake ? commitStart : wake;
                int be1 = (24 * 60) < sleepEnd ? (24 * 60) : sleepEnd;
                if (be1 > bs1) busy.add(new int[]{bs1, be1});
                // [0, commitEnd) ∩ نافذة الدراسة
                int bs2 = 0 > wake ? 0 : wake;
                int be2 = commitEnd < sleepEnd ? commitEnd : sleepEnd;
                if (be2 > bs2) busy.add(new int[]{bs2, be2});
                // امتداد نافذة الدراسة بعد منتصف الليل (sleepEnd > 24*60)
                if (sleepEnd > 24 * 60) {
                    int bs3 = commitStart > wake ? commitStart : wake;
                    int be3 = (commitEnd + 24 * 60) < sleepEnd ? (commitEnd + 24 * 60) : sleepEnd;
                    if (be3 > bs3) busy.add(new int[]{bs3, be3});
                }
            }
        }
    }

    private static int normMinOfDay(int m) {
        return ((m % (24 * 60)) + (24 * 60)) % (24 * 60);
    }

    /** تقاطع [a,b) مع [wake, sleepEnd) — إن بقي طول > 0 يُضاف كـbusy. */
    private static void appendBusyClipped(List<int[]> busy, int a, int b, int wake, int sleepEnd) {
        int bs = Math.max(a, wake);
        int be = Math.min(b, sleepEnd);
        if (be > bs) busy.add(new int[]{bs, be});
    }

    /** دمج busy متداخل أو متلامس (b[0] <= last.end) حتى لا يظهر free بطول 0 بين التزامين. */
    private static List<int[]> mergeBusyStrict(List<int[]> busy) {
        if (busy == null || busy.isEmpty()) return new ArrayList<>();
        Collections.sort(busy, Comparator.comparingInt(x -> x[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] b : busy) {
            if (b == null || b.length < 2 || b[1] <= b[0]) continue;
            if (merged.isEmpty() || b[0] > merged.get(merged.size() - 1)[1]) {
                merged.add(new int[]{b[0], b[1]});
            } else {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], b[1]);
            }
        }
        return merged;
    }

    /**
     * [wake, sleepEnd) ناقص busy المدمج = free.
     * يضمن: لا free يتقاطع مع أي busy؛ يستغل ما قبل الالتزام وما بعده.
     */
    private static List<int[]> invertBusyToFree(int wake, int sleepEnd, List<int[]> mergedBusy) {
        List<int[]> free = new ArrayList<>();
        int cursor = wake;
        if (mergedBusy != null) {
            for (int[] b : mergedBusy) {
                if (b[1] <= cursor) continue;
                if (b[0] > cursor) {
                    int fe = Math.min(b[0], sleepEnd);
                    if (fe > cursor) free.add(new int[]{cursor, fe});
                }
                cursor = Math.max(cursor, b[1]);
                if (cursor >= sleepEnd) break;
            }
        }
        if (cursor < sleepEnd) free.add(new int[]{cursor, sleepEnd});

        // قص العرض داخل اليوم الميلادي 0..24*60 مع الإبقاء على كل جزء صالح
        List<int[]> out = new ArrayList<>();
        int dayEnd = Math.min(sleepEnd, 24 * 60);
        for (int[] w : free) {
            int a = Math.max(w[0], wake);
            int b = Math.min(w[1], dayEnd);
            if (b > a && a < 24 * 60) {
                if (b > 24 * 60) b = 24 * 60;
                if (b > a) out.add(new int[]{a, b});
            }
        }
        return out;
    }

    /**
     * هل الفترة [start, end) (دقائق من منتصف الليل) تتقاطع مع أي Commitment في هذا اليوم؟
     * true = تداخل ممنوع (حتى لو ثانية واحدة).
     */
    private boolean overlapsCommitment(Calendar day, int start, int end) {
        if (day == null) return false;
        int a = start;
        int b = end;
        if (b <= a) b += 24 * 60;
        int dow = day.get(Calendar.DAY_OF_WEEK);
        for (Commitment c : commitments) {
            if (c == null || !c.matchesDay(dow)) continue;
            int cs = normMinOfDay(c.startMin);
            int ce = normMinOfDay(c.endMin);
            if (ce <= cs) ce += 24 * 60;
            if (a < ce && b > cs) return true;
        }
        return false;
    }

    /** جلسة مخزّنة تتقاطع مع التزام يومها؟ */
    private boolean sessionOverlapsCommitment(Session s) {
        if (s == null || s.day == null) return false;
        try {
            Calendar day = dayCal(s.day);
            int a = s.startMin;
            int b = s.endMin;
            if (b <= a) b += 24 * 60;
            return overlapsCommitment(day, a, b);
        } catch (Exception e) {
            return false;
        }
    }


    public static Calendar nowLocal() {
        return Calendar.getInstance();
    }

    public static int nowMinOfDay() {
        Calendar n = nowLocal();
        return n.get(Calendar.HOUR_OF_DAY) * 60 + n.get(Calendar.MINUTE);
    }

    public long sessionStartMillis(Session s) {
        Calendar c = dayCal(s.day);
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, s.startMin) / 60);
        c.set(Calendar.MINUTE, Math.max(0, s.startMin) % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public long sessionEndMillis(Session s) {
        Calendar c = dayCal(s.day);
        int end = s.endMin;
        if (end <= s.startMin) end += 24 * 60;
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, end);
        return c.getTimeInMillis();
    }

    public boolean sessionStarted(Session s, long nowMs) {
        return nowMs >= sessionStartMillis(s) && nowMs < sessionEndMillis(s);
    }

    public final List<String> lastConflicts = new ArrayList<>();
    public final List<String> lastSuggestions = new ArrayList<>();
    /** تشخيص Smart Recovery — للمطور فقط */
    public String lastSrDiag = "";

    private void markMissedAndProtect(long nowMs) {
        for (Session s : sessions) {
            if (s.done) continue;
            if (sessionEndMillis(s) <= nowMs) s.missed = true;
        }
    }

    /**
     * فحص خفيف للفائتة بدون buildSchedule كامل.
     * يحدّث Session.missed ويحفظ إن تغيّر شيء.
     * @return عدد الجلسات الفائتة الحالية (!done && missed)
     */
    public int refreshMissedFlags() {
        long nowMs = System.currentTimeMillis();
        boolean changed = false;
        java.util.ArrayList<Session> newlyMissed = new java.util.ArrayList<>();
        for (Session s : sessions) {
            if (s == null || s.done) continue;
            boolean shouldMiss = sessionEndMillis(s) <= nowMs;
            if (shouldMiss && !s.missed) {
                s.missed = true;
                changed = true;
                newlyMissed.add(s);
            }
        }
        if (changed) save();
        // إشعار نظام للجلسات التي أصبحت فائتة الآن (بدون تكرار)
        if (app != null) {
            for (Session s : newlyMissed) {
                try {
                    if (s.id == null || SessionAlarmScheduler.wasMissedNotified(app, s.id)) continue;
                    String name = s.taskName != null ? s.taskName : "محاضرة";
                    SessionAlarmReceiver.postMissedNotification(app, s.id, name);
                    SessionAlarmScheduler.markMissedNotified(app, s.id);
                } catch (Exception ignored) {}
            }
        }
        int n = 0;
        for (Session s : sessions) {
            if (s != null && s.missed && !s.done) n++;
        }
        return n;
    }

    /**
     * تأجيل جلسة في نفس اليوم بمقدار delayMin دقائق، ثم إزاحة الجلسات التالية
     * غير المكتملة وغير المثبتة. لا يستدعي buildSchedule.
     * @return null عند النجاح، وإلا رسالة خطأ للمستخدم
     */
    public String postponeSessionInDay(String sessionId, int delayMin) {
        if (sessionId == null) return "الجلسة غير موجودة.";
        if (delayMin < 1) return "أدخل عدد دقائق أكبر من صفر.";
        if (delayMin > 12 * 60) return "التأجيل كبير جدًا (حد أقصى 12 ساعة).";
        Session target = null;
        for (Session x : sessions) {
            if (x != null && sessionId.equals(x.id)) { target = x; break; }
        }
        if (target == null) return "الجلسة غير موجودة.";
        if (target.done) return "لا يمكن تأجيل جلسة مكتملة.";
        if (target.day == null || target.day.isEmpty()) return "يوم الجلسة غير محدد.";

        final String day = target.day;
        Calendar dCal = dayCal(day);
        if (isRestDay(dCal)) return "يوم راحة — لا يمكن التعديل.";

        int origStart = target.startMin;
        int dur = Math.max(1, target.durationMin);
        int newStart = origStart + delayMin;
        int newEnd = newStart + dur;
        if (newStart >= 24 * 60) return "التأجيل يتجاوز منتصف الليل.";

        // جلسات تالية قابلة للتحريك: نفس اليوم، بعد/عند بداية الهدف، غير done وغير pinned
        List<Session> following = new ArrayList<>();
        for (Session x : sessions) {
            if (x == null || x.id == null) continue;
            if (x.id.equals(target.id)) continue;
            if (!day.equals(x.day) || x.done) continue;
            if (x.startMin < origStart) continue;
            if (x.userPinned) {
                // مثبتة لاحقًا: يجب ألا تتداخل مع الموضع الجديد للهدف
                int os = x.startMin, oe = x.endMin;
                if (oe <= os) oe += 24 * 60;
                if (newStart < oe && newEnd > os) {
                    return "تعارض مع جلسة مثبتة («" + (x.taskName != null ? x.taskName : "") + "»).";
                }
                continue;
            }
            following.add(x);
        }
        following.sort(Comparator.comparingInt(a -> a.startMin));

        // أزل الهدف مؤقتًا للتحقق من التعارض
        sessions.remove(target);
        if (windowConflicts(dCal, newStart, newEnd)) {
            sessions.add(target);
            return "التعارض مع النوم أو التزام أو خارج نافذة الدراسة.";
        }
        for (Session o : sessions) {
            if (o == null || !day.equals(o.day) || o.done) continue;
            int os = o.startMin, oe = o.endMin;
            if (oe <= os) oe += 24 * 60;
            if (newStart < oe && newEnd > os) {
                sessions.add(target);
                return "تعارض مع جلسة أخرى في نفس الوقت.";
            }
        }

        target.startMin = newStart % (24 * 60);
        target.endMin = newEnd % (24 * 60);
        if (target.endMin == 0 && newEnd > 0) target.endMin = 24 * 60;
        target.durationMin = dur;
        target.missed = false;
        sessions.add(target);

        // إزاحة التالية في free slots بعد نهاية الهدف
        if (!following.isEmpty()) {
            java.util.HashSet<String> ids = new java.util.HashSet<>();
            for (Session f : following) if (f.id != null) ids.add(f.id);
            sessions.removeIf(s -> s != null && s.id != null && ids.contains(s.id));

            int breakGap = Math.max(0, settings.breakMin);
            int cursor = target.endMin;
            if (cursor <= target.startMin) cursor = target.startMin + dur;

            List<int[]> free = freeSlotsForDay(dCal, day);
            if (day.equals(todayStr())) free = clipPastForToday(free, nowMinOfDay());
            free = subtractBusy(free, 0, cursor);

            int idx = 0;
            for (int[] slot : free) {
                int c = Math.max(slot[0], cursor);
                int slotEnd = slot[1];
                while (idx < following.size() && c + 1 <= slotEnd) {
                    Session s = following.get(idx);
                    int sd = Math.max(1, s.durationMin);
                    if (c + sd > slotEnd) break;
                    // لا تتداخل مع جلسات مثبتة/مكتملة متبقية
                    boolean hitFixed = false;
                    for (Session o : sessions) {
                        if (o == null || !day.equals(o.day)) continue;
                        int os = o.startMin, oe = o.endMin;
                        if (oe <= os) oe += 24 * 60;
                        if (c < oe && c + sd > os) { hitFixed = true; break; }
                    }
                    if (hitFixed) {
                        c += Math.max(5, breakGap);
                        continue;
                    }
                    if (windowConflicts(dCal, c, c + sd)) {
                        c += Math.max(5, breakGap);
                        continue;
                    }
                    s.day = day;
                    s.startMin = c % (24 * 60);
                    s.endMin = (c + sd) % (24 * 60);
                    if (s.endMin == 0 && c + sd > 0) s.endMin = 24 * 60;
                    s.durationMin = sd;
                    s.missed = false;
                    sessions.add(s);
                    idx++;
                    c += sd + breakGap;
                }
            }
            // ما تبقى: أقرب يوم لاحق
            while (idx < following.size()) {
                Session s = following.get(idx++);
                placeSessionSoonest(s);
            }
        }

        save();
        return null;
    }

    private boolean hasPinnedSession(String taskId) {
        for (Session s : sessions) if (taskId.equals(s.taskId) && s.userPinned) return true;
        return false;
    }

    private boolean windowConflicts(Calendar day, int start, int end) {
        int[] ws = wakeSleepFor(day);
        int wake = studyStartMin(day);
        int sleep = ws[1];
        if (sleep <= wake) sleep += 24 * 60;
        int a = start;
        int b = end;
        if (b <= a) b += 24 * 60;
        if (a < wake || b > sleep) return true;
        // التزام = مشغول بالكامل — أي تداخل مرفوض
        if (overlapsCommitment(day, a, b)) return true;
        String dayStr = DAY.format(day.getTime());
        for (Session s : sessions) {
            if (!dayStr.equals(s.day)) continue;
            int ss = s.startMin;
            int se = s.endMin;
            if (se <= ss) se += 24 * 60;
            if (a < se && b > ss) return true;
        }
        return false;
    }

    private List<String> suggestWindows(int needMin, int limit) {
        List<String> out = new ArrayList<>();
        Calendar now = nowLocal();
        Calendar day = (Calendar) now.clone();
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        String today = todayStr();
        int nowM = nowMinOfDay();
        for (int i = 0; i < 5 && out.size() < limit; i++) {
            String dayStr = DAY.format(day.getTime());
            List<int[]> free = freeSlotsForDay(day, dayStr);
            if (dayStr.equals(today)) free = clipPastForToday(free, nowM);
            for (int[] sl : free) {
                if (sl[1] - sl[0] >= needMin) {
                    int st = sl[0];
                    int en = st + needMin;
                    out.add(dayStr + " " + minToTime(st) + " – " + minToTime(en));
                    if (out.size() >= limit) return out;
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    private int placePinnedTask(Task t, Map<String, Integer> remaining, Map<String, Integer> scheduledCount) {
        lastConflicts.clear();
        int rem = remaining.getOrDefault(t.id, 0);
        if (rem < 1) return 0;
        if (t.pinDay == null || t.pinDay.isEmpty()) {
            lastConflicts.add(t.name + ": مفيش تاريخ محدد لوقت البداية.");
            return 0;
        }
        Calendar day = dayCal(t.pinDay);
        int cursor = Math.max(0, t.pinStartMin);
        int sm = Math.max(10, settings.sessionMin);
        int breakGap = Math.max(0, settings.breakMin);
        int already = scheduledCount.getOrDefault(t.id, 0);
        int totalParts = Math.max(1, (int) Math.ceil(t.durationMin / (double) sm));
        int placed = 0;

        Calendar pinStart = (Calendar) day.clone();
        pinStart.set(Calendar.HOUR_OF_DAY, cursor / 60);
        pinStart.set(Calendar.MINUTE, cursor % 60);
        pinStart.set(Calendar.SECOND, 0);
        pinStart.set(Calendar.MILLISECOND, 0);
        long nowMs = nowLocal().getTimeInMillis();
        if (pinStart.getTimeInMillis() < nowMs - 30000) {
            lastConflicts.add(t.name + ": الوقت اللي اخترته (" + t.pinDay + " " + minToTime(cursor) + ") فات خلاص.");
            lastSuggestions.addAll(suggestWindows(Math.min(rem, sm), 5));
            return 0;
        }

        // كل جلسات المهمة تبدأ من الوقت المحدد: الأولى عنده، والباقي بعده متتالي
        int guard = 0;
        while (rem > 0 && guard++ < 200) {
            int piece = rem > sm ? sm : rem;
            int[] ws = wakeSleepFor(day);
            int wake = studyStartMin(day);
            int sleep = ws[1];
            if (sleep <= wake) sleep += 24 * 60;

            // لو خرجنا عن نافذة اليوم، انتقل لليوم التالي من الاستيقاظ
            if (cursor + piece > sleep || isRestDay(day)) {
                day.add(Calendar.DAY_OF_YEAR, 1);
                // تخطّي أيام الراحة
                int skip = 0;
                while (isRestDay(day) && skip++ < 14) day.add(Calendar.DAY_OF_YEAR, 1);
                int[] ws2 = wakeSleepFor(day);
                cursor = studyStartMin(day);
                continue;
            }
            if (cursor < wake) cursor = wake;

            int end = cursor + piece;
            if (windowConflicts(day, cursor, end)) {
                // حرّك بعد التعارض قليلاً أو لليوم التالي
                cursor = end;
                if (cursor + 10 > sleep) {
                    day.add(Calendar.DAY_OF_YEAR, 1);
                    int skip = 0;
                    while (isRestDay(day) && skip++ < 14) day.add(Calendar.DAY_OF_YEAR, 1);
                    int[] ws2 = wakeSleepFor(day);
                    cursor = studyStartMin(day);
                }
                // لو أول جلسة متعارضة عند الوقت المثبّت — سجّل تعارض
                if (placed == 0) {
                    lastConflicts.add(t.name + ": من " + minToTime(Math.max(0, t.pinStartMin))
                            + " مش فاضي بالكامل. هحاول أكمل بعده/أيام تانية.");
                }
                continue;
            }

            Session s = new Session();
            s.taskId = t.id;
            s.day = DAY.format(day.getTime());
            s.startMin = cursor % (24 * 60);
            s.endMin = end % (24 * 60);
            if (s.endMin == 0 && end > 0) s.endMin = 24 * 60;
            s.durationMin = piece;
            s.taskName = t.name;
            s.subject = t.subject;
            s.sessionIndex = already + placed + 1;
            s.sessionTotal = Math.max(totalParts, already + placed + 1);
            s.priority = t.priority;
            s.backlog = t.backlog;
            s.userPinned = (placed == 0); // أول جلسة مثبتة عند اختيار المستخدم
            sessions.add(s);
            placed++;
            rem -= piece;
            cursor = end + (rem > 0 ? breakGap : 0);
        }

        remaining.put(t.id, Math.max(0, rem));
        t.remainingMin = Math.max(0, rem);
        scheduledCount.put(t.id, already + placed);
        return placed;
    }

    /**
     * Scheduler v10 — منطق جديد بسيط وحتمي.
     * Priority HARD → Exam → Deadline → Lecture# داخل المادة → توزيع متوازن على الأيام.
     */
    public String buildSchedule(int mode, int daysCount, String untilDay) {
        Calendar now = nowLocal();
        markMissedAndProtect(now.getTimeInMillis());
        rollPastIncompleteSessions();
        lastConflicts.clear();
        lastSuggestions.clear();
        lastBrain = new PlanningBrain(this);
        lastBrain.analyze();

        // امسح الجلسات القابلة للتحريك فقط
        sessions.removeIf(s -> !(s.done || s.userPinned));
        // لا تُبقى أي جلسة غير مكتملة متداخلة مع التزام (حتى لو كانت userPinned)
        sessions.removeIf(s -> s != null && !s.done && sessionOverlapsCommitment(s));

        Map<String, Integer> scheduledMin = new HashMap<>();
        Map<String, Integer> scheduledCount = new HashMap<>();
        for (Session s : sessions) {
            scheduledMin.put(s.taskId, scheduledMin.getOrDefault(s.taskId, 0) + Math.max(0, s.durationMin));
            scheduledCount.put(s.taskId, scheduledCount.getOrDefault(s.taskId, 0) + 1);
        }

        Map<String, Integer> remaining = new HashMap<>();
        for (Task t : tasks) {
            if (t.done) {
                remaining.put(t.id, 0);
                continue;
            }
            int rem = Math.max(0, t.durationMin - scheduledMin.getOrDefault(t.id, 0));
            remaining.put(t.id, rem);
            t.remainingMin = rem;
        }

        int pinnedPlaced = 0;
        for (Task t : tasks) {
            if (!t.pinStart || t.done) continue;
            if (hasPinnedSession(t.id)) continue;
            if (remaining.getOrDefault(t.id, 0) < 10) continue;
            pinnedPlaced += placePinnedTask(t, remaining, scheduledCount);
        }

        int needTotal = 0;
        for (int v : remaining.values()) needTotal += Math.max(0, v);
        if (needTotal <= 0 && pinnedPlaced == 0) {
            save();
            return "الجدول زي ما هو. مفيش مهام جديدة تحتاج جلسات.";
        }

        // Exam maps
        Map<String, Exam> taskExam = buildTaskExamMap();
        Map<String, String> taskFinish = new HashMap<>();
        Map<String, Integer> taskExamDays = new HashMap<>();
        String today = todayStr();
        for (Map.Entry<String, Exam> en : taskExam.entrySet()) {
            String fin = examFinishDate(en.getValue());
            if (fin != null) taskFinish.put(en.getKey(), fin);
            int d = daysUntil(en.getValue().day);
            if (d >= 0) taskExamDays.put(en.getKey(), d);
        }

        // Work days
        Calendar startCal = nowLocal();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        Calendar endCal = (Calendar) startCal.clone();
        if (mode == DIST_WEEK) {
            settings.planDays = 7;
            endCal.add(Calendar.DAY_OF_YEAR, 7);
        } else if (mode == DIST_DAYS) {
            int n = Math.max(1, Math.min(90, daysCount));
            settings.planDays = n;
            endCal.add(Calendar.DAY_OF_YEAR, n);
        } else if (mode == DIST_DEADLINE) {
            settings.planDays = 14;
            endCal.add(Calendar.DAY_OF_YEAR, 14);
            if (untilDay != null && !untilDay.isEmpty()) {
                try {
                    Calendar u = Calendar.getInstance();
                    u.setTime(DAY.parse(untilDay));
                    if (u.after(startCal)) endCal = u;
                } catch (Exception ignored) {}
            }
        } else {
            endCal.add(Calendar.DAY_OF_YEAR, Math.max(1, settings.planDays > 0 ? settings.planDays : 7));
        }

        List<String> workDays = new ArrayList<>();
        Calendar tmp = (Calendar) startCal.clone();
        while (tmp.before(endCal)) {
            String ds = DAY.format(tmp.getTime());
            if (!isRestDay(tmp) && !isNoLectureDay(ds)) workDays.add(ds);
            tmp.add(Calendar.DAY_OF_YEAR, 1);
        }
        if (workDays.isEmpty()) {
            save();
            return "مفيش أيام شغل متاحة في الفترة.";
        }

        int nowMin = nowMinOfDay();
        Map<String, Integer> dayCap = new LinkedHashMap<>();
        Map<String, Integer> dayUsed = new LinkedHashMap<>();
        // day -> taskId -> minutes
        Map<String, LinkedHashMap<String, Integer>> quota = new LinkedHashMap<>();
        for (String ds : workDays) {
            Calendar d = Calendar.getInstance();
            try { d.setTime(DAY.parse(ds)); } catch (Exception e) { continue; }
            List<int[]> free = freeSlotsForDay(d, ds);
            if (ds.equals(today)) free = clipPastForToday(free, nowMin);
            int cap = 0;
            for (int[] sl : free) cap += Math.max(0, sl[1] - sl[0]);
            // ساعات المذاكرة: سقف اختياري ناعم — لا يُطبَّق إن كانت الميزة معطّلة
            if (settings.studyTimeEnabled && settings.studyMaxMin > 0 && cap > settings.studyMaxMin) {
                cap = settings.studyMaxMin;
            }
            dayCap.put(ds, cap);
            dayUsed.put(ds, 0);
            quota.put(ds, new LinkedHashMap<>());
        }

        // —— SORT: Priority HARD فقط أولًا ——
        List<Task> ordered = new ArrayList<>();
        for (Task t : tasks) {
            if (!t.done && remaining.getOrDefault(t.id, 0) > 0) ordered.add(t);
        }
        ordered.sort((a, b) -> compareTasksForSchedule(a, b, taskExamDays));

        int sm = Math.max(10, settings.sessionMin);
        int breakGap = Math.max(0, settings.breakMin);

        // —— ALLOCATE ——
        // mode 0: توازي — كل يوم جزء من كل محاضرة
        // mode 1: متتابع متوازن — نخلّص محاضرة قبل اللي بعدها، والحمل اليومي ≈ الإجمالي÷الأيام
        // mode 2: محاضرة واحدة في اليوم (يوم كامل لمحاضرة واحدة حتى تخلص)
        int distMode = settings.lectureDistMode;
        if (distMode < 0 || distMode > 2) distMode = 0;

        if (distMode == 1) {
            // «جلسات المحاضرة ورا بعض»
            // إجمالي المدة ÷ عدد الأيام = مدة مستهدفة لكل يوم
            // ثم ملء بالتتابع: A ثم B ثم C (ممنوع A→B→A)
            int totalRem = 0;
            for (Task t : ordered) totalRem += Math.max(0, remaining.getOrDefault(t.id, 0));
            int nDays = Math.max(1, workDays.size());
            int targetDaily = Math.max(1, totalRem / nDays); // قسمة بسيطة
            int remainder = totalRem - (targetDaily * nDays); // باقي القسمة يوزّع على أول أيام

            int[] remArr = new int[ordered.size()];
            for (int i = 0; i < ordered.size(); i++) {
                remArr[i] = Math.max(0, remaining.getOrDefault(ordered.get(i).id, 0));
            }
            int ti = 0;
            int dayIndex = 0;
            for (String ds : workDays) {
                int room = dayCap.getOrDefault(ds, 0);
                if (room < 1) { dayIndex++; continue; }
                int used = dayUsed.getOrDefault(ds, 0);
                int budget = targetDaily + (dayIndex < remainder ? 1 : 0);
                budget = Math.min(budget, room - used);
                dayIndex++;
                if (budget < 1) continue;
                while (budget > 0 && ti < ordered.size()) {
                    while (ti < ordered.size() && remArr[ti] < 1) ti++;
                    if (ti >= ordered.size()) break;
                    Task t = ordered.get(ti);
                    if (!lectureAllowedOnDay(t, ds, taskFinish, remaining)) break;
                    int freeNow = room - used;
                    if (freeNow < 1) break;
                    int put = Math.min(budget, Math.min(remArr[ti], freeNow));
                    if (put < 1) break;
                    quota.get(ds).put(t.id, quota.get(ds).getOrDefault(t.id, 0) + put);
                    used += put;
                    dayUsed.put(ds, used);
                    remArr[ti] -= put;
                    budget -= put;
                    if (remArr[ti] < 1) ti++;
                }
            }
            // متبقي بسبب قيود يوم معيّن: أكمل بالتتابع الزمني
            for (String ds : workDays) {
                int room = dayCap.getOrDefault(ds, 0) - dayUsed.getOrDefault(ds, 0);
                if (room < 1) continue;
                int used = dayUsed.getOrDefault(ds, 0);
                while (room > 0 && ti < ordered.size()) {
                    while (ti < ordered.size() && remArr[ti] < 1) ti++;
                    if (ti >= ordered.size()) break;
                    Task t = ordered.get(ti);
                    if (!lectureAllowedOnDay(t, ds, taskFinish, remaining)) break;
                    int put = Math.min(remArr[ti], room);
                    if (put < 1) break;
                    quota.get(ds).put(t.id, quota.get(ds).getOrDefault(t.id, 0) + put);
                    used += put;
                    room -= put;
                    dayUsed.put(ds, used);
                    remArr[ti] -= put;
                    if (remArr[ti] < 1) ti++;
                }
            }
        } else {
            for (Task t : ordered) {
                int rem = remaining.getOrDefault(t.id, 0);
                if (rem < 1) continue;

                String finish = taskFinish.get(t.id);
                Subject subj = findSubject(t.subject);
                boolean useFixed = subj != null && !subj.fixedDows.isEmpty()
                        && t.isLecture() && !t.backlog && !subjectHasOpenBacklog(t.subject, remaining);

                List<String> valid = new ArrayList<>();
                for (String ds : workDays) {
                    if (dayCap.getOrDefault(ds, 0) < 1) continue;
                    if (finish != null && ds.compareTo(finish) > 0) continue;
                    if (useFixed) {
                        try {
                            Calendar c = Calendar.getInstance();
                            c.setTime(DAY.parse(ds));
                            if (!subj.fixedDows.contains(c.get(Calendar.DAY_OF_WEEK))) continue;
                        } catch (Exception ignored) { continue; }
                    }
                    valid.add(ds);
                }
                if (valid.isEmpty()) continue;

                int left = rem;
                if (distMode == 2) {
                    // محاضرة واحدة في اليوم: يوم فاضي بالكامل لمحاضرة واحدة حتى تخلص
                    for (String ds : valid) {
                        if (left < 1) break;
                        if (dayUsed.getOrDefault(ds, 0) > 0) continue;
                        int room = dayCap.getOrDefault(ds, 0);
                        if (room < 1) continue;
                        int put = Math.min(left, room);
                        quota.get(ds).put(t.id, put);
                        dayUsed.put(ds, put);
                        left -= put;
                    }
                } else {
                    // mode 0 توازي
                    int total = rem;
                    int n = valid.size();
                    int base = total / n;
                    int extra = total % n;
                    for (int i = 0; i < n && left > 0; i++) {
                        int want = base + (i < extra ? 1 : 0);
                        if (want < 1) continue;
                        String ds = valid.get(i);
                        int room = dayCap.getOrDefault(ds, 0) - dayUsed.getOrDefault(ds, 0);
                        if (room < 1) continue;
                        int put = Math.min(want, Math.min(room, left));
                        if (put < 1) continue;
                        quota.get(ds).put(t.id, quota.get(ds).getOrDefault(t.id, 0) + put);
                        dayUsed.put(ds, dayUsed.getOrDefault(ds, 0) + put);
                        left -= put;
                    }
                    for (int pass = 0; pass < 2 && left > 0; pass++) {
                        for (String ds : valid) {
                            if (left < 1) break;
                            int room = dayCap.getOrDefault(ds, 0) - dayUsed.getOrDefault(ds, 0);
                            if (room < 1) continue;
                            int put = Math.min(left, room);
                            quota.get(ds).put(t.id, quota.get(ds).getOrDefault(t.id, 0) + put);
                            dayUsed.put(ds, dayUsed.getOrDefault(ds, 0) + put);
                            left -= put;
                        }
                    }
                }
            }
        }

        // —— PLACE sessions في free slots حسب ترتيب ordered ——
        lastPlanningDiag = new PlanningDiag();
        int placedSessions = 0;
        for (String dayStr : workDays) {
            LinkedHashMap<String, Integer> q = quota.get(dayStr);
            if (q == null || q.isEmpty()) continue;

            Calendar d = Calendar.getInstance();
            try { d.setTime(DAY.parse(dayStr)); } catch (Exception e) { continue; }
            // مراحل الـfree للتشخيص: بعد Commitments → بعد الجلسات الموجودة → بعد قص الماضي
            List<int[]> freeRaw = freeSlots(d); // بعد commitments/prayers/exams فقط
            List<int[]> freeAfterSess = freeSlotsForDay(d, dayStr); // + occupied sessions
            List<int[]> free = freeAfterSess;
            if (dayStr.equals(today)) free = clipPastForToday(free, nowMin);
            if (dayStr.equals(today) && lastPlanningDiag != null) {
                PlanningDiag dg = lastPlanningDiag;
                dg.line("— PLACE اليوم " + dayStr + " —");
                dg.line("nowMin=" + nowMin + " (" + minToTime(nowMin) + ")");
                dg.line("free بعد Commitments/prayers:");
                for (int i = 0; i < freeRaw.size(); i++) {
                    int[] sl = freeRaw.get(i);
                    dg.line("  raw[" + i + "] " + minToTime(sl[0] % (24 * 60)) + "–" + minToTime(sl[1] % (24 * 60))
                            + " (" + Math.max(0, sl[1] - sl[0]) + "د)");
                }
                dg.line("free بعد sessions موجودة:");
                for (int i = 0; i < freeAfterSess.size(); i++) {
                    int[] sl = freeAfterSess.get(i);
                    dg.line("  sess[" + i + "] " + minToTime(sl[0] % (24 * 60)) + "–" + minToTime(sl[1] % (24 * 60))
                            + " (" + Math.max(0, sl[1] - sl[0]) + "د)");
                }
                dg.line("free بعد قص الماضي (القابل للاستخدام):");
                for (int i = 0; i < free.size(); i++) {
                    int[] sl = free.get(i);
                    dg.line("  usable[" + i + "] " + minToTime(sl[0] % (24 * 60)) + "–" + minToTime(sl[1] % (24 * 60))
                            + " (" + Math.max(0, sl[1] - sl[0]) + "د)");
                }
            }
            if (free.isEmpty()) {
                if (dayStr.equals(today) && lastPlanningDiag != null)
                    lastPlanningDiag.line("  ⚠ لا يوجد free قابل للاستخدام بعد القص — تخطّي اليوم");
                continue;
            }

            List<String> order = new ArrayList<>();
            for (Task t : ordered) {
                if (q.getOrDefault(t.id, 0) > 0) order.add(t.id);
            }
            for (String id : q.keySet()) {
                if (!order.contains(id) && q.getOrDefault(id, 0) > 0) order.add(id);
            }

            int slotIdx = 0;
            int cursor = free.get(0)[0];
            int slotEnd = free.get(0)[1];
            if (dayStr.equals(today) && lastPlanningDiag != null) {
                lastPlanningDiag.line("أول cursor فعلي=" + minToTime(cursor % (24 * 60))
                        + " slotEnd=" + minToTime(slotEnd % (24 * 60)) + " slotIdx=0");
            }

            // حمل خفيف: تأخير بسيط + فجوات صغيرة من الفراغ الزائد
            int needDay = 0;
            int nTasks = 0;
            int nPieces = 0;
            for (String id : order) {
                int v = q.getOrDefault(id, 0);
                if (v > 0) {
                    needDay += v;
                    nTasks++;
                    nPieces += Math.max(1, (v + sm - 1) / sm);
                }
            }
            int freeMin = 0;
            for (int[] sl : free) freeMin += Math.max(0, sl[1] - sl[0]);
            // slack بعد خصم الـBreak بين كل القطع
            int slack = Math.max(0, freeMin - needDay - Math.max(0, nPieces - 1) * breakGap);
            // فجوة ناعمة بين الجلسات فقط — ممنوع أي pad صباحي ثابت (+30) فوق Morning Prep
            int interGap = (nPieces >= 2 && slack >= 40) ? Math.min(45, slack / nPieces) : 0;
            if (lastPlanningDiag != null) lastPlanningDiag.interGapUsed = Math.max(lastPlanningDiag.interGapUsed, interGap);
            // cursor يبدأ من أول free slot = studyStartMin (استيقاظ + تجهيز) بدون إضافة إضافية

            for (String taskId : order) {
                int need = q.getOrDefault(taskId, 0);
                if (need <= 0) continue;
                Task pick = findTask(taskId);
                if (pick == null) continue;

                while (need > 0 && slotIdx < free.size()) {
                    if (cursor >= slotEnd) {
                        int prevIdx = slotIdx;
                        slotIdx++;
                        if (slotIdx >= free.size()) {
                            if (dayStr.equals(today) && lastPlanningDiag != null)
                                lastPlanningDiag.line("  انتقال: slot " + prevIdx + " انتهى → لا slots أخرى");
                            break;
                        }
                        cursor = free.get(slotIdx)[0];
                        slotEnd = free.get(slotIdx)[1];
                        if (dayStr.equals(today) && lastPlanningDiag != null)
                            lastPlanningDiag.line("  انتقال: slot " + prevIdx + " → " + slotIdx
                                    + " cursor=" + minToTime(cursor % (24 * 60))
                                    + " (سبب: cursor بلغ نهاية الـslot السابق)");
                    }
                    int avail = slotEnd - cursor;
                    if (avail < 1) {
                        if (dayStr.equals(today) && lastPlanningDiag != null)
                            lastPlanningDiag.line("  تخطّي slot " + slotIdx + ": avail=" + avail + " < 1");
                        slotIdx++;
                        continue;
                    }

                    // استخدم المتاح في الـslot الحالي دائمًا — لا تتخطَّ Free قبل Commitment
                    // لمجرد أن باقي الـslot أصغر من sessionMin كامل.
                    int piece;
                    if (need >= sm) piece = Math.min(sm, avail);
                    else piece = Math.min(need, avail);
                    if (piece < 1) {
                        // لا يوجد متاح هنا؛ انتقل للـslot التالي فقط
                        if (dayStr.equals(today) && lastPlanningDiag != null)
                            lastPlanningDiag.line("  تخطّي slot " + slotIdx + ": piece<1 avail=" + avail
                                    + " need=" + need + " sm=" + sm);
                        slotIdx++;
                        if (slotIdx >= free.size()) break;
                        cursor = free.get(slotIdx)[0];
                        slotEnd = free.get(slotIdx)[1];
                        continue;
                    }

                    int endM = cursor + piece;
                    Session s = new Session();
                    s.taskId = taskId;
                    s.day = dayStr;
                    s.startMin = cursor % (24 * 60);
                    s.endMin = endM % (24 * 60);
                    if (s.endMin == 0 && endM > 0) s.endMin = 24 * 60;
                    s.durationMin = piece;
                    s.taskName = pick.name;
                    s.subject = pick.subject;
                    int already = scheduledCount.getOrDefault(taskId, 0);
                    s.sessionIndex = already + 1;
                    s.sessionTotal = Math.max(1, (int) Math.ceil(pick.durationMin / (double) sm));
                    s.priority = pick.priority;
                    s.backlog = pick.backlog;
                    sessions.add(s);
                    placedSessions++;
                    if (dayStr.equals(today) && lastPlanningDiag != null && placedSessions == 1) {
                        lastPlanningDiag.line("أول Session فعلي: " + (pick.name == null ? "?" : pick.name)
                                + " " + minToTime(s.startMin) + "–" + minToTime(s.endMin)
                                + " (slotIdx=" + slotIdx + ")");
                    }
                    scheduledCount.put(taskId, already + 1);
                    need -= piece;
                    q.put(taskId, need);
                    cursor = endM;

                    boolean moreSame = need > 0;
                    boolean moreOther = false;
                    if (!moreSame) {
                        boolean after = false;
                        for (String oid : order) {
                            if (oid.equals(taskId)) { after = true; continue; }
                            if (after && q.getOrDefault(oid, 0) > 0) { moreOther = true; break; }
                        }
                    }
                    // بعد الجلسة: Break طبيعي. Gap إضافي مسموح (حتى لنفس المحاضرة).
                    // «ورا بعض» = ترتيب المحاضرة محفوظ، وليس إلصاق زمني بدون فجوات.
                    if (moreSame || moreOther) {
                        int afterS = cursor;
                        cursor += breakGap;
                        if (interGap > 0) cursor += interGap;
                        if (settings.prayersEnabled && settings.hasLocation) {
                            int[] prayers = prayerStartMinutes(d);
                            int pd = Math.max(5, Math.min(120, settings.prayerDurationMin));
                            for (int ps : prayers) {
                                if (ps < 0) continue;
                                int pe = ps + pd;
                                if (ps >= afterS && ps <= afterS + breakGap + interGap + 30) {
                                    if (pe > cursor) cursor = pe;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // تحديث remaining النهائي
        Map<String, Integer> placedMin = new HashMap<>();
        for (Session s : sessions) {
            if (s.done || s.userPinned) continue;
            placedMin.put(s.taskId, placedMin.getOrDefault(s.taskId, 0) + Math.max(0, s.durationMin));
        }
        for (Task t : tasks) {
            if (t.done) continue;
            int pin = 0;
            for (Session s : sessions) {
                if (t.id.equals(s.taskId) && (s.done || s.userPinned)) pin += Math.max(0, s.durationMin);
            }
            t.remainingMin = Math.max(0, t.durationMin - pin - placedMin.getOrDefault(t.id, 0));
        }

        // —— تشخيص آخر تخطيط (للعرض فقط) ——
        try {
            PlanningDiag diag = lastPlanningDiag != null ? lastPlanningDiag : new PlanningDiag();
            int prevGap = diag.interGapUsed;
            // احتفظ بأسطر PLACE التفصيلية إن وُجدت
            java.util.List<String> placeLines = new ArrayList<>(diag.lines);
            diag.lines.clear();
            diag.interGapUsed = prevGap;
            diag.runAt = todayStr() + " " + minToTime(nowMinOfDay());
            Calendar todayCal = nowLocal();
            int[] wsDiag = wakeSleepFor(todayCal);
            diag.wakeMin = studyStartMin(todayCal);
            diag.sleepMin = ((wsDiag[1] % (24 * 60)) + (24 * 60)) % (24 * 60);
            diag.morningPrep = normalizeMorningPrep(settings.morningPrepMin);
            diag.sessionMin = Math.max(10, settings.sessionMin);
            diag.breakMin = Math.max(0, settings.breakMin);
            diag.interGapUsed = prevGap;
            diag.planDays = settings.planDays;
            diag.line("— الإعدادات —");
            diag.line("nowMin=" + nowMin + " (" + minToTime(nowMin) + ")");
            diag.line("distMode=" + settings.lectureDistMode);
            diag.line("— Commitments —");
            if (commitments.isEmpty()) diag.line("(لا يوجد)");
            for (Commitment c : commitments) {
                String days = c.daysOfWeek != null && !c.daysOfWeek.isEmpty()
                        ? c.daysOfWeek.toString() : ("dow=" + c.dayOfWeek);
                diag.line((c.title == null || c.title.isEmpty() ? "التزام" : c.title)
                        + " " + minToTime(c.startMin) + "–" + minToTime(c.endMin) + " " + days);
            }
            // أعد أسطر PLACE (مراحل free + cursor + انتقالات)
            for (String pl : placeLines) diag.line(pl);
            diag.line("— Free slots / Sessions لكل يوم تخطيط —");
            for (String ds : workDays) {
                Calendar dc = Calendar.getInstance();
                try { dc.setTime(DAY.parse(ds)); } catch (Exception ex) { continue; }
                List<int[]> flRaw = freeSlots(dc);
                List<int[]> flSess = freeSlotsForDay(dc, ds);
                List<int[]> fl = flSess;
                if (ds.equals(today)) fl = clipPastForToday(fl, nowMin);
                int cap = 0;
                for (int[] sl : fl) cap += Math.max(0, sl[1] - sl[0]);
                diag.line("يوم " + ds + " سعة=" + cap + "د");
                if (ds.equals(today)) {
                    diag.line("  [raw بعد Commitments]");
                    for (int i = 0; i < flRaw.size(); i++) {
                        int[] sl = flRaw.get(i);
                        diag.line("    raw[" + i + "] " + minToTime(sl[0] % (24 * 60))
                                + "–" + minToTime(sl[1] % (24 * 60))
                                + " (" + Math.max(0, sl[1] - sl[0]) + "د)");
                    }
                    diag.line("  [بعد sessions]");
                    for (int i = 0; i < flSess.size(); i++) {
                        int[] sl = flSess.get(i);
                        diag.line("    sess[" + i + "] " + minToTime(sl[0] % (24 * 60))
                                + "–" + minToTime(sl[1] % (24 * 60))
                                + " (" + Math.max(0, sl[1] - sl[0]) + "د)");
                    }
                    diag.line("  [بعد قص الماضي = usable]");
                }
                for (int i = 0; i < fl.size(); i++) {
                    int[] sl = fl.get(i);
                    diag.line("  free[" + i + "] " + minToTime(sl[0] % (24 * 60))
                            + "–" + minToTime(sl[1] % (24 * 60))
                            + " (" + Math.max(0, sl[1] - sl[0]) + "د)");
                }
                int first = -1;
                for (Session s : sessions) {
                    if (s == null || s.day == null || !s.day.equals(ds)) continue;
                    if (s.userPinned) continue;
                    if (first < 0 || s.startMin < first) first = s.startMin;
                    diag.line("  session " + (s.taskName == null ? "?" : s.taskName)
                            + " " + minToTime(s.startMin) + "–" + minToTime(s.endMin)
                            + " (" + s.durationMin + "د)");
                }
                if (first >= 0) diag.line("  أول جلسة: " + minToTime(first));
                // تنبيه إن وُجد free صباحي ولم تُستخدم أي جلسة فيه
                if (!fl.isEmpty() && first >= 0) {
                    int[] firstFree = fl.get(0);
                    if (first >= firstFree[1]) {
                        diag.line("  ⚠ أول جلسة بعد نهاية أول free slot — راقب القفز بين الـslots");
                    }
                }
            }
            lastPlanningDiag = diag;
        } catch (Exception ignored) {}

        save();
        placedSessions += pinnedPlaced;

        StringBuilder warn = new StringBuilder();
        for (Task t : tasks) {
            if (t.done || t.remainingMin <= 0) continue;
            String fin = taskFinish.get(t.id);
            if (fin != null) {
                warn.append("\n⚠ ").append(t.name).append(": متبقي ").append(t.remainingMin)
                        .append(" د قبل ").append(fin);
            }
        }

        if (placedSessions == 0) {
            return "مفيش وقت فاضي كفاية للجلسات." + insightSuffix();
        }
        int left = 0;
        for (Task t : tasks) if (!t.done) left += Math.max(0, t.remainingMin);
        if (left > 0) {
            return "اتضافت " + placedSessions + " جلسة. لسه " + left + " د من غير مكان." + warn + insightSuffix();
        }
        return "اتضافت " + placedSessions + " جلسة." + warn + insightSuffix();
    }

    private Map<String, Exam> buildTaskExamMap() {
        Map<String, Exam> map = new HashMap<>();
        if (exams == null) return map;
        for (Exam e : exams) {
            if (e == null || e.done) continue;
            if (e.day == null || e.day.isEmpty()) continue;
            if (daysUntil(e.day) < 0) continue;

            for (Task t : tasks) {
                if (t.done || !t.isStudy()) continue;
                if (e.id != null && e.id.equals(t.linkedExamId)) putCloserExam(map, t.id, e);
            }

            if (e.lastLectureTaskId != null && !e.lastLectureTaskId.isEmpty()) {
                Task last = findTask(e.lastLectureTaskId);
                if (last == null || !last.isLecture()) continue;
                String sub = last.subject == null ? "" : last.subject;
                int maxNum = extractLectureNumFromName(last.name);
                for (Task t : tasks) {
                    if (t.done || !t.isLecture()) continue;
                    if (t.subject == null || !t.subject.equals(sub)) continue;
                    int n = extractLectureNumFromName(t.name);
                    if (maxNum >= 0 && n >= 0) {
                        if (n <= maxNum) putCloserExam(map, t.id, e);
                    } else if (t.id.equals(last.id)) {
                        putCloserExam(map, t.id, e);
                    }
                }
            } else if (e.subject != null && !e.subject.trim().isEmpty()) {
                String c = PlanningBrain.canon(e.subject);
                for (Task t : tasks) {
                    if (t.done || !t.isLecture()) continue;
                    if (t.subject == null) continue;
                    if (PlanningBrain.canon(t.subject).equals(c) || t.subject.equals(e.subject)) {
                        putCloserExam(map, t.id, e);
                    }
                }
            }
        }
        return map;
    }

    private void putCloserExam(Map<String, Exam> map, String taskId, Exam e) {
        Exam cur = map.get(taskId);
        if (cur == null) { map.put(taskId, e); return; }
        int dNew = daysUntil(e.day);
        int dOld = daysUntil(cur.day);
        if (dNew >= 0 && (dOld < 0 || dNew < dOld)) map.put(taskId, e);
    }

    private String examFinishDate(Exam e) {
        if (e == null || e.day == null || e.day.isEmpty()) return null;
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(DAY.parse(e.day));
            cal.add(Calendar.DAY_OF_YEAR, -2);
            String finish = DAY.format(cal.getTime());
            String today = todayStr();
            if (finish.compareTo(today) < 0) finish = today;
            return finish;
        } catch (Exception ex) {
            return null;
        }
    }

    private int daysUntil(String day) {
        if (day == null || day.isEmpty()) return -1;
        try {
            long a = DAY.parse(todayStr()).getTime();
            long b = DAY.parse(day).getTime();
            int days = (int) ((b - a) / (24L * 60 * 60 * 1000));
            return days < 0 ? -1 : days;
        } catch (Exception e) {
            return -1;
        }
    }

    /** استخراج رقم المحاضرة من الاسم (Lecture 2 / محاضرة 3 / Chapter 1) */
    private static int extractLectureNumFromName(String name) {
        if (name == null || name.trim().isEmpty()) return -1;
        String n = name.trim();
        // أنماط شائعة
        java.util.regex.Pattern[] patterns = new java.util.regex.Pattern[] {
                java.util.regex.Pattern.compile("(?i)(?:lecture|lesson|chapter|ch\\.?|lec\\.?|ل\\.?|محاضرة|درس|فصل)\\s*[:\\-]?\\s*(\\d{1,3})"),
                java.util.regex.Pattern.compile("(?i)(?:^|\\s)(\\d{1,3})\\s*(?:$|\\s|-)"),
                java.util.regex.Pattern.compile("(\\d{1,3})")
        };
        for (java.util.regex.Pattern p : patterns) {
            java.util.regex.Matcher m = p.matcher(n);
            if (m.find()) {
                try {
                    int v = Integer.parseInt(m.group(1));
                    if (v >= 0 && v < 10000) return v;
                } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    /** أوقات فاضية بعد النوم والالتزامات وجلسات اليوم الموجودة بالفعل */
    private List<int[]> freeSlotsForDay(Calendar day, String dayStr) {
        return freeSlotsForDay(day, dayStr, null);
    }

    /** excludeSessionId: تجاهل جلسة معيّنة (مفيد عند نقل/تعديل نفس الجلسة). */
    private List<int[]> freeSlotsForDay(Calendar day, String dayStr, String excludeSessionId) {
        List<int[]> free = freeSlots(day);
        List<int[]> occupied = new ArrayList<>();
        int pad = Math.max(0, settings.breakMin);
        for (Session s : sessions) {
            if (!dayStr.equals(s.day)) continue;
            if (excludeSessionId != null && excludeSessionId.equals(s.id)) continue;
            int a = s.startMin;
            int b = s.endMin;
            if (b <= a) b += 24 * 60;
            // الجلسات المكتملة تحجز وقتها الفعلي فقط (بدون بريك بعدها) حتى يُستغل الوقت بعد الإنهاء المبكر
            int extra = s.done ? 0 : pad;
            occupied.add(new int[]{a, b + extra});
        }
        for (int[] occ : occupied) {
            free = subtractBusy(free, occ[0], occ[1]);
        }
        return free;
    }

    /** الدقائق المتاحة في نفس موضع الجلسة (بدون احتساب الجلسة نفسها). */
    public int availableSlotMinutesAt(String sessionId, String dayStr, int startMin) {
        Session self = null;
        for (Session x : sessions) if (sessionId != null && sessionId.equals(x.id)) { self = x; break; }
        Calendar day = dayCal(dayStr);
        List<int[]> free = freeSlotsForDay(day, dayStr, sessionId);
        if (dayStr != null && dayStr.equals(todayStr())) free = clipPastForToday(free, nowMinOfDay());
        int start = ((startMin % (24 * 60)) + 24 * 60) % (24 * 60);
        for (int[] sl : free) {
            if (start >= sl[0] && start < sl[1]) return Math.max(0, sl[1] - start);
            // لو البداية قبل السلوط بقليل والسلوط ي overlapping
            if (start < sl[0] && start + (self != null ? self.durationMin : 0) > sl[0]) {
                return Math.max(0, sl[1] - sl[0]);
            }
        }
        // fallback: أقصى سلوت في اليوم
        int best = 0;
        for (int[] sl : free) best = Math.max(best, sl[1] - sl[0]);
        return best;
    }

    private List<int[]> subtractBusy(List<int[]> free, int s, int e) {
        List<int[]> out = new ArrayList<>();
        for (int[] w : free) {
            if (e <= w[0] || s >= w[1]) out.add(w);
            else {
                if (w[0] < s) out.add(new int[]{w[0], s});
                if (e < w[1]) out.add(new int[]{e, w[1]});
            }
        }
        return out;
    }

    /**
     * قص الوقت الماضي لليوم الحالي فقط: كل free slot يصبح [max(start, nowMin), end]
     * إذا بقي end > start. لا يحذف الـslot بالكامل إلا إذا كان كله في الماضي.
     * يفصل بوضوح بين Busy(Commitment) والوقت الماضي.
     */
    private List<int[]> clipPastForToday(List<int[]> free, int nowMin) {
        List<int[]> out = new ArrayList<>();
        if (free == null) return out;
        for (int[] w : free) {
            if (w == null || w.length < 2) continue;
            int a = Math.max(w[0], nowMin);
            int b = w[1];
            if (b > a) out.add(new int[]{a, b});
        }
        return out;
    }

    /** حجم التراكم (دقائق متبقية) لمادة */
    private int subjectBacklogMin(String subject) {
        if (subject == null) return 0;
        int n = 0;
        for (Task x : tasks) {
            if (x.done) continue;
            if (subject.equals(x.subject) && x.backlog) n += Math.max(0, x.remainingMin > 0 ? x.remainingMin : x.durationMin);
        }
        return n;
    }

    private int subjectOpenCount(String subject) {
        if (subject == null) return 0;
        int n = 0;
        for (Task x : tasks) {
            if (!x.done && subject.equals(x.subject)) n++;
        }
        return n;
    }

    private int subjectScheduledMin(String subject) {
        if (subject == null) return 0;
        int n = 0;
        for (Session s : sessions) {
            if (subject.equals(s.subject)) n += Math.max(0, s.durationMin);
        }
        return n;
    }

    /**
     * إلحاح امتحان لمادة معيّنة فقط — يرفع planningScore لمحاضرات نفس المادة.
     * لا يفرض يوم/ساعة للجلسة؛ الموعد يظل من free slots والقيود الأخرى.
     * مطابقة المادة عبر PlanningBrain.canon (تطبيع الاسم).
     */

    /** أيام حتى أقرب امتحان قادم للمادة؛ -1 لو مفيش */
    private int daysToNearestExam(String subject) {
        if (subject == null || exams == null) return -1;
        String c = PlanningBrain.canon(subject);
        String today = todayStr();
        int best = Integer.MAX_VALUE;
        for (Exam e : exams) {
            if (e.done) continue;
            if (e.subject == null || e.subject.trim().isEmpty()) continue;
            String ec = PlanningBrain.canon(e.subject);
            if (!ec.equals(c) && !e.subject.equals(subject)) continue;
            if (e.day == null || e.day.isEmpty()) continue;
            try {
                long a = DAY.parse(today).getTime();
                long b = DAY.parse(e.day).getTime();
                int days = (int) ((b - a) / (24L * 60 * 60 * 1000));
                if (days < 0) continue;
                if (days < best) best = days;
            } catch (Exception ignored) {}
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /**
     * Effective Priority: لو امتحان خلال 7 أيام → HIGH (2).
     * وإلا تفضل الأولوية الأصلية. الحد الأعلى HIGH.
     */
    private int effectivePriority(Task t) {
        if (t == null) return 0;
        int base = Math.max(0, Math.min(2, t.priority));
        int d = daysToNearestExam(t.subject);
        if (d < 0) return base;
        if (d <= 7) return 2; // خلال أسبوع → HIGH
        return Math.max(base, 1); // أي امتحان أبعد → على الأقل MEDIUM
    }

    /**
     * تاريخ إنهاء مستهدف للمادة = أقرب امتحان − 2 يوم (حتى لو الامتحان بعيد).
     * شرط أساسي: كل محاضرات المادة تخلص قبل الموعد ده.
     */
    private String subjectTargetFinishDate(String subject) {
        if (subject == null || exams == null) return null;
        String c = PlanningBrain.canon(subject);
        String today = todayStr();
        String nearestDay = null;
        int best = Integer.MAX_VALUE;
        for (Exam e : exams) {
            if (e.done) continue;
            if (e.subject == null || e.subject.trim().isEmpty()) continue;
            String ec = PlanningBrain.canon(e.subject);
            if (!ec.equals(c) && !e.subject.equals(subject)) continue;
            if (e.day == null || e.day.isEmpty()) continue;
            try {
                long a = DAY.parse(today).getTime();
                long b = DAY.parse(e.day).getTime();
                int days = (int) ((b - a) / (24L * 60 * 60 * 1000));
                if (days < 0) continue;
                if (days < best) {
                    best = days;
                    nearestDay = e.day;
                }
            } catch (Exception ignored) {}
        }
        if (nearestDay == null) return null;
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(DAY.parse(nearestDay));
            cal.add(Calendar.DAY_OF_YEAR, -2);
            String finish = DAY.format(cal.getTime());
            // لو قبل النهاردة استخدم النهاردة كحد أدنى للتخطيط
            if (finish.compareTo(today) < 0) finish = today;
            return finish;
        } catch (Exception e) {
            return null;
        }
    }

    private double examUrgency(String subject) {
        if (subject == null || exams == null) return 0;
        String c = PlanningBrain.canon(subject);
        double best = 0;
        String today = todayStr();
        for (Exam e : exams) {
            if (e.done) continue;
            if (e.subject == null || e.subject.trim().isEmpty()) continue;
            String ec = PlanningBrain.canon(e.subject);
            if (!ec.equals(c) && !e.subject.equals(subject)) continue;
            if (e.day == null || e.day.isEmpty()) continue;
            int days = 999;
            try {
                long a = DAY.parse(today).getTime();
                long b = DAY.parse(e.day).getTime();
                days = (int) ((b - a) / (24L * 60 * 60 * 1000));
            } catch (Exception ignored) {}
            if (days < 0) continue; // امتحان فات
            double prep = Math.max(0, Math.min(100, e.prepLevel));
            // قرب أقوى → أولوية أعلى لمحاضرات نفس المادة عند تعارض السعة
            double u = 0;
            if (days <= 1) u = 95;       // غدًا / النهاردة
            else if (days <= 3) u = 70;
            else if (days <= 7) u = 45;
            else if (days <= 14) u = 22;
            else if (days <= 30) u = 8;
            else u = 2;
            u += (100 - prep) * 0.15; // تحضير ضعيف يرفع الإلحاح
            if (u > best) best = u;
        }
        return best;
    }

    private double deadlineUrgency(Task t) {
        if (t.deadline == null || t.deadline.length() < 8) return 0;
        try {
            long a = DAY.parse(todayStr()).getTime();
            long b = DAY.parse(t.deadline).getTime();
            int days = (int) ((b - a) / (24L * 60 * 60 * 1000));
            if (days < 0) return 22; // متأخر
            if (days <= 1) return 30;
            if (days <= 3) return 18;
            if (days <= 7) return 10;
            return 3;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean hasMissedSessions(Task t) {
        for (Session s : sessions) {
            if (t.id.equals(s.taskId) && s.missed && !s.done) return true;
        }
        return false;
    }

    /** عقوبة لو في محاضرة أسبق بنفس المادة لسه متتغطتش */
    private double earlierLecturePenalty(Task t, Map<String, Integer> remaining) {
        int ln = lectureNumber(t);
        if (ln < 0) return 0;
        double pen = 0;
        for (Task x : tasks) {
            if (x.done || x.id.equals(t.id)) continue;
            if (x.subject == null || !x.subject.equals(t.subject)) continue;
            int lx = lectureNumber(x);
            if (lx >= 0 && lx < ln && remaining.getOrDefault(x.id, 0) > 0) pen += 12;
        }
        return pen;
    }

    /**
     * تقييم متعدد العوامل — مش أولوية لوحدها.
     * القيود الصلبة (نوم/راحة/مقفول) خارج السكور في الـplacement.
     */
    private double effectiveScore(Task t) {
        return planningScore(t, null);
    }

    private double planningScore(Task t, Map<String, Integer> remaining) {
        if (t == null) return -1e9;
        double s = 0;
        // 1) أولوية المهمة (عامل قوي لكن مش الوحيد)
        s += PRIORITY_WEIGHT[Math.max(0, Math.min(2, t.priority))] * 80.0; // حاسم: عالية >> متوسطة >> منخفضة
        // 2) حجم التراكم في المادة + عدد المحاضرات المتبقية
        s += Math.min(48, subjectBacklogMin(t.subject) * 0.15);
        s += Math.min(18, subjectOpenCount(t.subject) * 3.0);
        // 3) جديد vs متراكم — منع تراكم الجديد بدون تجاهل الـbacklog الكبير
        // لا ترفع الجديد فوق المتراكم بلا سبب؛ backlog أكبر يأخذ دفعة
        if (t.backlog) s += 14 + Math.min(18, subjectBacklogMin(t.subject) * 0.08);
        else s += 10;
        // 4) امتحان قريب لنفس المادة فقط (أولوية تخطيط — مش تحديد موعد)
        s += examUrgency(t.subject);
        // 5) Deadline
        s += deadlineUrgency(t);
        // 6) فائت
        if (hasMissedSessions(t)) s += 16;
        // 7) مدة متبقية للمهمة (مهمة كبيرة تحتاج تقدم)
        int rem = remaining != null ? remaining.getOrDefault(t.id, t.remainingMin) : t.remainingMin;
        s += Math.min(12, rem * 0.04);
        // 8) توازن المواد: لو المادة اخدت كتير بالفعل يقل السكور شوية
        s -= Math.min(20, subjectScheduledMin(t.subject) * 0.03);
        // 9) ترتيب المحاضرات
        if (remaining != null) s -= earlierLecturePenalty(t, remaining);
        // 10) PlanningBrain إن وجد
        if (lastBrain != null) s += lastBrain.scoreTask(t) * 0.35;
        return s;
    }

    private String insightSuffix() {
        if (settings.lastInsight == null || settings.lastInsight.isEmpty()) return "";
        return "\n\n" + settings.lastInsight;
    }

    private Task pickNext(List<Task> fresh, List<Task> old, Map<String, Integer> remaining,
                          Set<String> finishedIds, int freshStreak) {
        return pickNext(fresh, old, remaining, finishedIds, freshStreak, null, null, 0);
    }

    /** preferredTaskId: أكمل نفس المحاضرة قبل أي مهمة أخرى */
    private Task pickNext(List<Task> fresh, List<Task> old, Map<String, Integer> remaining,
                          Set<String> finishedIds, int freshStreak,
                          String preferredTaskId, String dayStr, int packedDay) {
        List<Task> pool = new ArrayList<>();
        for (List<Task> list : new List[]{fresh, old}) {
            for (Task t : list) {
                int rem = remaining.getOrDefault(t.id, 0);
                if (rem <= 0) continue;
                if (t.dependsOn != null && !t.dependsOn.isEmpty() && !finishedIds.contains(t.dependsOn))
                    continue;
                if (t.pinStart && !hasPinnedSession(t.id)) continue;
                pool.add(t);
            }
        }
        if (pool.isEmpty()) return null;

        if (preferredTaskId != null) {
            for (Task t : pool) {
                if (preferredTaskId.equals(t.id) && remaining.getOrDefault(t.id, 0) > 0) return t;
            }
        }

        if (dayStr != null) {
            for (Task t : pool) {
                int rem = remaining.getOrDefault(t.id, 0);
                if (rem <= 0) continue;
                if (!startedToday(t.id, dayStr)) continue;
                int half = Math.max(1, t.durationMin / 2);
                if (rem < half || rem < 60) return t;
            }
        }

        Task best = null;
        for (Task t : pool) {
            if (best == null) { best = t; continue; }
            int pt = PRIORITY_WEIGHT[Math.max(0, Math.min(2, t.priority))];
            int pb = PRIORITY_WEIGHT[Math.max(0, Math.min(2, best.priority))];
            double st = planningScore(t, remaining);
            double sb = planningScore(best, remaining);
            double examGap = examUrgency(t.subject) + deadlineUrgency(t)
                    - examUrgency(best.subject) - deadlineUrgency(best);
            if (pt > pb) {
                if (examGap < -25) { }
                else best = t;
            } else if (pt < pb) {
                if (examGap > 25) best = t;
            } else {
                if (st > sb) best = t;
                else if (Math.abs(st - sb) < 0.01) {
                    int c = (t.name == null ? "" : t.name).compareTo(best.name == null ? "" : best.name);
                    if (c < 0) best = t;
                    else if (c == 0 && t.id.compareTo(best.id) < 0) best = t;
                }
            }
        }
        return best;
    }

    private boolean startedToday(String taskId, String dayStr) {
        for (Session s : sessions) {
            if (taskId.equals(s.taskId) && dayStr.equals(s.day)) return true;
        }
        return false;
    }

    /**
     * لو اليوم زحمة وفيه مهمة عالية الأهمية، حرّر وقت من جلسات أقل أهمية
     * (غير مكتملة وغير مقفلة) في نفس اليوم فقط.
     */
    private void freeTodayForHighPriority(Task pick, Map<String, Integer> remaining,
                                          Map<String, Integer> scheduledCount, String today) {
        if (pick == null || today == null) return;
        int pickPri = PRIORITY_WEIGHT[Math.max(0, Math.min(2, pick.priority))];
        double pickScore = planningScore(pick, remaining);
        // نزاحم لو الأولوية أعلى (عالية > متوسطة > منخفضة) أو السكور أعلى بوضوح
        if (pickPri <= 1 && pickScore < 50) return; // منخفضة ما تزاحمش بسهولة

        List<Session> victims = new ArrayList<>();
        for (Session s : new ArrayList<>(sessions)) {
            if (!today.equals(s.day)) continue;
            if (s.done || s.userPinned) continue; // مكتمل / مقفول = قيد صلب
            Task ot = findTask(s.taskId);
            if (ot == null || ot.id.equals(pick.id)) continue;
            int otPri = PRIORITY_WEIGHT[Math.max(0, Math.min(2, ot.priority))];
            // نزاحم فقط أولوية أقل صراحة (عالية تزاحم متوسطة/منخفضة)
            if (otPri >= pickPri) continue;
            victims.add(s);
        }
        victims.sort((a, b) -> {
            Task ta = findTask(a.taskId);
            Task tb = findTask(b.taskId);
            int pa = ta == null ? 0 : PRIORITY_WEIGHT[Math.max(0, Math.min(2, ta.priority))];
            int pb = tb == null ? 0 : PRIORITY_WEIGHT[Math.max(0, Math.min(2, tb.priority))];
            if (pa != pb) return Integer.compare(pa, pb); // الأقل أولوية أولًا
            return Double.compare(planningScore(ta, remaining), planningScore(tb, remaining));
        });
        int need = Math.max(settings.sessionMin, 25);
        int freed = 0;
        for (Session s : victims) {
            if (freed >= need) break;
            sessions.remove(s);
            int rem = remaining.getOrDefault(s.taskId, 0) + s.durationMin;
            remaining.put(s.taskId, rem);
            Task ot = findTask(s.taskId);
            if (ot != null) {
                ot.remainingMin = rem;
                if (ot.done && rem > 0) ot.done = false;
            }
            int cnt = scheduledCount.getOrDefault(s.taskId, 1);
            scheduledCount.put(s.taskId, Math.max(0, cnt - 1));
            freed += s.durationMin;
        }
    }


    /**
     * جلسات فات موعدها وغير مكتملة وغير مقفولة → تتشال من الماضي
     * عشان تتعاد في أقرب وقت متاح عند التخطيط (Reflow مش rebuild كامل).
     */
    public void rollPastIncompleteSessions() {
        String today = todayStr();
        boolean changed = false;
        List<Session> past = new ArrayList<>();
        for (Session s : sessions) {
            if (s.done || s.userPinned) continue;
            if (s.day != null && s.day.compareTo(today) < 0) past.add(s);
        }
        // رتّب: الأقدم أولاً؛ عند الترحيل لليوم يصبحون في المقدمة
        past.sort(Comparator.comparing((Session s) -> s.day == null ? "" : s.day)
                .thenComparingInt(s -> s.startMin));
        for (Session s : past) {
            sessions.remove(s);
            placeSessionAtStartOfDay(s, today);
            changed = true;
        }
        if (changed) save();
    }

    /** ضع الجلسة في أول وقت صالح من اليوم المحدد (مع احترام النوم/الالتزامات/البريك) */
    public void placeSessionAtStartOfDay(Session s, String day) {
        if (s == null || day == null) return;
        Calendar d = dayCal(day);
        if (isRestDay(d)) {
            // أول يوم شغل بعد day
            for (int i = 0; i < 14; i++) {
                d.add(Calendar.DAY_OF_YEAR, 1);
                if (!isRestDay(d)) break;
            }
            day = DAY.format(d.getTime());
            d = dayCal(day);
        }
        List<int[]> free = freeSlotsForDay(d, day);
        if (day.equals(todayStr())) free = clipPastForToday(free, nowMinOfDay());
        int dur = Math.max(1, s.durationMin);
        int breakGap = Math.max(0, settings.breakMin);
        // أول سلوط يكفي؛ إن لم يوجد قص المدة للسلوط (المتبقي يُعاد في remaining عبر build لاحقًا)
        for (int[] slot : free) {
            int room = slot[1] - slot[0];
            if (room < 1) continue;
            int place = Math.min(dur, room);
            s.day = day;
            s.startMin = slot[0] % (24 * 60);
            s.endMin = (slot[0] + place) % (24 * 60);
            if (s.endMin == 0 && place > 0) s.endMin = 24 * 60;
            s.durationMin = place;
            // أزح الجلسات غير المثبتة/غير المكتملة في نفس اليوم للخلف بعد هذه الجلسة + بريك
            shiftDaySessionsAfter(day, s.endMin, breakGap, s.id);
            sessions.add(s);
            return;
        }
        // لا سعة صالحة (مثلاً اليوم مغطى بالتزام) — لا تُوضع الجلسة في وقت عشوائي
        // ولا داخل التزام. تُترك بدون إدراج؛ المتبقي يُعالَج في build لاحقًا.
    }

    private void shiftDaySessionsAfter(String day, int afterEndMin, int breakGap, String excludeId) {
        int cursor = afterEndMin + breakGap;
        List<Session> later = new ArrayList<>();
        for (Session x : sessions) {
            if (!day.equals(x.day) || x.done || x.userPinned) continue;
            if (excludeId != null && excludeId.equals(x.id)) continue;
            if (x.startMin >= afterEndMin - 1) later.add(x);
        }
        later.sort(Comparator.comparingInt(a -> a.startMin));
        sessions.removeAll(later);
        List<int[]> free = freeSlotsForDay(dayCal(day), day);
        free = subtractBusy(free, 0, cursor);
        int idx = 0;
        for (int[] slot : free) {
            int c = Math.max(slot[0], cursor);
            while (idx < later.size()) {
                Session x = later.get(idx);
                int dur = Math.max(1, x.durationMin);
                if (c + dur > slot[1]) break;
                x.startMin = c % (24 * 60);
                x.endMin = (c + dur) % (24 * 60);
                if (x.endMin == 0 && dur > 0) x.endMin = 24 * 60;
                sessions.add(x);
                idx++;
                c += dur + breakGap;
            }
        }
        while (idx < later.size()) {
            Session x = later.get(idx++);
            placeSessionSoonest(x);
        }
    }

    /** ترحيل متبقي مهمة لليوم التالي كأول شيء */
    public void rolloverRemainingToNextDay(String taskId) {
        Task t = findTask(taskId);
        if (t == null || t.done) return;
        int rem = Math.max(0, t.remainingMin);
        if (rem < 1) {
            // احسب من الجلسات
            int doneMin = 0;
            for (Session s : sessions) if (taskId.equals(s.taskId) && s.done) doneMin += s.durationMin;
            rem = Math.max(0, t.durationMin - doneMin);
        }
        if (rem < 1) return;
        // احذف جلسات غير مكتملة لهذه المهمة اليوم/الماضي
        String today = todayStr();
        sessions.removeIf(s -> taskId.equals(s.taskId) && !s.done && !s.userPinned
                && s.day != null && s.day.compareTo(today) <= 0);
        Calendar d = nowLocal();
        d.add(Calendar.DAY_OF_YEAR, 1);
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 14 && isRestDay(d); i++) d.add(Calendar.DAY_OF_YEAR, 1);
        String next = DAY.format(d.getTime());
        int sm = Math.max(10, settings.sessionMin);
        int left = rem;
        int idx = 1;
        int totalParts = Math.max(1, (int) Math.ceil(rem / (double) sm));
        while (left > 0) {
            int piece = Math.min(left, sm);
            Session s = new Session();
            s.taskId = taskId;
            s.taskName = t.name;
            s.subject = t.subject;
            s.durationMin = piece;
            s.priority = t.priority;
            s.backlog = t.backlog;
            s.sessionIndex = idx++;
            s.sessionTotal = totalParts;
            placeSessionAtStartOfDay(s, next);
            left -= piece;
            // بعد أول قطعة، باقي القطع لنفس اليوم بعد البريك عبر placeSessionSoonest لو لزم
            if (left > 0 && s.day != null) next = s.day;
        }
        t.remainingMin = rem;
        save();
    }

    /** جلسة اليوم الحالية غير المكتملة (بدأت أو التالية) للمادة النشطة */

    /**
     * Extra: يخصم من أقرب حصص مستقبلية لنفس taskId فقط (مش نفس المادة).
     * studiedMin = ما تم إنجازه فعليًا. لا يعيد ترتيب باقي الجدول.
     */
    public void applyExtraSameTask(String taskId, int studiedMin) {
        if (taskId == null || studiedMin < 1) return;
        Task t = findTask(taskId);
        if (t == null || t.done) return;
        int left = studiedMin;
        String today = todayStr();
        // 1) أقرب جلسات مستقبلية (أو لاحقة اليوم) لنفس الـTask فقط — من الأقرب زمنيًا
        List<Session> future = new ArrayList<>();
        for (Session s : sessions) {
            if (!taskId.equals(s.taskId) || s.done || s.userPinned) continue;
            if (s.day == null) continue;
            if (s.day.compareTo(today) < 0) continue;
            if (s.day.equals(today) && s.endMin <= nowMinOfDay()) continue;
            future.add(s);
        }
        future.sort(Comparator
                .comparing((Session s) -> s.day == null ? "" : s.day)
                .thenComparingInt(s -> s.startMin));
        // تخطّى جلسة اليوم الحالية إن كانت هي مصدر الـExtra (أول جلسة اليوم الجارية)
        for (Session s : future) {
            if (left < 1) break;
            if (s.day.equals(today) && s.startMin <= nowMinOfDay() && s.endMin > nowMinOfDay()) {
                // لا تخصم من نفس الجلسة الجارية هنا — نحدّث remaining فقط
                continue;
            }
            int take = Math.min(left, Math.max(0, s.durationMin));
            if (take < 1) continue;
            s.durationMin -= take;
            if (s.durationMin < 1) {
                sessions.remove(s);
            } else {
                s.endMin = (s.startMin + s.durationMin) % (24 * 60);
                if (s.endMin == 0 && s.durationMin > 0) s.endMin = 24 * 60;
            }
            left -= take;
        }
        // 2) حدّث remaining للمهمة
        int doneMin = 0;
        for (Session s : sessions) {
            if (taskId.equals(s.taskId) && s.done) doneMin += Math.max(0, s.durationMin);
        }
        // اعتبر studiedMin منجزًا إضافيًا
        t.remainingMin = Math.max(0, t.durationMin - doneMin - studiedMin);
        if (t.remainingMin <= 0) {
            t.done = true;
            t.remainingMin = 0;
        }
        save();
    }

    public Session findCurrentIncompleteSessionToday() {
        String today = todayStr();
        int now = nowMinOfDay();
        Session cur = null, next = null;
        for (Session s : sessions) {
            if (!today.equals(s.day) || s.done) continue;
            if (s.startMin <= now && now < (s.endMin <= s.startMin ? s.endMin + 24 * 60 : s.endMin)) {
                cur = s;
                break;
            }
            if (s.startMin > now && (next == null || s.startMin < next.startMin)) next = s;
        }
        if (cur != null) return cur;
        if (next != null) return next;
        for (Session s : sessions) {
            if (today.equals(s.day) && !s.done) return s;
        }
        return null;
    }

    /**
     * هدف «إضافي»: آخر محاضرة ظهرت في جدول اليوم وما زال لها متبقي (حتى لو باقي الجلسات بكرة).
     * لا يفتح محاضرة جديدة لم تظهر النهاردة.
     * @return Session من اليوم لنفس المهمة (للعرض/التايمر) أو null
     */
    public Session findExtraTargetSession() {
        String today = todayStr();
        // آخر جلسة (حسب endMin) لمهمة محاضرة لها متبقي
        Session best = null;
        int bestEnd = -1;
        for (Session s : sessions) {
            if (!today.equals(s.day)) continue;
            Task t = findTask(s.taskId);
            if (t == null || t.done || !t.isLecture()) continue;
            int rem = taskRemainingMinutes(t);
            if (rem < 1) continue;
            int end = s.endMin <= s.startMin ? s.endMin + 24 * 60 : s.endMin;
            if (end >= bestEnd) {
                bestEnd = end;
                best = s;
            }
        }
        return best;
    }

    /** المتبقي الحقيقي للمهمة = المدة − المكتمل من الجلسات */
    public int taskRemainingMinutes(Task t) {
        if (t == null) return 0;
        int doneMin = 0;
        for (Session s : sessions) {
            if (t.id.equals(s.taskId) && s.done) doneMin += Math.max(0, s.durationMin);
        }
        return Math.max(0, t.durationMin - doneMin);
    }

    /**
     * إنهاء مبكر: حرّك الجلسات التالية في نفس اليوم للأمام لاستغلال الوقت.
     * @param fromMin وقت التحرير (بالدقائق من منتصف الليل) — عادة الوقت الحالي
     */
    public void reflowTodayAfterEarlyFinish(String day, int fromMin) {
        if (day == null) return;
        int breakGap = Math.max(0, settings.breakMin);
        // ابدأ من لحظة الإنهاء الفعلي (لا من بداية الجلسة القديمة)
        int cursor = fromMin;
        if (day.equals(todayStr())) cursor = Math.max(fromMin, nowMinOfDay());

        List<Session> movable = new ArrayList<>();
        for (Session s : sessions) {
            if (!day.equals(s.day)) continue;
            if (s.done || s.userPinned) continue;
            // الجلسات اللي كانت بعد نقطة الإنهاء (أو متداخلة مع الوقت المحرَّر)
            if (s.endMin <= fromMin && s.startMin < fromMin) continue;
            if (s.startMin < fromMin && s.endMin > fromMin) {
                // كانت متداخلة — تتحرك كاملة للأمام
            } else if (s.startMin < fromMin) {
                continue;
            }
            movable.add(s);
        }
        // حافظ على ترتيب المحاضرات/الجلسات الأصلي
        movable.sort(Comparator.comparingInt(a -> a.startMin));
        if (movable.isEmpty()) return;

        java.util.HashSet<String> moveIds = new java.util.HashSet<>();
        for (Session s : movable) if (s.id != null) moveIds.add(s.id);
        sessions.removeIf(s -> s != null && s.id != null && moveIds.contains(s.id));

        List<int[]> free = freeSlotsForDay(dayCal(day), day);
        // ابدأ من لحظة التحرير — الوقت قبلها مش متاح لجلسات جديدة
        free = subtractBusy(free, 0, Math.max(0, cursor));

        int idx = 0;
        for (int[] slot : free) {
            int c = Math.max(slot[0], cursor);
            int end = slot[1];
            while (idx < movable.size() && c + 1 <= end) {
                Session s = movable.get(idx);
                int dur = Math.max(1, s.durationMin);
                if (c + dur > end) {
                    // الـslot مش كافي — جرّب السلوط التالي بدون تخطي الجلسة
                    break;
                }
                s.day = day;
                s.startMin = c % (24 * 60);
                s.endMin = (c + dur) % (24 * 60);
                if (s.endMin == 0 && c + dur > 0) s.endMin = 24 * 60;
                s.durationMin = dur;
                sessions.add(s);
                idx++;
                c += dur + breakGap;
            }
        }
        while (idx < movable.size()) {
            Session s = movable.get(idx++);
            placeSessionSoonest(s);
        }
        save(); // يحفّز resync للمنبهات
    }

    private void placeSessionSoonest(Session s) {
        Task t = findTask(s.taskId);
        int pref = (t != null) ? t.preferredDow : 0;
        Subject subj = t != null ? findSubject(t.subject) : null;
        boolean useFixed = subj != null && !subj.fixedDows.isEmpty() && t != null && !t.backlog
                && !subjectHasOpenBacklog(t.subject, null);
        Calendar d = nowLocal();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        // Preferred: أول تاريخ مطابق فقط خلال أسبوعين، بدون توزيع على أسابيع متعددة لنفس المحاضرة عند reflow
        String lockedDate = null;
        if (pref >= 1 && pref <= 7) {
            Calendar probe = (Calendar) d.clone();
            for (int i = 0; i < 14; i++) {
                if (i > 0) probe.add(Calendar.DAY_OF_YEAR, 1);
                if (isRestDay(probe)) continue;
                if (probe.get(Calendar.DAY_OF_WEEK) == pref) {
                    lockedDate = DAY.format(probe.getTime());
                    break;
                }
            }
        }
        for (int i = 0; i < 45; i++) {
            if (i > 0) d.add(Calendar.DAY_OF_YEAR, 1);
            if (isRestDay(d)) continue;
            String ds = DAY.format(d.getTime());
            if (lockedDate != null && !lockedDate.equals(ds)) continue;
            if (lockedDate == null && useFixed && !subj.fixedDows.contains(d.get(Calendar.DAY_OF_WEEK))) continue;
            List<int[]> free = freeSlotsForDay(d, ds);
            if (ds.equals(todayStr())) free = clipPastForToday(free, nowMinOfDay());
            for (int[] slot : free) {
                int room = slot[1] - slot[0];
                if (room < s.durationMin) continue; // لا تتجاوز نافذة الدراسة
                int start = slot[0];
                int end = start + s.durationMin;
                if (end > slot[1]) continue;
                s.day = ds;
                s.startMin = start % (24 * 60);
                s.endMin = end % (24 * 60);
                if (s.endMin == 0 && end > 0) s.endMin = 24 * 60;
                sessions.add(s);
                return;
            }
        }
        // لا نضع Session خارج free slots / Study Window — لو مفيش مكان صالح نسيب الجلسة بدون إدراج قسري
        // (الدقائق تفضل في remaining وتظهر في رسالة leftover عند إعادة التخطيط)
    }

    public void markSessionDone(String sessionId, boolean done) {
        for (Session s : sessions) {
            if (s.id.equals(sessionId)) {
                s.done = done;
                // Early Finish: قص نهاية الجلسة للوقت الحالي عشان الوقت الفاضي يدخل الـReflow
                if (done && s.day != null && s.day.equals(todayStr())) {
                    int now = nowMinOfDay();
                    if (now > s.startMin && now < s.endMin) {
                        s.endMin = now;
                        s.durationMin = Math.max(1, s.endMin - s.startMin);
                    }
                }
                Task t = findTask(s.taskId);
                if (t != null) {
                    int scheduledDone = 0;
                    boolean hasSession = false;
                    boolean allSessionsDone = true;
                    for (Session x : sessions) {
                        if (x.taskId == null || !x.taskId.equals(t.id)) continue;
                        hasSession = true;
                        if (x.done) scheduledDone += Math.max(0, x.durationMin);
                        else allSessionsDone = false;
                    }
                    t.remainingMin = Math.max(0, t.durationMin - scheduledDone);
                    // كل الجلسات المرتبطة انتهت → المهمة مكتملة حتى لو المتبقي الحسابي > 0
                    if (hasSession && allSessionsDone) {
                        t.done = true;
                        t.remainingMin = 0;
                    } else {
                        t.done = t.remainingMin <= 0;
                    }
                }
                save();
                return;
            }
        }
    }

    public void completeTask(String taskId) {
        Task t = findTask(taskId);
        if (t == null) return;
        t.done = true;
        t.remainingMin = 0;
        for (Session s : sessions) if (taskId.equals(s.taskId)) s.done = true;
        save();
    }

    /** مسح المهام المكتملة فقط (وجلساتها). لا يمس المهام النشطة. */
    public int clearCompletedTasks() {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (Task t : tasks) {
            if (t != null && t.done && t.id != null) ids.add(t.id);
        }
        if (ids.isEmpty()) return 0;
        tasks.removeIf(t -> t != null && t.id != null && ids.contains(t.id));
        sessions.removeIf(s -> s != null && s.taskId != null && ids.contains(s.taskId));
        save();
        return ids.size();
    }

    /** حذف الجدول المُولَّد فقط — لا يمس المهام/الامتحانات/المواد/الإعدادات.
     *  يحذف الجلسات غير المكتملة فقط؛ المكتملة تبقى للإحصائيات. */
    public void clearGeneratedSchedule() {
        sessions.removeIf(s -> s != null && !s.done);
        save();
    }

    /** نقل يدوي لجلسة مع تثبيتها حتى لا يعيدها Scheduler بدون سبب. */
    public String moveSessionManual(String sessionId, String newDay, int newStartMin) {
        Session s = null;
        for (Session x : sessions) if (sessionId.equals(x.id)) { s = x; break; }
        if (s == null) return "الجلسة غير موجودة.";
        if (s.done) return "لا يمكن نقل جلسة مكتملة.";
        int dur = Math.max(1, s.durationMin);
        int start = ((newStartMin % (24 * 60)) + 24 * 60) % (24 * 60);
        int end = (start + dur) % (24 * 60);
        if (end == 0 && dur > 0) end = 24 * 60;
        // تعارض مع جلسات أخرى في نفس اليوم
        for (Session o : sessions) {
            if (o.id.equals(s.id) || o.done) continue;
            if (o.day == null || !o.day.equals(newDay)) continue;
            int os = o.startMin, oe = o.endMin;
            if (oe <= os) oe += 24 * 60;
            int ns = start, ne = start + dur;
            if (ns < oe && ne > os) return "تعارض مع جلسة أخرى في نفس الوقت.";
        }
        Calendar day = dayCal(newDay);
        if (isRestDay(day)) return "يوم راحة — لا يمكن وضع جلسة.";
        if (windowConflicts(day, start, start + dur)) return "تعارض مع نوم أو التزام.";
        s.day = newDay;
        s.startMin = start;
        s.endMin = end;
        s.userPinned = true;
        s.missed = false;
        save();
        return null;
    }

    /** تعديل مدة جلسة مع إعادة توزيع الفرق على جلسات نفس المحاضرة. */
    
    /** هل يمكن وضع جلسة بهذا الوقت؟ null = صالح، وإلا رسالة تعارض. */
    public String canPlaceSessionAt(String sessionId, String newDay, int newStartMin) {
        Session s = null;
        for (Session x : sessions) if (sessionId.equals(x.id)) { s = x; break; }
        if (s == null) return "الجلسة غير موجودة.";
        if (s.done) return "لا يمكن نقل جلسة مكتملة.";
        int dur = Math.max(1, s.durationMin);
        int start = ((newStartMin % (24 * 60)) + 24 * 60) % (24 * 60);
        for (Session o : sessions) {
            if (o.id.equals(s.id) || o.done) continue;
            if (o.day == null || !o.day.equals(newDay)) continue;
            int os = o.startMin, oe = o.endMin;
            if (oe <= os) oe += 24 * 60;
            int ns = start, ne = start + dur;
            if (ns < oe && ne > os) return "تعارض مع جلسة أخرى";
        }
        Calendar day = dayCal(newDay);
        if (isRestDay(day)) return "يوم راحة";
        if (windowConflicts(day, start, start + dur)) return "تعارض مع نوم/التزام";
        return null;
    }

    /** أقرب وقت صالح في اليوم قرب preferredStart (من free slots). -1 لو مفيش. */
    public int nearestValidStart(String sessionId, String dayStr, int preferredStart) {
        Session s = null;
        for (Session x : sessions) if (sessionId.equals(x.id)) { s = x; break; }
        if (s == null) return -1;
        int dur = Math.max(1, s.durationMin);
        Calendar day = dayCal(dayStr);
        if (isRestDay(day)) return -1;
        // استبعد الجلسة نفسها حتى يمكن نقلها داخل نفس اليوم
        java.util.List<int[]> free = freeSlotsForDay(day, dayStr, sessionId);
        if (dayStr.equals(todayStr())) free = clipPastForToday(free, nowMinOfDay());
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        int pref = ((preferredStart % (24 * 60)) + 24 * 60) % (24 * 60);
        for (int[] sl : free) {
            // جرّب البداية عند pref لو داخل الـslot، وإلا بداية الـslot
            int cand = pref;
            if (cand < sl[0]) cand = sl[0];
            if (cand + dur > sl[1]) {
                // لو pref متأخر جدًا، جرب بداية الـslot
                if (sl[0] + dur <= sl[1]) cand = sl[0];
                else continue;
            }
            if (cand + dur > sl[1]) continue;
            // تحقق تعارض مع جلسات أخرى
            String err = canPlaceSessionAt(sessionId, dayStr, cand);
            if (err != null) {
                // تقدّم داخل الـslot بدقة 5 د
                boolean ok = false;
                for (int t = sl[0]; t + dur <= sl[1]; t += 5) {
                    if (canPlaceSessionAt(sessionId, dayStr, t) == null) {
                        cand = t; ok = true; break;
                    }
                }
                if (!ok) continue;
            }
            int dist = Math.abs(cand - pref);
            if (dist < bestDist) {
                bestDist = dist;
                best = cand;
            }
        }
        return best;
    }

public String adjustSessionDuration(String sessionId, int newDur) {
        Session s = null;
        for (Session x : sessions) if (sessionId.equals(x.id)) { s = x; break; }
        if (s == null) return "الجلسة غير موجودة.";
        if (s.done) return "لا يمكن تعديل جلسة مكتملة.";
        newDur = Math.max(5, Math.min(240, newDur));
        int old = Math.max(1, s.durationMin);
        if (newDur == old) return null;
        int delta = newDur - old;
        // قائمة جلسات نفس المهمة غير المكتملة
        java.util.ArrayList<Session> same = new java.util.ArrayList<>();
        for (Session x : sessions) {
            if (s.taskId != null && s.taskId.equals(x.taskId) && !x.done) same.add(x);
        }
        same.sort((a, b) -> {
            int c = (a.day == null ? "" : a.day).compareTo(b.day == null ? "" : b.day);
            if (c != 0) return c;
            return Integer.compare(a.startMin, b.startMin);
        });
        if (delta > 0) {
            // خصم من آخر جلسة أخرى
            Session donor = null;
            for (int i = same.size() - 1; i >= 0; i--) {
                if (!same.get(i).id.equals(s.id) && same.get(i).durationMin > 5) {
                    donor = same.get(i); break;
                }
            }
            if (donor == null) return "مفيش جلسة أخرى من نفس المحاضرة لخصم الوقت منها.";
            int take = Math.min(delta, donor.durationMin - 5);
            if (take < 1) return "لا يمكن زيادة المدة أكثر.";
            donor.durationMin -= take;
            donor.endMin = (donor.startMin + donor.durationMin) % (24 * 60);
            if (donor.endMin == 0 && donor.durationMin > 0) donor.endMin = 24 * 60;
            s.durationMin = old + take;
            s.endMin = (s.startMin + s.durationMin) % (24 * 60);
            if (s.endMin == 0 && s.durationMin > 0) s.endMin = 24 * 60;
        } else {
            int give = -delta;
            Session recv = null;
            for (int i = same.size() - 1; i >= 0; i--) {
                if (!same.get(i).id.equals(s.id)) { recv = same.get(i); break; }
            }
            if (recv == null) return "مفيش جلسة أخرى لإعادة الوقت إليها.";
            if (s.durationMin - give < 5) give = s.durationMin - 5;
            if (give < 1) return "المدة صغيرة جدًا.";
            s.durationMin -= give;
            s.endMin = (s.startMin + s.durationMin) % (24 * 60);
            if (s.endMin == 0 && s.durationMin > 0) s.endMin = 24 * 60;
            recv.durationMin += give;
            recv.endMin = (recv.startMin + recv.durationMin) % (24 * 60);
            if (recv.endMin == 0 && recv.durationMin > 0) recv.endMin = 24 * 60;
        }
        save();
        return null;
    }

    /** اقتراحات لإعادة وضع جلسة فائتة مع سبب لكل اقتراح. */
    public static class MissedSuggestion {
        public String day;
        public int startMin;
        public int endMin;
        public String reason;
        public String label;
    }

    public java.util.List<MissedSuggestion> suggestMissedPlacements(Session missed, int limit) {
        java.util.ArrayList<MissedSuggestion> out = new java.util.ArrayList<>();
        if (missed == null || missed.done) return out;
        int need = Math.max(1, missed.remainingDurationMin());
        if (need < 1) need = 1;
        String excludeId = missed.id;
        lastSrDiag = "sid=" + excludeId + " need=" + need
                + " executed=" + missed.executedMin
                + " duration=" + missed.durationMin;
        Calendar day = nowLocal();
        String today = todayStr();
        int nowM = nowMinOfDay();
        boolean hasToday = false;
        boolean hasTomorrow = false;
        for (int i = 0; i < 14 && out.size() < limit; i++) {
            String dayStr = DAY.format(day.getTime());
            if (isRestDay(day) || isNoLectureDay(dayStr)) {
                day.add(Calendar.DAY_OF_YEAR, 1);
                continue;
            }
            // استثنِ الجلسة الفائتة نفسها من الـoccupied حتى يظهر وقت صالح لإعادة وضعها
            java.util.List<int[]> free = freeSlotsForDay(day, dayStr, excludeId);
            if (dayStr.equals(today)) free = clipPastForToday(free, nowM);
            for (int[] sl : free) {
                if (sl[1] - sl[0] < need) continue;
                // تحقق نهائي — مع تجاهل الجلسة الفائتة نفسها
                if (windowConflictsExcluding(day, sl[0], sl[0] + need, excludeId)) continue;
                MissedSuggestion ms = new MissedSuggestion();
                ms.day = dayStr;
                ms.startMin = sl[0];
                ms.endMin = sl[0] + need;
                if (dayStr.equals(today)) {
                    ms.reason = "اليوم";
                    hasToday = true;
                } else if (!hasTomorrow) {
                    ms.reason = "غدًا";
                    hasTomorrow = true;
                } else {
                    ms.reason = dayLabelAr(dayStr);
                }
                if (missed.executedMin > 0) {
                    ms.label = "استكمال المحاضرة — " + need + " د · "
                            + ms.reason + " — " + minToTime(ms.startMin);
                } else {
                    ms.label = ms.reason + " — " + minToTime(ms.startMin);
                }
                out.add(ms);
                if (out.size() >= limit) {
                    lastSrDiag += " suggestions=" + out.size();
                    return out;
                }
                break; // اقتراح واحد لكل يوم
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        lastSrDiag += " suggestions=" + out.size();
        return out;
    }

    /** مثل windowConflicts مع إمكانية استثناء جلسة واحدة (إعادة جدولة فائتة). */
    private boolean windowConflictsExcluding(Calendar day, int start, int end, String excludeSessionId) {
        int[] ws = wakeSleepFor(day);
        int wake = studyStartMin(day);
        int sleep = ws[1];
        if (sleep <= wake) sleep += 24 * 60;
        int a = start;
        int b = end;
        if (b <= a) b += 24 * 60;
        if (a < wake || b > sleep) return true;
        if (overlapsCommitment(day, a, b)) return true;
        String dayStr = DAY.format(day.getTime());
        for (Session s : sessions) {
            if (s == null) continue;
            if (excludeSessionId != null && excludeSessionId.equals(s.id)) continue;
            if (!dayStr.equals(s.day)) continue;
            int ss = s.startMin;
            int se = s.endMin;
            if (se <= ss) se += 24 * 60;
            if (a < se && b > ss) return true;
        }
        return false;
    }

    /** تطبيق اختيار المستخدم لجلسة فائتة دون المساس بباقي جلسات المحاضرة. */
    public String placeMissedSession(String sessionId, String day, int startMin) {
        Session s = null;
        for (Session x : sessions) if (sessionId.equals(x.id)) { s = x; break; }
        if (s == null) return "الجلسة غير موجودة.";
        int dur = Math.max(1, s.remainingDurationMin());
        int st = ((startMin % (24 * 60)) + 24 * 60) % (24 * 60);
        int en = st + dur;
        Calendar dayCal = dayCal(day);
        if (windowConflictsExcluding(dayCal, st, en, s.id)) {
            return "تعارض مع نوم أو التزام — اختر وقتًا خارج الالتزام.";
        }
        s.day = day;
        s.startMin = st;
        s.endMin = en % (24 * 60);
        if (s.endMin == 0 && dur > 0) s.endMin = 24 * 60;
        s.durationMin = dur;
        s.executedMin = 0;
        s.missed = false;
        s.userPinned = true; // لا تُمس في rebuild عشوائي
        save();
        if (app != null && s.id != null) {
            try { SessionAlarmScheduler.clearMissedNotified(app, s.id); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * تقديم جلسة في نفس اليوم بمقدار earlierMin دقائق (قبل الموعد الأصلي).
     * لا يحرّك الجلسات التالية؛ يتحقق من التعارض فقط.
     */
    public String advanceSessionInDay(String sessionId, int earlierMin) {
        if (sessionId == null) return "الجلسة غير موجودة.";
        if (earlierMin < 1) return "أدخل عدد دقائق أكبر من صفر.";
        if (earlierMin > 12 * 60) return "التقديم كبير جدًا (حد أقصى 12 ساعة).";
        Session target = null;
        for (Session x : sessions) {
            if (x != null && sessionId.equals(x.id)) { target = x; break; }
        }
        if (target == null) return "الجلسة غير موجودة.";
        if (target.done) return "لا يمكن تعديل جلسة مكتملة.";
        if (target.day == null || target.day.isEmpty()) return "يوم الجلسة غير محدد.";

        final String day = target.day;
        Calendar dCal = dayCal(day);
        if (isRestDay(dCal)) return "يوم راحة — لا يمكن التعديل.";

        int origStart = target.startMin;
        int dur = Math.max(1, target.durationMin);
        int newStart = origStart - earlierMin;
        if (newStart < 0) return "الوقت المطلوب قبل منتصف الليل.";
        int newEnd = newStart + dur;

        sessions.remove(target);
        if (windowConflicts(dCal, newStart, newEnd)) {
            sessions.add(target);
            // رسالة أوضح حسب السبب الأرجح
            int[] ws = wakeSleepFor(dCal);
            int wake = studyStartMin(dCal);
            int sleep = ws[1];
            if (sleep <= wake) sleep += 24 * 60;
            if (newStart < wake) {
                return "الوقت ده قبل بداية وقت الدراسة (الاستيقاظ/التجهيز).";
            }
            if (newEnd > sleep) {
                return "الوقت ده بعد موعد النوم.";
            }
            if (overlapsCommitment(dCal, newStart, newEnd)) {
                return "الوقت ده متعارض مع التزام عندك.";
            }
            return "مفيش وقت متاح كفاية للمحاضرة في الوقت ده (تعارض مع جلسة أخرى).";
        }
        target.startMin = newStart % (24 * 60);
        target.endMin = newEnd % (24 * 60);
        if (target.endMin == 0 && newEnd > 0) target.endMin = 24 * 60;
        target.durationMin = dur;
        target.missed = false;
        target.userPinned = true;
        sessions.add(target);
        save();
        return null;
    }

    public Session findSessionById(String sessionId) {
        if (sessionId == null) return null;
        for (Session s : sessions) {
            if (s != null && sessionId.equals(s.id)) return s;
        }
        return null;
    }

    public java.util.List<Session> listMissedSessions() {
        java.util.ArrayList<Session> out = new java.util.ArrayList<>();
        for (Session s : sessions) {
            if (s != null && s.missed && !s.done) out.add(s);
        }
        return out;
    }

    public void deleteTask(String taskId) {
        if (taskId == null) return;
        tasks.removeIf(t -> t.id.equals(taskId));
        // احتفظ بالجلسات المكتملة للتاريخ/الإحصائيات؛ احذف غير المكتملة فقط
        sessions.removeIf(s -> taskId.equals(s.taskId) && !s.done);
        save();
        // إعادة بناء من المهام المتبقية فقط (الجلسات المكتملة تبقى)
        int days = Math.max(1, settings.planDays > 0 ? settings.planDays : 14);
        buildSchedule(DIST_DAYS, days, null);
    }


    /**
     * ترتيب المحاضرة داخل نفس المادة:
     * رقم المحاضرة المستخرج من الحقل أو الاسم؛ -1 = غير معروف.
     */

    /**
     * مقارنة عوامل الجدولة فقط — بدون ID أو ترتيب قاعدة البيانات.
     * تُرجع 0 عند التعادل الكامل (يحتاج اختيار المستخدم لكسر التعادل بين المحاضرات).
     */
    /**
     * مقارنة عوامل الجدولة — نفس ترتيب مفاضلة Planner الأصلي (15.4) بالكامل.
     * لا ID / لا ترتيب قاعدة بيانات. تُرجع 0 عند التعادل الحقيقي فقط.
     */

    /** أيام حتى أقرب يوم نزول للمادة؛ MAX إن لم تُضبط مواعيد. ليس Deadline. */
    int daysUntilLectureRelease(String subject) {
        if (!settings.lectureReleaseEnabled || subject == null || subject.trim().isEmpty())
            return Integer.MAX_VALUE;
        try {
            JSONArray arr = new JSONArray(settings.lectureReleaseJson == null ? "[]" : settings.lectureReleaseJson);
            Calendar today = Calendar.getInstance();
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("enabled", true)) continue;
                String sub = o.optString("subject", "");
                if (!subject.equalsIgnoreCase(sub)) continue;
                JSONArray days = o.optJSONArray("days");
                if (days == null || days.length() == 0) continue;
                for (int look = 0; look <= 14; look++) {
                    Calendar c = (Calendar) today.clone();
                    c.add(Calendar.DAY_OF_YEAR, look);
                    int dow = c.get(Calendar.DAY_OF_WEEK);
                    for (int j = 0; j < days.length(); j++) {
                        if (days.getInt(j) == dow) {
                            if (look < best) best = look;
                        }
                    }
                }
            }
            return best;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    int compareTasksForSchedule(Task a, Task b, Map<String, Integer> taskExamDays) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        if (taskExamDays == null) taskExamDays = new HashMap<>();

        // Exam Priority = Ordering فقط (لا يغيّر كمية المذاكرة أو مدة الجلسة).
        // Internal Deadline = Exam Date - 2 أيام.
        // Urgency ≈ remainingWork / remainingAvailableDays حتى الـInternal Deadline.
        int examDaysA = taskExamDays.containsKey(a.id) ? taskExamDays.get(a.id) : -1;
        int examDaysB = taskExamDays.containsKey(b.id) ? taskExamDays.get(b.id) : -1;
        // أيام متاحة حتى Internal Deadline (exam - 2)، لا سالب
        int ida = examDaysA >= 0 ? Math.max(0, examDaysA - 2) : -1;
        int idb = examDaysB >= 0 ? Math.max(0, examDaysB - 2) : -1;
        boolean hasExamA = ida >= 0 && a.remainingMin > 0;
        boolean hasExamB = idb >= 0 && b.remainingMin > 0;

        if (hasExamA || hasExamB) {
            double urgA = hasExamA
                    ? (a.remainingMin / (double) Math.max(1, ida + 1))
                    : 0.0;
            double urgB = hasExamB
                    ? (b.remainingMin / (double) Math.max(1, idb + 1))
                    : 0.0;
            // أعلى urgency أولًا (مادة متأخرة عن المعدل أو deadline أقرب)
            int urgCmp = Double.compare(urgB, urgA);
            if (urgCmp != 0) return urgCmp;
            // نفس urgency: الأقرب لـ Internal Deadline أولًا
            if (hasExamA && hasExamB && ida != idb) return Integer.compare(ida, idb);
            if (hasExamA != hasExamB) return hasExamA ? -1 : 1;
        }

        // أولوية أصلية HIGH > MEDIUM > LOW
        int pa = Math.max(0, Math.min(2, a.priority));
        int pb = Math.max(0, Math.min(2, b.priority));
        if (pa != pb) return Integer.compare(pb, pa);

        // 3b) ترتيب مؤقت من Dialog التشابه لهذه العملية فقط (لا يُحفظ على Task)
        Integer soa = planSessionOrder.get(a.id);
        Integer sob = planSessionOrder.get(b.id);
        if (soa != null && sob != null && !soa.equals(sob)) return Integer.compare(soa, sob);
        if (soa != null && sob == null) return -1;
        if (soa == null && sob != null) return 1;

        // مواعيد نزول المحاضرات: أولوية ناعمة فقط عند التفعيل — ليست Deadline
        if (settings.lectureReleaseEnabled) {
            int ra = daysUntilLectureRelease(a.subject);
            int rb = daysUntilLectureRelease(b.subject);
            // أصغر = أقرب نزول → أولوية أعلى (قيمة سالبة في المقارنة)
            if (ra != rb) return Integer.compare(ra, rb);
        }

        // deadline المهمة
        String adl = a.deadline == null ? "" : a.deadline;
        String bdl = b.deadline == null ? "" : b.deadline;
        boolean ha = !adl.isEmpty();
        boolean hb = !bdl.isEmpty();
        if (ha && !hb) return -1;
        if (!ha && hb) return 1;
        if (ha && hb && !adl.equals(bdl)) return adl.compareTo(bdl);

        // 4) عند تساوي الأولوية والامتحان: المحاضرة قبل المذاكرة
        if (a.isLecture() != b.isLecture()) return a.isLecture() ? -1 : 1;

        // 5) نفس المادة + محاضرتان → ترتيب السلسلة (رقم المحاضرة / من الاسم)
        // لا ترتيب أبجدي. لا يتقدّم على خطوات 1–4.
        String sa = a.subject == null ? "" : a.subject;
        String sb = b.subject == null ? "" : b.subject;
        if (sa.equalsIgnoreCase(sb) && a.isLecture() && b.isLecture()) {
            int na = lectureSequenceKey(a);
            int nb = lectureSequenceKey(b);
            if (na >= 0 && nb >= 0 && na != nb) return Integer.compare(na, nb);
        }

        // 6) جديدة قبل قديمة
        if (a.backlog != b.backlog) return a.backlog ? 1 : -1;

        // 7) اسم
        int sc = sa.compareToIgnoreCase(sb);
        if (sc != 0) return sc;
        String an = a.name == null ? "" : a.name;
        String bn = b.name == null ? "" : b.name;
        int cn = an.compareToIgnoreCase(bn);
        if (cn != 0) return cn;
        // تعادل كامل — لا ID ولا database order
        return 0;
    }

    /**
     * أول مجموعة تعادل تستحق Dialog في عملية التخطيط الحالية.
     * التعادل = نفس الأولوية + نفس طبقة إلحاح الامتحان + عمل متبقٍ.
     * الحسم لهذه العملية فقط عبر planSessionResolvedKeys / planSessionOrder — لا يُحفظ على Task.
     */
    public List<Task> findFirstScheduleTieGroup() {
        Map<String, Exam> taskExam = buildTaskExamMap();
        Map<String, Integer> taskExamDays = new HashMap<>();
        for (Map.Entry<String, Exam> en : taskExam.entrySet()) {
            int d = daysUntil(en.getValue().day);
            if (d >= 0) taskExamDays.put(en.getKey(), d);
        }
        List<Task> cands = new ArrayList<>();
        for (Task t : tasks) {
            if (t == null || t.done) continue;
            int rem = Math.max(0, t.remainingMin);
            if (rem < 1) {
                int scheduled = 0;
                for (Session s : sessions) {
                    if (s != null && t.id != null && t.id.equals(s.taskId)) {
                        scheduled += Math.max(0, s.durationMin);
                    }
                }
                rem = Math.max(0, t.durationMin - scheduled);
            }
            if (rem < 1) continue;
            cands.add(t);
        }
        boolean[] visited = new boolean[cands.size()];
        for (int i = 0; i < cands.size(); i++) {
            if (visited[i]) continue;
            List<Task> bucket = new ArrayList<>();
            Task seed = cands.get(i);
            for (int j = 0; j < cands.size(); j++) {
                if (visited[j]) continue;
                Task t = cands.get(j);
                if (t == seed || sameScheduleTieBucket(seed, t, taskExamDays)) {
                    visited[j] = true;
                    bucket.add(t);
                }
            }
            if (bucket.size() < 2) continue;
            String key = tieGroupKey(bucket);
            if (key != null && planSessionResolvedKeys.contains(key)) continue;
            return bucket;
        }
        return null;
    }

    /** نفس طبقات التعادل: أولوية + طبقة Exam Priority (urgency) قبل العوامل الناعمة. */
    private boolean sameScheduleTieBucket(Task a, Task b, Map<String, Integer> taskExamDays) {
        if (a == null || b == null) return false;
        if (taskExamDays == null) taskExamDays = new HashMap<>();
        int examDaysA = taskExamDays.containsKey(a.id) ? taskExamDays.get(a.id) : -1;
        int examDaysB = taskExamDays.containsKey(b.id) ? taskExamDays.get(b.id) : -1;
        int ida = examDaysA >= 0 ? Math.max(0, examDaysA - 2) : -1;
        int idb = examDaysB >= 0 ? Math.max(0, examDaysB - 2) : -1;
        boolean hasExamA = ida >= 0 && a.remainingMin > 0;
        boolean hasExamB = idb >= 0 && b.remainingMin > 0;
        if (hasExamA != hasExamB) return false;
        if (hasExamA && hasExamB) {
            double urgA = a.remainingMin / (double) Math.max(1, ida + 1);
            double urgB = b.remainingMin / (double) Math.max(1, idb + 1);
            // نفس الطبقة إن تقاربت urgency (لا فرق جوهري) ونفس Internal Deadline تقريبًا
            if (Math.abs(urgA - urgB) > 1e-6) return false;
            if (ida != idb) return false;
        }
        int pa = Math.max(0, Math.min(2, a.priority));
        int pb = Math.max(0, Math.min(2, b.priority));
        return pa == pb;
    }

    private static int lectureSequenceKey(Task t) {
        if (t == null) return -1;
        int n = lectureNumber(t);
        if (n >= 0) return n;
        n = extractLectureNumFromName(t.name);
        return n >= 0 ? n : -1;
    }





    /** استخراج رقم المحاضرة رقميًا من اسم المهمة (محاضرة 10 → 10) */
    public static int lectureNumber(Task t) {
        if (t == null || t.name == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(t.name);
        int last = -1;
        while (m.find()) {
            try { last = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return last;
    }

    /**
     * بعد تغيير مدة الجلسة الافتراضية: أعد حساب عدد الجلسات للمهام غير المكتملة فقط.
     * لا تلمس الجلسات المكتملة أو المقفلة (userPinned).
     */
    public void applySettingsToIncompleteTasks() {
        int sm = Math.max(10, Math.min(200, settings.sessionMin));
        for (Task t : tasks) {
            if (t.done) continue;
            if (t.durationMin <= 0) continue;
            int count = Math.max(1, (int) Math.ceil(t.durationMin / (double) sm));
            // لا تكبّر عدد الجلسات عن المدة
            if (count > t.durationMin) count = t.durationMin;
            t.sessionCount = count;
            int[] parts = Task.splitDurations(t.durationMin, count);
            t.sessionTargetMin = parts[0];
            // remaining من الجلسات المكتملة فقط
            int doneMin = 0;
            for (Session s : sessions) {
                if (t.id.equals(s.taskId) && s.done) doneMin += s.durationMin;
            }
            t.remainingMin = Math.max(0, t.durationMin - doneMin);
            if (t.remainingMin <= 0) t.done = true;
        }
        // احذف الجلسات غير المكتملة وغير المقفلة فقط — إعادة البناء تتم عند «إنشاء الجدول»
        sessions.removeIf(s -> !s.done && !s.userPinned);
        save();
    }

    /** ترتيب المهام داخل المادة حسب رقم المحاضرة التصاعدي ثم الأولوية */
    public static int compareLectureOrder(Task a, Task b) {
        int la = lectureNumber(a);
        int lb = lectureNumber(b);
        if (la >= 0 && lb >= 0 && la != lb) return Integer.compare(la, lb);
        if (la >= 0 && lb < 0) return -1;
        if (lb >= 0 && la < 0) return 1;
        // لا نستخدم priority هنا — الأولوية تُحسم في ordered.sort فقط
        return Integer.compare(a.sortOrder, b.sortOrder);
    }


    /**
     * تحديث بيانات الجلسات فقط (أولوية/اسم/مادة/backlog) بدون حذف أي Session.
     * يُستخدم عند تعديل metadata مثل Priority.
     */
    public void updateTaskSessionMetadata(Task t) {
        if (t == null) return;
        int doneMin = 0;
        int doneCount = 0;
        for (Session s : sessions) {
            if (!t.id.equals(s.taskId)) continue;
            s.priority = t.priority;
            s.backlog = t.backlog;
            s.taskName = t.name;
            s.subject = t.subject;
            if (s.done) {
                doneMin += s.durationMin;
                doneCount++;
            }
        }
        t.remainingMin = Math.max(0, t.durationMin - doneMin);
        if (t.remainingMin <= 0) t.done = true;
        int total = Math.max(t.sessionCount, doneCount);
        for (Session s : sessions) {
            if (t.id.equals(s.taskId)) s.sessionTotal = total;
        }
        save();
    }

    /** عند تغيير مدة المهمة: احذف الجلسات غير المكتملة فقط وأعد حساب المتبقي (المكتملة تبقى). */
    public void syncTaskSessions(Task t) {
        if (t == null) return;
        sessions.removeIf(s -> t.id.equals(s.taskId) && !s.done);
        int doneMin = 0;
        int doneCount = 0;
        for (Session s : sessions) {
            if (t.id.equals(s.taskId) && s.done) {
                doneMin += s.durationMin;
                doneCount++;
            }
        }
        t.remainingMin = Math.max(0, t.durationMin - doneMin);
        if (t.remainingMin <= 0) t.done = true;
        int total = Math.max(t.sessionCount, doneCount);
        for (Session s : sessions) {
            if (t.id.equals(s.taskId)) {
                s.sessionTotal = total;
                s.priority = t.priority;
                s.backlog = t.backlog;
                s.taskName = t.name;
                s.subject = t.subject;
            }
        }
        save();
    }

    /** مزامنة remainingMin لكل المهام من الجلسات المكتملة — مصدر موحّد للإحصائيات. */
    public void refreshRemainingFromSessions() {
        for (Task t : tasks) {
            if (t == null) continue;
            int doneMin = 0;
            for (Session s : sessions) {
                if (t.id.equals(s.taskId) && s.done) doneMin += Math.max(0, s.durationMin);
            }
            if (t.done) {
                t.remainingMin = 0;
            } else {
                t.remainingMin = Math.max(0, t.durationMin - doneMin);
                if (t.remainingMin <= 0) {
                    t.done = true;
                    t.remainingMin = 0;
                }
            }
        }
    }

    public int totalMinutes() {
        int n = 0;
        for (Task t : tasks) n += t.durationMin;
        return n;
    }

    public int doneMinutes() {
        // الإحصائية تعتمد على كل مهمة ككل: لو المهمة اكتملت، تُحسب مدتها كاملة
        // حتى لو كانت الجلسة التي أنهتها أقصر/جزئية. هذا يمنع ظهور 2% بعد إكمال مهمة واحدة.
        int n = 0;
        for (Task t : tasks) {
            if (t == null) continue;
            int taskTotal = Math.max(0, t.durationMin);
            if (t.done) {
                n += taskTotal;
                continue;
            }

            int taskDone = 0;
            for (Session s : sessions) {
                if (s == null || s.taskId == null || !s.taskId.equals(t.id)) continue;
                if (s.done) taskDone += Math.max(0, s.durationMin);
                else taskDone += Math.max(0, Math.min(s.durationMin, s.executedMin));
            }
            n += Math.min(taskTotal, taskDone);
        }
        return n;
    }

    public Map<String, int[]> progressBySubject() {
        Map<String, int[]> m = new HashMap<>();
        for (Task t : tasks) {
            int[] a = m.getOrDefault(t.subject, new int[]{0, 0});
            a[1] += t.durationMin;
            m.put(t.subject, a);
        }
        Set<String> counted = new HashSet<>();
        for (Session s : sessions) {
            if (!s.done) continue;
            int[] a = m.getOrDefault(s.subject, new int[]{0, 0});
            a[0] += s.durationMin;
            m.put(s.subject, a);
            counted.add(s.taskId);
        }
        for (Task t : tasks) {
            if (t.done && !counted.contains(t.id)) {
                int[] a = m.getOrDefault(t.subject, new int[]{0, 0});
                a[0] += t.durationMin;
                m.put(t.subject, a);
            }
        }
        return m;
    }

    public int backlogRemainingMin() {
        int n = 0;
        for (Task t : tasks) if (t.backlog && !t.done) n += Math.max(0, t.remainingMin);
        return n;
    }

    public void ensureTaskOrder() {
        boolean allZero = true;
        for (Task x : tasks) if (x.sortOrder != 0) { allZero = false; break; }
        if (allZero) {
            for (int i = 0; i < tasks.size(); i++) tasks.get(i).sortOrder = i;
        }
    }

    public void moveTask(Task t, int dir) {
        ensureTaskOrder();
        java.util.List<Task> g = new java.util.ArrayList<>();
        for (Task x : tasks) {
            if (x.done == t.done && x.backlog == t.backlog) g.add(x);
        }
        g.sort((a, b) -> Integer.compare(a.sortOrder, b.sortOrder));
        int i = g.indexOf(t);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= g.size()) return;
        int tmp = g.get(i).sortOrder;
        g.get(i).sortOrder = g.get(j).sortOrder;
        g.get(j).sortOrder = tmp;
        save();
    }

    public int newRemainingMin() {
        int n = 0;
        for (Task t : tasks) if (!t.backlog && !t.done) n += Math.max(0, t.remainingMin);
        return n;
    }

    public String applyChatIntent(PlanningIntentParser.Intent in) {
        if (in == null || "UNKNOWN".equals(in.type)) return "مفيش تعديل اتطبق.";
        long now = System.currentTimeMillis();
        switch (in.type) {
            case "FOCUS":
                PlanningBrain.applyPhrase(this, (in.temporary ? "الفترة دي " : "دائما ") + "اهتم ب" + in.subject);
                if (in.redistributeOnly) settings.workload = 0;
                rebuildSoft();
                return "اتطبقت أولوية " + in.subject + (in.redistributeOnly ? " من غير زيادة الوقت." : ".");
            case "UNFOCUS":
                PlanningBrain.retire(this, in.subject, now, in.raw);
                rebuildSoft();
                return "اتلغت أولوية " + in.subject + ".";
            case "REPLACE_FOCUS":
                if (in.subject2.length() > 0) PlanningBrain.retire(this, in.subject2, now, in.raw);
                PlanningBrain.applyPhrase(this, "ركزلي على " + in.subject);
                rebuildSoft();
                return "الاهتمام الحالي بقى " + in.subject + " واتشال تأثير " + in.subject2 + ".";
            case "ADD_EXAM":
                upsertExam(in.subject, in.day);
                rebuildSoft();
                return "امتحان " + in.subject + " اتسجل.";
            case "POSTPONE_EXAM":
                postponeExam(in.subject, Math.max(1, in.daysOffset));
                rebuildSoft();
                return "اتأجل امتحان " + in.subject + ".";
            case "CANCEL_EXAM":
                for (Exam e : exams) if (PlanningBrain.canon(e.subject).equals(PlanningBrain.canon(in.subject))) e.done = true;
                rebuildSoft();
                return "امتحان " + in.subject + " مش هيأثر على الخطة.";
            case "SET_SESSION":
                settings.sessionMin = Math.max(10, Math.min(200, in.minutes));
                rebuildSoft();
                return "مدة الجلسة " + settings.sessionMin + "د.";
            case "SET_BREAK":
                settings.breakMin = Math.max(1, Math.min(200, in.minutes));
                rebuildSoft();
                return "مدة الراحة " + settings.breakMin + "د.";
            case "PREVENT_NEW":
                settings.planningStyle = "new";
                rebuildSoft();
                return "التخطيط هيحمي المحاضرات الجديدة.";
            case "REST_DAY":
                try {
                    Calendar c = dayCal(in.day);
                    settings.restDays.add(c.get(Calendar.DAY_OF_WEEK));
                } catch (Exception ignored) {}
                rebuildSoft();
                return "اتضاف يوم راحة.";
            case "LIGHTEN_TODAY":
                return lightenToday();
            default:
                return "مفيش تعديل.";
        }
    }

    private void rebuildSoft() {
        save();
        buildSchedule(DIST_WEEK, 0, null);
    }

    private void upsertExam(String subject, String day) {
        if (subject == null || subject.isEmpty()) return;
        if (day == null || day.isEmpty()) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 3);
            day = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        }
        for (Exam e : exams) {
            if (PlanningBrain.canon(e.subject).equals(PlanningBrain.canon(subject)) && !e.done) {
                e.day = day;
                return;
            }
        }
        Exam e = new Exam();
        e.subject = subject;
        e.title = "امتحان " + subject;
        e.day = day;
        exams.add(e);
        if (findSubject(subject) == null) addSubject(subject);
    }

    private void postponeExam(String subject, int days) {
        for (Exam e : exams) {
            if (e.done) continue;
            if (subject != null && subject.length() > 0
                    && !PlanningBrain.canon(e.subject).equals(PlanningBrain.canon(subject))) continue;
            try {
                Calendar c = dayCal(e.day);
                c.add(Calendar.DAY_OF_YEAR, days);
                e.day = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            } catch (Exception ignored) {}
            return;
        }
    }

    private String lightenToday() {
        String today = todayStr();
        Session last = null;
        for (Session s : sessionsForDay(today)) {
            if (!s.done && !s.userPinned) last = s;
        }
        if (last == null) { save(); return "مفيش جلسة قابلة للنقل النهارده."; }
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        String nxt = DAY.format(c.getTime());
        last.day = nxt;
        save();
        return "اتنقلت «" + last.taskName + "» لـ " + dayLabelAr(nxt) + ".";
    }
}
