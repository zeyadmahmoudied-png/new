# Firebase / FCM setup

1. Create/select the Firebase project used by My Plan.
2. Add Android app with package `com.myplan.app`.
3. Download `google-services.json` and place it at `app/google-services.json`.
4. Build the app normally. The Google Services Gradle plugin is applied automatically when the file exists.
5. FCM tokens are registered against `devices.fcm_token`; the Control Center/backend sends pushes.

Do not put a Firebase service-account JSON, Supabase service_role key, or other server secret inside the Android project.
