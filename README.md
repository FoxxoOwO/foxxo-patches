# 🦊 Foxxo Patches

Custom Morphe Patches repository by [FoxxoOwO](https://github.com/FoxxoOwO).

### 📲 How to use these patches in Morphe

Click here to add this repository as a patch source in Morphe:  
👉 **[Add to Morphe](https://morphe.software/add-source?github=FoxxoOwO/foxxo-patches)**

Or add manually in Morphe Manager:
- **Source:** `FoxxoOwO/foxxo-patches`

---

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0](https://github.com/FoxxoOwO/foxxo-patches/releases/tag/v1.4.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 Instagram&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 447.0.0.55.81 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Chat only](#chat-only) | Turns Instagram into a chat-only messenger: opens Direct instead of the feed and removes the home feed, Reels, Explore, Stories, ads and suggestions. Long-press the Direct title (or the Profile tab) to open the Feurstagram settings. |  |
| [Clone](#clone) | Renames the package and app label so the patched build installs alongside a stock Instagram instead of replacing it. | • Package name<br>• App name |
| [Debug bridge](#debug-bridge) | Development only: exposes the Feurstagram settings over ADB broadcasts so they can be driven from a shell instead of the on-screen panel. |  |

</details>

<details open>
<summary>📦 AI Plant Doctor&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.3.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock premium](#unlock-premium) | Unlocks all AI Plant Doctor premium features by bypassing Google Play Billing and returning an active annual subscription. |  |

</details>

<!-- PATCHES_END -->

---

## 🌿 AI Plant Doctor (`me.jodoin.aiplantdoctor`)

### What the patch does
Unlocks all premium features of the **AI Plant Doctor** app (`me.jodoin.aiplantdoctor v3.3.0_antisplit`) by intercepting Google Play Billing communication. The app always detects an active annual subscription (`me.jodoin.aiplantdoctor.premium_annual`).

### Unlocked features
- Unlimited plant diagnosis (AI Plant Doctor)
- Full access to the Budsy assistant
- Unlimited pest and disease detection
- Personalized care plans
- Advanced plant health analysis
- AI diagnostics and Day Pass features

### How it works
The app is built with Flutter. The subscription check logic resides in Dart code (`libapp.so`), which obtains the purchase state across the Pigeon IPC bridge from the Java `in_app_purchase_android` plugin.
The patch intercepts the `queryPurchasesAsync` call at the Java layer and injects a mocked response containing an active `me.jodoin.aiplantdoctor.premium_annual` purchase, completely bypassing Google Play BillingClient.

## 📸 Instagram Direct / Chat-Only (`com.instagram.android`)

### What the patch does
Transforms Instagram into a distraction-free, dedicated messenger (Messenger for Instagram):
- **Direct launch into DMs:** The app automatically launches directly into Direct Messages (inbox) upon startup.
- **Feed and distraction removal:** Blocks the main home feed, Stories, Explore/search, and recommended Reels feed.
- **Clean bottom navigation:** Hides Home, Explore, Reels, and Creation tabs from the bottom bar, leaving only Direct and Profile.
- **Messenger-like back navigation:** Pressing Back in the main Direct inbox moves the app to the background / home screen instead of returning to an empty feed.
- **In-chat media playback:** Posts and Reels shared in direct messages remain fully viewable and playable (via signature check bypass).
- **Settings:** Long-press the Direct inbox title bar or the Profile tab to open Feurstagram settings.

---

## 📜 License
[GPLv3](LICENSE)
