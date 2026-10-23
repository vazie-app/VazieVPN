import type { ComponentChildren, JSX } from "preact";
import { useEffect, useRef } from "preact/hooks";
import { Icon, type IconName } from "./Icon.tsx";
import { back } from "./router.ts";
import { useEnv } from "./env.tsx";

export function TopBar({ title, onBack, trailing }: { title: string; onBack?: () => void; trailing?: ComponentChildren }) {
  const { t } = useEnv();
  return (
    <header class="topbar">
      <button class="icon-button" onClick={onBack ?? (() => back())} aria-label={t.t("settings_back")}>
        <Icon name="back" />
      </button>
      <h1>{title}</h1>
      {trailing}
    </header>
  );
}

export function CloseBar({ title, onClose }: { title: string; onClose: () => void }) {
  const { t } = useEnv();
  return (
    <header class="topbar">
      <h1>{title}</h1>
      <button class="icon-button tonal" onClick={onClose} aria-label={t.t("config_close")}>
        <Icon name="close" />
      </button>
    </header>
  );
}

type ButtonVariant = "primary" | "secondary" | "outline" | "destructive" | "text";

export function Button({
  variant = "primary",
  icon,
  compact,
  children,
  ...rest
}: { variant?: ButtonVariant; icon?: IconName; compact?: boolean } & JSX.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button {...rest} class={`button ${variant}${compact ? " compact" : ""}`}>
      {icon && <Icon name={icon} />}
      {children}
    </button>
  );
}

export function Card({ children, padded, class: className }: { children: ComponentChildren; padded?: boolean; class?: string }) {
  return <div class={`card${padded ? " padded" : ""}${className ? ` ${className}` : ""}`}>{children}</div>;
}

export function Section({ title, trailing }: { title: string; trailing?: ComponentChildren }) {
  return (
    <h2 class={`section-title${trailing ? " row" : ""}`}>
      <span>{title}</span>
      {trailing}
    </h2>
  );
}

export function Row({
  icon,
  title,
  subtitle,
  trailing,
  onClick,
  href,
  chevron,
  mark,
}: {
  icon?: IconName;
  mark?: string;
  title: string;
  subtitle?: string;
  trailing?: ComponentChildren;
  onClick?: () => void;
  href?: string;
  chevron?: boolean;
}) {
  const inner = (
    <>
      {icon && (
        <span class="mark" aria-hidden="true">
          <Icon name={icon} width={20} height={20} />
        </span>
      )}
      {mark && (
        <span class="mark" aria-hidden="true">
          {mark}
        </span>
      )}
      <span class="grow">
        <div class="title">{title}</div>
        {subtitle && <div class="sub">{subtitle}</div>}
      </span>
      {trailing && <span class="trail">{trailing}</span>}
      {chevron && <Icon name="chevron" class="chevron" width={20} height={20} />}
    </>
  );
  if (href) {
    return (
      <a class="row" href={href} target="_blank" rel="noopener noreferrer">
        {inner}
      </a>
    );
  }
  if (onClick) {
    return (
      <button class="row" onClick={onClick}>
        {inner}
      </button>
    );
  }
  return <div class="row">{inner}</div>;
}

export function SwitchRow({ title, subtitle, checked, onChange }: { title: string; subtitle?: string; checked: boolean; onChange: (next: boolean) => void }) {
  return (
    <div class="row">
      <span class="grow">
        <div class="title">{title}</div>
        {subtitle && <div class="sub wrap">{subtitle}</div>}
      </span>
      <button class="switch" role="switch" aria-checked={checked} aria-label={title} onClick={() => onChange(!checked)} />
    </div>
  );
}

export function DetailRow({ label, value, mono = true, children }: { label: string; value?: string | null; mono?: boolean; children?: ComponentChildren }) {
  return (
    <div class="detail-row">
      <span class="k">{label}</span>
      <span class={`v${mono ? "" : " plain"}`}>
        {value ?? "—"}
        {children}
      </span>
    </div>
  );
}

export function Banner({ tone, title, children }: { tone?: "warning" | "error" | "success"; title?: string; children: ComponentChildren }) {
  return (
    <div class={`banner${tone ? ` ${tone}` : ""}`} role={tone === "error" ? "alert" : undefined}>
      {title && <strong>{title}</strong>}
      {children}
    </div>
  );
}

export function Dialog({
  title,
  body,
  confirm,
  cancel,
  destructive,
  confirmDisabled,
  onConfirm,
  onCancel,
  children,
}: {
  title: string;
  body?: string;
  confirm: string;
  cancel: string;
  destructive?: boolean;
  confirmDisabled?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ComponentChildren;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.querySelector<HTMLElement>("input, button.primary, button.destructive")?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCancel();
    };
    addEventListener("keydown", onKey);
    return () => removeEventListener("keydown", onKey);
  }, [onCancel]);
  return (
    <div class="scrim" onClick={(e) => e.target === e.currentTarget && onCancel()}>
      <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title" ref={ref}>
        <h2 id="dialog-title">{title}</h2>
        {body && <p>{body}</p>}
        {children}
        <div class="actions">
          <Button variant={destructive ? "destructive" : "primary"} onClick={onConfirm} disabled={confirmDisabled}>
            {confirm}
          </Button>
          <Button variant="text" onClick={onCancel}>
            {cancel}
          </Button>
        </div>
      </div>
    </div>
  );
}

export function TextField({
  label,
  value,
  onInput,
  placeholder,
  mono,
  multiline,
  error,
  onEnter,
  autofocus,
}: {
  label?: string;
  value: string;
  onInput: (value: string) => void;
  placeholder?: string;
  mono?: boolean;
  multiline?: boolean;
  error?: string | null;
  onEnter?: () => void;
  autofocus?: boolean;
}) {
  const Tag = multiline ? "textarea" : "input";
  return (
    <label class="field">
      {label && <span class="label">{label}</span>}
      <Tag
        class={`input${mono ? " mono" : ""}`}
        value={value}
        placeholder={placeholder}
        autofocus={autofocus}
        autocomplete="off"
        autocapitalize="off"
        spellcheck={false}
        onInput={(e) => onInput((e.currentTarget as HTMLInputElement).value)}
        onKeyDown={(e: KeyboardEvent) => {
          if (e.key === "Enter" && !multiline && onEnter) onEnter();
        }}
      />
      {error && <div class="error-text">{error}</div>}
    </label>
  );
}

export function Loading() {
  const { t } = useEnv();
  return <div class="loading">{t.t("x_loading")}</div>;
}

/** Copies text, and clears the clipboard again 30 seconds later if it is still what was put there. */
export async function copySecret(text: string): Promise<void> {
  await navigator.clipboard.writeText(text);
  setTimeout(async () => {
    try {
      if ((await navigator.clipboard.readText()) === text) await navigator.clipboard.writeText("");
    } catch {
      // The clipboard is not readable without focus; leaving it is the safe failure.
    }
  }, 30_000);
}
