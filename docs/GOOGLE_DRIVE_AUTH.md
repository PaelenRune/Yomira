# Google Drive account connection

Available in **8.0.4-Beta (18)** under Settings → Google Drive Sync.

## User flow

1. Tap **Google Hesabı ile Giriş Yap / Sign in with Google**.
2. Choose an account in Google's account picker and grant Yomira's app-data permission.
3. Settings displays the connected email and manual/automatic sync status.
4. Tap **Şimdi Eşitle / Sync Now**, or explicitly enable automatic sync.
5. **Çıkış Yap / Hesabı Ayır** immediately removes the local account connection,
   disables automatic sync, and asks Google Play services to revoke access and sign out.
   If remote revocation fails, Settings reports it; remove access from your Google
   account's third-party connections page if necessary. Existing Drive backups are not deleted.

Cancellation leaves sync disabled. Consent failures and unavailable Play services are
reported without storing a connection. Recoverable credential failures clear the local
connection so consent can be requested again. A transient network failure does not
discard the connected account. Connecting alone does not upload anything.

The selected email is persisted in private SharedPreferences (Android backup is disabled).
Tokens are obtained on an IO dispatcher from Google Play services for the selected account;
Yomira does not persist tokens, passwords, client secrets, or refresh tokens. Only the
`https://www.googleapis.com/auth/drive.appdata` Drive scope is requested, alongside
the basic Google sign-in profile/email. The hidden `appDataFolder/yomira_sync.json`
contains sync data, not arbitrary Drive files. Connecting a different account does
not clear the local library: the existing two-way sync merges local data with that account.

## Required Google Cloud configuration (maintainer)

An APK build alone cannot provision OAuth. Before distributing a working connection:

1. Use a Google Cloud project and enable **Google Drive API**.
2. Configure the Google Auth Platform consent screen/branding, audience, support
   contacts, and data access for the app-data scope. While in Testing, add all test
   Google accounts; publish/configure verification as required for your audience.
3. Create an **Android OAuth client** for package **`com.yomira.app`** with signing
   certificate SHA-1:
   **`E0:37:95:B7:5B:F9:FF:F4:F2:5A:0D:56:B3:C5:5C:39:C7:30:83:76`**.
   Both debug and release currently use this repository's release signing configuration.
   Register any different signing certificate separately (including Play App Signing).
4. No web client ID, client secret, `google-services.json`, or Firebase plugin is
   required by this Android-account Drive flow. The package/certificate association
   must exist in Google's console; it cannot be supplied by inventing a client ID.

`DEVELOPER_ERROR`/status 10 usually indicates an OAuth package/certificate mismatch.
Access-denied errors can indicate consent audience/test-user restrictions. Check these
settings before changing the application's token code.

The implementation uses the Google Sign-In compatibility API in Play services Auth
21.4.0 and `GoogleAuthUtil` for Drive tokens. Google has deprecated that sign-in API;
a future migration should use Credential Manager for identity and the separate Google
Identity Services Authorization API for Drive consent, not request Drive scopes as an
ID-token-only login.

## Device verification checklist

Requires an Android device/emulator with Google Play services, network access, and the
Cloud configuration above. JVM tests cannot verify Google's consent UI or a live token.

- Fresh install: sign-in explanation/button visible; sync and automatic switch disabled.
- Cancel picker: no connection or upload; retry remains possible.
- Grant consent: email visible; manual sync succeeds and a second sync restores/merges.
- Restart application: email/consent restored and manual sync still works.
- Enable automatic sync: opt-in persists and background sync works.
- Disconnect: email removed, automatic sync off, manual action disabled after restart.
- Reconnect a different account: new email displayed; old account not silently selected.
- Revoke access in Google settings: next credential failure prompts reconnection.
- Offline, missing Play services, and OAuth configuration errors: no false sync success.

The last-sync timestamp is the existing device-local history, not proof of a successful
sync to the newly selected account. An already-running background HTTP request may
finish during disconnect; further token requests are gated immediately.
