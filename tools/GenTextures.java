import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Textures 16x16 de YAFS, deuxième style (YAF-090) : un objet par texture, dessiné par formes,
 * contour de la teinte du remplissage, léger relief. Usage : java tools/GenTextures.java src/main/resources/assets/yafs/textures build/textures-sheet.png (le code des textures) <textures> <planche.png>
 */
public class GenTextures {

    interface Shape { boolean in(double x, double y); }
    interface Paint { int at(double x, double y); }

    static int rgb(String hex) { return 0xFF000000 | Integer.parseInt(hex.substring(1), 16); }

    static int mul(int c, double f) {
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (c & 255) * f));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Contour : la même teinte, bien plus sombre et un peu froide. */
    static int tone(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((int) (r * 0.40) << 16) | ((int) (g * 0.40) << 8) | (int) Math.min(255, b * 0.48 + 6);
    }

    static BufferedImage img() { return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); }

    static boolean solid(BufferedImage im, int x, int y) {
        return x >= 0 && y >= 0 && x < 16 && y < 16 && (im.getRGB(x, y) >>> 24) != 0;
    }

    static void fill(BufferedImage im, Shape s, Paint p) {
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                if (s.in(x + 0.5, y + 0.5)) im.setRGB(x, y, p.at(x + 0.5, y + 0.5));
    }

    static void px(BufferedImage im, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < 16 && y < 16) im.setRGB(x, y, c);
    }

    static boolean circle(double x, double y, double cx, double cy, double r) {
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
    }

    static boolean box(double x, double y, double x0, double y0, double x1, double y1) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }

    /** Contour tonal sur le bord, puis un relief : clair en haut à gauche, sombre en bas à droite. */
    static BufferedImage finish(BufferedImage im) {
        BufferedImage out = img();
        boolean[][] edge = new boolean[16][16];
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if (!solid(im, x, y)) continue;
                edge[y][x] = !solid(im, x - 1, y) || !solid(im, x + 1, y) || !solid(im, x, y - 1) || !solid(im, x, y + 1);
            }
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if (!solid(im, x, y)) continue;
                int c = im.getRGB(x, y);
                if (edge[y][x]) { out.setRGB(x, y, tone(c)); continue; }
                boolean hi = (x > 0 && y > 0 && edge[y - 1][x - 1]) || (x > 0 && edge[y][x - 1]) || (y > 0 && edge[y - 1][x]);
                boolean lo = (x < 15 && y < 15 && edge[y + 1][x + 1]) || (x < 15 && edge[y][x + 1]) || (y < 15 && edge[y + 1][x]);
                out.setRGB(x, y, hi && !lo ? mul(c, 1.14) : lo && !hi ? mul(c, 0.86) : c);
            }
        return out;
    }

    // Modules

    static BufferedImage lamp() {
        BufferedImage im = img();
        fill(im, (x, y) -> circle(x, y, 8, 6.6, 5.4) || (y >= 10 && y <= 12.2 && Math.abs(x - 8) <= 2.7),
                (x, y) -> {
                    double d = Math.hypot(x - 6, y - 4.6);
                    if (d < 1.7) return rgb("#fff8cf");
                    if (d < 3.6) return rgb("#ffe680");
                    if ((x - 8) + (y - 6.6) > 4.6) return rgb("#d9a226");
                    return rgb("#f5cb45");
                });
        fill(im, (x, y) -> box(x, y, 5.3, 12, 10.7, 15.2),
                (x, y) -> ((int) y % 2 == 0) ? rgb("#bcc2cc") : rgb("#868c99"));
        return finish(im);
    }

    static BufferedImage flame() {
        BufferedImage im = img();
        fill(im, (x, y) -> {
            if (y < 1.2 || y > 15) return false;
            double t = (y - 1.2) / 13.8;
            double hw = t < 0.72 ? 0.4 + 6.0 * Math.pow(t / 0.72, 0.8) : 6.4 * Math.sqrt(Math.max(0, 1 - Math.pow((t - 0.72) / 0.28, 2)));
            double wob = Math.sin(t * 9) * 0.5 * (1 - t);
            return Math.abs(x - 8 - wob) <= hw;
        }, (x, y) -> {
            double t = (y - 1.2) / 13.8;
            double hw = t < 0.72 ? 0.4 + 6.0 * Math.pow(t / 0.72, 0.8) : 6.4;
            double n = Math.abs(x - 8) / Math.max(0.6, hw);
            if (t > 0.62 && n < 0.30) return rgb("#fff0b0");
            if (t > 0.45 && n < 0.58) return rgb("#ffc23d");
            if (n < 0.80) return rgb("#f37a2a");
            return rgb("#c9381c");
        });
        return finish(im);
    }

    static BufferedImage snowflake() {
        BufferedImage im = img();
        fill(im, (x, y) -> {
            if (y < 1 || y > 15) return false;
            double hw = y < 4.5 ? (y - 1) / 3.5 * 5.4 : (y <= 11.5 ? 5.4 : (15 - y) / 3.5 * 5.4);
            return Math.abs(x - 8) <= hw;
        }, (x, y) -> {
            if (y < 4.5) return x < 8 ? rgb("#e3f9ff") : rgb("#bdeefc");
            if (y <= 11.5) {
                if (x < 8) return y < 8 ? rgb("#a9e4f6") : rgb("#86cdea");
                return y < 8 ? rgb("#6dbbe0") : rgb("#4f98c8");
            }
            return x < 8 ? rgb("#5aa6d2") : rgb("#3f86b6");
        });
        for (int[] p : new int[][]{{5, 5}, {5, 6}, {6, 5}, {6, 4}}) px(im, p[0], p[1], rgb("#ffffff"));
        return finish(im);
    }

    static BufferedImage drop() {
        BufferedImage im = img();
        fill(im, (x, y) -> circle(x, y, 8, 10, 4.9) || (y >= 1.5 && y <= 10 && Math.abs(x - 8) <= (y - 1.5) / 8.5 * 4.6),
                (x, y) -> {
                    if (Math.hypot(x - 6.2, y - 9) < 1.4 || (Math.abs(x - 6.1) < 0.7 && y > 6.5 && y < 10)) return rgb("#d6e8ff");
                    if ((x - 8) + (y - 10) > 4.0) return rgb("#2f66c9");
                    if (y < 6) return rgb("#4f93f0");
                    return rgb("#3f7fe0");
                });
        return finish(im);
    }

    static BufferedImage gear(int tier) {
        int[][] pal = {
                {rgb("#e0a070"), rgb("#bd7440"), rgb("#8f4f28")},
                {rgb("#e1e6ec"), rgb("#b2bac4"), rgb("#7c8591")},
                {rgb("#9ff5ef"), rgb("#4fd6cf"), rgb("#2a9e9a")}};
        int[] p = pal[tier - 1];
        BufferedImage im = img();
        fill(im, (x, y) -> {
            double dx = x - 8, dy = y - 8, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
            double tooth = Math.sin(8 * a) > -0.15 ? 7.3 : 5.6;
            return r <= tooth && r >= 2.1;
        }, (x, y) -> {
            double r = Math.hypot(x - 8, y - 8);
            if (r < 3.6) return p[2];
            if ((x - 8) + (y - 8) < -1.5) return p[0];
            if ((x - 8) + (y - 8) > 3.5) return p[2];
            return p[1];
        });
        return finish(im);
    }

    /** Une pousse : une tige, deux feuilles qui montent de chaque côté. */
    static void sprout(BufferedImage im, int x, int top, boolean big) {
        int stem = rgb("#4f8a2c"), leaf = rgb("#7fcf3f"), leafHi = rgb("#b6ef6a");
        for (int y = top + 1; y <= 10; y++) px(im, x, y, stem);
        int[][] left = big ? new int[][]{{-1, 1}, {-2, 1}, {-2, 0}, {-3, 0}, {-3, -1}} : new int[][]{{-1, 1}, {-2, 0}};
        for (int[] p : left) {
            px(im, x + p[0], top + 1 + p[1], p[1] <= 0 ? leafHi : leaf);
            px(im, x - p[0], top + 1 + p[1], p[1] <= 0 ? leaf : leafHi);
        }
        px(im, x, top, leafHi);
    }

    static BufferedImage density(int tier) {
        BufferedImage im = img();
        fill(im, (x, y) -> box(x, y, 3 + (y - 10.5) * 0.12, 10.5, 13 - (y - 10.5) * 0.12, 15.2),
                (x, y) -> (x - 8) + (y - 12) > 2.5 ? rgb("#9a5a30") : (x < 6 ? rgb("#d28454") : rgb("#bb6d3f")));
        fill(im, (x, y) -> box(x, y, 3.2, 10.4, 12.8, 11.9), (x, y) -> rgb("#4a3220"));
        // Le pot reçoit son contour ; les pousses, trop fines pour en supporter un, viennent après.
        im = finish(im);
        int[] xs = tier == 1 ? new int[]{8} : tier == 2 ? new int[]{5, 10} : new int[]{4, 8, 12};
        for (int x : xs) sprout(im, x, tier == 1 ? 3 : 5, tier < 3);
        return im;
    }

    static BufferedImage battery() {
        BufferedImage im = img();
        fill(im, (x, y) -> box(x, y, 6.2, 1, 9.8, 3.4), (x, y) -> rgb("#c5cbd4"));
        fill(im, (x, y) -> box(x, y, 3.8, 3.2, 12.2, 15), (x, y) -> {
            if (y < 6) return rgb("#c5cbd4");
            if (x < 6) return rgb("#ec7457");
            if (x > 10) return rgb("#972f23");
            return rgb("#d04a34");
        });
        int bolt = rgb("#ffd84a");
        int[][] b = {{9, 7}, {8, 7}, {7, 8}, {8, 8}, {7, 9}, {8, 10}, {7, 11}, {8, 11}, {7, 12}};
        for (int[] p : b) px(im, p[0], p[1], bolt);
        return finish(im);
    }

    static BufferedImage orb() {
        BufferedImage im = img();
        fill(im, (x, y) -> circle(x, y, 8, 8, 6.4), (x, y) -> {
            double r = Math.hypot(x - 8, y - 8);
            if (r > 5.0) return (x - 8) + (y - 8) < -1.0 ? rgb("#8a6cf0") : ((x - 8) + (y - 8) > 3.0 ? rgb("#3f2d86") : rgb("#6c4fd4"));
            if (Math.hypot(x - 6.2, y - 6) < 2.0) return rgb("#3b2c63");
            return rgb("#17111f");
        });
        return finish(im);
    }

    static BufferedImage chest() {
        BufferedImage im = img();
        fill(im, (x, y) -> box(x, y, 2, 4, 14, 14.4), (x, y) -> {
            if (y < 7.2) return x < 8 ? rgb("#c0924d") : rgb("#a97b3b");
            if (y > 12.4) return rgb("#6c4a21");
            return x < 8 ? rgb("#a47436") : rgb("#8a5f2b");
        });
        fill(im, (x, y) -> box(x, y, 2, 6.9, 14, 8.0), (x, y) -> rgb("#8b919c"));
        fill(im, (x, y) -> box(x, y, 6.6, 6.2, 9.4, 9.6), (x, y) -> rgb("#e8bf4a"));
        px(im, 7, 8, rgb("#7a5a14"));
        px(im, 8, 8, rgb("#7a5a14"));
        return finish(im);
    }

    static BufferedImage bin() {
        BufferedImage im = img();
        fill(im, (x, y) -> (y >= 4.5 && circle(x, y, 8, 9, 5.6)) || box(x, y, 3, 8, 13, 9), (x, y) -> {
            int[] c = {rgb("#6b4a2b"), rgb("#58391f"), rgb("#7a5835")};
            return c[(int) ((x * 7 + y * 13) % 3)];
        });
        fill(im, (x, y) -> box(x, y, 2.4, 8.6, 13.6, 15),
                (x, y) -> (int) x % 3 == 0 ? rgb("#8a5f2b") : (x < 8 ? rgb("#b88540") : rgb("#a2733a")));
        fill(im, (x, y) -> box(x, y, 2.4, 8.6, 13.6, 9.7), (x, y) -> rgb("#6e4a22"));
        fill(im, (x, y) -> box(x, y, 2.4, 13.4, 13.6, 14.5), (x, y) -> rgb("#6e4a22"));
        px(im, 8, 3, rgb("#4f8a2c"));
        px(im, 8, 4, rgb("#4f8a2c"));
        for (int[] p : new int[][]{{7, 2}, {6, 3}, {9, 2}, {10, 3}}) px(im, p[0], p[1], rgb("#7fcf3f"));
        return finish(im);
    }

    // Items sans carte

    static BufferedImage compost() {
        BufferedImage im = img();
        Random rng = new Random(4);
        int[] browns = {rgb("#5a3d22"), rgb("#6d4b2c"), rgb("#4a3019")};
        fill(im, (x, y) -> y > 5 && y <= 14.4 && ((x - 8) * (x - 8)) / 42.0 + ((y - 14.4) * (y - 14.4)) / 77.0 <= 1,
                (x, y) -> {
                    int c = browns[rng.nextInt(browns.length)];
                    return (x + y < 14) ? mul(c, 1.18) : c;
                });
        for (int[] p : new int[][]{{5, 10}, {9, 8}, {11, 11}, {7, 12}}) px(im, p[0], p[1], rgb("#7aa13f"));
        for (int[] p : new int[][]{{6, 9}, {10, 12}}) px(im, p[0], p[1], rgb("#d8caa0"));
        return finish(im);
    }

    static BufferedImage sack() {
        BufferedImage im = img();
        fill(im, (x, y) -> (y >= 5 && y <= 14.8 && circle(x, y, 8, 10.2, 5.8)) || box(x, y, 6.2, 2.8, 9.8, 5.4),
                (x, y) -> x < 6 ? rgb("#dcc084") : (x > 10.4 ? rgb("#a98648") : rgb("#c8a96a")));
        for (int x = 6; x <= 9; x++) px(im, x, 4, rgb("#7a4b22"));
        px(im, 5, 2, rgb("#c8a96a"));
        px(im, 10, 2, rgb("#c8a96a"));
        fill(im, (x, y) -> box(x, y, 5, 8, 11, 12), (x, y) -> y < 8.9 ? rgb("#58b255") : (y > 11 ? rgb("#2d702c") : rgb("#3f9a3d")));
        for (int[] p : new int[][]{{7, 9}, {8, 9}, {8, 10}, {9, 10}}) px(im, p[0], p[1], rgb("#e8f8d8"));
        return finish(im);
    }

    // Blocs

    static BufferedImage fertileSoil() {
        Random rng = new Random(31);
        int[] tones = {rgb("#2e2016"), rgb("#382819"), rgb("#43301d"), rgb("#4d3822")};
        BufferedImage im = img();
        int[][] grid = new int[8][8];
        for (int[] row : grid) for (int i = 0; i < 8; i++) row[i] = rng.nextInt(4);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int v = grid[y / 2][x / 2];
            if (rng.nextInt(5) == 0) v = Math.max(0, Math.min(3, v + rng.nextInt(3) - 1));
            im.setRGB(x, y, tones[v]);
        }
        for (int i = 0; i < 6; i++) {
            int x = rng.nextInt(15), y = rng.nextInt(15);
            px(im, x, y, rgb("#5a8a31"));
            if (rng.nextBoolean()) px(im, x + 1, y, rgb("#6fa63d"));
        }
        for (int i = 0; i < 5; i++) px(im, rng.nextInt(16), rng.nextInt(16), rgb("#8a6a3c"));
        return im;
    }

    static BufferedImage greenhouseBase() {
        int base = rgb("#b3b9bf");
        Random rng = new Random(3);
        BufferedImage im = img();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = mul(base, 0.985 + rng.nextDouble() * 0.03);
            if (y == 0 || x == 0) c = mul(base, 1.08);
            if (y == 15 || x == 15) c = mul(base, 0.86);
            im.setRGB(x, y, c);
        }
        for (int[] p : new int[][]{{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
            px(im, p[0], p[1], mul(base, 0.66));
            px(im, p[0] - 1, p[1] - 1, mul(base, 1.12));
        }
        return im;
    }

    static BufferedImage glass() {
        BufferedImage im = img();
        int frame = rgb("#d7e0e8"), dark = rgb("#9db0bf");
        for (int i = 0; i < 16; i++) { px(im, i, 0, frame); px(im, 0, i, frame); px(im, i, 15, dark); px(im, 15, i, dark); }
        for (int i = 0; i < 4; i++) { px(im, 3 + i, 11 - i, rgb("#eef6fb")); px(im, 7 + i, 7 - i, rgb("#eef6fb")); }
        return im;
    }

    static BufferedImage icon(int tier) {
        BufferedImage im = greenhouseBase();
        for (int y = 0; y < 11; y++) for (int x = 0; x < 16; x++) {
            boolean edge = x == 0 || x == 15 || y == 0 || y == 10;
            im.setRGB(x, y, edge ? (x == 0 || y == 0 ? rgb("#d7e0e8") : rgb("#8fa3b3")) : rgb("#c3e4f1"));
        }
        for (int i = 0; i < 4; i++) px(im, 3 + i, 8 - i, rgb("#f3fafe"));
        int pin = rgb("#e3b94d");
        int width = tier * 2 + (tier - 1), start = (16 - width) / 2;
        for (int i = 0; i < tier; i++) { px(im, start + i * 3, 13, pin); px(im, start + i * 3 + 1, 13, pin); }
        return im;
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        out.put("item/module_grow_lamp", lamp());
        out.put("item/module_heater", flame());
        out.put("item/module_cooler", snowflake());
        out.put("item/module_rain_collector", drop());
        for (int t = 1; t <= 3; t++) out.put("item/module_accelerator_" + t, gear(t));
        for (int t = 1; t <= 3; t++) out.put("item/module_density_" + t, density(t));
        out.put("item/module_battery", battery());
        out.put("item/module_blackout", orb());
        out.put("item/module_capacity", chest());
        out.put("item/module_composter", bin());
        out.put("item/compost", compost());
        out.put("item/fertilizer", sack());
        out.put("block/fertile_soil", fertileSoil());
        out.put("block/greenhouse_base", greenhouseBase());
        out.put("block/greenhouse_glass", glass());
        for (int t = 1; t <= 3; t++) out.put("block/greenhouse_icon_" + t, icon(t));

        for (Map.Entry<String, BufferedImage> e : out.entrySet()) {
            File f = new File(root, e.getKey() + ".png");
            f.getParentFile().mkdirs();
            ImageIO.write(e.getValue(), "png", f);
        }

        int scale = 8, cell = 16 * scale + 14, columns = 6;
        List<String> names = new ArrayList<>(out.keySet());
        int rows = (names.size() + columns - 1) / columns, rowHeight = cell + 16;
        BufferedImage sheet = new BufferedImage(columns * cell, rows * rowHeight * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int band = 0; band < 2; band++) {
            g.setColor(band == 0 ? new Color(0x74, 0x78, 0x80) : new Color(0x1c, 0x1e, 0x22));
            g.fillRect(0, band * rows * rowHeight, sheet.getWidth(), rows * rowHeight);
            for (int i = 0; i < names.size(); i++) {
                int cx = (i % columns) * cell + 7, cy = band * rows * rowHeight + (i / columns) * rowHeight + 4;
                BufferedImage im = out.get(names.get(i));
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int c = im.getRGB(x, y);
                    if ((c >>> 24) == 0) continue;
                    g.setColor(new Color(c, true));
                    g.fillRect(cx + x * scale, cy + y * scale, scale, scale);
                }
                g.setColor(band == 0 ? Color.BLACK : Color.WHITE);
                g.drawString(names.get(i).substring(names.get(i).indexOf('/') + 1).replace("module_", ""), cx, cy + 16 * scale + 11);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[1]));
    }
}
