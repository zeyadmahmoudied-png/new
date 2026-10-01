# My Plan ↔ Control Center Contract v1

This document is the single naming/state contract for the app and the Supabase Control Center.

## 1. Communication
- `remote_messages`: in-app announcements, inbox messages, push payload source, maintenance/update/warning notices.
- `message_receipts`: delivered/read/dismissed state per installation.
- `notification_jobs`: immediate/scheduled push jobs.

## 2. Announcement types
`new_feature`, `general`, `premium`, `maintenance`, `warning`, `update`.

Each announcement supports title, body, optional image/icon, primary/secondary buttons, action (`CLOSE`, `OPEN_URL`, `OPEN_UPDATE`, `OPEN_SCREEN`), deep link, audience, start/end, priority, dismissibility and frequency.

## 3. Audiences
`all`, `free`, `premium`, `user`, `group`, `device`, `version`, `android`.

The app must evaluate these values consistently; Control Center must not invent another spelling.

## 4. Premium
`premium_grants.status`: `active | revoked | expired`.
`grant_type`: `manual | trial | subscription | promo | developer`.

The app treats server grants as remote entitlement data; local developer/test premium is separate.

## 5. Updates
`app_versions` stores version name/code, APK URL, release notes, release date, minimum supported version code and force-update state.
APK URLs must be HTTPS.

## 6. Security
- No Supabase `service_role` key in Android.
- Control Center admin operations use Supabase Auth and `cc_*` security-definer RPCs.
- App security events use `myplan_log_security_event`.

## 7. Ads
The schema reserves the Control Center contract, but Ads remain disabled/inactive in the current My Plan implementation until explicitly enabled.

## 8. Offline-first
Planner data remains local. Control Center manages remote operational state only; it does not replace the local planner database.
