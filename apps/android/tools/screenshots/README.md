# Screenshots

Every published image of Vazie VPN — the README's screenshots and showcase, the store screenshots and the store
banner — is made from real screenshots of the debug build on an emulator. Nothing is drawn by hand.

## 1. An emulator

Any phone AVD with a 1080×2400 screen (for example Pixel 8, Android 16, `google_apis` arm64-v8a):

```bash
sdkmanager "system-images;android-36;google_apis;arm64-v8a"
avdmanager create avd -n VazieShots -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_8
emulator -avd VazieShots -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -port 5560 &
```

A clean status bar (9:41, full battery, no notifications):

```bash
adb -s emulator-5560 shell settings put global sysui_demo_allowed 1
for c in "enter" "clock -e hhmm 0941" "battery -e level 100 -e plugged false" \
         "network -e wifi show -e level 4 -e fully true -e mobile hide" "notifications -e visible false"; do
  adb -s emulator-5560 shell am broadcast -a com.android.systemui.demo -e command $c
done
```

## 2. Demonstration data

Install the debug build (`./gradlew assembleDebug`, then `adb install app/build/outputs/apk/debug/app-universal-debug.apk`),
skip the welcome tour, and add three configurations through **Add configuration** — only documentation addresses
(RFC 5737) and `example.com` names, never a real server:

| Name | Link |
|---|---|
| Home server | `vless://00000000-0000-4000-8000-000000000001@192.0.2.10:443?encryption=none&security=reality&sni=www.example.com&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=0123abcd&type=tcp&flow=xtls-rprx-vision` |
| Office | `vless://00000000-0000-4000-8000-000000000002@vpn.example.com:443?encryption=none&security=tls&sni=vpn.example.com&fp=chrome&type=ws&host=vpn.example.com&path=/ws` |
| Travel | `vless://00000000-0000-4000-8000-000000000003@198.51.100.20:443?encryption=none&security=tls&sni=edge.example.org&fp=chrome&type=grpc&serviceName=tunnel` |

Select **Home server**, and in **Split tunneling** choose «All but the chosen apps» with Calendar and Clock.

## 3. Capture and compose

```bash
tools/screenshots/capture.sh en /tmp/vazie-shots/en
tools/screenshots/capture.sh ru /tmp/vazie-shots/ru
python3 -m pip install pillow
python3 tools/screenshots/compose.py /tmp/vazie-shots/en /tmp/vazie-shots/ru
```

`capture.sh` drives the app with `adb` (tap coordinates are for a 1080×2400 screen) and takes five screens per
language. `compose.py` writes `docs/assets/screenshots/`, `docs/assets/showcase/` and
`fastlane/metadata/android/*/images/`. Look at every image before committing it.
