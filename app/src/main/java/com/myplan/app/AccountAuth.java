package com.myplan.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local Account + Authentication Foundation.
 * Offline-first. No server. No fake tokens. Study data stays in myplan_v3.
 * Credentials live only in myplan_accounts_v1 (never in study Backup).
 */
public final class AccountAuth {
    private AccountAuth() {}

    private static final String PREFS = "myplan_accounts_v1";
    private static final String KEY_ACCOUNTS = "accounts_json";
    private static final String KEY_SESSION = "session_user_id";

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public enum AuthState { LOGGED_OUT, LOGGED_IN }

    public static final class Account {
        public String userId;
        public String email;
        public String displayName;
        public String accountState; // active | deleted_local
        public long createdAt;
        public long updatedAt;
        public long lastLoginAt;
        /** Never export these in study backup */
        public String passwordSalt;
        public String passwordHash;

        public JSONObject toPublicJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("userId", userId);
            o.put("email", email);
            o.put("displayName", displayName == null ? "" : displayName);
            o.put("accountState", accountState);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            o.put("lastLoginAt", lastLoginAt);
            return o;
        }

        static Account fromJson(JSONObject o) {
            Account a = new Account();
            a.userId = o.optString("userId", "");
            a.email = o.optString("email", "");
            a.displayName = o.optString("displayName", "");
            a.accountState = o.optString("accountState", "active");
            a.createdAt = o.optLong("createdAt", 0);
            a.updatedAt = o.optLong("updatedAt", 0);
            a.lastLoginAt = o.optLong("lastLoginAt", 0);
            a.passwordSalt = o.optString("passwordSalt", "");
            a.passwordHash = o.optString("passwordHash", "");
            return a;
        }

        JSONObject toStoreJson() throws Exception {
            JSONObject o = toPublicJson();
            o.put("passwordSalt", passwordSalt == null ? "" : passwordSalt);
            o.put("passwordHash", passwordHash == null ? "" : passwordHash);
            return o;
        }
    }

    public static final class ValidationResult {
        public final boolean ok;
        public final String messageAr;
        public ValidationResult(boolean ok, String messageAr) {
            this.ok = ok;
            this.messageAr = messageAr;
        }
    }

    public static final class AuthResult {
        public final boolean ok;
        public final String messageAr;
        public final Account account;
        public AuthResult(boolean ok, String messageAr, Account account) {
            this.ok = ok;
            this.messageAr = messageAr;
            this.account = account;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static AuthState getAuthState(Context c) {
        String sid = getSessionUserId(c);
        if (sid == null || sid.isEmpty()) return AuthState.LOGGED_OUT;
        Account a = findByUserId(c, sid);
        if (a == null || !"active".equals(a.accountState)) return AuthState.LOGGED_OUT;
        return AuthState.LOGGED_IN;
    }

    public static boolean isLoggedIn(Context c) {
        return getAuthState(c) == AuthState.LOGGED_IN;
    }

    public static String getSessionUserId(Context c) {
        return sp(c).getString(KEY_SESSION, "");
    }

    public static Account getCurrentAccount(Context c) {
        if (!isLoggedIn(c)) return null;
        return findByUserId(c, getSessionUserId(c));
    }

    public static ValidationResult validateRegistration(String email, String password, String confirm, String displayName) {
        String e = email == null ? "" : email.trim();
        if (e.isEmpty()) return new ValidationResult(false, "أدخل البريد الإلكتروني.");
        if (!EMAIL_RE.matcher(e).matches()) return new ValidationResult(false, "صيغة البريد غير صحيحة.");
        if (password == null || password.isEmpty()) return new ValidationResult(false, "أدخل كلمة المرور.");
        if (password.length() < 8) return new ValidationResult(false, "كلمة المرور يجب ألا تقل عن 8 أحرف.");
        if (!password.equals(confirm)) return new ValidationResult(false, "تأكيد كلمة المرور غير متطابق.");
        if (displayName != null && displayName.trim().length() > 40) {
            return new ValidationResult(false, "الاسم المعروض طويل جدًا.");
        }
        return new ValidationResult(true, "");
    }

    public static AuthResult registerLocal(Context c, String email, String password, String confirm, String displayName) {
        ValidationResult v = validateRegistration(email, password, confirm, displayName);
        if (!v.ok) return new AuthResult(false, v.messageAr, null);
        String e = email.trim().toLowerCase(Locale.US);
        if (findByEmail(c, e) != null) {
            return new AuthResult(false, "يوجد حساب محلي بهذا البريد على هذا الجهاز.", null);
        }
        long now = System.currentTimeMillis();
        Account a = new Account();
        a.userId = newStableUserId();
        a.email = e;
        a.displayName = displayName == null ? "" : displayName.trim();
        a.accountState = "active";
        a.createdAt = now;
        a.updatedAt = now;
        a.lastLoginAt = now;
        a.passwordSalt = randomSalt();
        a.passwordHash = hashPassword(password, a.passwordSalt);
        List<Account> all = listAccounts(c);
        all.add(a);
        saveAccounts(c, all);
        setSession(c, a.userId);
        // Bridge to existing User ID slot (identity only — not installation id)
        AppInfrastructure.setUserId(c, a.userId);
        AppInfrastructure.log(c, "Account", "local_register userId=" + a.userId);
        return new AuthResult(true, "تم إنشاء الحساب المحلي.", a);
    }

    public static AuthResult loginLocal(Context c, String email, String password) {
        String e = email == null ? "" : email.trim().toLowerCase(Locale.US);
        if (e.isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(false, "أدخل البريد وكلمة المرور.", null);
        }
        Account a = findByEmail(c, e);
        if (a == null) return new AuthResult(false, "لا يوجد حساب محلي بهذا البريد على هذا الجهاز.", null);
        if (!"active".equals(a.accountState)) {
            return new AuthResult(false, "هذا الحساب غير نشط محليًا.", null);
        }
        String h = hashPassword(password, a.passwordSalt);
        if (h == null || !h.equals(a.passwordHash)) {
            return new AuthResult(false, "بيانات الدخول غير صحيحة.", null);
        }
        a.lastLoginAt = System.currentTimeMillis();
        a.updatedAt = a.lastLoginAt;
        updateAccount(c, a);
        setSession(c, a.userId);
        AppInfrastructure.setUserId(c, a.userId);
        AppInfrastructure.log(c, "Account", "local_login userId=" + a.userId);
        return new AuthResult(true, "تم تسجيل الدخول المحلي.", a);
    }

    /**
     * Ends auth session only. Does NOT delete study data, premium test, backups, or alarms.
     */
    public static void logout(Context c) {
        setSession(c, "");
        // Keep stable User ID on device for future linking; session is what matters for "logged in"
        AppInfrastructure.log(c, "Account", "local_logout");
    }

    /** Contract only — no server deletion. Marks local account deleted without wiping study data. */
    public static AuthResult deleteLocalAccountRecord(Context c) {
        Account a = getCurrentAccount(c);
        if (a == null) return new AuthResult(false, "لا يوجد حساب مسجّل الدخول.", null);
        a.accountState = "deleted_local";
        a.updatedAt = System.currentTimeMillis();
        a.passwordHash = "";
        a.passwordSalt = "";
        updateAccount(c, a);
        setSession(c, "");
        AppInfrastructure.log(c, "Account", "local_account_marked_deleted userId=" + a.userId);
        return new AuthResult(true, "تم حذف سجل الحساب المحلي فقط. بيانات الدراسة لم تُمس.", a);
    }

    // ── Interfaces for future backend (no fake server) ──

    public interface AuthenticationService {
        AuthResult register(String email, String password, String confirm, String displayName);
        AuthResult login(String email, String password);
        void logout();
        boolean isLoggedIn();
        Account currentAccount();
    }

    public static final class LocalAuthenticationService implements AuthenticationService {
        private final Context c;
        public LocalAuthenticationService(Context c) { this.c = c.getApplicationContext(); }
        @Override public AuthResult register(String email, String password, String confirm, String displayName) {
            return registerLocal(c, email, password, confirm, displayName);
        }
        @Override public AuthResult login(String email, String password) {
            return loginLocal(c, email, password);
        }
        @Override public void logout() { AccountAuth.logout(c); }
        @Override public boolean isLoggedIn() { return AccountAuth.isLoggedIn(c); }
        @Override public Account currentAccount() { return getCurrentAccount(c); }
    }

    public interface AccountRepository {
        Account findById(String userId);
        Account findByEmail(String email);
        List<Account> list();
    }

    public static final class LocalAccountRepository implements AccountRepository {
        private final Context c;
        public LocalAccountRepository(Context c) { this.c = c.getApplicationContext(); }
        @Override public Account findById(String userId) { return findByUserId(c, userId); }
        @Override public Account findByEmail(String email) {
            return AccountAuth.findByEmail(c, email == null ? "" : email.trim().toLowerCase(Locale.US));
        }
        @Override public List<Account> list() { return listAccounts(c); }
    }

    public interface SessionManager {
        AuthState state();
        String sessionUserId();
        void clearSession();
    }

    public static final class LocalSessionManager implements SessionManager {
        private final Context c;
        public LocalSessionManager(Context c) { this.c = c.getApplicationContext(); }
        @Override public AuthState state() { return getAuthState(c); }
        @Override public String sessionUserId() { return getSessionUserId(c); }
        @Override public void clearSession() { logout(c); }
    }

    public interface AccountDeletionService {
        /** Future server deletion — not implemented without backend. */
        AuthResult requestServerDeletion();
    }

    public static final class UnsupportedAccountDeletionService implements AccountDeletionService {
        @Override public AuthResult requestServerDeletion() {
            return new AuthResult(false, "حذف الحساب من السيرفر غير متاح — لا يوجد Backend حاليًا.", null);
        }
    }

    public interface EmailVerificationService {
        boolean isSupported();
        AuthResult sendVerification();
    }

    public static final class UnsupportedEmailVerificationService implements EmailVerificationService {
        @Override public boolean isSupported() { return false; }
        @Override public AuthResult sendVerification() {
            return new AuthResult(false, "تأكيد البريد يحتاج Backend حقيقي — غير مفعّل.", null);
        }
    }

    public interface PasswordRecoveryService {
        boolean isSupported();
        AuthResult requestReset(String email);
    }

    public static final class UnsupportedPasswordRecoveryService implements PasswordRecoveryService {
        @Override public boolean isSupported() { return false; }
        @Override public AuthResult requestReset(String email) {
            return new AuthResult(false, "استعادة كلمة المرور تحتاج Backend حقيقي — غير مفعّلة.", null);
        }
    }

    // ── storage helpers ──

    private static List<Account> listAccounts(Context c) {
        List<Account> out = new ArrayList<>();
        try {
            String raw = sp(c).getString(KEY_ACCOUNTS, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(Account.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    private static void saveAccounts(Context c, List<Account> list) {
        try {
            JSONArray a = new JSONArray();
            for (Account ac : list) a.put(ac.toStoreJson());
            sp(c).edit().putString(KEY_ACCOUNTS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void updateAccount(Context c, Account updated) {
        List<Account> all = listAccounts(c);
        List<Account> next = new ArrayList<>();
        for (Account a : all) {
            if (a.userId != null && a.userId.equals(updated.userId)) next.add(updated);
            else next.add(a);
        }
        saveAccounts(c, next);
    }

    private static Account findByUserId(Context c, String userId) {
        if (userId == null || userId.isEmpty()) return null;
        for (Account a : listAccounts(c)) {
            if (userId.equals(a.userId)) return a;
        }
        return null;
    }

    private static Account findByEmail(Context c, String emailLower) {
        if (emailLower == null || emailLower.isEmpty()) return null;
        for (Account a : listAccounts(c)) {
            if (emailLower.equalsIgnoreCase(a.email) && "active".equals(a.accountState)) return a;
        }
        return null;
    }

    private static void setSession(Context c, String userId) {
        sp(c).edit().putString(KEY_SESSION, userId == null ? "" : userId).apply();
    }

    private static String newStableUserId() {
        String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.US);
        return "U-" + raw.substring(0, 16);
    }

    private static String randomSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
        return sb.toString();
    }

    private static String hashPassword(String password, String salt) {
        if (password == null || salt == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // salt + password — local only; not a server credential protocol
            byte[] d = md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format(Locale.US, "%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
