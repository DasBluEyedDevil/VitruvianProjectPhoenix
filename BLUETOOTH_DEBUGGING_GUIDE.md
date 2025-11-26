# Bluetooth Debugging Guide for Vitruvian Redux

This guide explains how to capture detailed Bluetooth logs when experiencing connection issues with your Vitruvian trainer.

## Prerequisites

- Pixel 7 (or similar Android device with Android 12+)
- Vitruvian Redux app installed
- Your Vitruvian trainer powered on

## Step 1: Enable Developer Mode (One-Time Setup)

1. Open **Settings** on your phone
2. Scroll down and tap **About phone**
3. Find **Build number** and tap it **7 times quickly**
4. You'll see a message "You are now a developer!"
5. Enter your PIN if prompted

## Step 2: Enable Bluetooth HCI Snoop Log (One-Time Setup)

1. Open **Settings**
2. Scroll down and tap **System**
3. Tap **Developer options** (this appears after enabling Developer Mode)
4. Scroll down to the **Networking** section
5. Find **Enable Bluetooth HCI snoop log** and toggle it **ON**
6. Restart your phone for the setting to take effect

## Step 3: Reproduce the Issue

1. Open the **Vitruvian Redux** app
2. Navigate to **Settings** > **Protocol Tester**
3. Select either:
   - **Recommended** - to test connection protocols
   - **Exercise Cycle** - to test the full start/wait/stop sequence
4. Tap **Start Testing**
5. Wait for the test to complete

## Step 4: Export & Share Logs

1. After the test completes, tap the **Share** icon in the top-right corner
2. The app will bundle:
   - Test results report
   - Connection logs
   - Bluetooth HCI snoop log (if accessible)
3. Select **Email** or your preferred sharing method
4. Send to: **VitruvianRedux@gmail.com**

## What Gets Captured

- **Test Report**: Results of each phase (scan, connect, init, start, stop, etc.)
- **Connection Logs**: Detailed BLE events with timestamps and command data
- **HCI Snoop Log**: Raw Bluetooth packet captures (requires Developer Mode)

## Troubleshooting

### "Developer options" not appearing
Make sure you tapped Build number 7 times quickly. Try again from Settings > About phone.

### HCI snoop log toggle not available
Some manufacturers hide this option. Check under Developer options > Networking section.

### Test fails at "Scanning" phase
Ensure your Vitruvian trainer is powered on and Bluetooth is enabled on your phone.

## Privacy Note

The logs contain technical Bluetooth data only. MAC addresses are not logged by the app. The HCI snoop log may contain device identifiers - share only with trusted parties for debugging purposes.
