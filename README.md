# Call Light (Android)

Turns the top or bottom 10-50% of the screen white as a front light, with a floating ON/OFF
button that appears automatically when a WhatsApp call screen opens at night.

## Get the APK (no Android Studio needed)

1. Create a free GitHub account and a new empty repository.
2. Upload all files from this folder (keep the `.github` folder).
3. Open the repository's **Actions** tab, run **Build APK** (it also runs on every upload).
4. When it finishes, open the run and download **CallLight-apk**. Unzip it to get `app-debug.apk`.

## Or build with Android Studio

Open this folder in Android Studio, let it sync, then **Build > Build APK(s)**.
The file is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Install and set up on the phone

1. Copy the APK to the phone and open it (allow "install unknown apps" if asked).
2. Open **Call Light** and tap **1. Turn on service**, then switch on *Call Light*.
   - If the switch is greyed out (Android 13+): open the app's info page, tap the 3-dot menu,
     choose **Allow restricted settings**, then try again.
3. Choose top/bottom, size and night hours. Tap **2. Test light** to preview.
4. Start a WhatsApp call at night: the round ON/OFF button appears. Tap to toggle, drag to move.

## Notes

- The Accessibility permission is used only to notice that the WhatsApp call screen opened.
  The service does not read call, chat or screen content (`canRetrieveWindowContent=false`).
- The button appears for any WhatsApp call screen (voice or video) because the two cannot be told
  apart without reading screen content.
- Leaving the call screen (home button, other app) hides the button and turns the light off.
