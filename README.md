# SA TV (Android phone + Android TV)

Modern dark UI, category chips, search, favorites, auto-failover between multiple
stream links, and a full-screen player (landscape + hidden system bars).

## Controls
Phone: tap = show/hide controls, swipe up/down = next/previous channel, long-press a card = favorite.
TV remote: OK = controls, Up/Down (or CH+/CH-) = change channel, Back = exit player.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo and upload this whole folder (keep the `.github` folder).
2. Open the repo -> Actions -> "Build APK" -> Run workflow.
3. When it finishes, download the `SATV-apk` artifact -> unzip -> `app-debug.apk`.

## Or build in Android Studio
File > Open this folder > Build > Build APK(s). Android Studio generates `local.properties` itself.

## Install
- Phone: copy the APK, tap it, allow "install unknown apps".
- iFFALCON / Android TV: put the APK on a USB drive (open with a file manager app),
  or use "Send Files to TV", or `adb connect <tv-ip>` then `adb install app-debug.apk`.

## Channels
Built-in Indian Bengali list: `app/src/main/assets/channels.m3u` (several links per channel are failover sources).
The app also loads https://iptv-org.github.io/iptv/languages/ben.m3u (Settings can change/disable it).
