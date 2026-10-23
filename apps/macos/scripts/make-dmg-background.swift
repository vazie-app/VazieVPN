// Draws the background of the installer window: the artwork, fitted to fill, a darker foot for the labels, a quiet
// arrow between the two icons, and one line of instruction.
//   swiftc scripts/make-dmg-background.swift -o /tmp/dmg-bg
//   /tmp/dmg-bg <geologica.ttf> <artwork.png> <out.png> "<line>"
import AppKit
import CoreText

let a = CommandLine.arguments
guard a.count == 5, let art = NSImage(contentsOfFile: a[2]) else {
    FileHandle.standardError.write(Data("usage: dmg-bg <font.ttf> <artwork.png> <out.png> <line>\n".utf8))
    exit(1)
}
CTFontManagerRegisterFontsForURL(URL(fileURLWithPath: a[1]) as CFURL, .process, nil)

let width = 660, height = 400, scale = 2
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width * scale, pixelsHigh: height * scale, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: width, height: height)  // 144 dpi: Finder shows it at 660 x 400 points
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSGraphicsContext.current?.imageInterpolation = .high

func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> NSColor {
    NSColor(srgbRed: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: alpha)
}

// The artwork, scaled to cover the window and centred.
let full = NSRect(x: 0, y: 0, width: width, height: height)
let factor = max(CGFloat(width) / art.size.width, CGFloat(height) / art.size.height)
let drawn = NSSize(width: art.size.width * factor, height: art.size.height * factor)
art.draw(in: NSRect(x: (CGFloat(width) - drawn.width) / 2, y: (CGFloat(height) - drawn.height) / 2, width: drawn.width, height: drawn.height), from: .zero, operation: .sourceOver, fraction: 1)

// A darker foot, so the names under the icons read.
NSGradient(colors: [color(0x0D1020, 0.78), color(0x0D1020, 0)])!.draw(in: NSRect(x: 0, y: 0, width: width, height: 190), angle: 90)

// The arrow between the icons.
let arrow = NSBezierPath()
arrow.lineWidth = 3
arrow.lineCapStyle = .round
arrow.lineJoinStyle = .round
arrow.move(to: NSPoint(x: 276, y: 175)); arrow.line(to: NSPoint(x: 384, y: 175))
arrow.move(to: NSPoint(x: 368, y: 191)); arrow.line(to: NSPoint(x: 386, y: 175)); arrow.line(to: NSPoint(x: 368, y: 159))
color(0xEEF0FA, 0.85).setStroke()
arrow.stroke()

let base = CTFontCreateWithName("Geologica" as CFString, 15, nil)
let weighted = CTFontCreateCopyWithAttributes(base, 15, nil, CTFontDescriptorCreateWithAttributes([kCTFontVariationAttribute: [2_003_265_652 as NSNumber: 500]] as CFDictionary))
let style = NSMutableParagraphStyle()
style.alignment = .center
let text = NSAttributedString(string: a[4], attributes: [.font: weighted as NSFont, .foregroundColor: color(0xDCE2FF), .paragraphStyle: style])
text.draw(in: NSRect(x: 0, y: 22, width: width, height: 24))

NSGraphicsContext.restoreGraphicsState()
try rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: a[3]))
