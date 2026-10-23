import AppKit
import SwiftUI
import VazieCore

/// The launcher icon the person chose, drawn the way Android composes it: a mark on a plate.
enum IconArt {
    enum Dot { case connected, busy, failed }

    private static var cache: [String: NSImage] = [:]

    static func mark(_ icon: AppIconId) -> NSImage? {
        if let hit = cache[icon.rawValue] { return hit }
        guard let url = Bundle.module.url(forResource: icon.rawValue, withExtension: "png", subdirectory: "marks"),
            let image = NSImage(contentsOf: url)
        else { return nil }
        cache[icon.rawValue] = image
        return image
    }

    static func asset(_ name: String, in folder: String) -> NSImage? {
        Bundle.module.url(forResource: name, withExtension: "png", subdirectory: folder).flatMap { NSImage(contentsOf: $0) }
    }

    private static func gradient(_ plate: AppIconPlate) -> NSGradient {
        let colors = (Icons.plateColors[plate] ?? ["#12111A"]).map { NSColor(Color(hex: $0)) }
        return NSGradient(colors: colors.count == 1 ? [colors[0], colors[0]] : colors) ?? NSGradient(starting: .black, ending: .black)!
    }

    private static let dotColors: [Dot: String] = [.connected: "9FD8B8", .busy: "E8C47C", .failed: "F0A77F"]

    /// `margin` leaves the transparent border a Dock icon has; the picker draws edge to edge.
    static func icon(_ icon: AppIconId, plate: AppIconPlate, size: CGFloat, dot: Dot? = nil, margin: CGFloat = 0) -> NSImage {
        NSImage(size: NSSize(width: size, height: size), flipped: false) { rect in
            let body = rect.insetBy(dx: size * margin, dy: size * margin)
            let path = NSBezierPath(roundedRect: body, xRadius: body.width * 0.225, yRadius: body.width * 0.225)
            NSGraphicsContext.saveGraphicsState()
            path.addClip()
            gradient(plate).draw(in: body, angle: -45)
            if let art = mark(icon) {
                // The launcher shows the middle 72 of the adaptive canvas's 108.
                let source = art.size.width * (72.0 / 108.0)
                let offset = (art.size.width - source) / 2
                let from = NSRect(x: offset, y: offset, width: source, height: source)
                if icon == .monochrome {
                    let tint = NSImage(size: body.size, flipped: false) { r in
                        art.draw(in: r, from: from, operation: .sourceOver, fraction: 1)
                        NSColor(Color(hex: plate == .light ? Icons.markInk : Icons.markPaper)).setFill()
                        r.fill(using: .sourceIn)
                        return true
                    }
                    tint.draw(in: body)
                } else {
                    art.draw(in: body, from: from, operation: .sourceOver, fraction: 1)
                }
            }
            NSGraphicsContext.restoreGraphicsState()
            if let dot {
                let d = body.width * 0.42
                let ring = NSRect(x: body.maxX - d - body.width * 0.02, y: body.minY + body.width * 0.02, width: d, height: d)
                NSColor(Color(hex: "0D1020")).setFill()
                NSBezierPath(ovalIn: ring).fill()
                NSColor(Color(hex: dotColors[dot] ?? "9FD8B8")).setFill()
                NSBezierPath(ovalIn: ring.insetBy(dx: max(1, body.width * 0.07), dy: max(1, body.width * 0.07))).fill()
            }
            return true
        }
    }

    /// The Mac's menu bar draws one-colour images, so the state is the mark's strength and a dot, not a hue.
    static func menuBar(_ dot: Dot?) -> NSImage {
        let size = NSSize(width: 20, height: 18)
        let image = NSImage(size: size, flipped: false) { rect in
            if let art = mark(.monochrome) {
                let source = art.size.width * 0.58
                let offset = (art.size.width - source) / 2
                let side = min(rect.height, 17)
                let target = NSRect(x: 0, y: (rect.height - side) / 2, width: side, height: side)
                art.draw(in: target, from: NSRect(x: offset, y: offset, width: source, height: source), operation: .sourceOver, fraction: dot == nil ? 0.62 : 1)
            }
            if let dot {
                let d: CGFloat = 7
                let spot = NSRect(x: rect.maxX - d - 0.5, y: 0.5, width: d, height: d)
                NSColor.black.setFill()
                if dot == .connected {
                    NSBezierPath(ovalIn: spot).fill()
                } else {
                    let ring = NSBezierPath(ovalIn: spot.insetBy(dx: 0.8, dy: 0.8))
                    ring.lineWidth = 1.6
                    NSColor.black.setStroke()
                    ring.stroke()
                }
            }
            return true
        }
        image.isTemplate = true
        return image
    }
}

/// One launcher icon, drawn exactly as the Dock shows it.
struct IconPreview: View {
    let icon: AppIconId
    let plate: AppIconPlate
    var size: CGFloat = 64

    var body: some View {
        Image(nsImage: IconArt.icon(icon, plate: plate, size: size * 2))
            .resizable()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
