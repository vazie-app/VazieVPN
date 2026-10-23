#!/usr/bin/env python3
"""Builds the published images from real screenshots taken by capture.sh.

    python3 tools/screenshots/compose.py <raw-en-dir> <raw-ru-dir>

Writes, for English and Russian:
  docs/assets/screenshots/<lang>/0N-*.png                  README screenshots (540×1200, rounded)
  docs/assets/showcase/showcase-<lang>.png                 the README's hero composition (2400×1200)
  fastlane/metadata/android/<locale>/images/phoneScreenshots/N.png   store screenshots (1080×1920, 9:16)
  fastlane/metadata/android/<locale>/images/featureGraphic.png       the store banner (1024×500)

Needs Pillow (`python3 -m pip install pillow`). The fonts are the app's own (core/designsystem/src/main/res/font).
Nothing here draws an interface: every phone screen is a screenshot of the real app.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT = os.path.join(ROOT, "core/designsystem/src/main/res/font/geologica.ttf")
ICON = os.path.join(ROOT, "docs/assets/brand/vazie-app-icon.png")

SCREENS = ["01-home", "02-add-configuration", "03-split-tunneling", "04-appearance", "05-home-light"]
README_SCREENS = SCREENS[:4]

# The Vazie palette: Night Indigo and lavender.
INK = (14, 16, 34)
INDIGO = (36, 26, 74)
LAVENDER = (167, 176, 255)
LILAC = (201, 182, 242)
WHITE = (244, 242, 252)
MUTED = (178, 176, 210)

CAPTIONS = {
    "en": [
        ("Connect in one tap", "Your own VLESS servers on one calm screen"),
        ("Paste a link, review, save", "Keys stay hidden and never leave the phone"),
        ("Choose what goes through VPN", "Split tunneling by app"),
        ("Make it yours", "Two themes and ten app icons"),
        ("Milk or Night Indigo", "No account, no ads, no analytics"),
    ],
    "ru": [
        ("Подключение одним нажатием", "Ваши VLESS-серверы на одном спокойном экране"),
        ("Вставьте, проверьте, сохраните", "Ключи скрыты и не покидают телефон"),
        ("Выбирайте, что идёт через VPN", "Раздельное туннелирование по приложениям"),
        ("Оформите по-своему", "Две темы и десять значков приложения"),
        ("Milk или Night Indigo", "Без аккаунта, рекламы и аналитики"),
    ],
}

HERO = {
    "en": ("Vazie VPN", "A calm client for your own\nVLESS configurations", ["VLESS · REALITY · TLS", "Split tunneling", "Widgets & Quick Settings", "No account · No analytics"]),
    "ru": ("Vazie VPN", "Спокойный клиент для ваших\nVLESS-конфигураций", ["VLESS · REALITY · TLS", "Раздельное туннелирование", "Виджеты и быстрые настройки", "Без аккаунта и аналитики"]),
}
STORE_LOCALE = {"en": "en-US", "ru": "ru-RU"}


def font(size, weight=400):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([weight, 0])
    return f


def background(w, h):
    """Night Indigo, lighter towards the top right, with a soft lavender glow."""
    corners = Image.new("RGB", (2, 2))
    corners.putdata([INK, INDIGO, INK, (24, 20, 52)])
    base = corners.resize((w, h), Image.BILINEAR)
    glow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(glow).ellipse((int(w * 0.5), int(-h * 0.3), int(w * 1.2), int(h * 0.5)), fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(min(w, h) // 4)).point(lambda v: int(v * 0.28))
    return Image.composite(Image.new("RGB", (w, h), LILAC), base, glow)


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def phone(shot, height):
    """The screenshot as a phone screen: rounded, with a thin lavender rim."""
    w = round(shot.width * height / shot.height)
    screen = rounded(shot.resize((w, height), Image.LANCZOS), radius=round(w * 0.075))
    rim = 6
    frame = Image.new("RGBA", (w + 2 * rim, height + 2 * rim), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle(
        (0, 0, frame.width - 1, frame.height - 1), round(w * 0.075) + rim, fill=(58, 52, 96, 255), outline=(*LAVENDER, 140), width=2
    )
    frame.alpha_composite(screen, (rim, rim))
    return frame


def paste_with_shadow(canvas, item, xy):
    shadow = Image.new("RGBA", (item.width + 160, item.height + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((80, 100, 80 + item.width, 100 + item.height), 80, fill=(4, 4, 16, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(40))
    canvas.alpha_composite(shadow, (xy[0] - 80, xy[1] - 80))
    canvas.alpha_composite(item, xy)


def centered(draw, y, text, f, fill, width):
    box = draw.textbbox((0, 0), text, font=f)
    draw.text(((width - (box[2] - box[0])) // 2, y), text, font=f, fill=fill)


def fitted(draw, text, size, weight, width):
    """The largest size up to [size] at which [text] fits in [width]."""
    while size > 20 and draw.textlength(text, font=font(size, weight)) > width:
        size -= 2
    return font(size, weight)


def store_image(shot, title, subtitle):
    w, h = 1080, 1920
    canvas = background(w, h).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    centered(draw, 120, title, fitted(draw, title, 64, 700, w - 120), WHITE, w)
    centered(draw, 214, subtitle, fitted(draw, subtitle, 36, 400, w - 120), MUTED, w)
    item = phone(shot, 1500)
    paste_with_shadow(canvas, item, ((w - item.width) // 2, 340))
    return canvas.convert("RGB")


def showcase(shots, lang):
    w, h = 2400, 1200
    canvas = background(w, h).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    name, tagline, chips = HERO[lang]
    icon = Image.open(ICON).convert("RGBA").resize((150, 150), Image.LANCZOS)
    canvas.alpha_composite(rounded(icon, 36), (150, 250))
    draw.text((150, 440), name, font=font(110, 700), fill=WHITE)
    draw.multiline_text((152, 590), tagline, font=font(46, 400), fill=MUTED, spacing=14)
    x, y = 152, 760
    chip_font = font(26, 500)
    for chip in chips:
        tw = draw.textlength(chip, font=chip_font)
        if x + tw + 40 > 960:
            x, y = 152, y + 64
        draw.rounded_rectangle((x, y, x + tw + 36, y + 48), 24, fill=(36, 32, 72), outline=(*LAVENDER, 110), width=2)
        draw.text((x + 18, y + 9), chip, font=chip_font, fill=LILAC)
        x += tw + 52
    # Three real screens, staggered.
    for i, (key, top) in enumerate([("01-home", 190), ("02-add-configuration", 100), ("03-split-tunneling", 190)]):
        item = phone(shots[key], 900)
        paste_with_shadow(canvas, item, (1010 + i * 455, top))
    return canvas.convert("RGB")


def feature_graphic(shots, lang):
    """The store banner (1024×500): the icon, the name, one line, and the real home screen."""
    w, h = 1024, 500
    canvas = background(w, h).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    icon = Image.open(ICON).convert("RGBA").resize((96, 96), Image.LANCZOS)
    canvas.alpha_composite(rounded(icon, 24), (64, 110))
    draw.text((64, 226), "Vazie VPN", font=font(64, 700), fill=WHITE)
    line = HERO[lang][1].replace("\n", " ")
    draw.multiline_text((66, 312), "\n".join(_wrap(draw, line, font(26, 400), 520)), font=font(26, 400), fill=MUTED, spacing=8)
    item = phone(shots["01-home"], 640)
    paste_with_shadow(canvas, item, (690, 70))
    return canvas.convert("RGB")


def _wrap(draw, text, f, width):
    lines, current = [], ""
    for word in text.split():
        candidate = (current + " " + word).strip()
        if draw.textlength(candidate, font=f) <= width:
            current = candidate
        else:
            lines.append(current)
            current = word
    return lines + [current]


def main(raw_en, raw_ru):
    for lang, raw in (("en", raw_en), ("ru", raw_ru)):
        shots = {key: Image.open(os.path.join(raw, key + ".png")).convert("RGB") for key in SCREENS}

        readme_dir = os.path.join(ROOT, "docs/assets/screenshots", lang)
        os.makedirs(readme_dir, exist_ok=True)
        for key in README_SCREENS:
            rounded(shots[key].resize((540, 1200), Image.LANCZOS), 40).save(os.path.join(readme_dir, key + ".png"), optimize=True)

        showcase_dir = os.path.join(ROOT, "docs/assets/showcase")
        os.makedirs(showcase_dir, exist_ok=True)
        showcase(shots, lang).save(os.path.join(showcase_dir, f"showcase-{lang}.png"), optimize=True)

        store_dir = os.path.join(ROOT, "fastlane/metadata/android", STORE_LOCALE[lang], "images/phoneScreenshots")
        os.makedirs(store_dir, exist_ok=True)
        for n, (key, (title, subtitle)) in enumerate(zip(SCREENS, CAPTIONS[lang]), start=1):
            store_image(shots[key], title, subtitle).save(os.path.join(store_dir, f"{n}.png"), optimize=True)
        feature_graphic(shots, lang).save(os.path.join(os.path.dirname(store_dir), "featureGraphic.png"), optimize=True)
    print("done")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
