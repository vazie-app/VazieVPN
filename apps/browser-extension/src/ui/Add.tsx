import { useEffect, useRef, useState } from "preact/hooks";
import type { ConfigPreview, Detection, SubscriptionFetchResult } from "../shared/api.ts";
import { ADVERTISED_FORMATS, parseConfig } from "../shared/parsers.ts";
import { toPreview } from "../shared/preview.ts";
import { isInsecureSubscription, parseSubscriptionLink } from "../shared/subscription.ts";
import { Banner, Button, Card, CloseBar, DetailRow, TextField } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { Icon } from "./Icon.tsx";
import { invalidMessage, subscriptionMessage, unsupportedMessage } from "./messages.ts";
import { navigate } from "./router.ts";
import { call } from "./rpc.ts";

type Step = "method" | "preview" | "review" | "done";

type Subscription =
  | { kind: "loading"; host: string }
  | { kind: "list"; result: Extract<SubscriptionFetchResult, { ok: true }> }
  | { kind: "failed"; host: string | null; reason: Extract<SubscriptionFetchResult, { ok: false }>["reason"] };

/** The example a person is shown: the one configuration-shaped string that is nobody's. */
const EXAMPLE = "vless://00000000-0000-0000-0000-000000000001@vpn.example.com:443?...";

function detect(text: string): Detection {
  if (text.trim() === "") return { kind: "waiting" };
  const result = parseConfig(text);
  if (result.kind === "recognized") return { kind: "recognized", preview: toPreview(result.draft) };
  if (result.kind === "unsupported") return { kind: "unsupported", protocolLabel: "VLESS", feature: result.feature };
  return { kind: "invalid", reason: result.reason };
}

export function Add() {
  const { t, app, toast } = useEnv();
  const [step, setStep] = useState<Step>("method");
  const [link, setLink] = useState("");
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [imported, setImported] = useState(0);
  const token = useRef<string | null>(null);

  const isSubscription = parseSubscriptionLink(link) !== null || isInsecureSubscription(link);
  const detection: Detection = isSubscription ? { kind: "waiting" } : detect(link);

  // A held subscription list is let go of when the page goes away.
  useEffect(() => () => {
    if (token.current) void call("discardSubscription", token.current);
  }, []);

  const close = () => navigate("/", { replace: true });

  const paste = async (): Promise<string | null> => {
    try {
      const text = (await navigator.clipboard.readText()).trim();
      setLink(text);
      return text;
    } catch {
      toast(t.t("x_clipboard_unavailable"));
      return null;
    }
  };

  const advance = async (text = link) => {
    const sub = parseSubscriptionLink(text);
    if (isInsecureSubscription(text)) {
      setSubscription({ kind: "failed", host: null, reason: "insecure" });
      setStep("preview");
      return;
    }
    if (sub) {
      setStep("preview");
      setSubscription({ kind: "loading", host: sub.host });
      // The browser asks for this one server and no other, at this click, and takes the access back afterwards.
      let granted = false;
      try {
        granted = await chrome.permissions.request({ origins: [sub.originPattern] });
      } catch {
        granted = false;
      }
      if (!granted) {
        setSubscription({ kind: "failed", host: sub.host, reason: "permission" });
        return;
      }
      const result = await call("fetchSubscription", text);
      if (result.ok) {
        token.current = result.token;
        setSubscription({ kind: "list", result });
      } else {
        setSubscription({ kind: "failed", host: result.host, reason: result.reason });
      }
      return;
    }
    setSubscription(null);
    const next = detect(text);
    if (next.kind === "recognized") setName(next.preview.suggestedName);
    setStep("preview");
  };

  const save = async () => {
    if (detection.kind !== "recognized" || name.trim() === "") return;
    setSaving(true);
    const id = await call("createFromLink", link, name.trim());
    setSaving(false);
    if (id === null) {
      toast(t.t("config_invalid_malformed"));
      return;
    }
    setImported(1);
    setLink("");
    setStep("done");
  };

  const importAll = async () => {
    if (subscription?.kind !== "list") return;
    setSaving(true);
    const ids = await call("importSubscription", subscription.result.token);
    token.current = null;
    setSaving(false);
    setImported(ids.length);
    setLink("");
    setStep("done");
  };

  if (step === "done") {
    return (
      <div class="frame">
        <header class="topbar" />
        <main class="scroll center">
          <div class="hero-mark">
            <div class="big">
              <Icon name="check" />
            </div>
          </div>
          <h1 style="font-size:24px;margin:12px 0 6px;font-variation-settings:'SHRP' 40">
            {imported > 1 ? t.n("config_done_many_title", imported) : t.t("config_done_title", name || t.t("app_name"))}
          </h1>
          <p class="note">{t.t("config_done_body")}</p>
        </main>
        <div style="padding:0 var(--gutter) 20px">
          <Button onClick={close}>{t.t("config_action_done")}</Button>
        </div>
      </div>
    );
  }

  if (step === "review" && detection.kind === "recognized") {
    const p = detection.preview;
    return (
      <div class="frame">
        <CloseBar title={t.t("config_review_title")} onClose={close} />
        <main class="scroll">
          <TextField label={t.t("config_field_name")} value={name} onInput={setName} placeholder={t.t("config_name_placeholder")} autofocus onEnter={() => void save()} />
          <div style="margin-top:14px">
            <Card>
              <DetailRow label={t.t("config_field_protocol")} value={[p.protocolLabel, p.securityLabel].filter(Boolean).join(" · ")} />
              <DetailRow label={t.t("config_field_transport")} value={p.transportLabel} />
              <DetailRow label={t.t("config_field_server")} value={p.server} />
              {p.serverName && <DetailRow label={t.t("config_field_sni")} value={p.serverName} />}
              {p.fingerprint && <DetailRow label={t.t("config_field_fingerprint")} value={p.fingerprint} />}
              {p.flow && <DetailRow label={t.t("config_field_flow")} value={p.flow} />}
              <DetailRow label={t.t("config_field_name")} value={name.trim() || "—"} mono={false} />
            </Card>
          </div>
          {p.keptParameters.length > 0 && (
            <Banner title={t.t("config_review_kept_title", p.keptParameters.join(", "))}>{t.t("config_review_kept_body")}</Banner>
          )}
          <p class="note">{t.t("config_review_secrets_note")}</p>
        </main>
        <div style="padding:0 var(--gutter) 20px">
          <Button onClick={() => void save()} disabled={saving || name.trim() === ""}>
            {t.t("config_action_save")}
          </Button>
        </div>
      </div>
    );
  }

  if (step === "preview") {
    return (
      <div class="frame">
        <CloseBar title={t.t("config_preview_title")} onClose={close} />
        <main class="scroll">
          <DetectionChip detection={detection} subscription={subscription} />
          <PreviewBody
            detection={detection}
            subscription={subscription}
            name={name}
            onBackToField={() => setStep("method")}
          />
        </main>
        <div style="padding:0 var(--gutter) 20px;display:grid;gap:8px">
          {subscription?.kind === "list" ? (
            <Button onClick={() => void importAll()} disabled={saving}>
              {t.n("config_action_add_all", subscription.result.configs.length)}
            </Button>
          ) : subscription?.kind === "loading" ? null : detection.kind === "recognized" ? (
            <Button onClick={() => setStep("review")}>{t.t("config_action_continue")}</Button>
          ) : (
            <>
              <Button
                icon="paste"
                onClick={async () => {
                  const text = await paste();
                  if (text) await advance(text);
                }}
              >
                {t.t("config_action_paste")}
              </Button>
              <Button variant="text" onClick={() => setStep("method")}>
                {t.t("settings_back")}
              </Button>
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <div class="frame">
      <CloseBar title={t.t("config_method_title")} onClose={close} />
      <main class="scroll">
        <h2 style="margin:6px 4px 6px;font-size:19px">{t.t("config_method_have_link")}</h2>
        <p class="body">{t.t("config_method_reads", ADVERTISED_FORMATS)}</p>
        <div class="input-row" style="margin-top:12px;align-items:flex-end">
          <TextField label={t.t("config_method_link_label")} value={link} onInput={setLink} mono multiline autofocus />
          <button class="icon-button tonal" style="width:48px;height:48px;margin-bottom:0" onClick={() => void paste()} aria-label={t.t("config_action_paste")}>
            <Icon name="paste" />
          </button>
        </div>
        <p class="note" style="margin-top:14px">
          {t.t("config_method_example_label")}
        </p>
        <div class="example" role="img" aria-label={t.t("config_method_example_a11y")}>
          {EXAMPLE}
        </div>
        <p class="body" style="margin-top:14px">
          {t.t("config_method_unsure")}
        </p>
        <p class="body" style="margin-top:8px">
          {t.t("config_method_subscription")}
        </p>
        <p class="note">{t.t("config_method_planned", ADVERTISED_FORMATS)}</p>
        {app.helper.kind === "missing" && <Banner tone="warning">{t.t("x_add_helper_hint")}</Banner>}
      </main>
      <div style="padding:0 var(--gutter) 20px">
        <Button onClick={() => void advance()} disabled={link.trim() === ""}>
          {t.t("config_action_continue")}
        </Button>
      </div>
    </div>
  );
}

function DetectionChip({ detection, subscription }: { detection: Detection; subscription: Subscription | null }) {
  const { t } = useEnv();
  let label: string;
  let detail: string | null = null;
  let tone = "info";
  if (subscription) {
    label = subscription.kind === "failed" ? t.t("config_detection_invalid") : t.t("config_detection_subscription");
    detail = subscription.kind === "list" ? subscription.result.host : subscription.host;
    tone = subscription.kind === "list" ? "success" : subscription.kind === "failed" ? "error" : "info";
  } else {
    switch (detection.kind) {
      case "waiting":
        label = t.t("config_detection_waiting");
        break;
      case "recognized":
        label = detection.preview.protocolLabel;
        detail = [detection.preview.securityLabel, detection.preview.transportLabel].filter(Boolean).join(" · ").toLowerCase() || null;
        tone = "success";
        break;
      case "unsupported":
        label = t.t("config_detection_unsupported");
        detail = detection.feature.value;
        tone = "warning";
        break;
      case "invalid":
        label = t.t("config_detection_invalid");
        tone = "error";
        break;
    }
  }
  const colors: Record<string, string> = {
    info: "background:var(--accent-container);color:var(--on-accent-container)",
    success: "background:var(--success-container);color:var(--on-success-container)",
    warning: "background:var(--warning-container);color:var(--on-warning-container)",
    error: "background:var(--error-container);color:var(--on-error-container)",
  };
  return (
    <div style="display:flex;align-items:center;gap:8px;margin:8px 0 14px">
      <span style={`${colors[tone]};padding:5px 12px;border-radius:999px;font-weight:600;font-size:13px`}>{label}</span>
      {detail && <span style="font-family:var(--mono);font-size:12px;color:var(--text-2)">{detail}</span>}
    </div>
  );
}

function PreviewCard({ preview, name }: { preview: ConfigPreview; name?: string }) {
  return (
    <Card>
      <div class="row">
        <span class="mark">{preview.mark}</span>
        <span class="grow">
          <div class="title">{name || preview.suggestedName}</div>
          <div class="sub" style="font-family:var(--mono)">
            {preview.server}
          </div>
        </span>
      </div>
    </Card>
  );
}

function PreviewBody({ detection, subscription, name, onBackToField }: { detection: Detection; subscription: Subscription | null; name: string; onBackToField: () => void }) {
  const { t } = useEnv();
  void onBackToField;
  if (subscription) {
    if (subscription.kind === "loading") {
      return (
        <>
          <p class="body">{t.t("config_subscription_loading", subscription.host)}</p>
          <p class="note pulse">…</p>
        </>
      );
    }
    if (subscription.kind === "failed") {
      return <p class="error-text" role="alert" style="margin:0 4px;font-size:14px">{subscriptionMessage(t, subscription.reason, subscription.host)}</p>;
    }
    const { configs, skipped, truncated } = subscription.result;
    return (
      <>
        <h2 style="margin:0 4px 10px;font-size:18px">{t.n("config_subscription_found", configs.length)}</h2>
        <Card class="list">
          {configs.map((config, index) => (
            <div class="row" key={index}>
              <span class="mark">{config.mark}</span>
              <span class="grow">
                <div class="title">{config.suggestedName}</div>
                <div class="sub" style="font-family:var(--mono)">
                  {config.server}
                </div>
              </span>
              <span class="trail">{[config.securityLabel, config.transportLabel].filter(Boolean).join(" · ")}</span>
            </div>
          ))}
        </Card>
        {truncated && <p class="note">{t.n("config_subscription_truncated", configs.length)}</p>}
        {skipped > 0 && <p class="note">{t.n("config_subscription_skipped", skipped)}</p>}
        <p class="note">{t.t("config_subscription_once")}</p>
      </>
    );
  }
  switch (detection.kind) {
    case "waiting":
      return (
        <>
          <h2 style="margin:0 4px 8px;font-size:18px">{t.t("config_paste_title")}</h2>
          <p class="body">{t.t("config_paste_body")}</p>
        </>
      );
    case "recognized":
      return <PreviewCard preview={detection.preview} name={name} />;
    case "unsupported":
      return <p class="body">{unsupportedMessage(t, detection.feature)}</p>;
    case "invalid":
      return <p class="error-text" role="alert" style="margin:0 4px;font-size:14px">{invalidMessage(t, detection.reason)}</p>;
  }
}

