import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the OCR fixture text with a REAL font, using the JDK's own AWT.
 *
 * <p>Why this exists, which is the fourth attempt at this file:
 *
 * <pre>
 *   ImageMagick convert   -> the runner has no ImageMagick; a2 SKIPPED
 *   Pillow + DejaVuSans   -> the runner has no PIL: "Pillow path failed
 *                            (No module named 'PIL'); using the fallback font"
 *   5x7 bitmap font       -> the glyphs are right but V is not: INYOICE INY-4471
 * </pre>
 *
 * <p>That last one is not fixable by tuning. A 5-column grid can only put a V's
 * arms at x=0 and x=4, so a 5x7 V is necessarily "vertical for most of its
 * height, then diagonal" -- which is a U, or a Y once the apex leaves a stem.
 * OCR was right about the font every time.
 *
 * <p>The JDK is the one dependency guaranteed to be on an Android build machine,
 * because gradle needs it, and AWT ships scalable fonts with it. No install, no
 * network, no sudo.
 *
 * <p>Run with Java 11+ single-file source execution:
 * <pre>java MakeInvoicePng.java &lt;out.png&gt; "&lt;text&gt;"</pre>
 */
public final class MakeInvoicePng {

    private static final int FONT_PT = 64;
    private static final int PAD = 48;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: MakeInvoicePng <out.png> [text]");
            System.exit(2);
        }
        File out = new File(args[0]);
        String text = args.length > 1 ? args[1] : "INVOICE INV-4471 DUE 2026-03-01";

        Font font = pickFont();
        if (font == null) {
            System.err.println("no scalable font available to AWT");
            System.exit(3);
        }

        // MEASURE, do not guess. A canvas sized by eye is how a fixture ends up
        // with its last character clipped and OCR reading a truncated word.
        BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontRenderContext frc = pg.getFontRenderContext();
        java.awt.geom.Rectangle2D b = font.getStringBounds(text, frc);
        LineMetrics lm = font.getLineMetrics(text, frc);
        int textW = (int) Math.ceil(b.getWidth());
        int textH = (int) Math.ceil(lm.getHeight());
        int ascent = (int) Math.ceil(lm.getAscent());
        pg.dispose();

        int w = textW + 2 * PAD;
        int h = textH + 2 * PAD;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(text, PAD - (int) Math.ceil(b.getX()), PAD + ascent);
        g.dispose();

        ImageIO.write(img, "png", out);

        long dark = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) < 0x808080) dark++;
            }
        }
        System.out.println("wrote " + out + "  " + w + "x" + h + "  text=\"" + text + "\"");
        System.out.println("  renderer : AWT " + font.getFontName() + " " + FONT_PT + "pt");
        System.out.println("  ink      : " + dark + " px ("
                           + String.format("%.1f", 100.0 * dark / ((long) w * h)) + "% of the page)");
    }

    /**
     * A real scalable face. SansSerif resolves through fontconfig to DejaVu Sans
     * on a Linux runner; the explicit list is a fallback for images where
     * fontconfig has nothing configured.
     */
    private static Font pickFont() {
        String[] names = {
            "SansSerif", "DejaVu Sans", "Liberation Sans", "FreeSans",
            "Arial", "Helvetica", "Noto Sans", "Roboto",
        };
        for (String n : names) {
            Font f = new Font(n, Font.PLAIN, FONT_PT);
            // A logical font that resolved to the JVM's last-resort dialog font
            // is still usable, so accept anything that is a real face.
            if (f.getFamily() != null && !"Dialog".equals(f.getFamily())) {
                return f;
            }
        }
        return null;
    }

    private MakeInvoicePng() {}
}
