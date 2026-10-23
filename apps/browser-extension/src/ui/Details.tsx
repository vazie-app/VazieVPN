import { trafficReading } from "../shared/format.ts";
import { Card, DetailRow, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";

/** The technical details of the live session, as rows. Opened from the traffic line on Home. */
export function Details() {
  const { app, t } = useEnv();
  const d = app.snapshot.diagnostics;
  const connected = app.snapshot.state.kind === "connected";
  const unit = (u: string) => t.t(`home_unit_${{ B: "bytes", KB: "kilobytes", MB: "megabytes", GB: "gigabytes" }[u]}`);
  const amount = (bytes: number | undefined) => {
    const r = trafficReading(bytes);
    return r ? t.t("home_value_amount", r.amount, unit(r.unit)) : t.t("home_value_unknown");
  };
  return (
    <div class="frame">
      <TopBar title={t.t("home_details_title")} />
      <main class="scroll">
        {!connected || !d ? (
          <Card padded>
            <strong>{t.t("home_details_empty_title")}</strong>
            <p class="note" style="margin:6px 0 0">
              {t.t("home_details_empty_body")}
            </p>
          </Card>
        ) : (
          <Card class="console">
            <DetailRow label={t.t("home_diagnostics_engine")} value={d.engine} />
            <DetailRow label={t.t("home_diagnostics_protocol")} value={d.protocol} />
            <DetailRow label={t.t("home_diagnostics_security")} value={d.security} />
            <DetailRow label={t.t("home_diagnostics_transport")} value={d.transport} />
            {d.flow && <DetailRow label={t.t("home_diagnostics_flow")} value={d.flow} />}
            <DetailRow label={t.t("home_diagnostics_endpoint")} value={d.endpointHost} />
            <DetailRow label={t.t("home_diagnostics_port")} value={String(d.endpointPort)} />
            <DetailRow label={t.t("home_diagnostics_latency")} value={d.latencyMs === null ? t.t("home_value_unknown") : t.t("home_value_milliseconds", d.latencyMs)} />
            <DetailRow label={t.t("home_diagnostics_traffic")} value={t.t("home_value_traffic", amount(app.traffic?.rx), amount(app.traffic?.tx))} />
            <DetailRow label={t.t("home_diagnostics_dns")} value={d.dnsMode} />
            <DetailRow label={t.t("home_diagnostics_ip")} value={t.t("home_value_traffic", t.t(d.ipv4 ? "home_value_on" : "home_value_off"), t.t(d.ipv6 ? "home_value_on" : "home_value_off"))} />
          </Card>
        )}
      </main>
    </div>
  );
}
