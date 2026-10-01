package com.myplan.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** فهم أوامر التخطيط من صياغة حرة بالعربي المصري قدر الإمكان، من غير API. */
public class PlanningIntentParser {
    public static class Intent {
        public String type = "UNKNOWN";
        public String subject = "";
        public String subject2 = "";
        public int minutes = 0;
        public int daysOffset = 0;
        public String day = "";
        public boolean temporary = false;
        public boolean redistributeOnly = false;
        public String raw = "";
        public String reply = "";
        public String proposal = "";
    }

    public static class SessionState {
        public String lastType = "";
        public String lastSubject = "";
        public boolean lastTemp = false;
    }

    public static Intent parse(Planner planner, String raw, SessionState st) {
        Intent i = new Intent();
        i.raw = raw == null ? "" : raw.trim();
        String n = PlanningBrain.normalize(i.raw);
        n = n.replace("الـ", "ال").replace("ال ", "ال");

        List<String> subs = subjectsIn(planner, n);
        List<String> negated = PlanningBrain.extractNegated(n, subs);
        // broader negation: مش + subject anywhere
        if (contains(n, "مش عايز", "مش عايزه", "مش هركز", "مش مهتم", "بطل", "بلاش", "خلاص من")) {
            for (String s : subs) if (!negated.contains(s)) {
                int pos = n.indexOf(PlanningBrain.normalize(s));
                if (pos >= 0) {
                    String before = n.substring(0, pos);
                    if (before.contains("مش") || before.contains("بلاش") || before.contains("بطل") || before.contains("خلاص"))
                        negated.add(s);
                }
            }
        }
        List<String> positive = new ArrayList<>();
        for (String s : subs) if (!negated.contains(s)) positive.add(s);

        boolean no = contains(n, "مش عايز", "مش عايزه", "خلاص بلاش", "بلاش", "غير رايي", "غير رأيي",
                "مش هركز", "بطل", "مش مهتم", "اتلغى", "الغي", "مش عايز اهتم");
        boolean period = contains(n, "الفتره دي", "الفترة دي", "الاسبوع", "الأسبوع", "كام يوم", "مؤقت");
        boolean noMoreTime = contains(n, "متزودش", "ما تزودش", "من غير زياده", "من غير زيادة",
                "متزودش وقت", "من غير وقت", "تقيلش", "من غير مبالغه", "من غير مبالغة");

        // follow-up constraint
        if (noMoreTime) {
            String sub = first(positive);
            if (sub.isEmpty() && st != null) sub = st.lastSubject;
            if (sub.length() > 0) {
                i.type = "FOCUS";
                i.subject = sub;
                i.temporary = st != null && st.lastTemp || period;
                i.redistributeOnly = true;
                i.reply = "هدي " + sub + " أولوية عن طريق إعادة التوزيع، من غير زيادة وقت المذاكرة.";
                i.proposal = "اهتمام " + sub + " + ضغط أخف.";
                return i;
            }
        }

        if (contains(n, "كمان", "بردو", "برضو", "ونفس", "نفس الكلام") && st != null && st.lastSubject.length() > 0) {
            if (positive.isEmpty()) {
                i.type = st.lastType.length() == 0 ? "FOCUS" : st.lastType;
                i.subject = st.lastSubject;
            } else {
                i.type = "FOCUS";
                i.subject = first(positive);
            }
            i.temporary = period || (st != null && st.lastTemp);
            i.reply = "هطبق نفس الاتجاه على " + i.subject + ".";
            i.proposal = "إضافة اهتمام " + i.subject;
            if (!"FOCUS".equals(i.type) && !"UNFOCUS".equals(i.type)) i.type = "FOCUS";
            return i;
        }

        if (isQuery(n)) return answerQuery(planner, n, subs);

        if (contains(n, "رجع اللي كان", "ارجع", "undo", "اللي فات")) {
            i.type = "UNDO";
            i.reply = "أقدر ألغي آخر اهتمام اتعمل من الشات.";
            i.proposal = "إلغاء آخر تعديل اهتمام.";
            return i;
        }

        if ((contains(n, "راحه", "راحة") && contains(n, "يوم")) || n.contains("يوم اجازه") || n.contains("يوم إجازة")) {
            i.type = "REST_DAY";
            i.day = suggestRestDay(planner);
            i.reply = "أنسب يوم راحة قريب من غير امتحان: " + labelDay(i.day) + ".";
            i.proposal = "تعيين " + labelDay(i.day) + " يوم راحة وإعادة التخطيط.";
            return i;
        }

        if (contains(n, "تقيل", "زحمه", "زحمة", "خنقه", "خنقة", "مش لاحق", "كثير جلسات", "كتير جلسات", "خفف اليوم", "خفف الجدول")) {
            i.type = "LIGHTEN_TODAY";
            i.reply = lightenPreview(planner);
            i.proposal = i.reply;
            return i;
        }

        int sess = extractDuration(n);
        if (sess > 0 && contains(n, "جلسه", "جلسة", "مذاكره", "مذاكرة", "الجلسة")) {
            i.type = "SET_SESSION";
            i.minutes = clamp(sess, 10, 200);
            i.reply = "مدة الجلسة هتبقى " + i.minutes + " دقيقة.";
            i.proposal = "تعديل مدة الجلسة إلى " + i.minutes + "د وإعادة التخطيط.";
            return i;
        }
        if (sess > 0 && contains(n, "راحه", "راحة", "بريك", "فاصل")) {
            i.type = "SET_BREAK";
            i.minutes = clamp(sess, 1, 200);
            i.reply = "مدة الراحة هتبقى " + i.minutes + " دقيقة.";
            i.proposal = "تعديل الراحة إلى " + i.minutes + "د وإعادة التخطيط.";
            return i;
        }

        if (contains(n, "اتاجل", "تاجل", "اتأجل", "تأجل", "اجلوه", "أجّلوه", "الاسبوع الجاي", "الأسبوع الجاي", "اسبوع كمان")) {
            i.type = "POSTPONE_EXAM";
            i.subject = first(positive.isEmpty() ? subs : positive);
            if (i.subject.isEmpty() && st != null) i.subject = st.lastSubject;
            i.daysOffset = contains(n, "اسبوع", "أسبوع") ? 7 : 3;
            i.reply = i.subject.isEmpty() ? "قول مادة الامتحان اللي اتأجل." :
                    "هأجل امتحان " + i.subject + " حوالي " + i.daysOffset + " أيام.";
            i.proposal = i.subject.isEmpty() ? "" : "تعديل موعد امتحان " + i.subject + " وإعادة الحساب.";
            return i;
        }

        if (contains(n, "امتحان") && (no || contains(n, "اتلغى", "الغي", "مش داخل", "مش هدخل"))) {
            i.type = "CANCEL_EXAM";
            i.subject = first(subs);
            i.reply = i.subject.isEmpty() ? "قول امتحان أنهي مادة." : "امتحان " + i.subject + " مش هيأثر على الخطة.";
            i.proposal = i.subject.isEmpty() ? "" : "إنهاء تأثير امتحان " + i.subject;
            return i;
        }

        if (contains(n, "امتحان", "اختبار", "ميد ترم", "ميدتيرم", "فاينل")) {
            i.type = "ADD_EXAM";
            i.subject = first(positive.isEmpty() ? subs : positive);
            i.day = parseDayWord(n);
            i.reply = i.subject.isEmpty() ? "قول مادة الامتحان والتاريخ لو تقدر." :
                    ("هسجّل امتحان " + i.subject + (i.day.isEmpty() ? "" : " " + labelDay(i.day)) + ".");
            i.proposal = i.subject.isEmpty() ? "" : "إضافة/تحديث امتحان " + i.subject + " ورفع أولويته حسب القرب.";
            return i;
        }

        if (contains(n, "الجديد مايتراكم", "الجديد يتراكم", "المحاضرات الجديده", "المحاضرات الجديدة", "متخلينيش اتأخر في الجديد")) {
            i.type = "PREVENT_NEW";
            i.reply = "هخلي الجدول يحمي المحاضرات الجديدة الأول.";
            i.proposal = "أسلوب التخطيط: منع تراكم الجديد.";
            return i;
        }

        if (contains(n, "تراكم", "القديم", "الباك لوج", "باكالوج", "اللي متراكم") && positive.isEmpty() && negated.isEmpty()) {
            i.type = "PREVENT_NEW";
            i.reply = "هخلي جزء أوضح من الوقت للتراكم.";
            planner.settings.planningStyle = "backlog";
            i.type = "PREVENT_NEW";
            i.proposal = "الميل نحو تقليل التراكم.";
            return i;
        }

        if ((no && !positive.isEmpty()) || (negated.size() > 0 && positive.size() > 0)) {
            i.type = "REPLACE_FOCUS";
            i.subject2 = first(negated);
            i.subject = first(positive);
            if (i.subject2.isEmpty()) i.subject2 = st != null ? st.lastSubject : "";
            i.temporary = period;
            i.reply = "هشيل تأثير " + i.subject2 + " وأركّز على " + i.subject + ".";
            i.proposal = "استبدال الاهتمام: " + i.subject2 + " ← " + i.subject;
            return i;
        }

        if (no && !negated.isEmpty()) {
            i.type = "UNFOCUS";
            i.subject = first(negated);
            i.reply = "هوقف أولوية " + i.subject + " من غير ما أمسح المادة.";
            i.proposal = "إلغاء اهتمام " + i.subject;
            return i;
        }

        if (!positive.isEmpty() && (contains(n, "اهتم", "ركز", "ركزلي", "اولويه", "أولوية", "حابب", "عايز",
                "عاوز", "زود", "زوّد", "مهم", "الاهم", "الأهم", "خليني", " dil"))) {
            i.type = "FOCUS";
            i.subject = first(positive);
            i.temporary = period || !contains(n, "دايما", "دائما", "على طول");
            i.reply = "هخلي " + i.subject + (i.temporary ? " اهتمام الفترة دي." : " اهتمام مستمر.");
            i.proposal = "رفع أولوية " + i.subject + " وإعادة توزيع الجلسات.";
            return i;
        }

        // subject only + short verb-less follow up after a planning chat
        if (!positive.isEmpty() && n.length() < 24 && st != null && st.lastType.length() > 0) {
            i.type = "FOCUS";
            i.subject = first(positive);
            i.temporary = true;
            i.reply = "هعتبر " + i.subject + " هو المطلوب دلوقتي.";
            i.proposal = "اهتمام " + i.subject;
            return i;
        }

        if (!positive.isEmpty()) {
            i.type = "FOCUS";
            i.subject = first(positive);
            i.temporary = true;
            i.reply = "فاهم إن الكلام عن " + i.subject + ". أرفعه كاهتمام مؤقت؟";
            i.proposal = "رفع أولوية " + i.subject;
            return i;
        }

        i.reply = "مش ماسك طلب تخطيط واضح. جرّب: اهتمام بمادة، امتحان، تخفيف اليوم، يوم راحة، مدة جلسة، أو راحة.";
        return i;
    }

    static boolean isQuery(String n) {
        return contains(n, "ايه الجدول", "عامل ايه", "كام جلسه", "كام جلسة", "عندي كام",
                "ايه الاولويه", "ايه الأولوية", "وريني", "ملخص", "امتى امتحان");
    }

    static Intent answerQuery(Planner p, String n, List<String> subs) {
        Intent i = new Intent();
        i.type = "UNKNOWN";
        String today = Planner.todayStr();
        List<Planner.Session> list = p.sessionsForDay(today);
        int open = 0;
        for (Planner.Session s : list) if (!s.done) open++;
        StringBuilder sb = new StringBuilder();
        sb.append("النهاردة ").append(list.size()).append(" جلسات، فاضل ").append(open).append(".");
        Planner.Exam near = null; int days = 999;
        Calendar t = PlanningBrain.startOfToday();
        for (Planner.Exam e : p.exams) {
            if (e.done) continue;
            int d = PlanningBrain.daysUntil(e.day, t);
            if (d >= 0 && d < days) { days = d; near = e; }
        }
        if (near != null) sb.append(" أقرب امتحان ").append(near.subject).append(" بعد ").append(days).append(" يوم.");
        String pref = "";
        long now = System.currentTimeMillis();
        for (Planner.Focus f : p.focuses) if (f.isActive(now)) { pref = f.subject; break; }
        if (pref.length() > 0) sb.append(" الاهتمام الحالي: ").append(pref).append(".");
        i.reply = sb.toString();
        return i;
    }

    static List<String> subjectsIn(Planner p, String n) {
        List<String> out = new ArrayList<>();
        List<String> known = new ArrayList<>();
        for (Planner.Subject s : p.subjects) known.add(s.name);
        known.addAll(PlanningBrain.knownSubjects(p));
        for (String s : known) {
            String ns = PlanningBrain.normalize(s);
            if (ns.startsWith("ال") && ns.length() > 3) ns = ns.substring(2);
            String hay = n;
            if (hay.contains(PlanningBrain.normalize(s)) || (ns.length() >= 3 && hay.contains(ns))) {
                Planner.Subject hit = p.findSubject(s);
                String use = hit != null ? hit.name : PlanningBrain.canon(s);
                if (!out.contains(use)) out.add(use);
            }
        }
        return out;
    }

    static boolean contains(String n, String... keys) {
        return PlanningBrain.contains(n, keys);
    }

    static String first(List<String> a) { return a == null || a.isEmpty() ? "" : a.get(0); }
    static String first(String s) { return s == null ? "" : s; }
    static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    static int extractDuration(String n) {
        if (n.contains("ساعتين ونص") || n.contains("ساعتين ونصف")) return 150;
        if (n.contains("ساعتين")) return 120;
        if (n.contains("ساعه ونص") || n.contains("ساعة ونص") || n.contains("ساعه ونصف")) return 90;
        if (n.contains("ساعه") || n.contains("ساعة")) return 60;
        if (n.contains("نص ساعه") || n.contains("نص ساعة") || n.contains("نصف ساعه")) return 30;
        if (n.contains("ربع ساعه") || n.contains("ربع ساعة")) return 15;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*(د|دقيق)").matcher(n);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*ساع").matcher(n);
        if (m.find()) return Integer.parseInt(m.group(1)) * 60;
        return 0;
    }

    static String parseDayWord(String n) {
        if (n.contains("السبت")) return offsetToDow(Calendar.SATURDAY);
        if (n.contains("الاحد") || n.contains("الأحد")) return offsetToDow(Calendar.SUNDAY);
        if (n.contains("الاثنين") || n.contains("الإثنين")) return offsetToDow(Calendar.MONDAY);
        if (n.contains("الثلاثاء")) return offsetToDow(Calendar.TUESDAY);
        if (n.contains("الاربعاء") || n.contains("الأربعاء")) return offsetToDow(Calendar.WEDNESDAY);
        if (n.contains("الخميس")) return offsetToDow(Calendar.THURSDAY);
        if (n.contains("الجمعه") || n.contains("الجمعة")) return offsetToDow(Calendar.FRIDAY);
        if (n.contains("بكره") || n.contains("بكرة") || n.contains("غدا") || n.contains("غدًا")) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 1);
            return fmt(c);
        }
        if (n.contains("بعد بكره") || n.contains("بعد بكرة")) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 2);
            return fmt(c);
        }
        return "";
    }

    static String offsetToDow(int dow) {
        Calendar t = Calendar.getInstance();
        if (t.get(Calendar.DAY_OF_WEEK) == dow) return fmt(t);
        return fmt(nextDow(dow));
    }

    static Calendar nextDow(int dow) {
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < 8; i++) {
            if (c.get(Calendar.DAY_OF_WEEK) == dow) return c;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c;
    }

    static String fmt(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String labelDay(String ymd) {
        if (ymd == null || ymd.isEmpty()) return "يوم مناسب";
        return Planner.dayLabelAr(ymd);
    }

    static String suggestRestDay(Planner p) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        for (int i = 0; i < 7; i++) {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            boolean exam = false;
            String day = fmt(c);
            for (Planner.Exam e : p.exams) if (day.equals(e.day)) exam = true;
            if (!exam && !p.settings.restDays.contains(dow)) return day;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return fmt(Calendar.getInstance());
    }

    static String lightenPreview(Planner p) {
        String today = Planner.todayStr();
        List<Planner.Session> list = p.sessionsForDay(today);
        int open = 0;
        Planner.Session last = null;
        for (Planner.Session s : list) {
            if (s.done || s.userPinned) continue;
            open++;
            last = s;
        }
        if (last == null) return "جدول اليوم مش زحمة بما يكفي لأنقل جلسة.";
        return "اليوم فيه " + open + " جلسات ناقصة. أقدر أنقل «" + last.taskName + "» لبكرة وأخفف اليوم.";
    }
}
