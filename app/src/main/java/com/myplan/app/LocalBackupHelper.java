package com.myplan.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * نسخة احتياطية محلية فقط داخل مجلد التطبيق.
 * لا ترفع إلى Supabase.
 */
public final class LocalBackupHelper {
    private LocalBackupHelper() {}

    private static final String FILE_NAME = "myplan_local_backup.json";

    public static File backupFile(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    public static boolean hasValidBackup(Context c) {
        File f = backupFile(c);
        if (!f.exists() || f.length() < 8) return false;
        try {
            String json = readBackup(c);
            return json != null && json.trim().startsWith("{") && json.contains("tasks");
        } catch (Exception e) {
            return false;
        }
    }

    public static long backupLastModified(Context c) {
        File f = backupFile(c);
        return f.exists() ? f.lastModified() : 0L;
    }

    /** يحفظ JSON دون حذف البيانات الحالية. */
    public static boolean saveBackupJson(Context c, String json) {
        if (json == null || json.trim().isEmpty()) return false;
        File f = backupFile(c);
        File tmp = new File(f.getParentFile(), FILE_NAME + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
            if (f.exists() && !f.delete()) {
                // تجاهل — سنحاول rename فوقه
            }
            if (!tmp.renameTo(f)) {
                // fallback
                try (FileOutputStream os2 = new FileOutputStream(f)) {
                    os2.write(json.getBytes(StandardCharsets.UTF_8));
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            return true;
        } catch (Exception e) {
            try { //noinspection ResultOfMethodCallIgnored
                tmp.delete(); } catch (Exception ignored) {}
            return false;
        }
    }

    public static String readBackup(Context c) throws Exception {
        File f = backupFile(c);
        if (!f.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
