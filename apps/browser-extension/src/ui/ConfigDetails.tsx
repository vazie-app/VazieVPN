import { useEffect, useState } from "preact/hooks";
import { lastUsedOf, type LastUsed } from "../shared/format.ts";
import { securityLabel, transportLabel } from "../shared/preview.ts";
import type { ProfileDetails } from "../shared/state.ts";
import { Banner, Card, copySecret, DetailRow, Dialog, Section, TextField, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { Icon, type IconName } from "./Icon.tsx";
import { back } from "./router.ts";
import { call } from "./rpc.ts";

const pad = (n: number) => String(n).padStart(2, "0");

export function lastUsedText(t: ReturnType<typeof useEnv>["t"], used: LastUsed): string {
  switch (used.kind) {
    case "never":
      return t.t("config_last_used_never");
    case "justNow":
      return t.t("config_last_used_just_now");
    case "today":
      return t.t("config_last_used_today", `${pad(used.hour)}:${pad(used.minute)}`);
    case "yesterday":
      return t.t("config_last_used_yesterday");
    case "earlier":
      return t.t("config_last_used_earlier", `${pad(used.day)}.${pad(used.month)}.${String(used.year).padStart(4, "0")}`);
  }
}

function ActionRow({ icon, label, onClick, destructive }: { icon: IconName; label: string; onClick: () => void; destructive?: boolean }) {
  return (
    <button class="row" onClick={onClick} style={destructive ? "color:var(--error)" : undefined}>
      <Icon name={icon} />
      <span class="grow">
        <div class="title">{label}</div>
      </span>
    </button>
  );
}

export function ConfigDetails({ id }: { id: string }) {
  const { t, app, toast } = useEnv();
  const [details, setDetails] = useState<ProfileDetails | null | undefined>(undefined);
  const [secret, setSecret] = useState<string | null>(null);
  const [rename, setRename] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Read again whenever the list changes: a rename, or a connection that has just marked it used.
  const key = JSON.stringify(app.profiles.find((p) => p.id === id));
  useEffect(() => {
    void call("details", id).then(setDetails);
  }, [id, key]);

  // A revealed credential does not outlive the page or a minute's looking away.
  useEffect(() => {
    if (secret === null) return;
    const timer = setTimeout(() => setSecret(null), 60_000);
    return () => clearTimeout(timer);
  }, [secret]);

  if (details === undefined) return <div class="frame" />;
  if (details === null) {
    return (
      <div class="frame">
        <TopBar title={t.t("config_section_configuration")} />
        <main class="scroll">
          <p class="body">{t.t("config_details_missing")}</p>
        </main>
      </div>
    );
  }

  const sec = securityLabel(details.security);
  const transport = [transportLabel(details.transport), details.transportDetail].filter(Boolean).join(" · ");
  return (
    <div class="frame">
      <TopBar title={details.name} />
      <main class="scroll">
        <Section title={t.t("config_section_configuration")} />
        <Card>
          <DetailRow label={t.t("config_field_protocol")} value={[details.protocolLabel, sec].filter(Boolean).join(" · ")} />
          <DetailRow label={t.t("config_field_transport")} value={transport} />
          <DetailRow label={t.t("config_field_server")} value={`${details.endpointHost}:${details.endpointPort}`} />
          {details.serverName && <DetailRow label={t.t("config_field_sni")} value={details.serverName} />}
          {details.fingerprint && <DetailRow label={t.t("config_field_fingerprint")} value={details.fingerprint} />}
          {details.flow && <DetailRow label={t.t("config_field_flow")} value={details.flow} />}
          <DetailRow label={t.t("config_field_last_used")} value={lastUsedText(t, lastUsedOf(details.lastUsedAt, Date.now()))} mono={false} />
        </Card>

        <Section title={t.t("config_section_credentials")} />
        <Card>
          <DetailRow label="UUID" value={secret ?? "••••••••-••••-••••-••••-••••••••••••"}>
            <button
              onClick={async () => {
                if (secret !== null) setSecret(null);
                else setSecret(await call("revealCredential", id));
              }}
            >
              {t.t(secret !== null ? "config_secret_hide" : "config_secret_show")}
            </button>
          </DetailRow>
        </Card>
        {details.unknownParameterNames.length > 0 && (
          <p class="note">
            {t.t("config_review_kept_title", details.unknownParameterNames.join(", "))}
            <br />
            {t.t("config_review_kept_body")}
          </p>
        )}

        <Section title={t.t("config_section_actions")} />
        <Card class="list">
          <ActionRow icon="edit" label={t.t("config_action_rename")} onClick={() => setRename(details.name)} />
          <ActionRow
            icon="copy"
            label={t.t("config_action_duplicate")}
            onClick={async () => {
              await call("duplicate", id, t.t("config_duplicate_name", details.name));
              toast(t.t("config_duplicated"));
            }}
          />
          <ActionRow icon="share" label={t.t("config_action_export")} onClick={() => setExporting(true)} />
        </Card>

        <Section title={t.t("config_section_danger")} />
        <Card>
          <ActionRow icon="trash" label={t.t("config_action_delete")} onClick={() => setDeleting(true)} destructive />
        </Card>
      </main>

      {rename !== null && (
        <Dialog
          title={t.t("config_rename_title")}
          confirm={t.t("config_rename_confirm")}
          cancel={t.t("config_rename_cancel")}
          confirmDisabled={rename.trim() === ""}
          onConfirm={async () => {
            const name = rename.trim();
            setRename(null);
            await call("rename", id, name);
          }}
          onCancel={() => setRename(null)}
        >
          <TextField label={t.t("config_field_name")} value={rename} onInput={setRename} autofocus />
          <p class="note">{t.t("config_rename_body")}</p>
        </Dialog>
      )}

      {exporting && (
        <Dialog
          title={t.t("config_export_title")}
          confirm={t.t("x_export_confirm")}
          cancel={t.t("config_export_cancel")}
          onConfirm={async () => {
            setExporting(false);
            const link = await call("exportLink", id);
            if (link) {
              await copySecret(link);
              toast(t.t("x_link_copied"));
            }
          }}
          onCancel={() => setExporting(false)}
        >
          <Banner tone="warning">{t.t("config_export_warning")}</Banner>
          <p class="note">{t.t("config_export_body")}</p>
        </Dialog>
      )}

      {deleting && (
        <Dialog
          title={t.t("config_delete_title")}
          body={t.t("config_delete_body", details.name)}
          confirm={t.t("config_delete_confirm")}
          cancel={t.t("config_delete_cancel")}
          destructive
          onConfirm={async () => {
            setDeleting(false);
            await call("remove", id);
            back();
          }}
          onCancel={() => setDeleting(false)}
        />
      )}
    </div>
  );
}

