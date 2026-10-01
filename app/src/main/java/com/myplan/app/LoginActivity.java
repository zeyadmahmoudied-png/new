package com.myplan.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * بوابة الدخول قبل واجهة My Plan.
 * يعتمد على AccountAuth المحلي الحالي — لا نظام حسابات جديد.
 */
public class LoginActivity extends Activity {
    private static final int BG = 0xFF0B0F16;
    private static final int CARD = 0xFF141A24;
    private static final int TEXT = 0xFFF0F3F8;
    private static final int MUTED = 0xFF8B95A8;
    private static final int ACCENT = 0xFF4B6DFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppInfrastructure.onLaunch(this);
        if (AccountAuth.isLoggedIn(this)) {
            goMain();
            return;
        }
        setContentView(buildLoginUi(false));
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // مرّر extras الإشعارات/الويدجت إن وُجدت
        Intent src = getIntent();
        if (src != null && src.getExtras() != null) {
            i.putExtras(src.getExtras());
        }
        startActivity(i);
        finish();
    }

    private View buildLoginUi(boolean registerMode) {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(48), dp(24), dp(32));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(box);

        TextView brand = new TextView(this);
        brand.setText("My Plan");
        brand.setTextColor(TEXT);
        brand.setTextSize(28);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        box.addView(brand);
        box.addView(space(dp(8)));
        TextView sub = new TextView(this);
        sub.setText(registerMode ? "إنشاء حساب محلي على هذا الجهاز" : "سجّل الدخول للمتابعة");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);
        box.addView(space(dp(28)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);

        EditText email = field("البريد الإلكتروني");
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(email);
        card.addView(space(dp(10)));

        EditText name = null;
        EditText conf = null;
        if (registerMode) {
            name = field("الاسم المعروض (اختياري)");
            card.addView(name);
            card.addView(space(dp(10)));
        }

        EditText pass = field("كلمة المرور");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(pass);
        card.addView(space(dp(10)));

        if (registerMode) {
            conf = field("تأكيد كلمة المرور");
            conf.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            card.addView(conf);
            card.addView(space(dp(10)));
        }

        TextView note = new TextView(this);
        note.setText("حساب محلي على هذا الجهاز فقط. بيانات الدراسة لا تُمسح عند تسجيل الخروج.");
        note.setTextColor(MUTED);
        note.setTextSize(12);
        card.addView(note);
        card.addView(space(dp(16)));

        Button primary = new Button(this);
        primary.setText(registerMode ? "إنشاء حساب" : "دخول");
        primary.setAllCaps(false);
        primary.setTextColor(0xFFFFFFFF);
        primary.setTextSize(15);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(ACCENT);
        pbg.setCornerRadius(dp(10));
        primary.setBackground(pbg);
        final boolean reg = registerMode;
        final EditText nameF = name;
        final EditText confF = conf;
        primary.setOnClickListener(v -> {
            AccountAuth.AuthResult r;
            if (reg) {
                r = AccountAuth.registerLocal(this,
                        email.getText().toString(),
                        pass.getText().toString(),
                        confF != null ? confF.getText().toString() : "",
                        nameF != null ? nameF.getText().toString() : "");
            } else {
                r = AccountAuth.loginLocal(this,
                        email.getText().toString(),
                        pass.getText().toString());
            }
            Toast.makeText(this, r.messageAr, Toast.LENGTH_LONG).show();
            if (r.ok) {
                final AccountAuth.Account acc = AccountAuth.getCurrentAccount(this);
                final android.content.Context appCtx = getApplicationContext();
                new Thread(() -> {
                    try {
                        if (com.myplan.app.supabase.SupabaseConfig.isConfigured(appCtx)) {
                            com.myplan.app.supabase.SupabaseRepository repo =
                                    new com.myplan.app.supabase.SupabaseRepository(appCtx);
                            if (acc != null) repo.registerAppUser(acc);
                            repo.upsertDevice();
                            com.myplan.app.supabase.AdminBanGate.refresh(appCtx);
                        }
                    } catch (Exception ignored) {}
                    runOnUiThread(() -> {
                        com.myplan.app.supabase.AdminBanGate.BanStatus ban =
                                com.myplan.app.supabase.AdminBanGate.cached(LoginActivity.this);
                        if (ban.deviceBanned) {
                            Toast.makeText(LoginActivity.this,
                                    (ban.deviceMessage != null && !ban.deviceMessage.isEmpty())
                                            ? ban.deviceMessage : "هذا الجهاز محظور.",
                                    Toast.LENGTH_LONG).show();
                            AccountAuth.logout(LoginActivity.this);
                            return;
                        }
                        if (ban.accountBanned) {
                            Toast.makeText(LoginActivity.this,
                                    (ban.accountMessage != null && !ban.accountMessage.isEmpty())
                                            ? ban.accountMessage : "هذا الحساب محظور.",
                                    Toast.LENGTH_LONG).show();
                            AccountAuth.logout(LoginActivity.this);
                            return;
                        }
                        goMain();
                    });
                }, "auth-register").start();
            }
        });
        card.addView(primary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        card.addView(space(dp(10)));

        Button switchBtn = new Button(this);
        switchBtn.setText(registerMode ? "لدي حساب — تسجيل الدخول" : "إنشاء حساب جديد");
        switchBtn.setAllCaps(false);
        switchBtn.setTextColor(ACCENT);
        switchBtn.setTextSize(14);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0x00000000);
        sbg.setStroke(dp(1), 0xFF2A3344);
        sbg.setCornerRadius(dp(10));
        switchBtn.setBackground(sbg);
        switchBtn.setOnClickListener(v -> setContentView(buildLoginUi(!reg)));
        card.addView(switchBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        if (!reg && LocalBackupHelper.hasValidBackup(this)) {
            card.addView(space(dp(12)));
            TextView restoreNote = new TextView(this);
            restoreNote.setText("توجد نسخة احتياطية محلية على هذا الجهاز");
            restoreNote.setTextColor(MUTED);
            restoreNote.setTextSize(12);
            card.addView(restoreNote);
            card.addView(space(dp(6)));
            Button restoreBtn = new Button(this);
            restoreBtn.setText("استعادة نسخة احتياطية");
            restoreBtn.setAllCaps(false);
            restoreBtn.setTextColor(0xFFFFFFFF);
            restoreBtn.setTextSize(14);
            GradientDrawable rbg = new GradientDrawable();
            rbg.setColor(0xFF2A9D6E);
            rbg.setCornerRadius(dp(10));
            restoreBtn.setBackground(rbg);
            restoreBtn.setOnClickListener(v -> confirmRestoreBackup());
            card.addView(restoreBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }

        box.addView(card);
        return sc;
    }

    private void confirmRestoreBackup() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("استعادة النسخة الاحتياطية؟")
                .setMessage("سيتم استبدال بيانات My Plan الحالية على هذا الجهاز بالنسخة المحفوظة. لا يمكن التراجع بعد التأكيد.")
                .setPositiveButton("استعادة", (d, w) -> {
                    try {
                        String json = LocalBackupHelper.readBackup(this);
                        if (json == null || json.trim().isEmpty()) {
                            Toast.makeText(this, "لا توجد نسخة صالحة", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Planner p = new Planner(this);
                        p.importJson(json);
                        Toast.makeText(this, "تمت استعادة النسخة الاحتياطية محليًا", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "فشلت الاستعادة: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0B0F16);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFF2A3344);
        e.setBackground(bg);
        return e;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
