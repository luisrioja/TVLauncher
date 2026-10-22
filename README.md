# TVLauncher

A tiny, ad-free home screen for Xiaomi Mi TV sets running Android TV. It replaces
PatchWall and the Google TV launcher with two rows:

- **Inputs**: the HDMI ports. When a device announces itself over HDMI-CEC
  (an Apple TV, a console…) its name is shown, with the port underneath.
  Ports with nothing plugged in are dimmed.
- **Apps**: the installed TV apps. Long-press OK on a tile to move it left or
  right, or to hide it. The last tile, "Ajustes", opens Android settings and
  brings hidden apps back.

There are no recommendations, no ads, no network access and no background
work. The UI is in Spanish; the strings live in
`app/src/main/res/values/strings.xml` if you want to translate them.

It also does two things a stock launcher won't:

- **Starts on an HDMI input.** After a reboot, and when the TV wakes from
  standby while the launcher was on screen, it switches straight to the input
  marked "al encender" ("on power-up"). By default that's the one with a CEC
  device whose name contains "Apple". Long-press an input to change it, or to
  turn this off.
- **Takes over the remote's home buttons.** The house button and the
  Xiaomi/PatchWall button always open this launcher. An accessibility service
  (`KeyService`) does this. It only filters key events, never reads screen
  content, and lets every other key through.

There is a single `Activity` and that one service, built from plain framework
views with no AndroidX and no dependencies. The release APK is about 63 KB.
On the test TV it takes ~650 ms to start cold and ~110 ms warm, and uses about
51 MB PSS (PatchWall used around 140 MB).

## Tested on

| | |
|---|---|
| Model | `MiTV-MOOQ0` (device `croods`), 50" |
| SoC | MediaTek `m7632`, 1.7 GB RAM, `armeabi-v7a` |
| Android | Android TV 11, build `RTM4.220307.164.8835` |
| Remote | Xiaomi RC over Bluetooth (`Vendor_2717_Product_32b9`) |

Other MediaTek-based Xiaomi Android TV 11 sets are likely to work, but they
haven't been tested. If you try one, please open an issue saying whether it
worked.

## Build

You need JDK 17 and the Android SDK (platform 35).

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

The release build is signed with your local debug key. That's fine for
sideloading, but if you ever build on another machine you will have to
uninstall the app before installing the new build.

## Install

1. On the TV, go to Settings → Device Preferences → About and press "Build"
   seven times. Then, in Developer options, turn on **USB debugging**. On these
   Xiaomi sets that also opens ADB over the network on port 5555. The TV must
   be on your main network, not a guest network, which isolates its clients.
2. From your computer:

```bash
adb connect <TV-IP>:5555        # accept the prompt on the TV
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell settings put secure enabled_accessibility_services luisrioja.tvlauncher/.KeyService
adb shell settings put secure accessibility_enabled 1
```

3. The system gives the Google launcher priority for HOME, so
   `set-home-activity` does nothing until it is disabled. Every step here can
   be undone with `pm enable`. This is what was disabled on the test TV:

```bash
for p in com.google.android.tvlauncher \
         com.mitv.tvhome.atv com.mitv.tvhome.mitvplus com.mitv.tvhome.michannel \
         com.google.android.tvrecommendations \
         com.miui.tv.analytics com.xiaomi.statistic \
         com.xiaomi.amazon.alexa com.xiaomi.mitv.tvmanager com.mitv.gallery \
         com.xiaomi.mitv.mediaexplorer com.google.android.videos com.google.android.play.games; do
  adb shell pm disable-user --user 0 $p
done
adb shell cmd package set-home-activity luisrioja.tvlauncher/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

If you have another launcher or a key remapper installed (Projectivy, Button
Mapper…), disable it too. Its accessibility service will fight this one for the
home button.

## Do not disable

- **`com.mediatek.wwtv.tvcenter`, `com.mediatek.*`, or anything with `tvinput`
  in its name.** HDMI switching goes through them, so without them there is no
  HDMI.
- **`com.google.android.katniss`.** The remote's microphone stops working
  without it.
- **The Google launcher, until you've checked this one works.** If the only
  HOME app left is broken, the TV boots to nothing useful and can only be fixed
  over ADB.
- **Anything, with `pm uninstall`.** Use `pm disable-user` instead. Packages
  removed with `pm uninstall` can put the TV into a boot loop after a factory
  reset.

## How it works

- **Switching inputs.** The launcher sends `ACTION_VIEW` with
  `TvContract.buildChannelUriForPassthroughInput(inputId)`. Xiaomi's live-TV
  activity (`com.mediatek.wwtv.tvcenter/com.mitv.livetv.tv.MainActivity`)
  handles it, matching on the MIME type `vnd.android.cursor.item/channel` that
  Android resolves from the URI. On the test TV, HDMI 1–3 are
  `com.mediatek.tvinput/.hdmi.HDMIInputService/HW4|HW5|HW6`. A CEC device shows
  up as a child input of its port, and the launcher always switches to the
  port, so a saved choice survives the device being off.
- **Remote buttons.** `KeyService` matches them by scan code: 172 is
  `KEY_HOMEPAGE`, the house, and 150 is `KEY_WWW`, the Xiaomi button. It can't
  match by key code because the vendor key layout file isn't readable without
  root. To find the codes on another remote, run `adb shell getevent -lt` and
  press the button.

## Undo

```bash
adb shell pm enable com.google.android.tvlauncher   # and the rest of the list above
adb shell settings put secure enabled_accessibility_services '""'
adb uninstall luisrioja.tvlauncher
```

## License

MIT, see [LICENSE](LICENSE).
