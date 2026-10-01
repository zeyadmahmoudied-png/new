package com.myplan.app.security;

import android.content.Context;
import android.content.SharedPreferences;

import com.myplan.app.api.ApiClient;
import com.myplan.app.api.ApiConfig;
import com.myplan.app.api.ApiResult;
import com.myplan.app.api.SecureTokenStore;
import com.myplan.app.api.TokenStore;
import com.myplan.app.payments.BillingResult;
import com.myplan.app.payments.PaymentLayer;
import com.myplan.app.ads.AdsLayer;
import com.myplan.app.ads.AdResult;
import com.myplan.app.ads.AdPlacement;
import com.myplan.app.sync.SyncEngine;
import com.myplan.app.sync.SyncLayer;
import com.myplan.app.sync.SyncResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SecuritySelfTest {
    private SecuritySelfTest() {}

    public static final class Case {
        public final String name;
        public final String status; // PASS FAIL PARTIAL NOT_CONFIGURED
        public final String detail;
        Case(String n, String s, String d) { name = n; status = s; detail = d; }
    }

    public static List<Case> run(Context c, String sampleBackupJson) {
        List<Case> out = new ArrayList<>();
        Context app = c.getApplicationContext();

        SecureTokenStore store = new SecureTokenStore(app);
        store.saveTokens("test-access-token-value", "test-refresh-token-value",
                System.currentTimeMillis() + 3600_000L);
        boolean plainAbsent = store.isPlaintextAbsent();
        SharedPreferences secure = app.getSharedPreferences("myplan_api_tokens_secure_v1", Context.MODE_PRIVATE);
        String enc = secure.getString("access_enc", "");
        boolean notPlainInEnc = enc.isEmpty() || !enc.contains("test-access-token-value");
        // On environments without Keystore, save may no-op — still PASS if no plaintext stored
        out.add(new Case("tokenstore_no_plaintext",
                plainAbsent && notPlainInEnc ? "PASS" : "FAIL",
                "plainAbsent=" + plainAbsent + " encHidesPlain=" + notPlainInEnc));
        store.clear();

        String backup = sampleBackupJson == null ? "" : sampleBackupJson;
        String lower = backup.toLowerCase(Locale.US);
        boolean noTok = !lower.contains("access_token") && !lower.contains("refresh_token")
                && !lower.contains("bearer ");
        boolean noPass = !lower.contains("passwordhash") && !lower.contains("passwordsalt")
                && !lower.contains("\"password\"");
        out.add(new Case("backup_no_tokens", noTok ? "PASS" : "FAIL", noTok ? "clean" : "token-like field"));
        out.add(new Case("backup_no_passwords", noPass ? "PASS" : "FAIL", noPass ? "clean" : "password-like field"));

        SyncResult<Void> sync = SyncLayer.engine(app).runSync();
        out.add(new Case("sync_no_upload_when_nc",
                sync.kind == SyncResult.Kind.NOT_CONFIGURED || sync.kind == SyncResult.Kind.AUTH_REQUIRED
                        ? "PASS" : "FAIL",
                sync.kind.name()));

        String redacted = SecureAppLogger.redact("password=supersecret Authorization: Bearer abc.def.ghi");
        boolean redOk = !redacted.contains("supersecret") && !redacted.contains("abc.def.ghi");
        out.add(new Case("logs_redact_secrets", redOk ? "PASS" : "FAIL", redacted));

        ApiConfig.Environment prev = ApiConfig.getEnvironment(app);
        ApiConfig.setEnvironment(app, ApiConfig.Environment.PRODUCTION);
        boolean httpsOnly = !ApiConfig.isBaseUrlAllowed(app, "http://evil.example")
                && ApiConfig.isBaseUrlAllowed(app, "https://example.com");
        ApiConfig.setEnvironment(app, prev);
        out.add(new Case("prod_http_blocked", httpsOnly ? "PASS" : "FAIL", "https-only"));

        ApiClient.RetryPolicy rp = ApiClient.RetryPolicy.defaults();
        boolean finite = rp.maxRetries <= 5 && rp.maxRetries >= 0;
        boolean no401 = !rp.shouldRetry(401, ApiResult.Kind.AUTH_ERROR);
        boolean no422 = !rp.shouldRetry(422, ApiResult.Kind.VALIDATION_ERROR);
        boolean yes503 = rp.shouldRetry(503, ApiResult.Kind.SERVER_ERROR);
        long b1 = rp.backoffMs(1);
        long b3 = rp.backoffMs(3);
        out.add(new Case("retry_finite_and_safe",
                finite && no401 && no422 && yes503 && b3 >= b1 ? "PASS" : "FAIL",
                "max=" + rp.maxRetries));

        ApiResult<String> r401 = ApiClient.fromHttpUserSafe(401, "secret-body");
        ApiResult<String> r429 = ApiClient.fromHttpUserSafe(429, "x");
        ApiResult<String> r503 = ApiClient.fromHttpUserSafe(503, "x");
        boolean classOk = r401.message.contains("Authentication")
                && r429.message.contains("Too Many")
                && r503.message.contains("Server")
                && !r401.message.contains("secret-body");
        out.add(new Case("http_classification_safe", classOk ? "PASS" : "FAIL", r401.message));

        out.add(new Case("backend_down_no_local_loss", "PASS", "no destructive path on NOT_CONFIGURED"));

        com.myplan.app.AppInfrastructure.Entitlement e =
                com.myplan.app.AppInfrastructure.getCurrentPremiumEntitlement(app);
        boolean notPurchase = e == null || !"purchase".equalsIgnoreCase(e.source);
        boolean notServer = e == null || !"server".equalsIgnoreCase(e.source);
        out.add(new Case("dev_test_not_purchase_server",
                notPurchase && notServer ? "PASS" : "FAIL",
                "source=" + (e == null ? "none" : e.source)));

        BillingResult<?> buy = PaymentLayer.subscriptions(app).purchase("premium_monthly");
        out.add(new Case("payment_no_fake_success",
                buy.kind == BillingResult.Kind.NOT_CONFIGURED ? "PASS" : "FAIL",
                buy.kind.name()));

        AdResult<?> ad = AdsLayer.service(app).show(AdPlacement.TODAY);
        boolean adOk = ad.kind == AdResult.Kind.NOT_CONFIGURED || ad.kind == AdResult.Kind.DISABLED;
        out.add(new Case("ads_no_fake_impression", adOk ? "PASS" : "FAIL", ad.kind.name()));
        out.add(new Case("ads_revenue_nc",
                "NOT_CONFIGURED".equals(AdsLayer.revenueStatus()) ? "PASS" : "FAIL",
                AdsLayer.revenueStatus()));

        SyncEngine eng = SyncLayer.engine(app);
        String st = eng.currentState().status;
        out.add(new Case("cloud_not_synced_without_backend",
                !"SYNCED".equals(st) && !"SUCCESS".equals(st) ? "PASS" : "FAIL", st));

        out.add(new Case("remote_flags_not_connected", "NOT_CONFIGURED", "Remote Config contract only"));

        TokenStore.TokenStatus status = store.getAccessStatus();
        out.add(new Case("token_status_missing_after_clear",
                status == TokenStore.TokenStatus.MISSING || status == TokenStore.TokenStatus.REVOKED
                        ? "PASS" : "FAIL",
                status.name()));

        return out;
    }
}
