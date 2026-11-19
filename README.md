# VyBe

A fast, gesture-driven video player for Android, in the spirit of VLC and MX Player.
Native Kotlin, Jetpack Compose and Media3 ExoPlayer. Builds in the cloud with GitHub Actions, so you don't need Android Studio.

## Features

- **Library**: scans the device for videos, with thumbnails, duration, resolution and size
- **Folders view**, search, sort (newest / name / longest / largest) and list or grid layout
- **Resume playback**: remembers where you stopped, with a progress bar on each video
- **Gestures**: swipe left side for brightness, right side for volume, swipe across to seek,
  double-tap left or right for ±10s, double-tap centre to play or pause, press and hold for 2× speed
- **Playback controls**: speed (0.25×–3×), aspect ratio (fit, crop, stretch), screen lock, rotate
- **Audio track and subtitle selection**, plus loading external `.srt`, `.vtt`, `.ass` and `.ssa` files
- **Picture-in-picture**, and auto-PiP when you leave the app while playing
- **Network streams**: MP4, MKV, WebM, HLS (`.m3u8`) and DASH (`.mpd`) links, with a recent list
- **Opens from other apps**: file managers, galleries and browsers can hand videos to VyBe
- Playlist playback: next and previous within the folder or list you started from

## Deploy on GitHub (no Android Studio needed)

1. Create a new empty repository on GitHub.
2. Push this project to it:
   ```bash
   cd VyBe
   git init
   git add .
   git commit -m "VyBe: initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/VyBe.git
   git push -u origin main
   ```
3. Open the **Actions** tab. The *Build VyBe APK* workflow runs on every push.
4. When it finishes, open the run and download the **VyBe-apk** artifact. It contains
   `VyBe-debug.apk` and `VyBe-release.apk`. Copy one to your phone and install it.

### Publish a downloadable release

Tag a version and push the tag. The workflow attaches the APKs to a GitHub Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Build locally

Open the folder in Android Studio (Koala or newer), or with Gradle 8.7 and JDK 17:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The CI workflow installs Gradle itself, so no Gradle wrapper is committed.
To add one for local use, run `gradle wrapper --gradle-version 8.7`.

## Signing

The release APK is signed with the debug key so it installs right away. To publish
on the Play Store or keep a stable update signature, create your own keystore and
point `signingConfigs` in `app/build.gradle.kts` at it, keeping the keystore
and passwords in GitHub Secrets (never commit them).

## Project layout

```
.github/workflows/build.yml     CI: builds the APKs, attaches them to releases on v* tags
app/src/main/java/com/vybe/player/
  MainActivity.kt               permissions, library loading
  LibraryScreen.kt              videos, folders and stream tabs
  PlayerActivity.kt             ExoPlayer, PiP, orientation, subtitles
  PlayerScreen.kt               controls overlay, gestures, track dialogs
  Data.kt                       MediaStore scan, preferences, formatting
  Theme.kt                      colours, logo, gradient button
```

## Notes

- Minimum Android version is 8.0 (API 26).
- Playback uses the codecs built into the device. Formats the phone's hardware can't decode
  (for example some AC3/DTS audio) may play without sound. Adding a software decoder extension
  such as FFmpeg is the usual way to extend this.
