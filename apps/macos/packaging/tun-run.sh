#!/bin/sh
# Vazie VPN — the root supervisor of the whole-Mac tunnel.
#
# It is NOT run from the app bundle. The app carries this text inside its own signed executable, and the one administrator
# prompt it shows writes it into a directory that only root can change (/private/var/run/app.vazie.vpn.tun) and starts it
# from there. Nothing here trusts a file the user can write, except to read a short command from the pipe `cmd`:
#
#   run.sh <owner uid> <parent pid> <xray to copy> <team id | -> <pinned sha-256 list | -> <kill switch 0|1>
#   run.sh --recover
#
# What it does, in order:
#   1. copies the engine into its own directory and refuses it unless the COPY is signed by the app's team or its SHA-256
#      is a pinned one (the check is of the copy, so the original cannot be swapped between the check and the run);
#   2. opens the pipe `cmd` (owned by the user, inside the root directory: the user can write to it and cannot replace it);
#   3. waits for `CONFIG <nonce> <json>`. The document is checked against an allow-list by this script before the engine
#      ever sees it: no log files, no listening beyond the loopback, no paths. The server's address, port and the tunnel's
#      name are taken from the checked document, not from the command line;
#   4. starts the engine, gives the tunnel interface its address, keeps the server's own connection off it, sends the Mac's
#      IPv4 into it, points DNS at a resolver inside it and turns IPv6 off for the service, and (if asked) loads a pf anchor
#      that blocks everything else. It reports `running <nonce>` only after a request made like any program's has passed;
#   5. supervises: engine gone, app gone (same process, not just the same number) or `STOP` undo everything. With the kill
#      switch the engine's loss keeps the block and waits for the next CONFIG; without it the network is put back.
#
# Status words (file `status`, readable by everyone): booting, ready, starting <n>, running <n>, lost blocked, lost open,
# failed <code> [<n>], stopped.
#
# The pf anchor is com.apple/050.VazieVPN under the system's own `anchor "com.apple/*"`; only its rules are loaded and
# flushed. The main ruleset and other apps' anchors are never touched, and no global flush is ever made.

umask 077

if [ "$(id -u)" = 0 ]; then
  PATH=/usr/sbin:/usr/bin:/bin:/sbin
  RUN=/private/var/run/app.vazie.vpn.tun
  ROOT=1
else
  # Tests only: a script that is not root can change nothing that matters, so it may be pointed at stand-ins.
  PATH="${VAZIE_TOOLS:+$VAZIE_TOOLS:}/usr/sbin:/usr/bin:/bin:/sbin"
  RUN="${VAZIE_RUN:-}"
  ROOT=0
fi
export PATH

ANCHOR="com.apple/050.VazieVPN"
DNS="1.1.1.1 1.0.0.1"
LOCAL_ADDRESS="100.127.77.1"
PEER_ADDRESS="100.127.77.2"
LOCAL_NETS="10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, 224.0.0.0/4"

OWNER="" PARENT="" XSRC="" TEAM="-" HASHES="-" KILL=0
PSTART=""
STATE="idle"
XPID=""
TUN="" IFACE="" GATEWAY="" SERVER="" PORT="" SPLIT=0
SVC="" DNS_OLD="" V6_OLD="" APPLIED=0
ROUTED=0 HOSTROUTED=0 PF_ON=0 PF_TOKEN=""
STOP=0 DONE=0 FD_OPEN=0 WAS_UP=0
FINAL="stopped"

# ---------------------------------------------------------------------------------------------------------------------
# The allow-list for the engine's configuration. Anything it does not name is refused. It is JavaScript for `osascript`
# (every Mac has it; nothing is installed), reads the document on stdin and prints OK, five facts and the document again.
JS_CHECK=$(cat <<'JS'
ObjC.import('Foundation');
var IPV4 = /^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$/;
function bad(w) { throw new Error(w); }
function plain(v, keys, w) {
  if (v === null || typeof v !== 'object' || Array.isArray(v)) bad(w);
  Object.keys(v).forEach(function (k) { if (keys.indexOf(k) < 0) bad(w + '.' + k); });
  return v;
}
function list(v, w, max) { if (!Array.isArray(v) || v.length > max) bad(w); return v; }
function str(v, w, re, max) {
  if (typeof v !== 'string' || v.length > (max || 512) || /[\u0000-\u001f\u007f]/.test(v) || (re && !re.test(v))) bad(w);
  return v;
}
function num(v, w, lo, hi) { if (typeof v !== 'number' || Math.floor(v) !== v || v < lo || v > hi) bad(w); return v; }
function flag(v, w) { if (typeof v !== 'boolean') bad(w); return v; }
function want(v, w, x) { if (v !== x) bad(w); return v; }
function oneOf(v, w, xs) { if (xs.indexOf(v) < 0) bad(w); return v; }
function opt(o, k, f) { if (o[k] !== undefined) f(o[k]); }

function sockopt(s, w) {
  plain(s, ['interface'], w);
  return str(s.interface, w + '.interface', /^(en|bridge)\d{1,3}$/, 16);
}

function stream(s, w) {
  plain(s, ['network', 'security', 'tlsSettings', 'realitySettings', 'rawSettings', 'wsSettings', 'grpcSettings', 'sockopt'], w);
  oneOf(s.network, w + '.network', ['raw', 'tcp', 'ws', 'grpc']);
  oneOf(s.security, w + '.security', ['none', 'tls', 'reality']);
  opt(s, 'tlsSettings', function (t) {
    plain(t, ['serverName', 'fingerprint', 'alpn'], w + '.tls');
    opt(t, 'serverName', function (v) { str(v, 'serverName', null, 253); });
    opt(t, 'fingerprint', function (v) { str(v, 'fingerprint', /^[A-Za-z0-9_]{1,40}$/, 40); });
    opt(t, 'alpn', function (v) { list(v, 'alpn', 8).forEach(function (a) { str(a, 'alpn', /^[A-Za-z0-9/.\-]{1,32}$/, 32); }); });
  });
  opt(s, 'realitySettings', function (r) {
    plain(r, ['serverName', 'fingerprint', 'publicKey', 'shortId', 'spiderX'], w + '.reality');
    opt(r, 'serverName', function (v) { str(v, 'serverName', null, 253); });
    str(r.fingerprint, 'fingerprint', /^[A-Za-z0-9_]{1,40}$/, 40);
    str(r.publicKey, 'publicKey', /^[A-Za-z0-9_\-+=\/]{1,128}$/, 128);
    str(r.shortId, 'shortId', /^[0-9a-fA-F]{0,32}$/, 32);
    str(r.spiderX, 'spiderX', null, 256);
  });
  opt(s, 'rawSettings', function (r) {
    plain(r, ['header'], w + '.raw');
    plain(r.header, ['type'], w + '.raw.header');
    str(r.header.type, 'header.type', /^[a-z]{1,16}$/, 16);
  });
  opt(s, 'wsSettings', function (v) {
    plain(v, ['path', 'host'], w + '.ws');
    str(v.path, 'ws.path', null, 512);
    opt(v, 'host', function (h) { str(h, 'ws.host', null, 253); });
  });
  opt(s, 'grpcSettings', function (v) {
    plain(v, ['serviceName', 'multiMode'], w + '.grpc');
    str(v.serviceName, 'grpc.serviceName', null, 256);
    flag(v.multiMode, 'grpc.multiMode');
  });
  var chosen = null;
  opt(s, 'sockopt', function (v) { chosen = sockopt(v, w + '.sockopt'); });
  return chosen;
}

function check(cfg) {
  var meta = { tun: null, server: null, port: null, iface: null, split: 0 };
  plain(cfg, ['log', 'stats', 'api', 'policy', 'inbounds', 'outbounds', 'routing'], 'config');
  want(plain(cfg.log, ['loglevel'], 'log').loglevel, 'log.loglevel', 'none');
  plain(cfg.stats, [], 'stats');
  plain(cfg.api, ['tag', 'services'], 'api');
  want(cfg.api.tag, 'api.tag', 'api');
  want(list(cfg.api.services, 'api.services', 1)[0], 'api.services', 'StatsService');
  plain(cfg.policy, ['system'], 'policy');
  plain(cfg.policy.system, ['statsOutboundUplink', 'statsOutboundDownlink'], 'policy.system');
  flag(cfg.policy.system.statsOutboundUplink, 'policy');
  flag(cfg.policy.system.statsOutboundDownlink, 'policy');

  var tuns = 0;
  list(cfg.inbounds, 'inbounds', 8).forEach(function (i) {
    plain(i, ['tag', 'protocol', 'listen', 'port', 'settings', 'sniffing'], 'inbound');
    oneOf(i.tag, 'inbound.tag', ['tun-in', 'http-in', 'socks-in', 'api']);
    if (i.protocol === 'tun') {
      tuns += 1;
      want(i.tag, 'tun.tag', 'tun-in');
      if (i.listen !== undefined || i.port !== undefined) bad('tun.listen');
      plain(i.settings, ['name', 'MTU'], 'tun.settings');
      meta.tun = str(i.settings.name, 'tun.name', /^utun\d{1,3}$/, 8);
      num(i.settings.MTU, 'tun.MTU', 576, 9000);
      opt(i, 'sniffing', function (s) {
        plain(s, ['enabled', 'destOverride', 'routeOnly'], 'sniffing');
        flag(s.enabled, 'sniffing.enabled');
        list(s.destOverride, 'sniffing.destOverride', 4).forEach(function (d) { oneOf(d, 'sniffing.destOverride', ['http', 'tls', 'quic']); });
        flag(s.routeOnly, 'sniffing.routeOnly');
      });
    } else {
      // Everything else this app opens is on the loopback, and only there.
      want(i.listen, 'inbound.listen', '127.0.0.1');
      num(i.port, 'inbound.port', 1024, 65535);
      if (i.sniffing !== undefined) bad('inbound.sniffing');
      if (i.protocol === 'http') {
        plain(i.settings, ['allowTransparent', 'userLevel'], 'http.settings');
        want(i.settings.allowTransparent, 'http.allowTransparent', false);
        num(i.settings.userLevel, 'http.userLevel', 0, 0);
      } else if (i.protocol === 'socks') {
        plain(i.settings, ['auth', 'udp', 'userLevel'], 'socks.settings');
        want(i.settings.auth, 'socks.auth', 'noauth');
        flag(i.settings.udp, 'socks.udp');
        num(i.settings.userLevel, 'socks.userLevel', 0, 0);
      } else if (i.protocol === 'dokodemo-door') {
        plain(i.settings, ['address'], 'api.settings');
        want(i.settings.address, 'api.address', '127.0.0.1');
      } else {
        bad('inbound.protocol');
      }
    }
  });
  if (tuns !== 1) bad('tun.count');

  var proxies = 0;
  list(cfg.outbounds, 'outbounds', 8).forEach(function (o) {
    plain(o, ['tag', 'protocol', 'settings', 'streamSettings'], 'outbound');
    if (o.protocol === 'vless') {
      proxies += 1;
      want(o.tag, 'proxy.tag', 'proxy');
      plain(o.settings, ['vnext'], 'vless.settings');
      var v = list(o.settings.vnext, 'vless.vnext', 1)[0];
      plain(v, ['address', 'port', 'users'], 'vless.vnext');
      meta.server = str(v.address, 'vless.address', IPV4, 15);
      meta.port = num(v.port, 'vless.port', 1, 65535);
      var u = list(v.users, 'vless.users', 1)[0];
      plain(u, ['id', 'encryption', 'level', 'flow'], 'vless.user');
      str(u.id, 'vless.id', /^[A-Za-z0-9._\-]{1,64}$/, 64);
      want(u.encryption, 'vless.encryption', 'none');
      num(u.level, 'vless.level', 0, 0);
      opt(u, 'flow', function (f) { oneOf(f, 'vless.flow', ['xtls-rprx-vision']); });
      meta.iface = stream(o.streamSettings, 'proxy.stream');
      if (!meta.iface) bad('proxy.interface');
    } else if (o.protocol === 'blackhole') {
      want(o.tag, 'block.tag', 'block');
      if (o.settings !== undefined || o.streamSettings !== undefined) bad('block');
    } else if (o.protocol === 'freedom') {
      want(o.tag, 'direct.tag', 'direct');
      if (o.settings !== undefined) bad('direct.settings');
      plain(o.streamSettings, ['sockopt'], 'direct.stream');
      sockopt(o.streamSettings.sockopt, 'direct.sockopt');
    } else {
      bad('outbound.protocol');
    }
  });
  if (proxies !== 1) bad('proxy.count');

  plain(cfg.routing, ['domainStrategy', 'rules'], 'routing');
  want(cfg.routing.domainStrategy, 'routing.domainStrategy', 'AsIs');
  list(cfg.routing.rules, 'routing.rules', 64).forEach(function (r) {
    plain(r, ['type', 'inboundTag', 'outboundTag', 'ip', 'domain', 'network', 'port'], 'rule');
    want(r.type, 'rule.type', 'field');
    oneOf(r.outboundTag, 'rule.outboundTag', ['proxy', 'block', 'direct', 'api']);
    list(r.inboundTag, 'rule.inboundTag', 4).forEach(function (t) { oneOf(t, 'rule.inboundTag', ['tun-in', 'http-in', 'socks-in', 'api']); });
    opt(r, 'ip', function (ips) { list(ips, 'rule.ip', 32).forEach(function (x) { str(x, 'rule.ip', /^[0-9a-f:.]{2,45}\/\d{1,3}$/i, 49); }); });
    opt(r, 'domain', function (ds) {
      meta.split = 1;
      list(ds, 'rule.domain', 600).forEach(function (x) { str(x, 'rule.domain', /^domain:[a-z0-9.\-\[\]:]{1,253}$/, 262); });
    });
    opt(r, 'network', function (n) { oneOf(n, 'rule.network', ['tcp', 'udp', 'tcp,udp']); });
    opt(r, 'port', function (p) { str(p, 'rule.port', /^\d{1,5}(-\d{1,5})?$/, 11); });
  });
  return meta;
}

function run() {
  var data = $.NSFileHandle.fileHandleWithStandardInput.readDataToEndOfFile;
  var text = ObjC.unwrap($.NSString.alloc.initWithDataEncoding(data, $.NSUTF8StringEncoding));
  if (!text || text.length > 262144) return 'ERR size';
  var cfg;
  try { cfg = JSON.parse(text); } catch (e) { return 'ERR json'; }
  var meta;
  try { meta = check(cfg); } catch (e) { return 'ERR ' + e.message; }
  return ['OK', 'TUN=' + meta.tun, 'SERVER=' + meta.server, 'PORT=' + meta.port, 'IFACE=' + meta.iface, 'SPLIT=' + meta.split, JSON.stringify(cfg)].join('\n');
}
JS
)

# ---------------------------------------------------------------------------------------------------------------------

die() { FINAL="failed $1"; exit "$2"; }

status() {
  [ -n "$RUN" ] && [ -d "$RUN" ] || return 0
  printf '%s\n' "$1" > "$RUN/status.tmp" 2>/dev/null && chmod 644 "$RUN/status.tmp" 2>/dev/null && mv -f "$RUN/status.tmp" "$RUN/status" 2>/dev/null
  return 0
}

# What the supervisor has changed, written down in a file only root can write, so that a copy of this script started later
# (after the first was killed) can undo it. A reboot clears it; the app's own record covers that case.
save_state() {
  [ -n "$RUN" ] && [ -d "$RUN" ] || return 0
  {
    printf 'SVC=%s\n' "$SVC"
    printf 'DNS_OLD=%s\n' "$DNS_OLD"
    printf 'V6_OLD=%s\n' "$V6_OLD"
    printf 'APPLIED=%s\n' "$APPLIED"
    printf 'SERVER=%s\n' "$SERVER"
    printf 'ROUTED=%s\n' "$ROUTED"
    printf 'HOSTROUTED=%s\n' "$HOSTROUTED"
    printf 'PF_ON=%s\n' "$PF_ON"
    printf 'PF_TOKEN=%s\n' "$PF_TOKEN"
  } > "$RUN/net.tmp" 2>/dev/null && mv -f "$RUN/net.tmp" "$RUN/net.state" 2>/dev/null
  return 0
}

load_state() {
  [ -f "$RUN/net.state" ] && [ ! -L "$RUN/net.state" ] || return 1
  while IFS== read -r key value; do
    case "$key" in
      SVC) SVC="$value" ;;
      DNS_OLD) DNS_OLD="$value" ;;
      V6_OLD) V6_OLD="$value" ;;
      APPLIED) APPLIED="$value" ;;
      SERVER) SERVER="$value" ;;
      ROUTED) ROUTED="$value" ;;
      HOSTROUTED) HOSTROUTED="$value" ;;
      PF_ON) PF_ON="$value" ;;
      PF_TOKEN) PF_TOKEN="$value" ;;
    esac
  done < "$RUN/net.state"
  return 0
}

is_ipv4() {
  case "$1" in
    *[!0-9.]*|""|.*|*.|*..*) return 1 ;;
  esac
  oldifs=$IFS; IFS=.; set -- $1; IFS=$oldifs
  [ "$#" = 4 ] || return 1
  for part in "$@"; do [ "$part" -le 255 ] 2>/dev/null || return 1; done
  return 0
}

# The Mac's own way out: a default route on a hardware interface. A VPN's `default` (utun…) is not it.
physical_route() {
  line=$(netstat -rn -f inet 2>/dev/null | awk '$1=="default" && $4 ~ /^(en|bridge)[0-9]+$/ {print $2, $4; exit}')
  GATEWAY=${line% *}
  IFACE_NOW=${line#* }
  [ -n "$line" ] && [ -n "$IFACE_NOW" ]
}

find_service() {
  SVC=$(networksetup -listnetworkserviceorder 2>/dev/null | awk -v dev="$1" '
    /^\([0-9*]+\) / { name=$0; sub(/^\([0-9*]+\) /, "", name) }
    /Hardware Port/ && index($0, "Device: " dev ")") { print name; exit }')
}

# -- the Mac's DNS and IPv6 -------------------------------------------------------------------------------------------

apply_net() {
  [ -n "$SVC" ] || return 0
  if [ "$APPLIED" != 1 ]; then
    DNS_OLD=$(networksetup -getdnsservers "$SVC" 2>/dev/null)
    case "$DNS_OLD" in *"aren't any"*|"") DNS_OLD="Empty" ;; esac
    DNS_OLD=$(printf '%s' "$DNS_OLD" | tr '\n' ' ' | sed 's/ *$//')
    V6_OLD=$(networksetup -getinfo "$SVC" 2>/dev/null | awk -F': ' '/^IPv6:/{print $2; exit}')
    APPLIED=1
    save_state
  fi
  # shellcheck disable=SC2086
  networksetup -setdnsservers "$SVC" $DNS >/dev/null 2>&1 || return 1
  # An IPv6 route would go around a tunnel that carries IPv4 only.
  if [ "$V6_OLD" = "Automatic" ]; then networksetup -setv6off "$SVC" >/dev/null 2>&1 || return 1; fi
  return 0
}

# Put back only what is still what this script set: if somebody else changed it since, it is theirs now.
restore_net() {
  [ "$APPLIED" = 1 ] && [ -n "$SVC" ] || { APPLIED=0; return 0; }
  now=$(networksetup -getdnsservers "$SVC" 2>/dev/null | tr '\n' ' ' | sed 's/ *$//')
  if [ "$now" = "$DNS" ]; then
    # shellcheck disable=SC2086
    networksetup -setdnsservers "$SVC" $DNS_OLD >/dev/null 2>&1
  fi
  if [ "$V6_OLD" = "Automatic" ]; then
    v6=$(networksetup -getinfo "$SVC" 2>/dev/null | awk -F': ' '/^IPv6:/{print $2; exit}')
    [ "$v6" = "Off" ] && networksetup -setv6automatic "$SVC" >/dev/null 2>&1
  fi
  APPLIED=0
  save_state
  return 0
}

# -- routes -----------------------------------------------------------------------------------------------------------

# Only what this script added is removed: the halves of the address space or a host route that another VPN made are its own.
unroute_tunnel() {
  if [ "$ROUTED" = 1 ]; then
    route -q delete -net 0.0.0.0/1 >/dev/null 2>&1
    route -q delete -net 128.0.0.0/1 >/dev/null 2>&1
    ROUTED=0
    save_state
  fi
}

unroute_server() {
  if [ "$HOSTROUTED" = 1 ] && [ -n "$SERVER" ]; then
    route -q delete -host "$SERVER" >/dev/null 2>&1
    HOSTROUTED=0
    save_state
  fi
}

unroute() {
  unroute_tunnel
  unroute_server
}

route_all() {
  unroute
  HOSTROUTED=1
  if is_ipv4 "$GATEWAY"; then
    route -q add -host "$SERVER" "$GATEWAY" >/dev/null 2>&1
  else
    route -q add -host "$SERVER" -interface "$IFACE" >/dev/null 2>&1
  fi
  ROUTED=1
  save_state
  route -q add -net 0.0.0.0/1 -interface "$TUN" >/dev/null 2>&1 || return 1
  route -q add -net 128.0.0.0/1 -interface "$TUN" >/dev/null 2>&1 || return 1
  return 0
}

# -- the kill switch --------------------------------------------------------------------------------------------------

# What may leave while the tunnel is the only way out: this Mac, the tunnel, the one connection to the server, DHCP, the
# local network and IPv6 link-local. Everything else is dropped, IPv4 and IPv6 alike. With a split, the engine's own direct
# connections must also leave, so root's sockets (the engine's) pass; the Mac's other programs are the user's, and do not.
pf_rules() {
  split_rule=""
  [ "$5" = 1 ] && split_rule="pass out quick on $2 proto { tcp, udp } from any to any user root keep state"
  cat <<EOF
# Vazie VPN kill switch. Loaded into one anchor; nothing else is touched.
pass quick on lo0 all
pass quick on $1 all
pass out quick on $2 inet proto tcp from any to $3 port $4 flags S/SA keep state
pass quick on $2 inet proto udp from any port 68 to any port 67 keep state
pass quick on $2 inet proto udp from any port 67 to any port 68 keep state
block drop quick on $2 proto { tcp, udp } from any to any port 53
pass out quick inet from any to { $LOCAL_NETS } keep state
pass in quick inet from { $LOCAL_NETS } to any keep state
pass quick inet6 from fe80::/10 to { fe80::/10, ff02::/16 } keep state
pass quick inet6 proto icmp6 from { fe80::/10, :: } to { fe80::/10, ff02::/16 }
$split_rule
block drop quick all
EOF
}

pf_apply() {
  pf_rules "$TUN" "$IFACE" "$SERVER" "$PORT" "$SPLIT" | pfctl -a "$ANCHOR" -f - >/dev/null 2>&1 || return 1
  if [ -z "$PF_TOKEN" ]; then
    out=$(pfctl -E 2>&1)
    PF_TOKEN=$(printf '%s\n' "$out" | awk '/Token/ {print $NF; exit}')
  fi
  PF_ON=1
  save_state
  # The anchor only counts if the system's own ruleset evaluates it, and the rules really are in it.
  pfctl -sr 2>/dev/null | grep -q 'anchor "com.apple/\*"' || return 1
  pfctl -a "$ANCHOR" -sr 2>/dev/null | grep -q 'block drop quick all' || return 1
  return 0
}

pf_remove() {
  [ "$PF_ON" = 1 ] || return 0
  pfctl -a "$ANCHOR" -F rules >/dev/null 2>&1
  [ -n "$PF_TOKEN" ] && pfctl -X "$PF_TOKEN" >/dev/null 2>&1
  PF_TOKEN=""
  PF_ON=0
  save_state
  return 0
}

# -- the engine -------------------------------------------------------------------------------------------------------

stop_engine() {
  [ -n "$XPID" ] || return 0
  kill "$XPID" 2>/dev/null
  n=0
  while kill -0 "$XPID" 2>/dev/null && [ "$n" -lt 20 ]; do sleep 0.1; n=$((n + 1)); done
  kill -9 "$XPID" 2>/dev/null
  wait "$XPID" 2>/dev/null
  XPID=""
  rm -f "$RUN/xray.pid"
}

# The engine is accepted only if the copy in this directory is signed by the app's team, or is a pinned build.
engine_ok() {
  bin="$RUN/xray"
  if [ "$TEAM" != "-" ] && codesign --verify --strict -R="anchor apple generic and certificate leaf[subject.OU] = \"$TEAM\"" "$bin" >/dev/null 2>&1; then
    return 0
  fi
  if [ "$HASHES" != "-" ]; then
    sum=$(shasum -a 256 "$bin" 2>/dev/null | awk '{print $1}')
    case ",$HASHES," in *",$sum,"*) [ -n "$sum" ] && return 0 ;; esac
  fi
  return 1
}

# Starts the engine from a document, undoing its own work on any failure. $1 nonce, $2 document.
start_engine() {
  nonce="$1"; doc="$2"
  status "starting $nonce"
  checked=$(printf '%s' "$doc" | osascript -l JavaScript -e "$JS_CHECK" 2>/dev/null)
  if [ "$(printf '%s\n' "$checked" | sed -n 1p)" != "OK" ]; then status "failed config $nonce"; return 1; fi
  new_tun=$(printf '%s\n' "$checked" | sed -n '2s/^TUN=//p')
  new_server=$(printf '%s\n' "$checked" | sed -n '3s/^SERVER=//p')
  new_port=$(printf '%s\n' "$checked" | sed -n '4s/^PORT=//p')
  new_iface=$(printf '%s\n' "$checked" | sed -n '5s/^IFACE=//p')
  SPLIT=$(printf '%s\n' "$checked" | sed -n '6s/^SPLIT=//p')
  cfg=$(printf '%s\n' "$checked" | sed -n '7p')
  case "$new_tun" in utun[0-9]|utun[0-9][0-9]|utun[0-9][0-9][0-9]) ;; *) status "failed config $nonce"; return 1 ;; esac
  is_ipv4 "$new_server" || { status "failed config $nonce"; return 1; }
  case "$new_port" in ""|*[!0-9]*) status "failed config $nonce"; return 1 ;; esac
  [ -n "$cfg" ] || { status "failed config $nonce"; return 1; }

  # The way out now. The document names the interface it was made for; if the network moved since, say so.
  if ! physical_route; then status "failed network $nonce"; return 1; fi
  if [ "$IFACE_NOW" != "$new_iface" ]; then status "failed network $nonce"; return 1; fi
  stop_engine
  # A server that changed address leaves its old host route behind; remove it before the new one is made.
  [ -n "$SERVER" ] && [ "$SERVER" != "$new_server" ] && unroute_server
  # The Mac moved to another network service (Wi-Fi to Ethernet, say): what was changed on the old one goes back, and the
  # new one is the one that is changed.
  if [ "$APPLIED" = 1 ] && [ -n "$IFACE" ] && [ "$IFACE" != "$new_iface" ]; then restore_net; fi
  TUN="$new_tun"; SERVER="$new_server"; PORT="$new_port"; IFACE="$new_iface"
  [ -n "$SVC" ] && [ "$APPLIED" = 1 ] || find_service "$IFACE"

  printf '%s' "$cfg" | "$RUN/xray" run -c stdin: >/dev/null 2>&1 &
  XPID=$!
  echo "$XPID" > "$RUN/xray.pid"

  up=0
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
    kill -0 "$XPID" 2>/dev/null || { bail engine; return 1; }
    if ifconfig "$TUN" >/dev/null 2>&1; then up=1; break; fi
    sleep 0.5
  done
  [ "$up" = 1 ] || { bail tun; return 1; }
  # An engine that opens its interface and then refuses its configuration is gone a moment later.
  sleep 0.3
  kill -0 "$XPID" 2>/dev/null || { bail engine; return 1; }

  ifconfig "$TUN" inet "$LOCAL_ADDRESS" "$PEER_ADDRESS" netmask 255.255.255.255 up >/dev/null 2>&1 || { bail address; return 1; }
  route_all || { bail route; return 1; }
  if [ "$KILL" = 1 ]; then
    pf_apply || { bail killswitch; return 1; }
  fi
  apply_net || { bail dns; return 1; }

  code=$(curl -s -m 12 -o /dev/null -w '%{http_code}' https://cp.cloudflare.com/generate_204 2>/dev/null)
  [ "$code" = "204" ] || { bail traffic; return 1; }

  STATE="running"
  WAS_UP=1
  write_info "$([ "$PF_ON" = 1 ] && echo on || echo off)"
  status "running $nonce"
  return 0
}

# What the app may read about the running tunnel. The server's address is not in it: this directory is readable by every user
# of the Mac, and where a person's VPN server is is theirs to tell.
write_info() {
  printf 'tun=%s\niface=%s\nkillswitch=%s\n' "$TUN" "$IFACE" "$1" > "$RUN/info.tmp" && chmod 644 "$RUN/info.tmp" && mv -f "$RUN/info.tmp" "$RUN/info"
}

# Undo, then say why: the app acts on the word, so what it describes must already be true.
bail() {
  abort_start
  status "failed $1 $nonce"
  return 1
}

# A start that did not work leaves nothing of itself behind: the engine, the routes, the block and the DNS go back. (The
# kill switch is not kept for a tunnel that never came up: nothing was being protected yet.)
abort_start() {
  stop_engine
  if [ "$KILL" = 1 ] && [ "$PF_ON" = 1 ] && [ "$WAS_UP" = 1 ]; then
    # A tunnel that had been up and could not be brought back: the block is kept, and the Mac says so.
    unroute_tunnel
    STATE="lost"
    write_info blocked
    return 0
  fi
  unroute
  pf_remove
  restore_net
  STATE="idle"
}

# The engine ended under a tunnel that had been up.
engine_lost() {
  stop_engine
  STATE="lost"
  if [ "$KILL" = 1 ] && [ "$PF_ON" = 1 ]; then
    # The block stays: the interface is gone, the default route is the physical one, and pf drops what comes through it.
    unroute_tunnel
    write_info blocked
    status "lost blocked"
  else
    unroute
    restore_net
    status "lost open"
  fi
}

finish() {
  [ "$DONE" = 1 ] && return 0
  DONE=1
  stop_engine
  unroute
  pf_remove
  restore_net
  if [ "$FD_OPEN" = 1 ]; then exec 3>&- 3<&-; fi
  rm -f "$RUN/cmd" "$RUN/xray" "$RUN/xray.pid" "$RUN/info" "$RUN/net.state" "$RUN/net.tmp" "$RUN/pid"
  status "$FINAL"
}

# Everything an earlier copy of this script may have left, undone. Safe to run twice and with nothing to undo.
recover() {
  load_state || true
  if [ -f "$RUN/xray.pid" ]; then
    old=$(cat "$RUN/xray.pid" 2>/dev/null)
    case "$old" in ""|*[!0-9]*) ;; *)
      # Only if that process is still the engine this directory holds, not whatever took the number since.
      if [ "$(ps -o command= -p "$old" 2>/dev/null | awk '{print $1}')" = "$RUN/xray" ]; then kill "$old" 2>/dev/null; fi ;;
    esac
  fi
  unroute
  pf_remove
  restore_net
  rm -f "$RUN/cmd" "$RUN/xray" "$RUN/xray.pid" "$RUN/info" "$RUN/net.state" "$RUN/net.tmp" "$RUN/pid"
  status stopped
}

# ---------------------------------------------------------------------------------------------------------------------

if [ "$1" = "--print-pf-rules" ]; then
  # For the tests: the rules for (tun, interface, server, port, split).
  pf_rules "$2" "$3" "$4" "$5" "${6:-0}"
  exit 0
fi

if [ "$1" = "--check-config" ]; then
  # For the tests: the checker alone.
  osascript -l JavaScript -e "$JS_CHECK"
  exit $?
fi

if [ -z "$RUN" ] || [ ! -d "$RUN" ] || [ -L "$RUN" ]; then echo "no run directory" >&2; exit 2; fi
if [ "$ROOT" = 1 ]; then
  [ "$(stat -f %u "$RUN" 2>/dev/null)" = 0 ] || { echo "run directory not root's" >&2; exit 2; }
  mode=$(stat -f %Sp "$RUN" 2>/dev/null)
  [ "$(printf '%s' "$mode" | cut -c6)" = "-" ] && [ "$(printf '%s' "$mode" | cut -c9)" = "-" ] || { echo "run directory is writable by others" >&2; exit 2; }
fi

if [ "$1" = "--recover" ]; then
  recover
  exit 0
fi

OWNER="$1"; PARENT="$2"; XSRC="$3"; TEAM="${4:--}"; HASHES="${5:--}"; KILL="${6:-0}"
case "$OWNER" in ""|*[!0-9]*) echo "bad owner" >&2; exit 2 ;; esac
case "$PARENT" in ""|*[!0-9]*) echo "bad parent" >&2; exit 2 ;; esac
case "$TEAM" in -) ;; [A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9]) ;; *) echo "bad team" >&2; exit 2 ;; esac
case "$HASHES" in -) ;; *[!0-9a-f,]*) echo "bad hashes" >&2; exit 2 ;; esac
case "$KILL" in 0|1) ;; *) echo "bad kill switch flag" >&2; exit 2 ;; esac
case "$XSRC" in /*) ;; *) echo "bad engine path" >&2; exit 2 ;; esac

# One supervisor at a time; a dead one's leftovers are undone first.
if [ -f "$RUN/pid" ]; then
  other=$(cat "$RUN/pid" 2>/dev/null)
  case "$other" in ""|*[!0-9]*) ;; *)
    if [ "$other" != "$$" ] && kill -0 "$other" 2>/dev/null; then echo "busy" >&2; exit 3; fi ;;
  esac
  recover
fi
echo "$$" > "$RUN/pid"
trap 'STOP=1' TERM INT HUP
trap 'finish' EXIT

PSTART=$(ps -o lstart= -p "$PARENT" 2>/dev/null)
[ -n "$PSTART" ] || die parent 4

status booting
cp "$XSRC" "$RUN/xray" 2>/dev/null && chmod 500 "$RUN/xray" 2>/dev/null || die engine 5
engine_ok || die integrity 6

rm -f "$RUN/cmd"
mkfifo -m 600 "$RUN/cmd" 2>/dev/null || die engine 5
[ "$ROOT" = 1 ] && chown "$OWNER" "$RUN/cmd" 2>/dev/null
exec 3<>"$RUN/cmd"
FD_OPEN=1
status ready

tick=0
while [ "$STOP" = 0 ]; do
  line=""
  if read -r -t 1 line <&3; then
    case "$line" in
      "CONFIG "*)
        rest=${line#CONFIG }
        nonce=${rest%% *}
        doc=${rest#* }
        case "$nonce" in ""|*[!0-9a-f]*) ;; *)
          if [ "${#nonce}" -ge 8 ] && [ "${#nonce}" -le 32 ] && [ "$doc" != "$rest" ]; then start_engine "$nonce" "$doc"; fi ;;
        esac ;;
      "STOP") STOP=1 ;;
    esac
  fi
  [ "$STOP" = 1 ] && break

  tick=$((tick + 1))
  kill -0 "$PARENT" 2>/dev/null || break
  # The same process, not a later one that was given its number.
  if [ $((tick % 5)) = 0 ] && [ "$(ps -o lstart= -p "$PARENT" 2>/dev/null)" != "$PSTART" ]; then break; fi

  if [ "$STATE" = "running" ] && [ -n "$XPID" ] && ! kill -0 "$XPID" 2>/dev/null; then engine_lost; fi
  # Somebody else flushing the block is noticed and put back.
  if [ "$KILL" = 1 ] && [ "$PF_ON" = 1 ] && [ $((tick % 5)) = 0 ]; then
    pfctl -a "$ANCHOR" -sr 2>/dev/null | grep -q 'block drop quick all' || pf_apply || status "failed killswitch"
  fi
done
finish
exit 0
