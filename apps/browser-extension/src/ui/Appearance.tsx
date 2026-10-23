import { useEffect, useRef } from "preact/hooks";
import { drawIcon, ICON_IDS, ICONS, plateCss } from "../shared/icons.ts";
import type { AppIconId, AppIconPlate, Appearance as AppearanceId } from "../shared/state.ts";
import { Section, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { call } from "./rpc.ts";

const marks = new Map<string, Promise<HTMLImageElement>>();
function loadMark(icon: AppIconId): Promise<HTMLImageElement> {
  let image = marks.get(icon);
  if (!image) {
    image = new Promise((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = reject;
      element.src = chrome.runtime.getURL(`icons/marks/${icon}.webp`);
    });
    marks.set(icon, image);
  }
  return image;
}

/** One launcher icon, drawn exactly as the toolbar draws it. */
export function IconPreview({ icon, plate, size = 64 }: { icon: AppIconId; plate: AppIconPlate; size?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    let alive = true;
    void loadMark(icon).then((mark) => {
      const element = canvas.current;
      const ctx = element?.getContext("2d");
      if (!alive || !element || !ctx) return;
      const scale = devicePixelRatio || 1;
      element.width = size * scale;
      element.height = size * scale;
      drawIcon(ctx, size * scale, mark, icon, plate, null, (w, h) => {
        const c = document.createElement("canvas");
        c.width = w;
        c.height = h;
        return c;
      });
    });
    return () => {
      alive = false;
    };
  }, [icon, plate, size]);
  return <canvas ref={canvas} style={`width:${size}px;height:${size}px;border-radius:22%`} aria-hidden="true" />;
}

const SWATCH: Record<AppearanceId, string[]> = {
  night_indigo: ["#0D1020", "#9DB0FF", "#F1E3C8"],
  milk: ["#FAF8F4", "#4A5BC4", "#1D1F3A"],
};

export function Appearance() {
  const { app, t } = useEnv();
  const { appearance, appIcon, appIconPlate } = app.prefs;
  const plates = ICONS[appIcon].plates;
  return (
    <div class="frame">
      <TopBar title={t.t("settings_appearance_title")} />
      <main class="scroll">
        <Section title={t.t("settings_theme")} />
        <div class="theme-choice">
          {(["night_indigo", "milk"] as const).map((id) => (
            <button key={id} class="theme-card" aria-pressed={appearance === id} onClick={() => void call("setPrefs", { appearance: id })}>
              <div class="swatch" style={`background:${SWATCH[id][0]}`}>
                {SWATCH[id].slice(1).map((color) => (
                  <i style={`background:${color}`} />
                ))}
              </div>
              <b>{t.t(`settings_appearance_${id}`)}</b>
            </button>
          ))}
        </div>

        <Section title={t.t("settings_app_icon")} />
        <p class="body" style="margin-bottom:12px">
          {t.t("x_app_icon_body")}
        </p>
        <div class="icon-grid">
          {ICON_IDS.map((id) => (
            <button
              key={id}
              class="icon-option"
              aria-pressed={appIcon === id}
              onClick={() => void call("setPrefs", { appIcon: id, appIconPlate: ICONS[id].plates.includes(appIconPlate) ? appIconPlate : ICONS[id].signature })}
            >
              <IconPreview icon={id} plate={ICONS[id].plates.includes(appIconPlate) ? appIconPlate : ICONS[id].signature} size={56} />
              <span>{t.t(`settings_app_icon_${id}`)}</span>
            </button>
          ))}
        </div>

        <Section title={t.t("settings_app_icon_plate")} />
        <div class="plate-row">
          {plates.map((plate) => (
            <button key={plate} class="plate-option" aria-pressed={appIconPlate === plate} onClick={() => void call("setPrefs", { appIconPlate: plate })}>
              <i style={`background:${plateCss(plate)}`} />
              <span>{t.t(`settings_app_icon_plate_${plate}`)}</span>
            </button>
          ))}
        </div>
      </main>
    </div>
  );
}
