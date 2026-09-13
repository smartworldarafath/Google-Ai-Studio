# Google AI Studio Apps — Android Wrapper

An open-source native Android wrapper for [Google AI Studio Apps](https://aistudio.google.com/apps), built in Kotlin with Jetpack Compose, high-performance WebView engine, persistent Google OAuth login support, and an in-app APK auto-updater.

---

## Features

- **Direct Live Web Experience**: Renders the complete, authentic UI and features directly from `https://aistudio.google.com/apps`.
- **Personal Google Authentication**:
  - Configured with custom Chrome Mobile/Desktop User-Agent strings (omitting `Version/4.0` / `wv` WebView tokens) so Google Sign-In (`accounts.google.com`) completes smoothly without embedded WebView security blocks.
  - Enabled 3rd-party cookies (`CookieManager.setAcceptThirdPartyCookies`) and synchronous cookie disk persistence (`CookieManager.getInstance().flush()`), preserving login sessions across app restarts.
- **In-App APK Auto-Updater ("App Updates")**:
  - Dedicated Material 3 section inside the native Settings page.
  - Live percentage progress bar when streaming APK downloads.
  - **State Persistence**: Remembers previously downloaded APK files in app storage across app restarts and navigation transitions, immediately presenting the "Install" action without redundant downloads.
  - Android 8.0+ `REQUEST_INSTALL_PACKAGES` permission handling and secure Android 7+ `FileProvider` package installation.
- **File Uploads & Downloads**:
  - Full `<input type="file">` file-picker support for AI Studio prompts, documents, images, and codebase imports via `WebChromeClient.onShowFileChooser`.
  - Android `DownloadManager` integration with cookie/auth forwarding for exporting projects, logs, or generated assets.
- **Navigation & Reliability**:
  - Native loading progress indicator.
  - Pull-to-refresh and backstack navigation (`webView.goBack()`).
  - No-internet error screen with retry affordance.
  - Desktop Site Mode toggle in Settings.

---

## Configuration

### 1. In-App Update Endpoint (`UPDATE_CONFIG_URL`)

The in-app update mechanism queries a remote JSON endpoint. To configure your production release server:

1. Open `app/src/main/java/com/example/update/AppUpdateManager.kt`
2. Update the `UPDATE_CONFIG_URL` constant:

```kotlin
companion object {
    var UPDATE_CONFIG_URL: String = "https://your-domain.com/path/to/app-update.json"
}
```

#### JSON Response Schema:
```json
{
  "latest_version_code": 2,
  "latest_version_name": "1.1.0",
  "apk_url": "https://your-domain.com/releases/aistudio-v1.1.0.apk",
  "changelog": "• Performance enhancements\n• Bug fixes and UI refinements"
}
```

*Note: You can also tap the edit icon in the Settings screen to test custom update URLs directly from the UI or use the "Test Update" demo simulator.*

---

### 2. Google OAuth Handling Approach

Google blocks authentication requests originating from default Android WebViews when they identify embedded webview signatures. This wrapper implements:

1. **User-Agent Normalization**: Uses standard Chrome Android / Linux desktop user-agents to prevent detection.
2. **Third-Party Cookie Support**: Enables `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)` for cross-domain Google SSO tokens.
3. **Custom Tabs Integration**: Auxiliary external links and Google account management pages seamlessly use `androidx.browser.customtabs.CustomTabsIntent`.

---

## Permissions Declared

- `android.permission.INTERNET`: Required for loading AI Studio and remote update configs.
- `android.permission.ACCESS_NETWORK_STATE`: For checking connectivity and rendering retry views.
- `android.permission.REQUEST_INSTALL_PACKAGES`: Required on Android 8.0+ to launch APK installations.
- `android.permission.POST_NOTIFICATIONS`: For download progress notifications.

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```


---

## ☕ Support / Buy Me a Coffee & Become a Sponsor

If you find **Google Ai Studio** helpful and want to support ongoing development, maintenance, and new features, consider contributing through any of the options below! Your support means the world and helps keep this project open-source.

<div align="center">

<table>
  <tr>
    <td align="center" width="25%" valign="top">
      <h4>☕ SupportKori</h4>
      <a href="https://www.supportkori.com/arafathrahman" target="_blank">
        <img src="assets/supportkori-qr.jpg" alt="SupportKori QR" width="180" style="border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.15);" />
      </a><br/><br/>
      <a href="https://www.supportkori.com/arafathrahman" target="_blank">
        <img src="https://img.shields.io/badge/Support-SupportKori-FF5E5B?style=for-the-badge&logo=buy-me-a-coffee&logoColor=white" alt="SupportKori Badge" />
      </a><br/>
      <sub>Cards / bKash / Nagad / Global</sub>
    </td>
    <td align="center" width="25%" valign="top">
      <h4>⚡ nsave</h4>
      <img src="assets/nsave-qr.jpg" alt="nsave QR" width="180" style="border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.15);" /><br/><br/>
      <img src="https://img.shields.io/badge/nsave-@arafath__rahman9-000000?style=for-the-badge&logoColor=white" alt="nsave Badge" /><br/>
      <sub>Ntag: <code>@arafath_rahman9</code></sub>
    </td>
    <td align="center" width="25%" valign="top">
      <h4>🔴 RedotPay</h4>
      <img src="assets/redotpay-qr.jpg" alt="RedotPay QR" width="180" style="border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.15);" /><br/><br/>
      <img src="https://img.shields.io/badge/RedotPay-1965421414-E51E2B?style=for-the-badge&logoColor=white" alt="RedotPay Badge" /><br/>
      <sub>ID: <code>1965421414</code></sub>
    </td>
    <td align="center" width="25%" valign="top">
      <h4>🅿️ Payoneer</h4>
      <img src="assets/payoneer-info.jpg" alt="Payoneer Info" width="180" style="border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.15);" /><br/><br/>
      <a href="mailto:arafathrahman710@gmail.com?subject=Support%20via%20Payoneer">
        <img src="https://img.shields.io/badge/Payoneer-70366820-FF4800?style=for-the-badge&logo=payoneer&logoColor=white" alt="Payoneer Badge" />
      </a><br/>
      <sub>Email: <code>arafathrahman710@gmail.com</code></sub>
    </td>
  </tr>
</table>

<br/>

| Method | Details / Direct Link |
| :--- | :--- |
| **☕ SupportKori** | [https://www.supportkori.com/arafathrahman](https://www.supportkori.com/arafathrahman) |
| **⚡ nsave** | Ntag: `@arafath_rahman9` • `Md Arafath Rahman` |
| **🔴 RedotPay** | Account ID: `1965421414` |
| **🅿️ Payoneer** | Email: `arafathrahman710@gmail.com` • Customer ID: `70366820` |

</div>

