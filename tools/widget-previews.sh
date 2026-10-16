#!/usr/bin/env bash
# Regenerates the widget picker previews from the real widgets.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew -q :app:testDebugUnitTest --tests '*WidgetPreviewRenderTest*'

src=app/build/widget-previews
res=app/src/main/res
for widget in quick_connect dashboard; do
  for language in en ru; do
    for mode in day night; do
      case "$language-$mode" in
        en-day)   dir=drawable-xxhdpi ;;
        en-night) dir=drawable-night-xxhdpi ;;
        ru-day)   dir=drawable-ru-xxhdpi ;;
        ru-night) dir=drawable-ru-night-xxhdpi ;;
      esac
      mkdir -p "$res/$dir"
      cwebp -quiet -lossless -exact "$src/$widget-$mode-$language.png" -o "$res/$dir/widget_preview_$widget.webp"
    done
  done
done
ls -la "$res"/drawable*/widget_preview_*
