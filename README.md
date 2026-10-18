# 3dsInputRedirectionWearOS

Wear OS client for **Luma3DS Rosalina InputRedirection**.

This app connects a Bluetooth gamepad paired to your Wear OS smartwatch and redirects inputs over Wi-Fi UDP to your Nintendo 3DS running Luma3DS InputRedirection.

## Requirements

- **Nintendo 3DS** with Luma3DS custom firmware and Rosalina InputRedirection enabled.
- **Wear OS smartwatch** running Wear OS 5+ (Android 11 / API 31+).
- **Bluetooth gamepad** (Xbox Wireless Controller, DualShock 4 / DualSense, Switch Pro Controller, 8BitDo, etc.) paired to the watch.
- Smartwatch and 3DS connected to the same local Wi-Fi network.

## Finding & Pairing Bluetooth Gamepads

Android on Wear OS can sometimes hide or bury Bluetooth gamepad pairing menus. This app provides multiple easy ways to find and pair your controller:

1. **In-App Bluetooth Discovery**:
   - Tap the gamepad icon (or tap the **🎮 Pair Gamepad** chip) on the home screen to open the **Bluetooth Gamepads** screen.
   - Tap **Scan for Gamepads** to automatically search for nearby discoverable game controllers.
   - Tap **Pair** next to any discovered controller to initiate pairing directly without leaving the app.
2. **Direct Watch Bluetooth Settings Shortcut**:
   - From the in-app Gamepads screen, tap **Watch Bluetooth Settings** to jump straight into the Wear OS Bluetooth pairing menu with a single tap.
3. **Active Controller Status**:
   - The app detects connected gamepads live via `InputManager` and highlights them on the home screen and in the Gamepads screen so you always know when your controller is ready.

## Controller Input Mapping

The app makes button mapping effortless with both **Auto-Map** and **Interactive Press-to-Map** features:

### 1. Auto-Map Features & Presets
- **✨ Auto-Detect Connected Pad**: Automatically inspects the name of your connected controller (Xbox, PlayStation, 8BitDo, Switch Pro) and configures the optimal button layout with one tap.
- **Nintendo Standard**: A/B/X/Y mapped directly to standard Nintendo layout.
- **Xbox Positional**: Swaps A/B and X/Y so your muscle memory matches the physical 3DS diamond button positions (Xbox B -> 3DS A, Xbox A -> 3DS B).
- **Xbox Labels**: Matches letter labels directly (Xbox A -> 3DS A, Xbox B -> 3DS B).
- **PlayStation Layout**: Maps Circle to 3DS A, Cross to 3DS B, Triangle to 3DS X, Square to 3DS Y.

### 2. Press Button to Map (Interactive Remap)
- **Guided Step-by-Step Wizard**:
  - Tap **Start Step-by-Step Remap** to begin a guided walkthrough.
  - The watch displays each button in sequence (e.g. `Press button for 3DS A`).
  - Simply press the physical button, analog trigger (`ZL`/`ZR`), or D-Pad direction on your Bluetooth controller. The app instantly detects the input, saves it, and advances to the next button.
  - You can skip any button or cancel at any time.
- **Individual Press-to-Map**:
  - In the mapping list, tap any button row (e.g. `3DS A`).
  - The app enters listening mode (`Press button on gamepad for 3DS A`).
  - Press the desired controller button to immediately assign it.

## Features

- **Standard 20-byte Luma3DS InputRedirection Protocol**: Wire-compatible with Rosalina UDP on port `4950` at 50 Hz (~20 ms frame rate).
- **Comprehensive Bluetooth Controller Input Translation**:
  - Circle Pad & C-Stick analog axes (`AXIS_X`, `AXIS_Y`, `AXIS_Z`, `AXIS_RZ`).
  - D-Pad hat axes (`AXIS_HAT_X`, `AXIS_HAT_Y`) and physical D-Pad keys.
  - Analog and digital triggers (`AXIS_LTRIGGER`, `AXIS_RTRIGGER`, `AXIS_BRAKE`, `AXIS_GAS`, `L2`, `R2`).
  - Face buttons, shoulder buttons, and Start/Select.
- **On-Watch Controls**:
  - **Touchpad Screen**: Drag on the watch display to emulate 3DS touchscreen taps and stylus strokes (`4:3` aspect ratio, `0..4095`).
  - **System Keys Screen**: Hold-to-press buttons for 3DS `HOME`, `POWER (Tap)`, and `POWER (Long)`.
- **Advanced Stick Options**:
  - Invert Stick Y / C-Stick Y.
  - Swap Left & Right Sticks.
  - C-Stick mode (Standard, D-Pad, ABXY, Smash Attack).
  - CPad (`1..4095`) and C-Stick (`1..255`) travel bounds.
- **Background ForegroundService**:
  - Runs with `PARTIAL_WAKE_LOCK` so UDP redirection continues uninterrupted when the watch display dims.

## Installation

The compiled debug APK is located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Installing on your Wear OS Watch via ADB

1. **Enable Developer Options & Wireless Debugging on your Watch**:
   - On the watch, go to **Settings > System > About > Versions** and tap **Build number** 7 times.
   - Go back to **Settings > Developer options**.
   - Enable **ADB debugging** and **Wireless debugging**.
   - Note the watch's IP address and pairing/connect port.

2. **Connect via ADB from your computer or phone**:
   ```bash
   adb connect <WATCH_IP>:<PORT>
   ```
   *(If prompted on the watch screen, tap "Always allow from this computer".)*

3. **Install the APK**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Building

To build the debug APK:

```bash
export ANDROID_HOME=/home/codespace/android-sdk
./gradlew assembleDebug
```

To run unit tests:

```bash
export ANDROID_HOME=/home/codespace/android-sdk
./gradlew testDebugUnitTest
```