package dev.bqmodern;

import java.util.UUID;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.UuidConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/** Hosts the pinned quest: the quest popup drawn at 75% size on top of the game, draggable and scrollable, also with chat open. */
public final class PinHud {
    private static ModernScreen view;

    private static final int CAP_NONE = 0, CAP_DRAG = 1, CAP_FORWARD = 2, CAP_RESIZE = 3, CAP_RESIZE_L = 4;
    private static int capture = CAP_NONE, capBtn, grabX, grabY;

    private PinHud() {}

    public static void pin(UUID id) {
        Theme.pinned = id;
        Theme.save();
    }

    private static int sw, sh;

    /** Pixels per gui unit that the quest UI uses: the chosen UI scale if set, otherwise the game's current one. */
    static int unitScale(Minecraft mc) {
        int actual = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        if (Theme.uiScale <= 0) return actual;
        int s = 1;
        while (s < Theme.uiScale && mc.displayWidth / (s + 1) >= 320 && mc.displayHeight / (s + 1) >= 240) s++;
        return s;
    }

    private static void metrics(Minecraft mc) {
        int u = unitScale(mc);
        sw = (mc.displayWidth + u - 1) / u;
        sh = (mc.displayHeight + u - 1) / u;
    }

    /** Scale from the quest UI's gui units to whatever scale the game is currently drawing with. */
    private static float drawRatio(Minecraft mc) {
        int actual = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        return unitScale(mc) / (float) actual;
    }

    private static int px() { return Math.max(0, Math.min(sw - ModernScreen.pinW(), Math.round(Theme.pinFx * sw))); }

    private static int py() { return Math.max(0, Math.min(sh - ModernScreen.pinH(), Math.round(Theme.pinFy * sh))); }

    private static ModernScreen view(Minecraft mc) {
        if (view == null) view = ModernScreen.newPinView(mc);
        return view;
    }

    private static boolean inside(int gx, int gy) {
        return gx >= px() && gy >= py() && gx < px() + ModernScreen.pinW() && gy < py() + ModernScreen.pinH();
    }

    static int[] guideFoldTarget(Minecraft mc) {
        if (view == null || !active(mc)) return null;
        metrics(mc);
        return view.pinFoldScreenRect(px(), py(), ModernScreen.pinK());
    }

    static int[] guideFavoriteTarget(Minecraft mc) {
        if (view == null || !active(mc)) return null;
        metrics(mc);
        return view.pinFavoriteScreenRect(px(), py(), ModernScreen.pinK());
    }

    private static boolean active(Minecraft mc) {
        if (Theme.pinned == null || mc.thePlayer == null || mc.gameSettings.hideGUI) return false;
        try {
            return QuestingAPI.getAPI(ApiReference.QUEST_DB).get(Theme.pinned) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void draw(Minecraft mc, int rmx, int rmy) {
        float r = drawRatio(mc);
        boolean scaled = Math.abs(r - 1f) > 1e-4f;
        if (scaled) {
            GL11.glPushMatrix();
            GL11.glScalef(r, r, 1f);
        }
        try {
            drawAll(mc);
        } finally {
            if (scaled) GL11.glPopMatrix();
        }
    }

    private static void drawAll(Minecraft mc) {
        drawBook(mc);
        if (!active(mc)) return;
        metrics(mc);
        int gx = Mouse.getX() * sw / mc.displayWidth;
        int gy = sh - Mouse.getY() * sh / mc.displayHeight - 1;
        if (mc.currentScreen == null) {
            gx = -1;
            gy = -1;
        }
        try {
            view(mc).drawPinned(Theme.pinned, px(), py(), gx, gy);
        } catch (Throwable t) {
            BQModern.LOG.error("Pinned quest failed to draw; unpinning", t);
            pin(null);
        }
    }

    private static void drawBook(Minecraft mc) {
        ModernScreen b = Hooks.kept;
        if (b == null) return;
        if (mc.thePlayer == null || Theme.fullscreen || !Theme.keepOpen) {
            Hooks.kept = null;
            return;
        }
        if (mc.gameSettings.hideGUI || Hooks.ours(mc.currentScreen)) return;
        metrics(mc);
        int gx = -1, gy = -1;
        if (mc.currentScreen != null) {
            gx = Mouse.getX() * sw / mc.displayWidth;
            gy = sh - Mouse.getY() * sh / mc.displayHeight - 1;
        }
        try {
            b.keptDraw(sw, sh, gx, gy);
        } catch (Throwable t) {
            BQModern.LOG.error("Kept quest book failed to draw; dropping it", t);
            Hooks.kept = null;
        }
    }

    private static boolean bookMouse(Minecraft mc) {
        ModernScreen b = Hooks.kept;
        if (b == null || mc.thePlayer == null || Hooks.ours(mc.currentScreen)) return false;
        metrics(mc);
        int gx = Mouse.getEventX() * sw / mc.displayWidth;
        int gy = sh - Mouse.getEventY() * sh / mc.displayHeight - 1;
        if (!b.keptBusy() && !b.keptHit(gx, gy)) return false;
        b.keptMouse();
        return true;
    }

    /** Handles the current mouse event. Returns true when it belonged to a pinned quest or the kept book and must not reach the screen. */
    public static boolean onMouse(Minecraft mc) {
        return pinMouse(mc) || bookMouse(mc);
    }

    private static boolean pinMouse(Minecraft mc) {
        if (!active(mc) || view == null) {
            capture = CAP_NONE;
            return false;
        }
        metrics(mc);
        int gx = Mouse.getEventX() * sw / mc.displayWidth;
        int gy = sh - Mouse.getEventY() * sh / mc.displayHeight - 1;
        int btn = Mouse.getEventButton();
        boolean down = Mouse.getEventButtonState();
        int wheel = Mouse.getEventDWheel();
        int vx = view.pinVx(gx, px()), vy = view.pinVy(gy, py());

        if (capture != CAP_NONE) {
            if (btn != -1 && !down && btn == capBtn) {
                if (capture == CAP_FORWARD) view.pinRelease(vx, vy, btn);
                else Theme.save();
                capture = CAP_NONE;
            } else if (btn == -1 && wheel == 0) {
                if (capture == CAP_RESIZE) {
                    float bw = ModernScreen.pinBaseW();
                    float k = Math.min((gx - px()) / bw, (gy - py()) / 320f);
                    k = Math.min(k, Math.min((sw - px()) / bw, (sh - py()) / 320f));
                    Theme.pinScale = Math.max(0.35f, Math.min(1.6f, k));
                } else if (capture == CAP_RESIZE_L) {
                    float bw = ModernScreen.pinBaseW();
                    float k = Math.min((grabX - gx) / bw, (gy - py()) / 320f);
                    k = Math.min(k, Math.min(grabX / bw, (sh - py()) / 320f));
                    Theme.pinScale = Math.max(0.35f, Math.min(1.6f, k));
                    Theme.pinFx = (grabX - ModernScreen.pinW()) / (float) sw;
                } else if (capture == CAP_DRAG) {
                    Theme.pinFx = (gx - grabX) / (float) sw;
                    Theme.pinFy = (gy - grabY) / (float) sh;
                    Theme.pinFx = px() / (float) sw;
                    Theme.pinFy = py() / (float) sh;
                } else {
                    view.pinMove(vx, vy, capBtn);
                }
            }
            return true;
        }
        if (!inside(gx, gy)) return false;
        if (wheel != 0) {
            view.pinWheel(wheel, vx, vy);
            return true;
        }
        if (down && btn >= 0) {
            capBtn = btn;
            int corner = btn == 0 ? view.pinGrabsCorner(vx, vy) : 0;
            if (corner == 1) {
                capture = CAP_RESIZE;
            } else if (corner == 2) {
                capture = CAP_RESIZE_L;
                grabX = px() + ModernScreen.pinW();
            } else if (btn == 0 && view.pinGrabsHeader(vx, vy)) {
                capture = CAP_DRAG;
                grabX = gx - px();
                grabY = gy - py();
            } else {
                capture = CAP_FORWARD;
                view.pinClick(vx, vy, btn);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- sharing

    /** Sends BetterQuesting's native share token; BQ turns the server echo into a clickable quest link. */
    public static void share(Minecraft mc, UUID id) {
        if (mc.thePlayer == null) return;
        IQuest q = QuestingAPI.getAPI(ApiReference.QUEST_DB).get(id);
        if (q == null) return;
        mc.thePlayer.sendChatMessage("betterquesting.msg.sharequest:" + UuidConverter.encodeUuid(id));
    }
}
