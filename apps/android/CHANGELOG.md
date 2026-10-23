# Changelog

Notable changes to Vazie VPN. Versions follow [Semantic Versioning](https://semver.org); each release is tagged
`vX.Y.Z` and published on the [Releases](https://github.com/vazie-app/VazieVPN/releases) page with its APKs and
checksums.

## 1.0.0 — Initial Public Release (not published yet)

The first release of Vazie VPN from this repository.

### Configurations
- Add your own VLESS configurations by pasting a `vless://` link, or a subscription link with several at once.
- REALITY and TLS security, over TCP, WebSocket and gRPC.
- Review before saving; unknown parameters are kept as they are.
- Rename, duplicate, export and delete; latency next to each configuration.
- Configurations are stored only on the device, encrypted with a key held in the Android Keystore.

### Connection
- One-tap connect and disconnect, with the tunnel's state in a notification.
- Split tunneling: all apps, all except chosen apps, or only chosen apps.

### Around the app
- Quick-connect and dashboard widgets, in light and dark.
- A Quick Settings tile and app shortcuts for recent configurations.
- Milk and Night Indigo themes and ten app icons.
- Built-in guides; English and Russian.

### Privacy
- No account, no ads, no analytics or crash-reporting SDKs.
- The app contacts the Vazie backend only for the update check, the About links and common questions — with nothing
  about the user, the device or the configurations.

