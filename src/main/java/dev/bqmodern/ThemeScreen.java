package dev.bqmodern;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/** Settings panel: theme colour wheel with hex input, UI scale, sounds and animations. */
public class ThemeScreen extends GuiScreen {
    private static final int W = 230, H = 346, R = 52;

    private final GuiScreen parent;
    private boolean dragging;
    private String hex = "";
    private boolean hexDirty;

    public ThemeScreen(GuiScreen parent) {
        this.parent = parent;
        syncHex();
    }

    public static void drawRect(int a, int b, int c, int d, int col) {
        Gui.drawRect(a, b, c, d, Theme.t(col));
    }

    private void syncHex() {
        hex = String.format("%06X", Theme.enabled ? Theme.current() : 0x2F6FD0);
    }

    private int px() { return (width - W) / 2; }

    private int py() { return (height - H) / 2; }

    private int wcx() { return px() + 16 + R; }

    private int wcy() { return py() + 36 + R; }

    private int[] scaleRect() { return new int[] { px() + 136, py() + 106, px() + 220, py() + 124 }; }

    private int[] soundRect() { return new int[] { px() + 136, py() + 128, px() + 220, py() + 146 }; }

    private int[] animRect() { return new int[] { px() + 136, py() + 150, px() + 220, py() + 168 }; }

    private int[] reqRect() { return new int[] { px() + 16, py() + 232, px() + 220, py() + 250 }; }

    private int[] bqThemeRect() { return new int[] { px() + 16, py() + 180, px() + 220, py() + 198 }; }

    private int[] sliderRect() { return new int[] { px() + 16, py() + 202, px() + 220, py() + 220 }; }

    private boolean sliding;

    private void slide(int mx) {
        int[] r = sliderRect();
        Theme.bgOpacity = Math.max(0, Math.min(100, Math.round((mx - r[0]) * 100f / (r[2] - r[0]))));
    }

    private int[] fullRect() { return new int[] { px() + 16, py() + 280, px() + 220, py() + 298 }; }

    private int[] keepRect() { return new int[] { px() + 16, py() + 300, px() + 220, py() + 318 }; }

    private int[] hideRect() { return new int[] { px() + 16, py() + 254, px() + 220, py() + 272 }; }

    private int[] tutorialRect() { return new int[] { px() + 16, py() + H - 24, px() + 92, py() + H - 6 }; }

    private int[] resetRect() { return new int[] { px() + 98, py() + H - 24, px() + 174, py() + H - 6 }; }

    private int[] doneRect() { return new int[] { px() + 178, py() + H - 24, px() + W - 8, py() + H - 6 }; }

    private static boolean in(int[] r, int x, int y) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    private void button(int[] r, String label, int color, int mx, int my) {
        drawRect(r[0], r[1], r[2], r[3], 0xFF0C0F16);
        drawRect(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, in(r, mx, my) ? 0xFF4F86E0 : color);
        FontRenderer f = fontRendererObj;
        f.drawStringWithShadow(label, (r[0] + r[2] - f.getStringWidth(label)) / 2, r[1] + 5, 0xFFFFFF);
    }

    private static String scaleLabel() {
        return Theme.uiScale == 0 ? "UI scale: Game" : "UI scale: x" + Theme.uiScale;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        try {
            parent.drawScreen(-1000, -1000, pt);
        } catch (Throwable t) {
            drawRect(0, 0, width, height, 0xFF101318);
        }
        FontRenderer f = fontRendererObj;
        int x0 = px(), y0 = py();
        drawRect(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF3A4661);
        drawRect(x0, y0, x0 + W, y0 + H, 0xFA161B26);
        drawRect(x0, y0, x0 + W, y0 + 24, 0xFF1D2433);
        drawRect(x0, y0 + 24, x0 + W, y0 + 25, 0xFF3A4661);
        f.drawStringWithShadow("Settings", x0 + 10, y0 + 8, 0xFFFFFF);

        Theme.drawWheel(wcx(), wcy(), R);
        double a = Theme.hue * 2 * Math.PI;
        double d = Theme.enabled ? Theme.sat * R : 0;
        int mxp = (int) Math.round(wcx() + Math.cos(a) * d), myp = (int) Math.round(wcy() + Math.sin(a) * d);
        Gui.drawRect(mxp - 3, myp - 3, mxp + 4, myp + 4, 0xFF000000);
        Gui.drawRect(mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFFFFFFFF);
        f.drawString("Theme colour", x0 + 16, y0 + 30 + 2 * R + 12, 0xFF8C96AA);

        int rx = x0 + 136;
        f.drawString("Hex code", rx, y0 + 30, 0xFF8C96AA);
        Gui.drawRect(rx, y0 + 41, rx + 84, y0 + 59, 0xFFFFFFFF);
        Gui.drawRect(rx + 1, y0 + 42, rx + 83, y0 + 58, 0xFF0E1118);
        String shown = "#" + hex + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        f.drawString(shown, rx + 4, y0 + 46, 0xFFFFFF);

        f.drawString("Preview", rx, y0 + 66, 0xFF8C96AA);
        Gui.drawRect(rx, y0 + 77, rx + 84, y0 + 93, 0xFF0C0F16);
        Gui.drawRect(rx + 1, y0 + 78, rx + 83, y0 + 92, 0xFF000000 | (Theme.enabled ? Theme.current() : 0x2F6FD0));

        button(scaleRect(), scaleLabel(), 0xFF3A4660, mx, my);
        button(soundRect(), Theme.sounds ? "Sounds: On" : "Sounds: Off", 0xFF3A4660, mx, my);
        button(animRect(), Theme.animations ? "Animations: On" : "Animations: Off", 0xFF3A4660, mx, my);
        f.drawString("Appearance", x0 + 16, y0 + 170, 0xFF8C96AA);
        button(bqThemeRect(), Theme.betterQuestingTheme ? "BetterQuesting Themes: On" : "BetterQuesting Themes: Off", 0xFF3A4660, mx, my);
        int[] sr = sliderRect();
        drawRect(sr[0], sr[1], sr[2], sr[3], 0xFF0C0F16);
        drawRect(sr[0] + 1, sr[1] + 1, sr[2] - 1, sr[3] - 1, 0xFF222838);
        int fillX = sr[0] + 1 + (sr[2] - sr[0] - 2) * Theme.bgOpacity / 100;
        drawRect(sr[0] + 1, sr[1] + 1, fillX, sr[3] - 1, 0xFF2F6FD0);
        drawRect(Math.max(sr[0] + 1, fillX - 1), sr[1] + 1, Math.min(sr[2] - 1, fillX + 1), sr[3] - 1, 0xFFFFFFFF);
        String sl = "Background opacity: " + Theme.bgOpacity + "%";
        f.drawStringWithShadow(sl, (sr[0] + sr[2] - f.getStringWidth(sl)) / 2, sr[1] + 5, 0xFFFFFF);
        f.drawString("Quest list", x0 + 16, y0 + 222, 0xFF8C96AA);
        button(reqRect(), Theme.requiredHighlight ? "Required quests: Yellow" : "Required quests: Off", 0xFF3A4660, mx, my);
        button(hideRect(), Theme.hideUseless ? "Hide empty categories: On" : "Hide empty categories: Off", 0xFF3A4660, mx, my);
        f.drawString("Book layout", x0 + 16, y0 + 272, 0xFF8C96AA);
        button(fullRect(), Theme.fullscreen ? "Fullscreen: On" : "Windowed mode: On", 0xFF3A4660, mx, my);
        button(keepRect(), Theme.fullscreen ? "Keep open: Windowed only" : Theme.keepOpen ? "Keep open: On" : "Keep open: Off",
                Theme.fullscreen ? 0xFF2A2E35 : 0xFF3A4660, mx, my);
        button(tutorialRect(), "Tutorial", 0xFF3A4660, mx, my);
        button(resetRect(), "Reset look", 0xFF3A4660, mx, my);
        button(doneRect(), "Done", 0xFF2F6FD0, mx, my);
        GL11.glColor4f(1, 1, 1, 1);
        if (Guide.active() && Guide.step() >= Guide.COLOR && Guide.step() <= Guide.HIDE_CATEGORIES) {
            Guide.draw(f, width, height, mx, my, guideTargetRect());
        }
    }

    private int[] guideTargetRect() {
        if (Guide.step() == Guide.COLOR) {
            return new int[] { wcx() - R, wcy() - R, wcx() + R, wcy() + R };
        }
        if (Guide.step() == Guide.OPACITY) return sliderRect();
        if (Guide.step() == Guide.HIDE_CATEGORIES) return hideRect();
        return null;
    }

    private void pick(int mx, int my) {
        double dx = mx - wcx(), dy = my - wcy();
        double ang = Math.atan2(dy, dx);
        if (ang < 0) ang += 2 * Math.PI;
        Theme.hue = (float) (ang / (2 * Math.PI));
        Theme.sat = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / R);
        Theme.enabled = true;
        hexDirty = false;
        syncHex();
    }

    /** Re-lays out this screen and its parent after the GUI scale changed. */
    private void rescale() {
        Hooks.syncScale(this);
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        parent.setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
        setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        if (Guide.active() && Guide.step() >= Guide.COLOR && Guide.step() <= Guide.HIDE_CATEGORIES) {
            boolean consumed = Guide.click(fontRendererObj, width, height, mx, my, btn, guideTargetRect());
            if (Guide.takeCloseSettingsRequest()) close();
            if (consumed) return;
        }
        if (btn != 0) return;
        double dx = mx - wcx(), dy = my - wcy();
        if (dx * dx + dy * dy <= (R + 4) * (R + 4)) {
            dragging = true;
            pick(mx, my);
        } else if (in(scaleRect(), mx, my)) {
            Theme.click();
            Theme.uiScale = (Theme.uiScale + 1) % 6;
            Theme.save();
            rescale();
        } else if (in(soundRect(), mx, my)) {
            Theme.sounds = !Theme.sounds;
            Theme.click();
            Theme.save();
        } else if (in(animRect(), mx, my)) {
            Theme.click();
            Theme.animations = !Theme.animations;
            Theme.save();
        } else if (in(reqRect(), mx, my)) {
            Theme.click();
            Theme.requiredHighlight = !Theme.requiredHighlight;
            Theme.save();
        } else if (in(bqThemeRect(), mx, my)) {
            Theme.click();
            Theme.betterQuestingTheme = !Theme.betterQuestingTheme;
            Theme.save();
        } else if (in(sliderRect(), mx, my)) {
            sliding = true;
            slide(mx);
        } else if (in(fullRect(), mx, my)) {
            Theme.click();
            Theme.fullscreen = !Theme.fullscreen;
            Theme.save();
        } else if (in(hideRect(), mx, my)) {
            Theme.click();
            Theme.hideUseless = !Theme.hideUseless;
            Theme.save();
        } else if (in(keepRect(), mx, my) && !Theme.fullscreen) {
            Theme.click();
            Theme.keepOpen = !Theme.keepOpen;
            Theme.save();
        } else if (in(tutorialRect(), mx, my)) {
            Theme.click();
            Guide.startAgain(parent instanceof ModernScreen ? (ModernScreen) parent : null);
            mc.displayGuiScreen(parent);
        } else if (in(resetRect(), mx, my)) {
            Theme.click();
            Theme.enabled = false;
            Theme.bgOpacity = 88;
            Theme.save();
            syncHex();
        } else if (in(doneRect(), mx, my)) {
            Theme.click();
            close();
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long t) {
        if (dragging) pick(mx, my);
        else if (sliding) slide(mx);
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int state) {
        if (state != -1) {
            if (sliding) Theme.save();
            dragging = false;
            sliding = false;
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (Guide.active() && Guide.step() >= Guide.COLOR && Guide.step() <= Guide.HIDE_CATEGORIES) {
            if (key == Keyboard.KEY_ESCAPE) Guide.escape();
            return;
        }
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
            close();
            return;
        }
        if (key == Keyboard.KEY_BACK) {
            hexDirty = true;
            if (!hex.isEmpty()) hex = hex.substring(0, hex.length() - 1);
        } else if (Character.digit(c, 16) >= 0 && hex.length() < 6) {
            if (!hexDirty) {
                hex = "";
                hexDirty = true;
            }
            hex += Character.toUpperCase(c);
        } else {
            return;
        }
        if (hex.length() == 6) Theme.setRgb(Integer.parseInt(hex, 16));
    }

    private void close() {
        Theme.save();
        mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
