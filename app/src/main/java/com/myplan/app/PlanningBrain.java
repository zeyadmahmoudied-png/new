package com.myplan.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * فهم الحالة الحالية فقط → استراتيجية → توزيع.
 * التفضيلات القديمة historical لا تدخل في الحساب.
 */
public class PlanningBrain {
    public static class Strategy {
        public String urgentFocus = "";
        public String personalFocus = "";
        public String backlogFocus = "";
        public boolean preventNewPile = true;
        public boolean respectRest = true;
        public String workload = "normal";
        public String summary = "";
    }

    public final List<String> detected = new ArrayList<>();
    public final List<String> decisions = new ArrayList<>();
    public final Map<String, Double> subjectBoost = new HashMap<>();
    public String insight = "";
    public Strategy strategy = new Strategy();
    private final Planner p;

    public PlanningBrain(Planner planner) { this.p = planner; }

    public void analyze() {
        detected.clear();
        decisions.clear();
        subjectBoost.clear();
        expireDue();
        long now = System.currentTimeMillis();
        Calendar today = startOfToday();

        int newRem = p.newRemainingMin();
        int oldRem = p.backlogRemainingMin();
        if (newRem > 0) detected.add("جديد متبقي " + newRem + "د");
        if (oldRem > 0) detected.add("تراكم " + oldRem + "د");

        Planner.Exam nearest = null;
        int nearestDays = 999;
        for (Planner.Exam e : p.exams) {
            if (e.done) continue;
            int days = daysUntil(e.day, today);
            if (days < 0) continue;
            double boost = examUrgency(days, e.importance, e.prepLevel);
            addBoost(e.subject, boost);
            detected.add("امتحان " + canon(e.subject) + " بعد " + days + "ي تحضير " + e.prepLevel + "%");
            if (days < nearestDays) { nearestDays = days; nearest = e; }
        }

        for (Planner.Focus f : p.focuses) {
            if (!f.isActive(now)) continue;
            if (!"USER_REQUEST".equals(f.reason) && !"CORRECTION".equals(f.reason)) continue;
            double w = f.strength * 2.2 * Math.max(0.4, f.confidence);
            if ("temporary".equals(f.scope)) w *= 0.85;
            addBoost(f.subject, w);
            detected.add("اهتمام حالي: " + canon(f.subject) + " (" + f.scope + ")");
        }

        String personal = topUserSubject(now);
        String urgent = nearest != null && nearestDays <= 10 ? canon(nearest.subject) : "";
        String backlogSub = biggestBacklogSubject();

        strategy = new Strategy();
        strategy.urgentFocus = urgent;
        strategy.personalFocus = personal;
        strategy.backlogFocus = backlogSub == null ? "" : backlogSub;
        strategy.preventNewPile = newRem > 0;
        strategy.respectRest = true;
        strategy.workload = p.settings.workload == 0 ? "light" : (p.settings.workload == 2 ? "high" : "normal");

        if (urgent.length() > 0) decisions.add("أولوية مؤقتة للامتحان: " + urgent);
        if (personal.length() > 0 && !personal.equals(urgent))
            decisions.add("اهتمام شخصي حالي: " + personal + " من غير ما يلغي الامتحان");
        if (strategy.preventNewPile) decisions.add("الجديد مايتراكمش");
        if (strategy.backlogFocus.length() > 0) decisions.add("تقدم تدريجي في تراكم " + strategy.backlogFocus);
        if ("light".equals(strategy.workload)) decisions.add("ضغط خفيف باختيارك");

        insight = buildInsight(urgent, personal, strategy.backlogFocus, nearestDays, newRem, oldRem);
        strategy.summary = insight;
        p.settings.lastInsight = insight;
    }

    public double scoreTask(Planner.Task t) {
        if (t == null) return 0;
        String sub = canon(t.subject);
        double s = Planner.PRIORITY_WEIGHT[Math.max(0, Math.min(2, t.priority))] * 50.0;
        if (!t.backlog) s += strategy.preventNewPile ? 14 : 7;
        else s += 5;
        if (sub.equals(strategy.urgentFocus)) s += 22;
        if (sub.equals(strategy.personalFocus) && !sub.equals(strategy.urgentFocus)) s += 14;
        if (t.backlog && sub.equals(strategy.backlogFocus)) s += 9;
        Double extra = subjectBoost.get(sub);
        if (extra != null) s += extra;
        if (t.deadline != null && t.deadline.length() >= 8) {
            int days = daysUntil(t.deadline, startOfToday());
            if (days >= 0 && days <= 2) s += 16;
            else if (days <= 7) s += 8;
        }
        return s;
    }

    public static void applyPhrase(Planner planner, String raw) {
        if (raw == null) return;
        String text = raw.trim();
        if (text.isEmpty()) return;
        planner.settings.planningNote = text;
        String n = normalize(text);
        long now = System.currentTimeMillis();

        if (contains(n, "اخف", "خفيف", "مرهق")) planner.settings.workload = 0;
        if (contains(n, "جلسات طويله", "جلسه طويله"))
            planner.settings.sessionMin = Math.max(planner.settings.sessionMin, 90);
        if (contains(n, "الجديد يتراكم", "الجديد مايتراكم", "المحاضرات الجديده"))
            planner.settings.planningStyle = "new";
        if (contains(n, "تراكم", "القديم", "الباك")) planner.settings.planningStyle = "backlog";
        if (contains(n, "امتحان", "متوتر")) planner.settings.planningStyle = "exam";

        boolean forever = contains(n, "دايما", "دائما", "على طول");
        boolean period = contains(n, "الفتره دي", "الفترة دي", "الاسبوع", "الأسبوع") || !forever;
        boolean correction = contains(n, "خلاص", "مش عايز", "مش عايزه", "بدل", "مش هركز", "بطل");

        List<String> mentioned = extractSubjects(planner, n);
        List<String> negated = extractNegated(n, mentioned);

        for (String sub : negated) {
            retire(planner, sub, now, text);
        }

        List<String> positive = new ArrayList<>();
        for (String sub : mentioned) if (!negated.contains(sub)) positive.add(sub);

        // "ركزلي على X" replaces other personal focuses
        boolean replace = contains(n, "ركزلي", "ركز على", "بدل") || (correction && !positive.isEmpty());
        if (replace) {
            for (Planner.Focus f : planner.focuses) {
                if (!f.isActive(now)) continue;
                if ("USER_REQUEST".equals(f.reason) || "CORRECTION".equals(f.reason)) {
                    if (!positive.contains(canon(f.subject))) {
                        f.scope = "historical";
                        f.expiresAt = now;
                    }
                }
            }
        }

        for (String sub : positive) {
            Planner.Focus f = new Planner.Focus();
            f.subject = sub;
            f.reason = correction ? "CORRECTION" : "USER_REQUEST";
            f.strength = 4;
            f.confidence = forever ? 0.95f : 0.75f;
            f.scope = forever ? "current" : "temporary";
            f.createdAt = now;
            f.expiresAt = forever ? 0 : now + 14L * 24 * 60 * 60 * 1000;
            f.text = text;
            // replace previous active same subject
            for (Planner.Focus old : planner.focuses) {
                if (canon(old.subject).equals(sub) && old.isActive(now)
                        && ("USER_REQUEST".equals(old.reason) || "CORRECTION".equals(old.reason))) {
                    old.scope = "historical";
                    old.expiresAt = now;
                }
            }
            planner.focuses.add(f);
        }
    }

    static void retire(Planner planner, String subject, long now, String text) {
        String sub = canon(subject);
        for (Planner.Focus f : planner.focuses) {
            if (canon(f.subject).equals(sub) && f.isActive(now)
                    && ("USER_REQUEST".equals(f.reason) || "CORRECTION".equals(f.reason))) {
                f.scope = "historical";
                f.expiresAt = now;
                f.text = (f.text == null ? "" : f.text) + " | ألغي: " + text;
            }
        }
    }

    private void expireDue() {
        long now = System.currentTimeMillis();
        for (Planner.Focus f : p.focuses) {
            if (!"historical".equals(f.scope) && f.expiresAt > 0 && now > f.expiresAt) {
                f.scope = "historical";
            }
        }
    }

    private String topUserSubject(long now) {
        Planner.Focus best = null;
        for (Planner.Focus f : p.focuses) {
            if (!f.isActive(now)) continue;
            if (!"USER_REQUEST".equals(f.reason) && !"CORRECTION".equals(f.reason)) continue;
            if (best == null || f.createdAt > best.createdAt || f.strength > best.strength) best = f;
        }
        return best == null ? "" : canon(best.subject);
    }

    private String biggestBacklogSubject() {
        Map<String, Integer> m = new HashMap<>();
        for (Planner.Task t : p.tasks) {
            if (!t.backlog || t.done) continue;
            String s = canon(t.subject);
            m.put(s, m.getOrDefault(s, 0) + Math.max(0, t.remainingMin));
        }
        String best = "";
        int n = 0;
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    private String buildInsight(String urgent, String personal, String backlog, int days, int newRem, int oldRem) {
        List<String> parts = new ArrayList<>();
        if (urgent.length() > 0 && days <= 7)
            parts.add("امتحان " + urgent + " بعد " + days + " يوم فرفعته مؤقتًا");
        if (personal.length() > 0 && !personal.equals(urgent))
            parts.add("حافظت على اهتمامك الحالي بـ" + personal);
        if (newRem > 0) parts.add("منعت تراكم الجديد");
        if (backlog.length() > 0 && oldRem > 0)
            parts.add("سيبت تقدم بسيط لتراكم " + backlog);
        if (parts.isEmpty()) return "وزّعت الوقت على المتاح دلوقتي من غير ما أكسر النوم أو الالتزامات.";
        StringBuilder sb = new StringBuilder("رتّبت الجدول لأن ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0 && i == parts.size() - 1) sb.append("، و");
            else if (i > 0) sb.append("، ");
            sb.append(parts.get(i));
        }
        sb.append(".");
        return sb.toString();
    }

    private void addBoost(String subject, double v) {
        String k = canon(subject);
        if (k.isEmpty()) return;
        subjectBoost.put(k, subjectBoost.getOrDefault(k, 0.0) + v);
    }

    private double examUrgency(int days, int importance, int prep) {
        double u = days <= 1 ? 20 : days <= 2 ? 16 : days <= 4 ? 11 : days <= 10 ? 6 : days <= 30 ? 2 : 0.4;
        double gap = (100 - Math.max(0, Math.min(100, prep))) / 18.0;
        return u * (1.0 + importance * 0.2) + gap;
    }

    static Calendar startOfToday() {
        Calendar c = Planner.nowLocal();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    static int daysUntil(String day, Calendar today) {
        try {
            Calendar c = Planner.dayCal(day);
            return (int) ((c.getTimeInMillis() - today.getTimeInMillis()) / 86400000L);
        } catch (Exception e) { return 999; }
    }

    static String normalize(String s) {
        if (s == null) return "";
        String t = s.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
                .replace("ة", "ه").replace("ى", "ي").replace("ؤ", "و").replace("ئ", "ي");
        t = t.replace("الانجليزي", "الانجليزي").replace("الانجليزى", "الانجليزي");
        return t;
    }

    static String canon(String s) {
        String n = normalize(s).trim();
        if (n.contains("نجليز")) return "إنجليزي";
        if (n.contains("كيميا")) return "كيمياء";
        if (n.contains("فيزيا")) return "فيزياء";
        if (n.contains("رياض")) return "رياضيات";
        if (s == null) return "";
        return s.trim();
    }

    static boolean contains(String n, String... keys) {
        for (String k : keys) if (n.contains(normalize(k))) return true;
        return false;
    }

    static List<String> knownSubjects(Planner planner) {
        List<String> known = new ArrayList<>();
        String[] base = {"إنجليزي", "كيمياء", "فيزياء", "رياضيات", "عربي", "أحياء", "تاريخ", "جغرافيا", "فرنساوي"};
        for (String b : base) known.add(b);
        for (Planner.Task t : planner.tasks) {
            if (t.subject != null && t.subject.trim().length() > 0 && !known.contains(t.subject.trim()))
                known.add(t.subject.trim());
        }
        for (Planner.Exam e : planner.exams) {
            if (e.subject != null && e.subject.trim().length() > 0 && !known.contains(e.subject.trim()))
                known.add(e.subject.trim());
        }
        return known;
    }

    static List<String> extractSubjects(Planner planner, String normalized) {
        List<String> out = new ArrayList<>();
        for (String s : knownSubjects(planner)) {
            String ns = normalize(s);
            if (ns.length() >= 3 && normalized.contains(ns)) {
                String c = canon(s);
                if (!out.contains(c)) out.add(c);
            }
        }
        return out;
    }

    static List<String> extractNegated(String normalized, List<String> mentioned) {
        List<String> out = new ArrayList<>();
        // "مش عايز ... الإنجليزي" or "خلاص الإنجليزي"
        for (String s : mentioned) {
            String ns = normalize(s);
            int i = normalized.indexOf(ns);
            if (i < 0) continue;
            String before = normalized.substring(Math.max(0, i - 18), i);
            if (before.contains("مش عايز") || before.contains("مش عايزه") || before.contains("خلاص")
                    || before.contains("بطل") || before.contains("مش هركز")) {
                out.add(s);
            }
        }
        return out;
    }
}
