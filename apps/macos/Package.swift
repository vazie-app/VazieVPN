// swift-tools-version: 6.0
import PackageDescription

// Vazie VPN for macOS. Two targets: `VazieCore` holds everything that is not a view (the link parser, the Xray
// configuration, the encrypted store, the engine and the system proxy) and is what the tests run against;
// `VazieVPN` is the SwiftUI app. `scripts/build-app.sh` turns the product into `Vazie VPN.app`.
let package = Package(
    name: "VazieVPN",
    defaultLocalization: "en",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "VazieVPN", targets: ["VazieVPN"]),
        .library(name: "VazieCore", targets: ["VazieCore"]),
    ],
    targets: [
        .target(
            name: "VazieCore",
            resources: [.copy("Resources/i18n")],
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
        .executableTarget(
            name: "VazieVPN",
            dependencies: ["VazieCore"],
            resources: [.copy("Resources/fonts"), .copy("Resources/marks"), .copy("Resources/brand")],
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
        .testTarget(
            name: "VazieCoreTests",
            dependencies: ["VazieCore"],
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
    ]
)
