// The launcher icon the person chose, drawn the way Android composes it: a mark on a plate. The ten marks are the
// app's own foreground art (`public/icons/marks`); the plates are its gradients (`ic_launcher_plates.xml`).

import type { AppIconId, AppIconPlate } from "./state.ts";

export type PlateColors = readonly [string] | readonly [string, string] | readonly [string, string, string];

/** Start, centre and end of each plate's gradient, from the corner the light comes from. */
export const PLATES: Record<AppIconPlate, PlateColors> = {
  deep: ["#4A1585", "#25115E", "#071A3A"],
  light: ["#F6F3FC", "#E7E1F4"],
  ink: ["#12111A"],
  forest: ["#005523", "#0C3A00", "#172B00"],
  rose: ["#760037", "#4D002D", "#3A0128"],
  amber: ["#453900", "#342000", "#251605"],
  mist: ["#7D6990", "#4D4A57", "#3A3A3A"],
};

export const ICONS: Record<AppIconId, { signature: AppIconPlate; plates: AppIconPlate[] }> = {
  orbit: { signature: "deep", plates: ["deep", "light", "ink"] },
  lime: { signature: "forest", plates: ["forest", "light", "ink"] },
  emerald: { signature: "forest", plates: ["forest", "light", "ink"] },
  magenta: { signature: "rose", plates: ["rose", "light", "ink"] },
  cotton_candy: { signature: "deep", plates: ["deep", "light", "ink"] },
  crimson: { signature: "rose", plates: ["rose", "light", "ink"] },
  gold: { signature: "amber", plates: ["amber", "light", "ink"] },
  pearl: { signature: "mist", plates: ["mist", "light", "ink"] },
  onyx: { signature: "mist", plates: ["mist", "light", "ink"] },
  monochrome: { signature: "ink", plates: ["light", "ink"] },
};

export const MARK_PAPER = "#F7F4FF";
export const MARK_INK = "#12111A";

export const ICON_IDS: AppIconId[] = [
  "orbit", "lime", "emerald", "magenta", "cotton_candy", "crimson", "gold", "pearl", "onyx", "monochrome",
];

/** The plate as a CSS background, for the picker. */
export function plateCss(plate: AppIconPlate): string {
  const colors = PLATES[plate];
  return colors.length === 1 ? colors[0] : `linear-gradient(135deg, ${colors.join(", ")})`;
}

type Ctx = OffscreenCanvasRenderingContext2D | CanvasRenderingContext2D;
type Canvas = OffscreenCanvas | HTMLCanvasElement;

export type Dot = "connected" | "busy" | "failed" | null;

const DOT_COLORS: Record<Exclude<Dot, null>, string> = {
  connected: "#9FD8B8",
  busy: "#E8C47C",
  failed: "#F0A77F",
};

/** Paints one icon at `size` pixels. `mark` is the foreground art (432 px, the adaptive canvas). */
export function drawIcon(
  ctx: Ctx,
  size: number,
  mark: CanvasImageSource,
  icon: AppIconId,
  plate: AppIconPlate,
  dot: Dot,
  makeCanvas: (w: number, h: number) => Canvas,
): void {
  ctx.clearRect(0, 0, size, size);
  const radius = size * 0.22;
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(0, 0, size, size, radius);
  ctx.clip();

  const colors = PLATES[plate];
  if (colors.length === 1) {
    ctx.fillStyle = colors[0];
  } else {
    const gradient = ctx.createLinearGradient(0, 0, size, size);
    colors.forEach((color, index) => gradient.addColorStop(index / (colors.length - 1), color));
    ctx.fillStyle = gradient;
  }
  ctx.fillRect(0, 0, size, size);

  // The launcher shows the middle 72 of the adaptive canvas's 108.
  const source = 432 * (72 / 108);
  const offset = (432 - source) / 2;
  if (icon === "monochrome") {
    // A flat mark: ink on a light plate, paper on a dark one.
    const tint = makeCanvas(size, size);
    const tctx = tint.getContext("2d") as Ctx;
    tctx.drawImage(mark, offset, offset, source, source, 0, 0, size, size);
    tctx.globalCompositeOperation = "source-in";
    tctx.fillStyle = plate === "light" ? MARK_INK : MARK_PAPER;
    tctx.fillRect(0, 0, size, size);
    ctx.drawImage(tint as CanvasImageSource, 0, 0);
  } else {
    ctx.drawImage(mark, offset, offset, source, source, 0, 0, size, size);
  }
  ctx.restore();

  if (dot) {
    const d = size * 0.42;
    const cx = size - d / 2 - size * 0.02;
    const cy = size - d / 2 - size * 0.02;
    ctx.beginPath();
    ctx.arc(cx, cy, d / 2, 0, Math.PI * 2);
    ctx.fillStyle = "#0D1020";
    ctx.fill();
    ctx.beginPath();
    ctx.arc(cx, cy, d / 2 - Math.max(1, size * 0.07), 0, Math.PI * 2);
    ctx.fillStyle = DOT_COLORS[dot];
    ctx.fill();
  }
}
