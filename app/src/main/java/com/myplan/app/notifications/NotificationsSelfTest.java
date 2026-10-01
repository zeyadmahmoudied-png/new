package com.myplan.app.notifications;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class NotificationsSelfTest {
    private NotificationsSelfTest() {}

    public static final class Case {
        public final String name;
        public final boolean pass;
        public final String detail;
        Case(String n, boolean p, String d) { name = n; pass = p; detail = d; }
    }

    public static List<Case> run(Context c) {
        List<Case> out = new ArrayList<>();
        NotificationService svc = NotificationsLayer.service(c);
        PushProvider push = svc.pushProvider();

        out.add(new Case("push_not_configured", !push.isConfigured(), push.providerName()));
        out.add(new Case("push_status", "NOT_CONFIGURED".equals(svc.pushStatus()), svc.pushStatus()));

        NotificationResult<String> token = push.getRegistrationToken();
        out.add(new Case("token_not_configured", token.kind == NotificationResult.Kind.NOT_CONFIGURED, token.kind.name()));

        NotificationResult<Void> testPush = push.sendTestPush();
        out.add(new Case("test_push_not_configured", testPush.kind == NotificationResult.Kind.NOT_CONFIGURED, testPush.kind.name()));

        NotificationRequest local = new NotificationRequest();
        local.type = NotificationType.SESSION_REMINDER;
        local.title = "test";
        local.requiresPush = false;
        NotificationResult<Void> loc = svc.scheduleLocal(local);
        out.add(new Case("local_schedule_contract", loc.kind == NotificationResult.Kind.SCHEDULED_LOCAL, loc.kind.name()));

        NotificationRequest remote = new NotificationRequest();
        remote.type = NotificationType.CLOUD_EVENT;
        remote.requiresPush = true;
        NotificationResult<Void> pr = svc.requestPush(remote);
        out.add(new Case("push_request_blocked", pr.kind == NotificationResult.Kind.NOT_CONFIGURED, pr.kind.name()));

        out.add(new Case("permission_label_honest",
                "UNKNOWN_UNTIL_RUNTIME".equals(svc.permissionStateLabel()), svc.permissionStateLabel()));

        return out;
    }
}
