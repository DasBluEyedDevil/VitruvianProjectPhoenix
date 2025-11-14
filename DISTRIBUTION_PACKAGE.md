# VitruvianRedux Beta 4 Patch 1 - Distribution Package

## 📦 Package Contents

### APK File
**Filename:** `VitruvianRedux-Beta4-Patch1-20251112.apk`
**Size:** 22 MB
**Version:** 0.4.0-beta (Patch 1)
**Build Date:** November 12, 2025

### Signing Information
- **Keystore:** Debug keystore
- **Valid Until:** May 14, 2055
- **SHA-256:** CC:91:A1:C3:C2:C6:4D:D3:CA:B2:95:28:FC:20:A6:6E:30:75:3C:BB:7A:EF:FD:C7:52:7C:86:C6:2D:D3:13:10

### Documentation
- **Release Notes:** `VitruvianRedux-Beta4-Patch1-ReleaseNotes.md`
- **Full Changelog:** Available in repository

---

## 🎯 What's New in This Patch

### 1. Enhanced Log Sharing
- **Email auto-filled:** VitruvianRedux@gmail.com
- **Pre-populated template:** Device info, issue description, repro steps
- **One-tap sharing:** Opens Android share sheet with everything ready

### 2. Issue #95 Diagnostic Instrumentation
- Tracks BLE characteristic lifecycle
- Logs when/why characteristics are invalidated
- Helps identify root cause of connection failures

---

## 📧 For Beta Testers

### How to Share Logs
1. Open VitruvianRedux app
2. Navigate to Connection Logs screen
3. Tap the **Share** button (top-right)
4. Tap "Share" in the dialog
5. Select your email app
6. Email is already addressed to VitruvianRedux@gmail.com
7. Fill in the description and hit send!

### When to Share Logs
If you experience:
- LED color changes not working
- Workout won't start
- "NUS RX characteristic not available" errors
- Any connection issues after successful pairing

**Please share logs!** They contain diagnostic data to help fix Issue #95.

---

## 🔧 Installation

### First-Time Installation
1. Enable "Install from Unknown Sources" in Android settings
2. Transfer APK to your Android device
3. Tap the APK file to install
4. Accept permissions when prompted

### Upgrading from Beta 4
1. Option A: Install over existing (keeps all data)
2. Option B: Uninstall Beta 4 first (clean install)
3. All routines and settings preserved automatically

---

## ⚙️ System Requirements

- **Minimum:** Android 8.0 (API 26)
- **Recommended:** Android 10+
- **Target:** Android 13 (API 36)
- **Storage:** 50 MB free space
- **Bluetooth:** BLE (Bluetooth Low Energy) required

---

## 🐛 Known Issues

### This is a Diagnostic Build
- Issue #95 is **NOT YET FIXED** in this patch
- This build adds logging to identify the root cause
- Next patch will include the actual fix

### Other Known Issues
- 3 pre-existing unit test failures (non-critical)
- Euclid eccentric mode under investigation
- Some Material Icons deprecated warnings

---

## 📞 Support & Feedback

**Email:** VitruvianRedux@gmail.com (auto-filled in log exports!)
**GitHub:** https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues
**Focus Issue:** #95 - Connection command failures

---

## 🙏 Special Thanks

Thanks to **connorcharless** for detailed bug reports and willingness to test diagnostic builds. Your logs will help us fix this issue permanently!

---

**Distribution prepared by:** Claude Code
**Build system:** Gradle 8.13.1, Kotlin 2.0.21
**Quality:** Following systematic-debugging methodology

