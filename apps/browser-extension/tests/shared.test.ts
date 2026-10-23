import assert from "node:assert/strict";
import { test } from "node:test";
import { parseConfig } from "../src/shared/parsers.ts";
import { serializeVless } from "../src/shared/vlessSerializer.ts";
import { isInsecureSubscription, parseSubscriptionLink, readSubscription } from "../src/shared/subscription.ts";
import { supports } from "../src/shared/support.ts";
import { xrayOutbounds } from "../src/shared/xrayConfig.ts";
import type { SplitTunnel } from "../src/shared/splitTunnel.ts";
import { addSite, normalizeSite, pacScript, routesThroughTunnel, siteOfUrl } from "../src/shared/splitTunnel.ts";
import { formatSessionDuration, lastUsedOf, safeMenuLabel, trafficReading } from "../src/shared/format.ts";
import { Secret } from "../src/shared/secret.ts";
import { readFaq, readLinks } from "../src/shared/backend.ts";
import type { VlessOutbound } from "../src/shared/model.ts";

const ID = "00000000-0000-4000-8000-000000000001";

function outboundOf(link: string): VlessOutbound {
  const result = parseConfig(link);
  assert.equal(result.kind, "recognized");
  return (result as Extract<typeof result, { kind: "recognized" }>).draft.outbound;
}

test("a link survives parse → serialize → parse", () => {
  const links = [
    `vless://${ID}@relay.example.net:443?security=reality&pbk=KEY&sid=ab&sni=www.example.com&fp=chrome&flow=xtls-rprx-vision&spx=%2F&foo=bar#Home%20server`,
    `vless://${ID}@relay.example.net:443?type=ws&security=tls&sni=relay.example.net&path=%2Fws&host=cdn.example.net&alpn=h2%2Chttp%2F1.1`,
    `vless://${ID}@[2001:db8::1]:8443?type=grpc&security=tls&serviceName=svc&mode=multi`,
  ];
  for (const link of links) {
    const first = parseConfig(link);
    assert.equal(first.kind, "recognized");
    const draft = (first as Extract<typeof first, { kind: "recognized" }>).draft;
    const again = parseConfig(serializeVless(draft.outbound, draft.suggestedName));
    assert.equal(again.kind, "recognized");
    assert.deepEqual((again as Extract<typeof again, { kind: "recognized" }>).draft, draft);
  }
});

test("secrets never print", () => {
  const secret = Secret.of("hunter2");
  assert.equal(`${secret}`, "Secret(…)");
  assert.equal(JSON.stringify({ secret }), '{"secret":"Secret(…)"}');
  assert.equal(secret.expose(), "hunter2");
});

test("subscription links: https only, no user info, the host is all that is shown", () => {
  assert.equal(parseSubscriptionLink("https://sub.example.com/token/abc?x=1")?.host, "sub.example.com");
  assert.equal(parseSubscriptionLink("https://sub.example.com:8443/t")?.originPattern, "https://sub.example.com:8443/*");
  assert.equal(parseSubscriptionLink("https://user:pw@sub.example.com/t"), null);
  assert.equal(parseSubscriptionLink("https://sub.example.com/t with space"), null);
  assert.equal(parseSubscriptionLink("vless://x"), null);
  assert.equal(isInsecureSubscription("http://sub.example.com/t"), true);
});

test("a subscription is read as links or as Base64 of links, and counts what it left out", () => {
  const good = `vless://${ID}@a.example.net:443#A`;
  const other = `vless://${ID}@b.example.net:443?type=kcp#B`;
  const plain = readSubscription(`${good}\n\n# comment\n${other}\nvmess://zzz\n`);
  assert.equal(plain.recognized.length, 1);
  assert.equal(plain.skipped, 2);
  const encoded = btoa(`${good}\n${good}`).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  assert.equal(readSubscription(encoded).recognized.length, 2);
  assert.equal(readSubscription("<html>nope</html>").recognized.length, 0);
});

test("what the engine cannot run is refused before anything starts", () => {
  assert.deepEqual(supports(outboundOf(`vless://${ID}@h.example:443?security=tls&allowInsecure=1`)), {
    ok: false,
    feature: "security",
  });
  assert.deepEqual(supports(outboundOf(`vless://${ID}@h.example:443?security=tls&fp=unsafe`)), {
    ok: false,
    feature: "security",
  });
  assert.deepEqual(supports(outboundOf(`vless://${ID}@h.example:443?security=tls&fp=chrome`)), { ok: true });
});

test("the Xray outbound is the app's: raw network, pre-quantum fingerprint, REALITY defaults", () => {
  const [proxy] = xrayOutbounds(
    outboundOf(`vless://${ID}@h.example:443?security=reality&pbk=KEY&fp=chrome&flow=xtls-rprx-vision`),
  ) as [Record<string, any>];
  assert.equal(proxy.protocol, "vless");
  assert.equal(proxy.streamSettings.network, "raw");
  assert.equal(proxy.streamSettings.realitySettings.fingerprint, "hellochrome_120");
  assert.equal(proxy.streamSettings.realitySettings.shortId, "");
  assert.equal(proxy.streamSettings.realitySettings.spiderX, "/");
  assert.equal(proxy.settings.vnext[0].users[0].flow, "xtls-rprx-vision");
  assert.equal(proxy.settings.vnext[0].users[0].encryption, "none");
});

test("sites are normalized from whatever was typed", () => {
  assert.equal(normalizeSite("https://Sub.Example.com/path?q=1"), "sub.example.com");
  assert.equal(normalizeSite("*.example.com"), "example.com");
  assert.equal(normalizeSite("example.com:8080"), "example.com");
  assert.equal(normalizeSite("192.168.1.1"), "192.168.1.1");
  assert.equal(normalizeSite("300.1.1.1"), null);
  assert.equal(normalizeSite("not a host"), null);
  assert.equal(normalizeSite(""), null);
  assert.equal(siteOfUrl("chrome://settings"), null);
  assert.equal(siteOfUrl("https://www.example.com/a"), "www.example.com");
  const added = addSite({ mode: "exclude", sites: [] }, "https://b.example.com");
  assert.deepEqual(added?.sites, ["b.example.com"]);
});

test("split tunneling decides per site, and a mode with no sites changes nothing", () => {
  assert.equal(routesThroughTunnel({ mode: "exclude", sites: [] }, "a.com"), true);
  assert.equal(routesThroughTunnel({ mode: "exclude", sites: ["a.com"] }, "x.a.com"), false);
  assert.equal(routesThroughTunnel({ mode: "exclude", sites: ["a.com"] }, "nota.com"), true);
  assert.equal(routesThroughTunnel({ mode: "include", sites: ["a.com"] }, "a.com"), true);
  assert.equal(routesThroughTunnel({ mode: "include", sites: ["a.com"] }, "b.com"), false);
});

function runPac(split: SplitTunnel, host: string): string {
  const find = new Function(`${pacScript(split, 4242)}; return FindProxyForURL;`)() as (u: string, h: string) => string;
  return find(`https://${host}/`, host);
}

test("the generated PAC agrees with routesThroughTunnel and never falls back to DIRECT for tunnel traffic", () => {
  const hosts = ["a.com", "x.a.com", "b.com", "localhost", "192.168.1.5", "10.0.0.1", "8.8.8.8", "printer.local", "intranet"];
  const splits: SplitTunnel[] = [
    { mode: "off", sites: [] },
    { mode: "exclude", sites: ["a.com"] },
    { mode: "include", sites: ["a.com"] },
  ];
  for (const split of splits) {
    for (const host of hosts) {
      const answer = runPac(split, host);
      const isLocal = ["localhost", "192.168.1.5", "10.0.0.1", "printer.local", "intranet"].includes(host);
      if (isLocal) assert.equal(answer, "DIRECT", `${split.mode} ${host}`);
      else assert.equal(answer, routesThroughTunnel(split, host) ? "PROXY 127.0.0.1:4242" : "DIRECT", `${split.mode} ${host}`);
    }
  }
});

test("durations, traffic and last-used read as the app reads them", () => {
  assert.equal(formatSessionDuration(252), "04:12");
  assert.equal(formatSessionDuration(3725), "1:02:05");
  assert.deepEqual(trafficReading(900), { amount: "900", unit: "B" });
  assert.deepEqual(trafficReading(1536), { amount: "1", unit: "KB" });
  assert.deepEqual(trafficReading(5 * 1024 * 1024 + 900_000), { amount: "5.8", unit: "MB" });
  assert.equal(trafficReading(null), null);
  const now = new Date(2026, 9, 19, 15, 0).getTime();
  assert.equal(lastUsedOf(null, now).kind, "never");
  assert.equal(lastUsedOf(now - 60_000, now).kind, "justNow");
  assert.equal(lastUsedOf(now + 60_000, now).kind, "justNow");
  assert.deepEqual(lastUsedOf(new Date(2026, 9, 19, 9, 5).getTime(), now), { kind: "today", hour: 9, minute: 5 });
  assert.equal(lastUsedOf(new Date(2026, 9, 18, 23, 0).getTime(), now).kind, "yesterday");
  assert.deepEqual(lastUsedOf(new Date(2026, 8, 1, 12, 0).getTime(), now), { kind: "earlier", year: 2026, month: 9, day: 1 });
});

test("a name that looks like an address never reaches a menu", () => {
  assert.equal(safeMenuLabel("Home server"), "Home server");
  assert.equal(safeMenuLabel("relay.example.net"), null);
  assert.equal(safeMenuLabel("203.0.113.7"), null);
  assert.equal(safeMenuLabel("a@b"), null);
  assert.equal(safeMenuLabel(ID), null);
  assert.equal(safeMenuLabel("A very long configuration name indeed"), "A very long configu…");
});

test("backend answers are read defensively", () => {
  const links = readLinks({
    links: [
      { key: "ok", section: "AUTHOR", url: "https://t.me/x", title: { ru: "а", en: "a" } },
      { key: "bad-url", section: "AUTHOR", url: "http://t.me/x", title: { ru: "а", en: "a" } },
      { key: "bad-section", section: "NOPE", url: "https://t.me/x", title: { ru: "а", en: "a" } },
      { key: "mail", section: "SUPPORT", url: "mailto:support@vazie.app", title: { ru: "п", en: "s" } },
    ],
  });
  assert.deepEqual(links?.links.map((l) => l.key), ["ok", "mail"]);
  assert.equal(readLinks({ links: [] }), null);
  assert.equal(readFaq({ items: [] })?.items.length, 0);
  assert.equal(readFaq({ items: [{ key: "k" }] }), null);
  const faq = readFaq({
    items: [{ key: "k", question: { ru: "в" }, answer: { ru: "о\nк", en: "a" }, link: { url: "//evil.example", label: { ru: "л" } } }],
  });
  assert.equal(faq?.items[0]?.link, null);
  assert.equal(faq?.items[0]?.answer.en, "a");
});
