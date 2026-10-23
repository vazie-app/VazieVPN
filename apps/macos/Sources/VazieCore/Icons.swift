import Foundation

/// The launcher icon the person chose, drawn the way Android composes it: a mark on a plate. The ten marks are the
/// app's own foreground art; the plates are its gradients (`ic_launcher_plates.xml`).
public enum Icons {
    /// Start, centre and end of each plate's gradient, from the corner the light comes from.
    public static let plateColors: [AppIconPlate: [String]] = [
        .deep: ["#4A1585", "#25115E", "#071A3A"],
        .light: ["#F6F3FC", "#E7E1F4"],
        .ink: ["#12111A"],
        .forest: ["#005523", "#0C3A00", "#172B00"],
        .rose: ["#760037", "#4D002D", "#3A0128"],
        .amber: ["#453900", "#342000", "#251605"],
        .mist: ["#7D6990", "#4D4A57", "#3A3A3A"],
    ]

    private static let table: [AppIconId: (signature: AppIconPlate, plates: [AppIconPlate])] = [
        .orbit: (.deep, [.deep, .light, .ink]),
        .lime: (.forest, [.forest, .light, .ink]),
        .emerald: (.forest, [.forest, .light, .ink]),
        .magenta: (.rose, [.rose, .light, .ink]),
        .cottonCandy: (.deep, [.deep, .light, .ink]),
        .crimson: (.rose, [.rose, .light, .ink]),
        .gold: (.amber, [.amber, .light, .ink]),
        .pearl: (.mist, [.mist, .light, .ink]),
        .onyx: (.mist, [.mist, .light, .ink]),
        .monochrome: (.ink, [.light, .ink]),
    ]

    public static func signature(for icon: AppIconId) -> AppIconPlate { table[icon]!.signature }
    public static func plates(for icon: AppIconId) -> [AppIconPlate] { table[icon]!.plates }

    /// The plate to use for `icon` when the person had `plate` chosen for another icon.
    public static func plate(_ plate: AppIconPlate, for icon: AppIconId) -> AppIconPlate {
        plates(for: icon).contains(plate) ? plate : signature(for: icon)
    }

    public static let markPaper = "#F7F4FF"
    public static let markInk = "#12111A"
}
