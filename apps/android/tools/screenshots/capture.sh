#!/bin/bash
# capture.sh <en|ru> <out-dir>: the five store/README screens of Vazie VPN, from the running debug build on the emulator.
# Demo data must already be in the app (three configurations, two apps chosen for split tunneling).
set -u
L=$1; OUT=$2; mkdir -p "$OUT"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5560}"
A="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=app.vazie.vpn.debug
tap(){ $A shell input tap "$1" "$2"; sleep "${3:-2}"; }
# Hides the keyboard if it is shown; a Back with no keyboard would leave the screen.
hidekb(){ $A shell dumpsys input_method | grep -q "mInputShown=true" && $A shell input keyevent 4; sleep 1; }
shot(){ $A exec-out screencap -p > "$OUT/$1.png"; }
home(){ $A shell am start -a android.intent.action.VIEW -d vazie-vpn://home $PKG >/dev/null 2>&1; sleep 3; }
settings(){ $A shell am start -a android.intent.action.VIEW -d vazie-vpn://settings $PKG >/dev/null 2>&1; sleep 3; }

$A shell cmd locale set-app-locales $PKG --locales "$L"; sleep 2
$A shell am force-stop $PKG; $A shell am start -n $PKG/app.vazie.vpn.MainActivity >/dev/null; sleep 4

home; shot 01-home

$A shell am start -a android.intent.action.VIEW -d vazie-vpn://add-config $PKG >/dev/null 2>&1; sleep 3
for try in 1 2 3 4; do
  tap 466 774 2
  $A shell input keycombination 113 29; $A shell input keyevent 67; sleep 1
  # In short chunks: one long `input text` drops characters while the keyboard catches up.
  for part in 'vless://00000000-0000-4000-' '8000-000000000004@203.0.113.7' ':443?encryption=none' '&security=reality' '&sni=www.example.com' '&fp=chrome&pbk=' 'AAAAAAAAAAAAAAAAAAAAA' 'AAAAAAAAAAAAAAAAAAAAAA' '&sid=0123abcd&type=tcp' '&flow=xtls-rprx-vision'; do
    $A shell "input text '$part'"; sleep 0.7
  done
  sleep 2
  $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  $A exec-out cat /sdcard/u.xml | grep -q 'security=reality&amp;sni=www.example.com&amp;fp=chrome' && break
  echo "retry typing ($try)" >&2
done
hidekb
tap 540 2226 3; tap 540 2226 3
tap 540 467 1
$A shell input keycombination 113 29; $A shell input keyevent 67
if [ "$L" = ru ]; then $A shell input text 'Backup'; else $A shell input text 'Backup'; fi
hidekb; $A shell input keyevent 61; sleep 1.5
shot 02-add-configuration
tap 984 216 2   # close without saving

settings; tap 250 764 3; shot 03-split-tunneling; tap 95 216 2
tap 220 497 3; shot 04-appearance
tap 450 615 3   # Milk
home; shot 05-home-light
settings; tap 220 497 3; tap 178 615 3   # back to Night Indigo
home
