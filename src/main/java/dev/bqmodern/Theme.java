package dev.bqmodern;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

/** Client theme: recolours the blue UI palette to a chosen hue/saturation, leaving text, greens and golds alone. */
public final class Theme {
    public static boolean enabled;
    public static boolean betterQuestingTheme;
    public static float hue = 0.6f;
    public static float sat = 0.75f;

    /** GUI scale override while the modern screens are open; 0 keeps the game's own setting. */
    public static int uiScale;
    public static boolean sounds = true;
    public static boolean animations = true;
    public static boolean requiredHighlight;
    /** Opacity of the quest map background, 0-100 percent. */
    public static int bgOpacity = 88;
    /** When off, the quest screen is a movable, resizable window; winW/winH of 0 means "not placed yet". */
    public static boolean fullscreen = true;
    public static boolean keepOpen;
    public static int winX, winY, winW, winH;
    /** Quest shown in the always-on-screen tracker, and its top-left as a fraction of the screen. */
    public static UUID pinned;
    public static float pinFx = 0.74f, pinFy = 0.08f, pinScale = 0.75f;
    public static boolean pinFolded;
    public static boolean sideFolded;
    public static final Set<UUID> favourites = new LinkedHashSet<UUID>();
    /** Favourited chapters, listed first in the sidebar. */
    public static final Set<UUID> favLines = new LinkedHashSet<UUID>();
    /** "world|player" keys that already finished or skipped the first-time guide. */
    public static final Set<String> guideDone = new LinkedHashSet<String>();
    /** Hides chapters that have no quests visible to the player. */
    public static boolean hideUseless;

    private static final float BASE_SAT = 0.75f;

    static {
        load();
    }

    private Theme() {}

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + "bqmodern.cfg");
    }

    public static void load() {
        try {
            File f = file();
            if (!f.exists()) return;
            Properties p = new Properties();
            FileReader r = new FileReader(f);
            try {
                p.load(r);
            } finally {
                r.close();
            }
            enabled = Boolean.parseBoolean(p.getProperty("themeEnabled", "false"));
            betterQuestingTheme = Boolean.parseBoolean(p.getProperty("betterQuestingTheme", "false"));
            hue = Float.parseFloat(p.getProperty("hue", "0.6"));
            sat = Float.parseFloat(p.getProperty("sat", "0.75"));
            uiScale = Math.max(0, Math.min(6, Integer.parseInt(p.getProperty("uiScale", "0"))));
            sounds = Boolean.parseBoolean(p.getProperty("sounds", "true"));
            animations = Boolean.parseBoolean(p.getProperty("animations", "true"));
            requiredHighlight = Boolean.parseBoolean(p.getProperty("requiredHighlight", "false"));
            bgOpacity = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("bgOpacity", "88"))));
            fullscreen = Boolean.parseBoolean(p.getProperty("fullscreen", "true"));
            keepOpen = Boolean.parseBoolean(p.getProperty("keepOpen", "false"));
            winX = Integer.parseInt(p.getProperty("winX", "0"));
            winY = Integer.parseInt(p.getProperty("winY", "0"));
            winW = Integer.parseInt(p.getProperty("winW", "0"));
            winH = Integer.parseInt(p.getProperty("winH", "0"));
            pinFx = Float.parseFloat(p.getProperty("pinFx", "0.74"));
            pinFy = Float.parseFloat(p.getProperty("pinFy", "0.08"));
            pinScale = Float.parseFloat(p.getProperty("pinScale", "0.75"));
            pinFolded = Boolean.parseBoolean(p.getProperty("pinFolded", "false"));
            sideFolded = Boolean.parseBoolean(p.getProperty("sideFolded", "false"));
            try {
                String pn = p.getProperty("pinned", "").trim();
                pinned = pn.isEmpty() ? null : UUID.fromString(pn);
            } catch (IllegalArgumentException ignored) {
                pinned = null;
            }
            hideUseless = Boolean.parseBoolean(p.getProperty("hideUseless", "false"));
            readIds(p.getProperty("favourites", ""), favourites);
            readIds(p.getProperty("favLines", ""), favLines);
            guideDone.clear();
            for (String s : p.getProperty("guideDone", "").split(",")) {
                if (!s.isEmpty()) guideDone.add(s.replace("%2C", ",").replace("%25", "%"));
            }
        } catch (Throwable t) {
            enabled = false;
        }
    }

    public static void save() {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            Properties p = new Properties();
            p.setProperty("themeEnabled", String.valueOf(enabled));
            p.setProperty("betterQuestingTheme", String.valueOf(betterQuestingTheme));
            p.setProperty("hue", String.valueOf(hue));
            p.setProperty("sat", String.valueOf(sat));
            p.setProperty("uiScale", String.valueOf(uiScale));
            p.setProperty("sounds", String.valueOf(sounds));
            p.setProperty("animations", String.valueOf(animations));
            p.setProperty("requiredHighlight", String.valueOf(requiredHighlight));
            p.setProperty("bgOpacity", String.valueOf(bgOpacity));
            p.setProperty("fullscreen", String.valueOf(fullscreen));
            p.setProperty("keepOpen", String.valueOf(keepOpen));
            p.setProperty("winX", String.valueOf(winX));
            p.setProperty("winY", String.valueOf(winY));
            p.setProperty("winW", String.valueOf(winW));
            p.setProperty("winH", String.valueOf(winH));
            p.setProperty("pinFx", String.valueOf(pinFx));
            p.setProperty("pinFy", String.valueOf(pinFy));
            p.setProperty("pinScale", String.valueOf(pinScale));
            p.setProperty("pinFolded", String.valueOf(pinFolded));
            p.setProperty("sideFolded", String.valueOf(sideFolded));
            p.setProperty("pinned", pinned == null ? "" : pinned.toString());
            p.setProperty("hideUseless", String.valueOf(hideUseless));
            p.setProperty("favourites", joinIds(favourites));
            p.setProperty("favLines", joinIds(favLines));
            StringBuilder gd = new StringBuilder();
            for (String s : guideDone) {
                if (gd.length() > 0) gd.append(',');
                gd.append(s.replace("%", "%25").replace(",", "%2C"));
            }
            p.setProperty("guideDone", gd.toString());
            FileWriter w = new FileWriter(f);
            try {
                p.store(w, "BQ Modern UI");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            // ignore: settings just won't persist
        }
    }

    private static void readIds(String csv, Set<UUID> out) {
        out.clear();
        for (String s : csv.split(",")) {
            if (s.trim().isEmpty()) continue;
            try {
                out.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed id
            }
        }
    }

    private static String joinIds(Set<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(u);
        }
        return sb.toString();
    }

    public static boolean toggleFavLine(UUID id) {
        boolean on = !favLines.remove(id);
        if (on) favLines.add(id);
        save();
        return on;
    }

    public static boolean toggleFavourite(UUID id) {
        boolean on = !favourites.remove(id);
        if (on) favourites.add(id);
        save();
        return on;
    }

    public static void click() {
        if (!sounds) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /** Remaps blue-ish colours of the default palette; everything else passes through. */
    public static int t(int argb) {
        if (betterQuestingTheme) {
            int rgb = argb & 0xFFFFFF;
            int themed = betterQuestingColor(rgb);
            return themed < 0 ? argb : (argb & 0xFF000000) | themed;
        }
        if (!enabled) return argb;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float h = hsb[0] * 360f;
        if (hsb[1] < 0.10f || h < 195f || h > 245f) return argb;
        float s = Math.min(1f, hsb[1] * (sat / BASE_SAT));
        return (argb & 0xFF000000) | (Color.HSBtoRGB(hue, s, hsb[2]) & 0xFFFFFF);
    }

    /** {from, to}: modern palette -> colours sampled from BetterQuesting's own gui_themes texture (parchment, tan buttons, brown frame). */
    private static final int[][] BQ_MAP = {
        { 0x101318, 0xFBF3DF }, { 0x0C0F16, 0x1F1E1C }, { 0x0E1118, 0x1F1E1C }, { 0x141926, 0x433F39 },
        { 0x161B26, 0x534E47 }, { 0x1A2030, 0x534E47 }, { 0x1D2433, 0x433F39 }, { 0x1E2433, 0x5E5850 },
        { 0x222838, 0x9A805D }, { 0x2C3446, 0xBEA481 }, { 0x2A2E35, 0x6B6458 }, { 0x3A4660, 0x9A805D },
        { 0x3A4661, 0x652816 }, { 0x3A4A6A, 0xAFA393 }, { 0x5A6478, 0xAFA393 }, { 0x2F6FD0, 0xBEA481 },
        { 0x4F86E0, 0xD9CDBC }, { 0x4DA3FF, 0xC00000 }, { 0x6FA8FF, 0xE6B87A }, { 0x7FC8FF, 0xE6B87A },
        { 0x8C96AA, 0xC5AB88 }, { 0xAAB4C8, 0xC5AB88 }, { 0x272B32, 0x9A805D }, { 0x181A1E, 0x534E47 },
        { 0x1B3B22, 0x6F8B45 }, { 0x5A5F66, 0x8A7A00 }, { 0x23262C, 0x534E47 }
    };

    private static int betterQuestingColor(int rgb) {
        int best = -1, bestD = 37;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        for (int i = 0; i < BQ_MAP.length; i++) {
            int k = BQ_MAP[i][0];
            int d = Math.abs(((k >> 16) & 255) - r) + Math.abs(((k >> 8) & 255) - g) + Math.abs((k & 255) - b);
            if (d < bestD) {
                bestD = d;
                best = BQ_MAP[i][1];
                if (d == 0) break;
            }
        }
        return best;
    }

    public static int current() {
        return Color.HSBtoRGB(hue, sat, 1f) & 0xFFFFFF;
    }

    public static void setRgb(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        hue = hsb[0];
        sat = hsb[1];
        enabled = true;
    }

    /** Hue/saturation disc: white centre, fully saturated rim. */
    public static void drawWheel(double cx, double cy, double radius) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glVertex2d(cx, cy);
        int n = 72;
        for (int i = 0; i <= n; i++) {
            double a = i * 2 * Math.PI / n;
            int c = Color.HSBtoRGB((float) (i % n) / n, 1f, 1f);
            GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
            GL11.glVertex2d(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius);
        }
        GL11.glEnd();
        GL11.glPopAttrib();
    }
}
