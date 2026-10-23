import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public final class GenerateLauncherIcons {

    /** The adaptive foreground canvas, in dp. */
    private static final double CANVAS_DP = 108;

    /** The circle every launcher mask is guaranteed to keep. Content is fitted inside it. */
    private static final double SAFE_DIAMETER_DP = 66;

    /** The part of the canvas a launcher shows at all. Used to render the legacy icons. */
    private static final double VIEWPORT_DP = 72;

    /** Pixels per dp, per density bucket, and the legacy icon size that goes with each. */
    private static final Density[] DENSITIES = {
        new Density("mdpi", 1, 48),
        new Density("hdpi", 1.5, 72),
        new Density("xhdpi", 2, 96),
        new Density("xxhdpi", 3, 144),
        new Density("xxxhdpi", 4, 192),
    };

    /** Where the approved launcher identity lives. Its layers are canonical; the tool only places them. */
    private static final String ICON_PACK = "design/vazie-adaptive-icon/source/";

    /** The canonical brand marks. */
    private static final String LOGOS = "design/logo/";

    /** The colourways added as flat exports at the top of {@code design/}. */
    private static final String DESIGN = "design/";

    /** One entry per <em>mark</em>, not per launcher icon. */
    private static final Mark[] MARKS = {
        new Mark(ICON_PACK + "ic_launcher_foreground_master.png", "ic_launcher_orbit_foreground", Anchor.BODY, true,
            new Layer(ICON_PACK + "ic_launcher_monochrome_1024.png", "ic_launcher_monochrome")),
        new Mark(LOGOS + "vazie-icon-emerald.png", "ic_launcher_lime_foreground", Anchor.BODY, false, null),
        new Mark(LOGOS + "vazie-icon-magenta.png", "ic_launcher_magenta_foreground", Anchor.BODY, false, null),
        new Mark(LOGOS + "vazie-icon-onyx.png", "ic_launcher_onyx_foreground", Anchor.BODY, false, null),
        new Mark(DESIGN + "vazie-cotton-candy-transparent-2k.png", "ic_launcher_cotton_candy_foreground", Anchor.BODY, false, null),
        new Mark(DESIGN + "vazie-crimson-transparent-2k.png", "ic_launcher_crimson_foreground", Anchor.BODY, false, null),
        new Mark(DESIGN + "vazie-electric-gold-transparent-2k.png", "ic_launcher_gold_foreground", Anchor.BODY, false, null),
        new Mark(DESIGN + "vazie-emerald-transparent-2k.png", "ic_launcher_emerald_foreground", Anchor.BODY, false, null),
        new Mark(DESIGN + "vazie-pearl-transparent-2k.png", "ic_launcher_pearl_foreground", Anchor.BODY, false, null),
    };

    private enum Anchor { BBOX, BODY }

    private record Density(String bucket, double scale, int legacySize) {}

    /** {@code source} is a path from the repository root; {@code output} a resource name. */
    private record Mark(String source, String output, Anchor anchor, boolean legacy, Layer themed) {}

    /** The traced layer a themed launcher tints, when the icon has one. */
    private record Layer(String source, String output) {}

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : ".");
        File res = new File(root, "android/app/src/main/res").isDirectory()
            ? new File(root, "android/app/src/main/res")
            : new File(root, "app/src/main/res");

        run(List.of(cwebp(), "-version"));
        System.out.println("cwebp: " + cwebp());
        File backgroundFile = new File(root, ICON_PACK + "ic_launcher_background_master.png");
        BufferedImage background = backgroundFile.isFile() ? ImageIO.read(backgroundFile) : null;

        for (Mark mark : MARKS) {
            File sourceFile = new File(root, mark.source());
            if (!sourceFile.isFile()) {
                System.out.println("skipped (no source in this checkout): " + mark.source());
                continue;
            }
            BufferedImage source = ImageIO.read(sourceFile);
            Placement placement = placementFor(source, mark.anchor());

            BufferedImage themed = mark.themed() == null
                ? null
                : ImageIO.read(new File(root, mark.themed().source()));
            if (themed != null) {
                placement.radius = Math.max(
                    placement.radius, furthestFrom(themed, placement.anchorX, placement.anchorY));
            }

            System.out.printf(
                "%s%n  anchor=%s at (%.1f, %.1f)  bboxCentre=(%.1f, %.1f)  furthestPixel=%.1fpx"
                    + "  ->  scale %.4f, mark spans %.1fdp x %.1fdp%n",
                mark.source(), mark.anchor(), placement.anchorX, placement.anchorY,
                placement.bboxCentreX, placement.bboxCentreY, placement.radius,
                placement.scaleFor(CANVAS_DP), placement.widthDp(), placement.heightDp());

            for (Density density : DENSITIES) {
                int canvas = (int) Math.round(CANVAS_DP * density.scale());
                BufferedImage foreground = renderForeground(source, placement, canvas);
                write(foreground,
                    new File(res, "mipmap-" + density.bucket() + "/" + mark.output() + ".webp"),
                    Encoding.ILLUSTRATION);

                if (themed != null) {
                    write(renderForeground(themed, placement, canvas),
                        new File(res, "mipmap-" + density.bucket() + "/" + mark.themed().output() + ".webp"),
                        Encoding.MASK);
                }

            }
        }
        System.exit(0);
    }

    private static final class Placement {
        double anchorX, anchorY, bboxCentreX, bboxCentreY, radius;
        int sourceWidth, sourceHeight, bboxWidth, bboxHeight;

        double scaleFor(double canvasDp) {
            return (SAFE_DIAMETER_DP / 2 / canvasDp) / (radius / sourceWidth) * (1.0 / sourceWidth) * sourceWidth;
        }

        double widthDp() {
            return bboxWidth * (SAFE_DIAMETER_DP / 2) / radius;
        }

        double heightDp() {
            return bboxHeight * (SAFE_DIAMETER_DP / 2) / radius;
        }
    }

    private static Placement placementFor(BufferedImage image, Anchor anchor) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[] visible = mask(image, 8);
        int[] box = bbox(visible, w, h);

        Placement placement = new Placement();
        placement.sourceWidth = w;
        placement.sourceHeight = h;
        placement.bboxWidth = box[2] - box[0] + 1;
        placement.bboxHeight = box[3] - box[1] + 1;
        placement.bboxCentreX = (box[0] + box[2] + 1) / 2.0;
        placement.bboxCentreY = (box[1] + box[3] + 1) / 2.0;

        if (anchor == Anchor.BODY) {
            // Erode hard enough to dissolve a ring, then keep the largest surviving island. What is
            // left is the solid shape a person reads as "the mark".
            int radius = Math.round(w * 0.05f);
            int[] body = bbox(largestIsland(erode(mask(image, 80), w, h, radius), w, h), w, h);
            placement.anchorX = (body[0] + body[2] + 1) / 2.0;
            placement.anchorY = (body[1] + body[3] + 1) / 2.0;
        } else {
            placement.anchorX = placement.bboxCentreX;
            placement.anchorY = placement.bboxCentreY;
        }

        placement.radius = furthestFrom(image, placement.anchorX, placement.anchorY);
        return placement;
    }

    /** How far the artwork reaches from a point - the radius a scale has to bring onto the safe circle. */
    private static double furthestFrom(BufferedImage image, double x0, double y0) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[] visible = mask(image, 8);
        double furthest = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!visible[y * w + x]) continue;
                double d = Math.hypot(x + 0.5 - x0, y + 0.5 - y0);
                if (d > furthest) furthest = d;
            }
        }
        return furthest;
    }

    private static BufferedImage renderForeground(BufferedImage source, Placement p, int canvas) {
        double safeRadiusPx = canvas * (SAFE_DIAMETER_DP / 2) / CANVAS_DP;
        double scale = safeRadiusPx / p.radius;
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(out.createGraphics());
        double drawWidth = source.getWidth() * scale;
        double drawHeight = source.getHeight() * scale;
        double x = canvas / 2.0 - p.anchorX * scale;
        double y = canvas / 2.0 - p.anchorY * scale;
        g.drawImage(source, (int) Math.round(x), (int) Math.round(y),
            (int) Math.round(drawWidth), (int) Math.round(drawHeight), null);
        g.dispose();
        return out;
    }

    private static BufferedImage legacy(BufferedImage foreground, BufferedImage background, int size, Shape mask) {
        int canvas = foreground.getWidth();
        int viewport = (int) Math.round(canvas * VIEWPORT_DP / CANVAS_DP);
        int inset = (canvas - viewport) / 2;

        BufferedImage composed = new BufferedImage(viewport, viewport, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gc = quality(composed.createGraphics());
        gc.drawImage(background, -inset, -inset, canvas, canvas, null);
        gc.drawImage(foreground, -inset, -inset, null);
        gc.dispose();

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(out.createGraphics());
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, size, size);
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(mask);
        g.drawImage(composed, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /** The legacy square icon gets 22% rounded corners: no launcher masks a legacy PNG. */
    private static Shape roundedSquare(int size) {
        double radius = size * 0.22;
        return new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2);
    }

    private static Graphics2D quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        return g;
    }

    /** How a rendered layer is encoded on its way to `res/`. */
    private enum Encoding {
        ILLUSTRATION("-q", "82", "-sharp_yuv", "-alpha_q", "100", "-m", "6"),
        MASK("-lossless", "-z", "9");

        private final String[] flags;

        Encoding(String... flags) {
            this.flags = flags;
        }
    }

    /** Writes one layer as WebP, through `cwebp`. */
    private static void write(BufferedImage image, File file, Encoding encoding) throws IOException {
        file.getParentFile().mkdirs();
        File staged = File.createTempFile("vazie-launcher-", ".png");
        try {
            ImageIO.write(image, "png", staged);
            List<String> command = new ArrayList<>();
            command.add(cwebp());
            command.add("-quiet");
            command.addAll(Arrays.asList(encoding.flags));
            command.add(staged.getAbsolutePath());
            command.add("-o");
            command.add(file.getAbsolutePath());
            run(command);
        } finally {
            Files.deleteIfExists(staged.toPath());
        }
        System.out.printf("    %s  (%s, %,d bytes)%n", file.getPath(), encoding, file.length());
    }

    /** Where `cwebp` is, and a refusal to guess when it is nowhere. */
    private static String cwebp() {
        String configured = System.getenv("VAZIE_CWEBP");
        return configured == null || configured.isBlank() ? "cwebp" : configured;
    }

    private static void run(List<String> command) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException missing) {
            throw new IOException(
                "could not run '" + command.get(0) + "'. Vazie's launcher rasters are WebP and this "
                    + "tool encodes them with libwebp's own cwebp, which is not part of the JDK or "
                    + "the Android SDK.\n"
                    + "  macOS:  brew install webp\n"
                    + "  Debian: sudo apt install webp\n"
                    + "  or set VAZIE_CWEBP to the binary you want used.",
                missing);
        }
        String output = new String(process.getInputStream().readAllBytes());
        int status;
        try {
            status = process.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while encoding", interrupted);
        }
        if (status != 0) {
            throw new IOException("cwebp failed (" + status + "):\n" + output);
        }
    }

    private static boolean[] mask(BufferedImage image, int threshold) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[] m = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                m[y * w + x] = ((image.getRGB(x, y) >>> 24) & 0xff) > threshold;
            }
        }
        return m;
    }

    private static int[] bbox(boolean[] m, int w, int h) {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y * w + x]) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) throw new IllegalStateException("nothing visible in the artwork");
        return new int[] {minX, minY, maxX, maxY};
    }

    private static boolean[] erode(boolean[] m, int w, int h, int r) {
        boolean[] horizontal = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean keep = true;
                for (int d = -r; d <= r && keep; d++) {
                    int xx = x + d;
                    if (xx < 0 || xx >= w || !m[y * w + xx]) keep = false;
                }
                horizontal[y * w + x] = keep;
            }
        }
        boolean[] both = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean keep = true;
                for (int d = -r; d <= r && keep; d++) {
                    int yy = y + d;
                    if (yy < 0 || yy >= h || !horizontal[yy * w + x]) keep = false;
                }
                both[y * w + x] = keep;
            }
        }
        return both;
    }

    private static boolean[] largestIsland(boolean[] m, int w, int h) {
        int[] label = new int[w * h];
        Map<Integer, Integer> sizes = new HashMap<>();
        int next = 0;
        for (int i = 0; i < w * h; i++) {
            if (!m[i] || label[i] != 0) continue;
            next++;
            int size = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(i);
            label[i] = next;
            while (!queue.isEmpty()) {
                int c = queue.poll();
                size++;
                int cx = c % w;
                for (int n : new int[] {c - 1, c + 1, c - w, c + w}) {
                    if (n < 0 || n >= w * h || label[n] != 0 || !m[n]) continue;
                    if (Math.abs((n % w) - cx) > 1) continue;
                    label[n] = next;
                    queue.add(n);
                }
            }
            sizes.put(next, size);
        }
        int best = sizes.entrySet().stream().max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElseThrow(() -> new IllegalStateException("no body found"));
        boolean[] island = new boolean[w * h];
        for (int i = 0; i < w * h; i++) island[i] = label[i] == best;
        return island;
    }

}
