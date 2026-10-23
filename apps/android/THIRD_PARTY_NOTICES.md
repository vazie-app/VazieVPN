# Third-party notices — the VPN engine

Vazie VPN ships one native library per ABI, `libgojni.so`, from the unmodified release of
[XTLS/libXray](https://github.com/XTLS/libXray) `v26.7.28` (verified by SHA-256 at build time). It is a Go program
that statically links Xray-core and the Go modules below. The list was read from the library itself (the module paths
compiled into it); each module's license text, with its copyright notice, is in
[`third-party/go-licenses/`](third-party/go-licenses).

The Android libraries and the fonts are listed in [NOTICE.md](NOTICE.md).

## Compatibility with GPL-3.0-or-later

Vazie VPN is licensed under GPL-3.0-or-later ([NOTICE.md](NOTICE.md)). Every component the APK carries allows that,
including the "or later" choice:

| License | Components | With GPL-3.0-or-later |
|---|---|---|
| **GPL-3.0-or-later** | `sagernet/sing`, `sagernet/sing-shadowsocks` | Same license; "or any later version" is in their own notice |
| **MPL-2.0** | Xray-core, REALITY | Allowed: no file is marked "Incompatible With Secondary Licenses"; MPL-2.0 names "GPL version 2.0 or any later version" as a Secondary License |
| **LGPL-3.0 with a static-linking exception** | `juju/ratelimit` | Allowed: LGPL-3.0 is GPL-3.0 with extra permissions; the library stays under its own terms |
| **Apache-2.0** | AndroidX, Kotlin, kotlinx, Ktor, OkHttp, Okio, Dagger/Hilt, Guava, javax/jakarta.inject, JSR-305, JetBrains annotations, JSpecify, Chucker (no-op), gVisor, gRPC, genproto, btree, netlink, netns, go-proxyproto | Allowed: Apache-2.0 is compatible with GPL version 3 and later |
| **BSD-2/BSD-3, MIT** | libXray, the Go runtime, `golang.org/x/*`, utls, miekg/dns, protobuf, quic-go, WireGuard-go, SLF4J and the other modules below | Allowed; their notices travel with the APK in this file and `third-party/go-licenses/` |
| **SIL OFL 1.1** | Geologica, Martian Mono fonts | Allowed: the OFL permits bundling the fonts with any software; the fonts stay under the OFL |

What each license asks of a binary distribution, and how it is met:

- **GPL-3.0**: the Corresponding Source of the whole APK — this repository for the app, and the engine source
  published with every release (see NOTICE.md).
- **MPL-2.0**: the source of the covered files stays available — the same engine source archive.
- **Apache-2.0**: the license text and any NOTICE file. Of the Android libraries only `jakarta.inject` has a NOTICE;
  it is packaged in the APK (`META-INF/NOTICE.md`).
- **BSD, MIT**: the copyright notice and license text — `third-party/go-licenses/` and NOTICE.md.

## Modules

| Module | Version | License | License text |
|---|---|---|---|
| [github.com/andybalholm/brotli](https://github.com/andybalholm/brotli) | `v1.0.6` | MIT | [github.com_andybalholm_brotli.txt](third-party/go-licenses/github.com_andybalholm_brotli.txt) |
| [github.com/apernet/quic-go](https://github.com/apernet/quic-go) | `v0.59.1-0.20260425001925…` | MIT | [github.com_apernet_quic-go.txt](third-party/go-licenses/github.com_apernet_quic-go.txt) |
| [github.com/cloudflare/circl](https://github.com/cloudflare/circl) | `v1.6.4` | BSD-3-Clause | [github.com_cloudflare_circl.txt](third-party/go-licenses/github.com_cloudflare_circl.txt) |
| [github.com/ghodss/yaml](https://github.com/ghodss/yaml) | `v1.0.1-0.20220118164431-…` | MIT and BSD-3-Clause | [github.com_ghodss_yaml.txt](third-party/go-licenses/github.com_ghodss_yaml.txt) |
| [github.com/google/btree](https://github.com/google/btree) | `v1.1.2` | Apache-2.0 | [github.com_google_btree.txt](third-party/go-licenses/github.com_google_btree.txt) |
| [github.com/google/uuid](https://github.com/google/uuid) | `v1.6.0` | BSD-3-Clause | [github.com_google_uuid.txt](third-party/go-licenses/github.com_google_uuid.txt) |
| [github.com/gorilla/websocket](https://github.com/gorilla/websocket) | `v1.5.3` | BSD-2-Clause | [github.com_gorilla_websocket.txt](third-party/go-licenses/github.com_gorilla_websocket.txt) |
| [github.com/juju/ratelimit](https://github.com/juju/ratelimit) | `v1.0.2` | LGPL-3.0 with a static-linking exception | [github.com_juju_ratelimit.txt](third-party/go-licenses/github.com_juju_ratelimit.txt) |
| [github.com/klauspost/compress](https://github.com/klauspost/compress) | `v1.17.4` | BSD-3-Clause (parts Apache-2.0, MIT) | [github.com_klauspost_compress.txt](third-party/go-licenses/github.com_klauspost_compress.txt) |
| [github.com/klauspost/cpuid/v2](https://github.com/klauspost/cpuid) | `v2.4.0` | MIT | [github.com_klauspost_cpuid_v2.txt](third-party/go-licenses/github.com_klauspost_cpuid_v2.txt) |
| [github.com/miekg/dns](https://github.com/miekg/dns) | `v1.1.72` | BSD-3-Clause | [github.com_miekg_dns.txt](third-party/go-licenses/github.com_miekg_dns.txt) |
| [github.com/pelletier/go-toml](https://github.com/pelletier/go-toml) | `v1.9.5` | MIT | [github.com_pelletier_go-toml.txt](third-party/go-licenses/github.com_pelletier_go-toml.txt) |
| [github.com/pion/dtls/v3](https://github.com/pion/dtls) | `v3.1.4` | MIT | [github.com_pion_dtls_v3.txt](third-party/go-licenses/github.com_pion_dtls_v3.txt) |
| [github.com/pion/stun/v3](https://github.com/pion/stun) | `v3.1.6` | MIT | [github.com_pion_stun_v3.txt](third-party/go-licenses/github.com_pion_stun_v3.txt) |
| [github.com/pion/transport/v4](https://github.com/pion/transport) | `v4.0.2` | MIT | [github.com_pion_transport_v4.txt](third-party/go-licenses/github.com_pion_transport_v4.txt) |
| [github.com/pires/go-proxyproto](https://github.com/pires/go-proxyproto) | `v0.15.0` | Apache-2.0 | [github.com_pires_go-proxyproto.txt](third-party/go-licenses/github.com_pires_go-proxyproto.txt) |
| [github.com/quic-go/qpack](https://github.com/quic-go/qpack) | `v0.6.0` | MIT | [github.com_quic-go_qpack.txt](third-party/go-licenses/github.com_quic-go_qpack.txt) |
| [github.com/refraction-networking/utls](https://github.com/refraction-networking/utls) | `v1.8.3-0.20260301010127-…` | BSD-3-Clause | [github.com_refraction-networking_utls.txt](third-party/go-licenses/github.com_refraction-networking_utls.txt) |
| [github.com/robfig/cron/v3](https://github.com/robfig/cron) | `v3.0.1` | MIT | [github.com_robfig_cron_v3.txt](third-party/go-licenses/github.com_robfig_cron_v3.txt) |
| [github.com/sagernet/sing](https://github.com/SagerNet/sing) | `v0.5.1` | **GPL-3.0-or-later** | [github.com_sagernet_sing.txt](third-party/go-licenses/github.com_sagernet_sing.txt) |
| [github.com/sagernet/sing-shadowsocks](https://github.com/SagerNet/sing-shadowsocks) | `v0.2.7` | **GPL-3.0-or-later** | [github.com_sagernet_sing-shadowsocks.txt](third-party/go-licenses/github.com_sagernet_sing-shadowsocks.txt) |
| [github.com/vishvananda/netlink](https://github.com/vishvananda/netlink) | `v1.3.1` | Apache-2.0 | [github.com_vishvananda_netlink.txt](third-party/go-licenses/github.com_vishvananda_netlink.txt) |
| [github.com/vishvananda/netns](https://github.com/vishvananda/netns) | `v0.0.5` | Apache-2.0 | [github.com_vishvananda_netns.txt](third-party/go-licenses/github.com_vishvananda_netns.txt) |
| [The Go runtime and standard library](https://github.com/golang/go) | `go1.26.5` | BSD-3-Clause | [golang.org_go_runtime_and_standard_library.txt](third-party/go-licenses/golang.org_go_runtime_and_standard_library.txt) |
| [github.com/xtls/libxray](https://github.com/XTLS/libXray) | `v26.7.28` | MIT | [github.com_xtls_libxray.txt](third-party/go-licenses/github.com_xtls_libxray.txt) |
| [github.com/xtls/reality](https://github.com/XTLS/REALITY) | `v0.0.0-20260322125925-92…` | MPL-2.0 | [github.com_xtls_reality.txt](third-party/go-licenses/github.com_xtls_reality.txt) |
| [github.com/xtls/xray-core](https://github.com/XTLS/Xray-core) | `v1.260327.1-0.2026072807…` | MPL-2.0 | [github.com_xtls_xray-core.txt](third-party/go-licenses/github.com_xtls_xray-core.txt) |
| [go4.org/netipx](https://github.com/go4org/netipx) | `v0.0.0-20231129151722-fd…` | BSD-3-Clause | [go4.org_netipx.txt](third-party/go-licenses/go4.org_netipx.txt) |
| [golang.org/x/crypto](https://github.com/golang/crypto) | `v0.54.0` | BSD-3-Clause | [golang.org_x_crypto.txt](third-party/go-licenses/golang.org_x_crypto.txt) |
| [golang.org/x/mobile](https://github.com/golang/mobile) | `v0.0.0-20260709172247-61…` | BSD-3-Clause | [golang.org_x_mobile.txt](third-party/go-licenses/golang.org_x_mobile.txt) |
| [golang.org/x/net](https://github.com/golang/net) | `v0.57.0` | BSD-3-Clause | [golang.org_x_net.txt](third-party/go-licenses/golang.org_x_net.txt) |
| [golang.org/x/sync](https://github.com/golang/sync) | `v0.22.0` | BSD-3-Clause | [golang.org_x_sync.txt](third-party/go-licenses/golang.org_x_sync.txt) |
| [golang.org/x/sys](https://github.com/golang/sys) | `v0.47.0` | BSD-3-Clause | [golang.org_x_sys.txt](third-party/go-licenses/golang.org_x_sys.txt) |
| [golang.org/x/text](https://github.com/golang/text) | `v0.40.0` | BSD-3-Clause | [golang.org_x_text.txt](third-party/go-licenses/golang.org_x_text.txt) |
| [golang.org/x/time](https://github.com/golang/time) | `v0.14.0` | BSD-3-Clause | [golang.org_x_time.txt](third-party/go-licenses/golang.org_x_time.txt) |
| [golang.zx2c4.com/wireguard](https://github.com/WireGuard/wireguard-go) | `v0.0.0-20250521234502-f3…` | MIT | [golang.zx2c4.com_wireguard.txt](third-party/go-licenses/golang.zx2c4.com_wireguard.txt) |
| [google.golang.org/genproto/googleapis/rpc](https://github.com/googleapis/go-genproto) | `v0.0.0-20260414002931-af…` | Apache-2.0 | [google.golang.org_genproto_googleapis_rpc.txt](third-party/go-licenses/google.golang.org_genproto_googleapis_rpc.txt) |
| [google.golang.org/grpc](https://github.com/grpc/grpc-go) | `v1.82.1` | Apache-2.0 | [google.golang.org_grpc.txt](third-party/go-licenses/google.golang.org_grpc.txt) |
| [google.golang.org/protobuf](https://github.com/protocolbuffers/protobuf-go) | `v1.36.11` | BSD-3-Clause | [google.golang.org_protobuf.txt](third-party/go-licenses/google.golang.org_protobuf.txt) |
| [gopkg.in/yaml.v2](https://github.com/go-yaml/yaml) | `v2.4.0` | MIT and Apache-2.0 | [gopkg.in_yaml.v2.txt](third-party/go-licenses/gopkg.in_yaml.v2.txt) |
| [gopkg.in/yaml.v3](https://github.com/go-yaml/yaml) | `v3.0.1` | MIT and Apache-2.0 | [gopkg.in_yaml.v3.txt](third-party/go-licenses/gopkg.in_yaml.v3.txt) |
| [gvisor.dev/gvisor](https://github.com/google/gvisor) | `v0.0.0-20260122175437-89…` | Apache-2.0 | [gvisor.dev_gvisor.txt](third-party/go-licenses/gvisor.dev_gvisor.txt) |
| [lukechampine.com/blake3](https://github.com/lukechampine/blake3) | `v1.4.1` | MIT | [lukechampine.com_blake3.txt](third-party/go-licenses/lukechampine.com_blake3.txt) |

The license texts were taken from each module's upstream repository on 16 October 2026. Where a module's license
changed between the pinned version and that date, the pinned version's license applies.
