package com.myplan.app.api;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline self-tests for API layer behavior. Does not call a real network by default.
 * Mock repository is test-only — never used as production auth.
 */
public final class ApiLayerSelfTest {
    private ApiLayerSelfTest() {}

    public static final class Case {
        public String name;
        public boolean pass;
        public String detail;
        Case(String name, boolean pass, String detail) {
            this.name = name;
            this.pass = pass;
            this.detail = detail;
        }
    }

    public static List<Case> run(Context c) {
        List<Case> out = new ArrayList<>();
        // NOT_CONFIGURED path
        ApiResult<String> health = new ApiClient(c).health();
        out.add(new Case("health_not_configured",
                health.kind == ApiResult.Kind.NOT_CONFIGURED,
                health.kind + " · " + health.message));

        ApiResult<RemoteModels.RemoteUser> login = ApiLayer.auth(c).login("a@b.co", "x");
        out.add(new Case("remote_login_not_configured",
                login.kind == ApiResult.Kind.NOT_CONFIGURED,
                login.kind.name()));

        ApiResult<RemoteModels.RemoteEntitlement> ent = ApiLayer.entitlements(c).fetchForCurrentUser();
        out.add(new Case("server_entitlement_not_connected",
                ent.kind == ApiResult.Kind.NOT_CONFIGURED,
                ent.kind.name()));

        // Mock-only classification tests
        out.add(mockCase("mock_success", ApiResult.success("ok"), ApiResult.Kind.SUCCESS));
        out.add(mockCase("mock_timeout", ApiResult.timeout("t"), ApiResult.Kind.TIMEOUT));
        out.add(mockCase("mock_network", ApiResult.network("n"), ApiResult.Kind.NETWORK_ERROR));
        out.add(mockCase("mock_401", ApiResult.fromHttp(401, "unauth"), ApiResult.Kind.AUTH_ERROR));
        out.add(mockCase("mock_403", ApiResult.fromHttp(403, "forbid"), ApiResult.Kind.AUTH_ERROR));
        out.add(mockCase("mock_404", ApiResult.fromHttp(404, "missing"), ApiResult.Kind.VALIDATION_ERROR));
        out.add(mockCase("mock_409", ApiResult.fromHttp(409, "conflict"), ApiResult.Kind.VALIDATION_ERROR));
        out.add(mockCase("mock_422", ApiResult.fromHttp(422, "invalid"), ApiResult.Kind.VALIDATION_ERROR));
        out.add(mockCase("mock_500", ApiResult.fromHttp(500, "err"), ApiResult.Kind.SERVER_ERROR));

        // Retry policy
        ApiClient.RetryPolicy rp = ApiClient.RetryPolicy.defaults();
        out.add(new Case("retry_5xx", rp.shouldRetry(503, ApiResult.Kind.SERVER_ERROR), "5xx should retry"));
        out.add(new Case("no_retry_401", !rp.shouldRetry(401, ApiResult.Kind.AUTH_ERROR), "401 no retry"));
        out.add(new Case("no_retry_422", !rp.shouldRetry(422, ApiResult.Kind.VALIDATION_ERROR), "422 no retry"));

        // Production HTTPS rule
        ApiConfig.Environment prev = ApiConfig.getEnvironment(c);
        ApiConfig.setEnvironment(c, ApiConfig.Environment.PRODUCTION);
        boolean httpsOnly = !ApiConfig.isBaseUrlAllowed(c, "http://example.com")
                && ApiConfig.isBaseUrlAllowed(c, "https://example.com");
        ApiConfig.setEnvironment(c, prev);
        out.add(new Case("prod_https_only", httpsOnly, "Production rejects HTTP"));

        return out;
    }

    private static Case mockCase(String name, ApiResult<?> r, ApiResult.Kind expected) {
        return new Case(name, r.kind == expected, r.kind.name());
    }
}
