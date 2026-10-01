# Build My Plan on GitHub Actions

1. Create a GitHub repository and upload the whole project.
2. Open **Actions**.
3. Select **Build My Plan APK**.
4. Click **Run workflow**.
5. Choose `release` (default) and run it.
6. Open the completed workflow run and download the **MyPlan-release** artifact.

## Optional Supabase configuration

The workflow builds even if no GitHub Secrets are configured. If you want the APK to contain the Supabase configuration at build time, add these repository secrets:

- `SUPABASE_URL` — the Supabase project base URL, for example `https://YOUR_PROJECT.supabase.co`
- `SUPABASE_ANON_KEY` — the publishable/anon key only. Never use a service-role/secret key.

## Firebase / FCM

`google-services.json` is optional for this current in-app announcement flow. If you later enable real FCM push, place the Firebase file at `app/google-services.json` as described in `docs/FIREBASE_SETUP.md`.

## Signing note

For convenience, the workflow generates a temporary CI signing key automatically. This is suitable for testing/installing the generated APK, but it is **not** a permanent Play Store/release signing key. For production distribution, replace the temporary key with your own protected GitHub Actions secrets and a permanent keystore.
