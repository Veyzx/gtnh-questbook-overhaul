package dev.bqmodern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.themes.IGuiTheme;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.client.themes.ResourceTheme;
import betterquesting.client.themes.ThemeRegistry;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.client.gui2.GuiHome;
import betterquesting.network.handlers.NetQuestAction;
import bq_standard.rewards.IRewardItemOutput;
import bq_standard.rewards.RewardXP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;

/** Modern quest book that reads everything live from BetterQuesting's databases. */
public class ModernScreen extends GuiScreen {
    private static final int SIDE_W = 150;
    private static final int SIDE_MIN = 16;

    /** Current width of the quest chapter sidebar (collapsed to a thin strip when folded). */
    private static int sideW() { return Theme.sideFolded ? SIDE_MIN : SIDE_W; }

    private int[] sideToggleRect() {
        return Theme.sideFolded ? new int[] { 0, 0, SIDE_MIN - 1, HEADER_H } : new int[] { SIDE_W - 22, 5, SIDE_W - 5, HEADER_H - 5 };
    }
    private static final int HEADER_H = 26;
    private static final int ROW_H = 24;
    private static final int FOOT_H = 24;
    private static final int ACCENT = 0xFF4DA3FF;
    private static final int GREEN = 0xFF3DC74E;
    private static final int GOLD = 0xFFFFC832;

    private static UUID curLine;
    private static final Map<UUID, double[]> views = new HashMap<UUID, double[]>();
    private static int sideScroll;

    private final RenderItem itemRender = new RenderItem();
    private final Map<String, ResourceLocation[]> shapeTex = new HashMap<String, ResourceLocation[]>();
    private static final ResourceLocation CHECK_TEX = new ResourceLocation("bqmodern", "textures/icons/checkmark.png");
    private static final ResourceLocation MARKER_TEX = new ResourceLocation("bqmodern", "textures/icons/marker.png");
    private static final ResourceLocation SETTINGS_TEX = new ResourceLocation("bqmodern", "textures/icons/settings.png");
    private int sf = 2;

    private final Map<UUID, EnumQuestState> states = new HashMap<UUID, EnumQuestState>();
    private final Set<UUID> shown = new HashSet<UUID>();
    private final Map<UUID, int[]> stats = new HashMap<UUID, int[]>();
    private int totalDone, totalAll;
    private long lastRefresh;
    private final Map<UUID, Long> autoChecked = new HashMap<UUID, Long>();
    private final List<UUID> autoList = new ArrayList<UUID>();

    private boolean dragging;
    private boolean scrollDrag;
    private int scrollGrab;
    private int lastMx, lastMy, dragDist;

    // quest window
    private UUID modalId;
    private IQuest modalQuest;
    private final List<CanvasEmpty> taskCvs = new ArrayList<CanvasEmpty>();
    private int taskScroll, builtTaskScroll, taskGrab, taskBar;
    private boolean taskDrag;
    private final List<CanvasEmpty> rewardCvs = new ArrayList<CanvasEmpty>();
    private int rewardScroll, descScroll;
    private float descPos;
    private List<String> rewText = new ArrayList<String>();
    private List<ItemStack> rewIcons = new ArrayList<ItemStack>();
    private int descMax, descBar, descGrab;
    private boolean descDrag;
    private long lastModalBuild;
    private boolean modalBroken;

    // search / favourites panel
    private boolean searchOpen, favOnly;
    private String query = "";
    private final List<UUID> results = new ArrayList<UUID>();
    private final Set<UUID> matches = new HashSet<UUID>();
    private Map<UUID, String> searchIndex;
    private Map<UUID, String> chapterOf;
    private int resScroll;

    // animation state
    private long lastFrame = System.nanoTime();
    private float dt = 0.016f;
    private float modalAnim;
    private final Map<Object, Float> hoverAmt = new HashMap<Object, Float>();

    public ModernScreen(GuiScreen parent) {
        for (String s : new String[] { "circle", "hexagon", "octagon", "rsquare", "square", "castle" }) {
            shapeTex.put(s, new ResourceLocation[] {
                    new ResourceLocation("bqmodern", "textures/shapes/" + s + "/background.png"),
                    new ResourceLocation("bqmodern", "textures/shapes/" + s + "/outline.png") });
        }
    }

    public void openQuest(UUID id) {
        if (id == null) return;
        IQuest q = qdb().get(id);
        if (q == null) return;
        for (Map.Entry<UUID, IQuestLine> l : ldb().getOrderedEntries()) {
            if (l.getValue().containsKey(id)) {
                curLine = l.getKey();
                break;
            }
        }
        showModal(id, q);
    }

    // ---------------------------------------------------------------- data

    private static IQuestDatabase qdb() { return QuestingAPI.getAPI(ApiReference.QUEST_DB); }

    private static IQuestLineDatabase ldb() { return QuestingAPI.getAPI(ApiReference.LINE_DB); }

    private EntityPlayer player() { return mc.thePlayer; }

    private UUID me() { return QuestingAPI.getQuestingUUID(mc.thePlayer); }

    private static String text(String s) {
        if (s == null) return "";
        String t = I18n.format(s);
        return t.replace("\\n", "\n");
    }

    private static String name(betterquesting.api.properties.IPropertyContainer c) {
        return text(c.getProperty(NativeProps.NAME));
    }

    private static ItemStack iconOf(betterquesting.api.properties.IPropertyContainer c) {
        try {
            BigItemStack b = c.getProperty(NativeProps.ICON);
            if (b == null) return null;
            ItemStack s = b.getBaseStack();
            if (s != null && s.getItemDamage() == 32767) s.setItemDamage(0);
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    private EnumQuestState state(UUID id) {
        EnumQuestState s = states.get(id);
        return s == null ? EnumQuestState.LOCKED : s;
    }

    private static boolean done(EnumQuestState s) {
        return s == EnumQuestState.COMPLETED || s == EnumQuestState.UNCLAIMED || s == EnumQuestState.REPEATABLE;
    }

    private void refresh() {
        lastRefresh = System.currentTimeMillis();
        EntityPlayer p = player();
        if (p == null) return;
        UUID uid = me();
        states.clear();
        shown.clear();
        stats.clear();
        totalDone = totalAll = 0;
        IQuestDatabase qd = qdb();
        for (Map.Entry<UUID, IQuestLine> l : ldb().getOrderedEntries()) {
            int[] st = new int[3];
            for (UUID id : l.getValue().keySet()) {
                IQuest q = qd.get(id);
                if (q == null) continue;
                boolean vis;
                try {
                    vis = QuestCache.isQuestShown(q, uid, p);
                } catch (Throwable t) {
                    vis = true;
                }
                if (!vis) continue;
                EnumQuestState s;
                try {
                    s = q.getState(p);
                } catch (Throwable t) {
                    s = EnumQuestState.LOCKED;
                }
                states.put(id, s);
                shown.add(id);
                if (s == EnumQuestState.UNLOCKED && hasPendingCheckbox(q, uid)) {
                    Long last = autoChecked.get(id);
                    if (last == null || System.currentTimeMillis() - last > 5000) {
                        autoChecked.put(id, System.currentTimeMillis());
                        autoList.add(id);
                    }
                }
                st[1]++;
                if (s == EnumQuestState.COMPLETED || s == EnumQuestState.UNCLAIMED) st[0]++;
                if (s == EnumQuestState.UNCLAIMED) st[2]++;
            }
            stats.put(l.getKey(), st);
            totalDone += st[0];
            totalAll += st[1];
        }
        if (!autoList.isEmpty()) {
            try {
                NetQuestAction.requestDetect(new ArrayList<UUID>(autoList));
            } catch (Throwable t) {
                BQModern.LOG.error("Auto check failed", t);
            }
            autoList.clear();
        }
    }

    private boolean hasPendingCheckbox(IQuest q, UUID uid) {
        try {
            for (DBEntry<ITask> e : q.getTasks().getEntries()) {
                ITask t = e.getValue();
                if (t instanceof bq_standard.tasks.TaskCheckbox && !t.isComplete(uid)) return true;
            }
        } catch (Throwable ignored) {
            // not a checkbox quest
        }
        return false;
    }

    // ---------------------------------------------------------------- layout helpers

    private int mapLeft() { return sideW(); }

    private int mapW() { return width - sideW(); }

    private int mapTop() { return HEADER_H; }

    private int mapH() { return height - HEADER_H; }

    private double[] view() {
        double[] v = views.get(curLine);
        if (v == null) {
            double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
            IQuestLine l = ldb().get(curLine);
            if (l != null) {
                for (Map.Entry<UUID, IQuestLineEntry> e : l.entrySet()) {
                    IQuestLineEntry en = e.getValue();
                    minX = Math.min(minX, en.getPosX());
                    maxX = Math.max(maxX, en.getPosX() + en.getSizeX());
                    minY = Math.min(minY, en.getPosY());
                    maxY = Math.max(maxY, en.getPosY() + en.getSizeY());
                }
            }
            if (minX > maxX) {
                minX = maxX = minY = maxY = 0;
            }
            double zx = mapW() / (maxX - minX + 120.0), zy = mapH() / (maxY - minY + 120.0);
            double z = Math.max(0.3, Math.min(1.5, Math.min(zx, zy)));
            v = new double[] { (minX + maxX) / 2, (minY + maxY) / 2, z };
            views.put(curLine, v);
        }
        return v;
    }

    private double sx(double wx, double[] v) { return mapLeft() + mapW() / 2.0 + (wx - v[0]) * v[2]; }

    private double sy(double wy, double[] v) { return mapTop() + mapH() / 2.0 + (wy - v[1]) * v[2]; }

    private void ensureLine() {
        IQuestLineDatabase d = ldb();
        if (curLine == null || !d.containsKey(curLine)) {
            List<Map.Entry<UUID, IQuestLine>> o = d.getOrderedEntries();
            curLine = o.isEmpty() ? null : o.get(0).getKey();
        }
    }

    private UUID nodeAt(int mx, int my) {
        if (curLine == null || mx < mapLeft() || my < mapTop()) return null;
        IQuestLine l = ldb().get(curLine);
        if (l == null) return null;
        double[] v = view();
        UUID best = null;
        for (Map.Entry<UUID, IQuestLineEntry> e : l.entrySet()) {
            if (!shown.contains(e.getKey())) continue;
            IQuestLineEntry en = e.getValue();
            double cx = sx(en.getPosX() + en.getSizeX() / 2.0, v), cy = sy(en.getPosY() + en.getSizeY() / 2.0, v);
            if (Math.abs(mx - cx) <= en.getSizeX() * v[2] / 2 && Math.abs(my - cy) <= en.getSizeY() * v[2] / 2) best = e.getKey();
        }
        return best;
    }

    // ---------------------------------------------------------------- gl helpers

    private void glColor(int argb) {
        argb = Theme.t(argb);
        GL11.glColor4f(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
    }

    /** Smoothly approaches 0/1 for hover effects; snaps instantly when animations are off. */
    private float hover(Object key, boolean on) {
        Float c = hoverAmt.get(key);
        float cur = c == null ? 0f : c.floatValue();
        float target = on ? 1f : 0f;
        if (!Theme.animations) cur = target;
        else cur += (target - cur) * Math.min(1f, dt * 14f);
        if (Math.abs(cur - target) < 0.01f) cur = target;
        if (cur == 0f) hoverAmt.remove(key);
        else hoverAmt.put(key, Float.valueOf(cur));
        return cur;
    }

    private static int withAlpha(int argb, float a) {
        int al = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 255) * a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private float pulse() {
        return Theme.animations ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0) : 1f;
    }

    /** Flat-colour drawing state; item rendering leaves culling/lighting on, which hides or blackens filled shapes. */
    private void flat() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void disc(double cx, double cy, double r, int argb) {
        flat();
        glColor(argb);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(cx, cy);
        for (int i = 0; i <= 20; i++) {
            double a = i * Math.PI / 10;
            GL11.glVertex2d(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void ring(double cx, double cy, double r, float w, int argb) {
        flat();
        glColor(argb);
        GL11.glLineWidth(w * sf);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16;
            GL11.glVertex2d(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void star(double cx, double cy, double r, boolean filled, int argb) {
        flat();
        glColor(argb);
        GL11.glLineWidth(1.4f * sf);
        GL11.glBegin(filled ? GL11.GL_TRIANGLE_FAN : GL11.GL_LINE_LOOP);
        if (filled) GL11.glVertex2d(cx, cy);
        for (int i = 0; i <= 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double rr = i % 2 == 0 ? r : r * 0.42;
            GL11.glVertex2d(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
        }
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void padlock(double cx, double cy, double s, int argb) {
        double bw = s * 0.9, bh = s * 0.6;
        drawRect((int) Math.round(cx - bw / 2), (int) Math.round(cy - bh / 2 + s * 0.15), (int) Math.round(cx + bw / 2),
                (int) Math.round(cy + bh / 2 + s * 0.15), argb);
        double sw = s * 0.55;
        line(cx - sw / 2, cy - bh / 2 + s * 0.15, cx - sw / 2, cy - s * 0.35, 1.2f, argb);
        line(cx + sw / 2, cy - bh / 2 + s * 0.15, cx + sw / 2, cy - s * 0.35, 1.2f, argb);
        line(cx - sw / 2, cy - s * 0.35, cx + sw / 2, cy - s * 0.35, 1.2f, argb);
    }

    private void tex(ResourceLocation rl, double x, double y, double w, double h, int argb) {
        mc.getTextureManager().bindTexture(rl);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        argb = Theme.t(argb);
        t.setColorRGBA((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
        t.addVertexWithUV(x, y + h, 0, 0, 1);
        t.addVertexWithUV(x + w, y + h, 0, 1, 1);
        t.addVertexWithUV(x + w, y, 0, 1, 0);
        t.addVertexWithUV(x, y, 0, 0, 0);
        t.draw();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void line(double x1, double y1, double x2, double y2, float w, int argb) {
        flat();
        glColor(argb);
        GL11.glLineWidth(w * sf);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void check(double x, double y, double s, int color) {
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? 0xFF000000 : color;
            float w = pass == 0 ? 3.2f : 1.8f;
            line(x + s * 0.15, y + s * 0.55, x + s * 0.4, y + s * 0.8, w, c);
            line(x + s * 0.4, y + s * 0.8, x + s * 0.88, y + s * 0.2, w, c);
        }
    }

    private void item(ItemStack s, double cx, double cy, double size) {
        if (s == null) return;
        GL11.glPushMatrix();
        GL11.glTranslatef((float) (cx - size / 2), (float) (cy - size / 2), 0);
        float sc = (float) (size / 16.0);
        GL11.glScalef(sc, sc, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        try {
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), s, 0, 0);
        } catch (Throwable ignored) {
            // broken stack icon; skip it
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private void scissor(int x, int y, int w, int h) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        if (pinMode) {
            float k = pinK();
            GL11.glScissor(Math.round((pinOx + x * k) * sf), mc.displayHeight - Math.round((pinOy + (y + h) * k) * sf),
                    Math.max(0, Math.round(w * k * sf)), Math.max(0, Math.round(h * k * sf)));
            return;
        }
        if (windowed()) {
            float k = winK();
            GL11.glScissor(Math.round((ox() + x * k) * sf), mc.displayHeight - Math.round((oy() + (y + h) * k) * sf),
                    Math.max(0, Math.round(w * k * sf)), Math.max(0, Math.round(h * k * sf)));
            return;
        }
        GL11.glScissor(x * sf, mc.displayHeight - (y + h) * sf, Math.max(0, w * sf), Math.max(0, h * sf));
    }

    private void unscissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private boolean in(int[] r, int mx, int my) { return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]; }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 28), g = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void button(int[] r, String label, boolean enabled, int color, int mx, int my) {
        float h = hover("btn:" + label + r[0] + ":" + r[1], enabled && in(r, mx, my));
        int c = !enabled ? 0xFF2A2E35 : mix(color, lighten(color), h);
        drawRect(r[0], r[1], r[2], r[3], 0xFF0C0F16);
        drawRect(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, c);
        String s = fontRendererObj.trimStringToWidth(label, r[2] - r[0] - 6);
        fontRendererObj.drawStringWithShadow(s, (r[0] + r[2] - fontRendererObj.getStringWidth(s)) / 2, r[1] + (r[3] - r[1] - 8) / 2,
                enabled ? 0xFFFFFF : 0x777777);
    }

    // ---------------------------------------------------------------- window mode

    private static final int WB = 5, WT = 16, WMIN_W = 240, WMIN_H = 160, WIN_VW = 480;
    private static final int EDGE_L = 1, EDGE_R = 2, EDGE_T = 4, EDGE_B = 8, MOVE = 16;
    private int realW, realH;
    private int winMode, winGrabX, winGrabY, winStartX, winStartY, winStartW, winStartH;
    private int heldBtn = -1;
    private int builtW, builtH;
    private UUID guideQuestId, guideQuestLine;

    private boolean windowed() { return !pinMode && !Theme.fullscreen; }

    /** Windowed mode lays the book out 480 virtual pixels wide and scales it to the window, so text and icons follow the window size. */
    private float winK() { return Math.max(0.1f, (Theme.winW - 2 * WB) / (float) WIN_VW); }

    /** True while this book is being drawn or driven from the HUD (kept open after its screen closed). */
    private boolean hosted;
    private boolean hostResizing;

    // ---------------------------------------------------------------- pinned quest view

    /** The pinned quest is this screen's quest popup, drawn scaled down on the HUD. 470x320 popup centred in a 490x340 virtual screen. */
    static float pinK() { return Math.max(0.35f, Math.min(1.6f, Theme.pinScale)); }
    static final int PIN_VW = 490, PIN_VH = 340, PIN_FOLD_W = 284;
    private boolean pinMode;
    private float pinOx, pinOy;
    private long sharedAt, pinStateAt;

    static ModernScreen newPinView(net.minecraft.client.Minecraft m) {
        ModernScreen s = new ModernScreen(null);
        s.pinMode = true;
        s.setWorldAndResolution(m, PIN_VW, PIN_VH);
        return s;
    }

    static int pinBaseW() { return Theme.pinFolded ? PIN_FOLD_W : 470; }

    static int pinW() { return Math.round(pinBaseW() * pinK()); }

    static int pinH() { return Math.round(320 * pinK()); }

    /** Draws the pinned quest with its top-left corner at (px, py) in real gui pixels. */
    void drawPinned(UUID id, int px, int py, int rmx, int rmy) {
        IQuest q = qdb().get(id);
        if (q == null) return;
        sf = PinHud.unitScale(mc);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (!id.equals(modalId)) showModal(id, q);
        modalQuest = q;
        modalAnim = 1f;
        if (System.currentTimeMillis() - pinStateAt > 500) {
            pinStateAt = System.currentTimeMillis();
            try {
                states.put(id, q.getState(player()));
            } catch (Throwable t) {
                states.put(id, EnumQuestState.LOCKED);
            }
        }
        float k = pinK();
        pinOx = px - m0x() * k;
        pinOy = py - m0y() * k;
        int vmx = Math.round((rmx - pinOx) / k), vmy = Math.round((rmy - pinOy) / k);
        GL11.glPushMatrix();
        GL11.glTranslatef(pinOx, pinOy, 0f);
        GL11.glScalef(k, k, 1f);
        try {
            drawModal(vmx, vmy);
            flat();
            int gx1 = m0x() + mw(), gy1 = m0y() + mh(), gx0 = m0x();
            boolean hot = pinGrabsCorner(vmx, vmy) == 1;
            boolean hotL = pinGrabsCorner(vmx, vmy) == 2;
            for (int i = 0; i < 3; i++) {
                int o = 3 + i * 3;
                for (int j = 0; j <= i; j++) {
                    drawRect(gx1 - o - 1, gy1 - 3 - j * 3 - 1, gx1 - o, gy1 - 3 - j * 3, hot ? 0xFFFFFFFF : 0xB0AAB4C8);
                    drawRect(gx0 + o, gy1 - 3 - j * 3 - 1, gx0 + o + 1, gy1 - 3 - j * 3, hotL ? 0xFFFFFFFF : 0xB0AAB4C8);
                }
            }
        } finally {
            GL11.glPopMatrix();
            unscissor();
        }
    }

    /** Virtual coordinates of a real gui pixel position, for the pinned view. */
    int pinVx(int rx, int px) { return Math.round((rx - px) / pinK()) + m0x(); }

    int pinVy(int ry, int py) { return Math.round((ry - py) / pinK()) + m0y(); }

    /** 1 for the bottom-right resize grip, 2 for the bottom-left one, 0 otherwise. */
    int pinGrabsCorner(int vx, int vy) {
        int gx0 = m0x(), gx1 = gx0 + mw(), gy1 = m0y() + mh();
        if (vy < gy1 - 14 || vy >= gy1) return 0;
        if (vx >= gx1 - 14 && vx < gx1) return 1;
        if (vx >= gx0 && vx < gx0 + 14) return 2;
        return 0;
    }

    /** True when the point is on the popup's title bar and not on one of its buttons, so it can drag the pin. */
    boolean pinGrabsHeader(int vx, int vy) {
        if (vy < m0y() || vy >= m0y() + 29 || vx < m0x() || vx >= m0x() + mw()) return false;
        return !in(closeRect(), vx, vy) && !in(favRect(), vx, vy) && !in(pinRect(), vx, vy) && !in(shareRect(), vx, vy) && !in(foldRect(), vx, vy);
    }

    void pinClick(int vx, int vy, int btn) { clickModal(vx, vy, btn); }

    void pinMove(int vx, int vy, int btn) { mouseClickMove(vx, vy, btn, 0L); }

    void pinRelease(int vx, int vy, int btn) { mouseMovedOrUp(vx, vy, btn); }

    void pinWheel(int d, int vx, int vy) { handleWheel(d, vx, vy); }

    private int ox() { return windowed() ? Theme.winX + WB : 0; }

    private int oy() { return windowed() ? Theme.winY + WT : 0; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Fullscreen: virtual size is the real screen. Windowed: virtual size is the window's content area. */
    private void applyWindow() {
        if (pinMode) {
            width = PIN_VW;
            height = PIN_VH;
            return;
        }
        if (realW <= 0 || realH <= 0) {
            realW = width;
            realH = height;
        }
        if (!windowed()) {
            width = realW;
            height = realH;
            return;
        }
        if (Theme.winW <= 0 || Theme.winH <= 0) {
            Theme.winW = Math.max(WMIN_W, realW * 2 / 3);
            Theme.winH = Math.max(WMIN_H, realH * 2 / 3);
            Theme.winX = (realW - Theme.winW) / 2;
            Theme.winY = (realH - Theme.winH) / 2;
        }
        Theme.winW = clamp(Theme.winW, Math.min(WMIN_W, realW), realW);
        Theme.winH = clamp(Theme.winH, Math.min(WMIN_H, realH), realH);
        Theme.winX = clamp(Theme.winX, 0, realW - Theme.winW);
        Theme.winY = clamp(Theme.winY, 0, realH - Theme.winH);
        width = WIN_VW;
        height = Math.max(1, Math.round((Theme.winH - WT - WB) / winK()));
    }

    @Override
    public void setWorldAndResolution(net.minecraft.client.Minecraft m, int w, int h) {
        if (!hostResizing && !pinMode && Hooks.kept == this) Hooks.kept = null;
        realW = w;
        realH = h;
        super.setWorldAndResolution(m, w, h);
        applyWindow();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (!pinMode && !hosted && !Theme.fullscreen && Theme.keepOpen) Hooks.kept = this;
    }

    // ---------------------------------------------------------------- book kept on screen

    boolean keptHit(int gx, int gy) {
        return gx >= Theme.winX - 1 && gy >= Theme.winY - 1 && gx < Theme.winX + Theme.winW + 1 && gy < Theme.winY + Theme.winH + 1;
    }

    boolean keptBusy() { return heldBtn != -1 || winMode != 0; }

    void keptMouse() {
        hosted = true;
        try {
            mouseInput();
        } finally {
            hosted = false;
        }
    }

    void keptDraw(int gw, int gh, int gx, int gy) {
        if (gw != realW || gh != realH) {
            hostResizing = true;
            try {
                setWorldAndResolution(mc, gw, gh);
            } finally {
                hostResizing = false;
            }
        }
        hosted = true;
        try {
            drawScreen(gx, gy, 0f);
        } finally {
            hosted = false;
        }
    }

    private int[] winMaxRect() { return new int[] { Theme.winX + Theme.winW - 36, Theme.winY + 3, Theme.winX + Theme.winW - 22, Theme.winY + WT - 2 }; }

    private int[] winCloseRect() { return new int[] { Theme.winX + Theme.winW - 20, Theme.winY + 3, Theme.winX + Theme.winW - 6, Theme.winY + WT - 2 }; }

    private int edgesAt(int rx, int ry) {
        int e = 0;
        if (rx < Theme.winX + WB) e |= EDGE_L;
        if (rx >= Theme.winX + Theme.winW - WB) e |= EDGE_R;
        if (ry < Theme.winY + 3) e |= EDGE_T;
        if (ry >= Theme.winY + Theme.winH - WB) e |= EDGE_B;
        int gs = 12;
        if (ry >= Theme.winY + Theme.winH - gs) {
            if (rx < Theme.winX + gs) e |= EDGE_L | EDGE_B;
            else if (rx >= Theme.winX + Theme.winW - gs) e |= EDGE_R | EDGE_B;
        }
        return e;
    }

    private void drawGrips(int rx, int ry) {
        int x = Theme.winX, y = Theme.winY, w = Theme.winW, h = Theme.winH;
        int e = winMode >= EDGE_L && winMode < MOVE ? winMode : edgesAt(rx, ry);
        boolean hotR = (e & EDGE_R) != 0 && (e & EDGE_B) != 0, hotL = (e & EDGE_L) != 0 && (e & EDGE_B) != 0;
        int cr = hotR ? 0xFFFFFFFF : 0xB0AAB4C8, cl = hotL ? 0xFFFFFFFF : 0xB0AAB4C8;
        for (int i = 0; i < 3; i++) {
            int o = 3 + i * 3;
            for (int j = 0; j <= i; j++) {
                int yy = y + h - 3 - j * 3;
                drawRect(x + w - o - 1, yy - 1, x + w - o, yy, cr);
                drawRect(x + o, yy - 1, x + o + 1, yy, cl);
            }
        }
    }

    private void drawWindowFrame(int rx, int ry) {
        int x = Theme.winX, y = Theme.winY, w = Theme.winW, h = Theme.winH;
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0C0F16);
        drawRect(x, y, x + w, y + h, 0xFF3A4661);
        drawRect(x + 1, y + 1, x + w - 1, y + WT, 0xFF1D2433);
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth("Quest Book", w - 60), x + 6, y + 4, 0xFFFFFF);
        int[] mr = winMaxRect(), cr = winCloseRect();
        drawRect(mr[0], mr[1], mr[2], mr[3], in(mr, rx, ry) ? 0xFF4F86E0 : 0xFF2C3446);
        drawRect(mr[0] + 3, mr[1] + 3, mr[2] - 3, mr[3] - 3, 0xFFFFFFFF);
        drawRect(mr[0] + 4, mr[1] + 4, mr[2] - 4, mr[3] - 4, in(mr, rx, ry) ? 0xFF4F86E0 : 0xFF2C3446);
        drawRect(cr[0], cr[1], cr[2], cr[3], in(cr, rx, ry) ? 0xFFC03030 : 0xFF2C3446);
        fontRendererObj.drawStringWithShadow("x", cr[0] + 4, cr[1] + 2, 0xFFFFFF);
        int e = winMode >= EDGE_L && winMode < MOVE ? winMode : edgesAt(rx, ry);
        if (e != 0 && (winMode == 0 || winMode < MOVE)) {
            int c = 0xFF6FA8FF;
            if ((e & EDGE_L) != 0) drawRect(x, y, x + WB, y + h, c);
            if ((e & EDGE_R) != 0) drawRect(x + w - WB, y, x + w, y + h, c);
            if ((e & EDGE_T) != 0) drawRect(x, y, x + w, y + 3, c);
            if ((e & EDGE_B) != 0) drawRect(x, y + h - WB, x + w, y + h, c);
        }
    }

    /** Returns true when the press was used by the window chrome. Coordinates are real screen coordinates. */
    private boolean windowPress(int rx, int ry, int btn) {
        if (btn != 0) return false;
        int x = Theme.winX, y = Theme.winY;
        if (rx < x || ry < y || rx >= x + Theme.winW || ry >= y + Theme.winH) return true;
        if (in(winCloseRect(), rx, ry)) {
            Theme.click();
            if (hosted) {
                Hooks.kept = null;
                return true;
            }
            if (modalQuest != null) closeModal();
            mc.displayGuiScreen(null);
            return true;
        }
        if (in(winMaxRect(), rx, ry)) {
            Theme.click();
            Theme.fullscreen = true;
            Theme.save();
            applyWindow();
            if (hosted) Hooks.kept = null;
            return true;
        }
        int e = edgesAt(rx, ry);
        if (e == 0 && ry < y + WT) e = MOVE;
        if (e == 0) return false;
        winMode = e;
        winGrabX = rx;
        winGrabY = ry;
        winStartX = x;
        winStartY = y;
        winStartW = Theme.winW;
        winStartH = Theme.winH;
        return true;
    }

    private void windowDrag(int rx, int ry) {
        int dx = rx - winGrabX, dy = ry - winGrabY;
        if (winMode == MOVE) {
            Theme.winX = winStartX + dx;
            Theme.winY = winStartY + dy;
        } else {
            int minW = Math.min(WMIN_W, realW), minH = Math.min(WMIN_H, realH);
            int nx = winStartX, ny = winStartY, nw = winStartW, nh = winStartH;
            if ((winMode & EDGE_R) != 0) nw = Math.max(minW, winStartW + dx);
            if ((winMode & EDGE_B) != 0) nh = Math.max(minH, winStartH + dy);
            if ((winMode & EDGE_L) != 0) {
                nw = Math.max(minW, winStartW - dx);
                nx = winStartX + winStartW - nw;
            }
            if ((winMode & EDGE_T) != 0) {
                nh = Math.max(minH, winStartH - dy);
                ny = winStartY + winStartH - nh;
            }
            Theme.winX = nx;
            Theme.winY = ny;
            Theme.winW = nw;
            Theme.winH = nh;
        }
        applyWindow();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawScreen(int rmx, int rmy, float pt) {
        applyWindow();
        if (!windowed()) {
            drawContent(rmx, rmy, pt);
        } else {
            drawWindowFrame(rmx, rmy);
            GL11.glPushMatrix();
            GL11.glTranslatef(ox(), oy(), 0f);
            float k = winK();
            GL11.glScalef(k, k, 1f);
            try {
                drawContent(Math.round((rmx - ox()) / k), Math.round((rmy - oy()) / k), pt);
            } finally {
                GL11.glPopMatrix();
                unscissor();
            }
            flat();
            drawGrips(rmx, rmy);
        }
        if (!hosted) PinHud.draw(mc, rmx, rmy);
        if (!hosted && !pinMode && Guide.isMainStep()) {
            flat();
            Guide.draw(fontRendererObj, realW, realH, rmx, rmy, guideTargetScreen());
        }
    }

    private void drawContent(int mx, int my, float pt) {
        sf = PinHud.unitScale(mc);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        ensureLine();
        if (System.currentTimeMillis() - lastRefresh > 500) refresh();
        boolean modal = modalQuest != null;
        modalAnim = modal ? (Theme.animations ? Math.min(1f, modalAnim + dt * 7f) : 1f) : 0f;
        int hx = modal ? -1 : mx, hy = modal ? -1 : my;
        boolean overPanel = searchOpen && !modal && in(panelRect(), mx, my);
        if (overPanel) {
            hx = -1;
            hy = -1;
        }

        drawRect(0, 0, width, height, (Theme.bgOpacity * 255 / 100) << 24 | 0x101318);
        drawMap(hx, hy);
        drawHeader(hx, hy);
        drawSidebar(hx, hy);

        UUID hover = nodeAt(hx, hy);
        if (hover != null && !modal) drawHoverCard(hover, mx, my);
        if (searchOpen && !modal) drawSearchPanel(mx, my);
        if (modal) drawModal(mx, my);
        if (!guideChecked && !pinMode && !hosted) {
            guideChecked = true;
            Guide.begin(this);
        }
    }

    private boolean guideChecked;

    private static List<String> rewardLines(IQuest q, List<ItemStack> icons) {
        List<String> out = new ArrayList<String>();
        try {
            for (DBEntry<IReward> e : q.getRewards().getEntries()) {
                IReward r = e.getValue();
                if (r instanceof IRewardItemOutput) {
                    for (BigItemStack b : ((IRewardItemOutput) r).getItemOutputs()) {
                        ItemStack s = b.getBaseStack();
                        if (s == null) continue;
                        if (s.getItemDamage() == 32767) s.setItemDamage(0);
                        icons.add(s);
                        out.add(b.stackSize + "x " + s.getDisplayName());
                    }
                } else if (r instanceof RewardXP) {
                    RewardXP x = (RewardXP) r;
                    icons.add(null);
                    out.add("\u00a7a" + x.amount + (x.levels ? " XP levels" : " XP"));
                } else {
                    icons.add(null);
                    out.add(text(r.getUnlocalisedName()));
                }
            }
        } catch (Throwable ignored) {
            // reward preview is best-effort
        }
        return out;
    }

    private void drawHoverCard(UUID id, int mx, int my) {
        IQuest q = qdb().get(id);
        if (q == null) return;
        FontRenderer f = fontRendererObj;
        List<ItemStack> icons = new ArrayList<ItemStack>();
        List<String> rewards = rewardLines(q, icons);
        int shownRewards = Math.min(6, rewards.size());
        String title = name(q);
        String stateText = stateLabel(state(id)) + (Theme.favourites.contains(id) ? "  \u00a76Favourite" : "");
        int w = Math.max(f.getStringWidth(title), f.getStringWidth(stateText));
        for (int i = 0; i < shownRewards; i++) w = Math.max(w, f.getStringWidth(rewards.get(i)) + 20);
        if (rewards.size() > shownRewards) w = Math.max(w, 60);
        w += 12;
        int h = 8 + 10 + 10 + (rewards.isEmpty() ? 0 : 14 + shownRewards * 18 + (rewards.size() > shownRewards ? 10 : 0)) + 4;
        int x = mx + 12, y = my - 4;
        if (x + w > width - 4) x = mx - w - 8;
        if (y + h > height - 4) y = height - 4 - h;
        if (y < 4) y = 4;
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A4661);
        drawRect(x, y, x + w, y + h, 0xF0141926);
        f.drawStringWithShadow(title, x + 6, y + 5, 0xFFFFFF);
        f.drawString(stateText, x + 6, y + 16, 0xFFFFFF);
        int cy = y + 30;
        if (!rewards.isEmpty()) {
            f.drawStringWithShadow("Rewards", x + 6, cy, 0xFFE0B030);
            cy += 12;
            for (int i = 0; i < shownRewards; i++) {
                ItemStack ic = icons.get(i);
                if (ic != null) item(ic, x + 14, cy + 8, 14);
                f.drawString(rewards.get(i), x + 6 + 16, cy + 4, 0xFFC8D0E0);
                cy += 18;
            }
            if (rewards.size() > shownRewards) {
                f.drawString("+" + (rewards.size() - shownRewards) + " more", x + 6, cy, 0xFF7C8599);
            }
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static String stateLabel(EnumQuestState s) {
        switch (s) {
            case COMPLETED: return "\u00a7aCompleted";
            case UNCLAIMED: return "\u00a76Rewards ready";
            case REPEATABLE: return "\u00a7bRepeatable";
            case UNLOCKED: return "\u00a7eIn progress";
            default: return "\u00a77Locked";
        }
    }

    private void drawHeader(int mx, int my) {
        FontRenderer f = fontRendererObj;
        drawRect(0, 0, width, HEADER_H, 0xFF141926);
        drawRect(0, HEADER_H - 1, width, HEADER_H, 0xFF0C0F16);
        IQuestLine l = curLine == null ? null : ldb().get(curLine);
        String title = l == null ? "Quests" : name(l);
        f.drawStringWithShadow(f.trimStringToWidth(title, width - sideW() - 160), sideW() + 8, 5, 0xFFFFFF);
        if (l != null) {
            String d = text(l.getProperty(NativeProps.DESC));
            if (!d.isEmpty()) {
                f.drawString(f.trimStringToWidth(d.replace("\n", " "), width - sideW() - 160), sideW() + 8, 15, 0xFF8C96AA);
            }
        }
        String all = totalDone + "/" + totalAll;
        int bw = 90;
        int bx = width - bw - 8;
        f.drawString(all, bx + bw - f.getStringWidth(all), 4, 0xFFAAB4C8);
        drawRect(bx, 16, bx + bw, 21, 0xFF0C0F16);
        if (totalAll > 0) drawRect(bx + 1, 17, bx + 1 + (bw - 2) * totalDone / totalAll, 20, ACCENT);

        int[] sr = searchBtnRect(), fr = favBtnRect();
        boolean hs = in(sr, mx, my), hf = in(fr, mx, my);
        float as = hover("hdr-search", hs || (searchOpen && !favOnly)), af = hover("hdr-fav", hf || (searchOpen && favOnly));
        drawRect(sr[0], sr[1], sr[2], sr[3], mix(0xFF1D2433, 0xFF3A4A6A, as));
        drawRect(fr[0], fr[1], fr[2], fr[3], mix(0xFF1D2433, 0xFF3A4A6A, af));
        double mcx = (sr[0] + sr[2]) / 2.0 - 1, mcy = (sr[1] + sr[3]) / 2.0 - 1;
        ring(mcx, mcy, 4, 1.4f, 0xFFE6EAF0);
        line(mcx + 3, mcy + 3, mcx + 7, mcy + 7, 1.6f, 0xFFE6EAF0);
        star((fr[0] + fr[2]) / 2.0, (fr[1] + fr[3]) / 2.0 + 0.5, 6, !Theme.favourites.isEmpty(), GOLD);
    }

    private int[] favBtnRect() { return new int[] { width - 128, 4, width - 110, 22 }; }

    private int[] searchBtnRect() { return new int[] { width - 150, 4, width - 132, 22 }; }

    // ---------------------------------------------------------------- search + favourites

    private static final int RES_ROW = 22, RES_MAX = 8;

    private int[] panelRect() {
        int w = Math.min(250, width - sideW() - 20);
        int rows = Math.max(1, Math.min(RES_MAX, results.size()));
        return new int[] { width - 8 - w, HEADER_H + 2, width - 8, HEADER_H + 2 + 32 + rows * RES_ROW + 4 };
    }

    private void buildIndex() {
        searchIndex = new HashMap<UUID, String>();
        chapterOf = new HashMap<UUID, String>();
        IQuestDatabase qd = qdb();
        for (Map.Entry<UUID, IQuestLine> l : ldb().getOrderedEntries()) {
            String cn = name(l.getValue());
            for (UUID id : l.getValue().keySet()) {
                IQuest q = qd.get(id);
                if (q == null || searchIndex.containsKey(id)) continue;
                searchIndex.put(id, (name(q) + " " + text(q.getProperty(NativeProps.DESC)) + " " + cn).toLowerCase());
                chapterOf.put(id, cn);
            }
        }
    }

    private void rebuildResults() {
        if (searchIndex == null) buildIndex();
        results.clear();
        matches.clear();
        resScroll = 0;
        String[] terms = query.toLowerCase().trim().split("\\s+");
        boolean any = !query.trim().isEmpty();
        List<UUID> favs = new ArrayList<UUID>();
        List<UUID> rest = new ArrayList<UUID>();
        for (Map.Entry<UUID, IQuestLine> l : ldb().getOrderedEntries()) {
            for (UUID id : l.getValue().keySet()) {
                if (!shown.contains(id) || results.contains(id) || favs.contains(id) || rest.contains(id)) continue;
                boolean fav = Theme.favourites.contains(id);
                if (favOnly && !fav) continue;
                if (!any && !favOnly) continue;
                String hay = searchIndex.get(id);
                if (hay == null) continue;
                boolean ok = true;
                if (any) {
                    for (String t : terms) {
                        if (!hay.contains(t)) {
                            ok = false;
                            break;
                        }
                    }
                }
                if (!ok) continue;
                (fav ? favs : rest).add(id);
            }
        }
        results.addAll(favs);
        results.addAll(rest);
        if (results.size() > 200) results.subList(200, results.size()).clear();
        if (any) matches.addAll(results);
    }

    private void openSearch(boolean fav) {
        searchOpen = true;
        favOnly = fav;
        rebuildResults();
    }

    private void closeSearch() {
        searchOpen = false;
        matches.clear();
    }

    private void drawSearchPanel(int mx, int my) {
        FontRenderer f = fontRendererObj;
        int[] p = panelRect();
        drawRect(p[0] - 1, p[1] - 1, p[2] + 1, p[3] + 1, 0xFF3A4661);
        drawRect(p[0], p[1], p[2], p[3], 0xF2141926);
        int fx = p[0] + 6, fw = p[2] - p[0] - 12;
        drawRect(fx, p[1] + 6, fx + fw, p[1] + 24, 0xFFFFFFFF);
        drawRect(fx + 1, p[1] + 7, fx + fw - 1, p[1] + 23, 0xFF0E1118);
        String shownQ = query + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        if (query.isEmpty()) {
            f.drawString((favOnly ? "Filter favourites..." : "Search quests...") + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : ""),
                    fx + 4, p[1] + 11, 0xFF5A6478);
        } else {
            f.drawString(f.trimStringToWidth(shownQ, fw - 8, true), fx + 4, p[1] + 11, 0xFFFFFF);
        }
        int y = p[1] + 32;
        if (results.isEmpty()) {
            String msg = favOnly ? "No favourites yet - star a quest" : query.trim().isEmpty() ? "Type to search names and descriptions" : "No quests found";
            f.drawString(f.trimStringToWidth(msg, fw), fx + 2, y + 6, 0xFF7C8599);
            return;
        }
        int maxScroll = Math.max(0, results.size() - RES_MAX);
        resScroll = Math.max(0, Math.min(resScroll, maxScroll));
        for (int i = 0; i < RES_MAX && resScroll + i < results.size(); i++) {
            UUID id = results.get(resScroll + i);
            IQuest q = qdb().get(id);
            if (q == null) continue;
            int ry = y + i * RES_ROW;
            int[] row = new int[] { p[0] + 3, ry, p[2] - 3, ry + RES_ROW };
            float h = hover("res:" + id, in(row, mx, my));
            drawRect(row[0], row[1], row[2], row[3], withAlpha(0xFF2F6FD0, 0.35f * h));
            item(iconOf(q), p[0] + 16, ry + RES_ROW / 2.0, 14);
            f.drawStringWithShadow(f.trimStringToWidth(name(q), fw - 52), p[0] + 28, ry + 3, 0xFFFFFF);
            GL11.glPushMatrix();
            GL11.glTranslatef(p[0] + 28, ry + 13, 0);
            GL11.glScalef(0.75f, 0.75f, 1f);
            f.drawString(f.trimStringToWidth(chapterOf.get(id) == null ? "" : chapterOf.get(id), (int) ((fw - 52) / 0.75f)), 0, 0, 0xFF8C96AA);
            GL11.glPopMatrix();
            if (Theme.favourites.contains(id)) star(p[2] - 14, ry + RES_ROW / 2.0, 5, true, GOLD);
        }
        if (maxScroll > 0) {
            int th = RES_MAX * RES_ROW;
            int bh = Math.max(10, th * RES_MAX / results.size());
            int by = y + (th - bh) * resScroll / maxScroll;
            drawRect(p[2] - 4, by, p[2] - 2, by + bh, 0x80FFFFFF);
        }
    }

    private void gotoQuest(UUID id) {
        IQuest q = qdb().get(id);
        if (q == null) return;
        for (Map.Entry<UUID, IQuestLine> l : ldb().getOrderedEntries()) {
            IQuestLineEntry en = l.getValue().get(id);
            if (en != null) {
                curLine = l.getKey();
                double[] v = view();
                v[0] = en.getPosX() + en.getSizeX() / 2.0;
                v[1] = en.getPosY() + en.getSizeY() / 2.0;
                v[2] = Math.max(v[2], 0.9);
                break;
            }
        }
        closeSearch();
        Theme.click();
        showModal(id, q);
    }

    private void clickSearchPanel(int mx, int my) {
        int[] p = panelRect();
        int y = p[1] + 32;
        for (int i = 0; i < RES_MAX && resScroll + i < results.size(); i++) {
            int ry = y + i * RES_ROW;
            if (my >= ry && my < ry + RES_ROW) {
                gotoQuest(results.get(resScroll + i));
                return;
            }
        }
    }

    private void drawMap(int mx, int my) {
        if (curLine == null) return;
        IQuestLine line = ldb().get(curLine);
        if (line == null) return;
        double[] v = view();
        scissor(mapLeft(), mapTop(), mapW(), mapH());

        // dependency lines
        for (Map.Entry<UUID, IQuestLineEntry> e : line.entrySet()) {
            if (!shown.contains(e.getKey())) continue;
            IQuest q = qdb().get(e.getKey());
            if (q == null) continue;
            IQuestLineEntry a = e.getValue();
            for (UUID req : q.getRequirements()) {
                IQuestLineEntry b = line.get(req);
                if (b == null || !shown.contains(req)) continue;
                boolean ok = done(state(req));
                boolean reqd = Theme.requiredHighlight && Boolean.TRUE.equals(q.getProperty(NativeProps.MAIN));
                double x1 = sx(b.getPosX() + b.getSizeX() / 2.0, v), y1 = sy(b.getPosY() + b.getSizeY() / 2.0, v);
                double x2 = sx(a.getPosX() + a.getSizeX() / 2.0, v), y2 = sy(a.getPosY() + a.getSizeY() / 2.0, v);
                int lineCol = reqd ? (ok ? 0xFFC9A227 : 0xFF8F7620) : ok ? 0xFF2E9A3C : 0xFF5A5F66;
                if (Theme.betterQuestingTheme) {
                    EnumQuestState qs = state(e.getKey());
                    lineCol = done(qs) ? 0xFF00C800 : qs == EnumQuestState.LOCKED ? 0xFFC00000 : 0xFFE8D000;
                }
                line(x1, y1, x2, y2, (float) Math.max(1.5, 2.5 * v[2]), lineCol);
            }
        }

        boolean filtering = searchOpen && !query.trim().isEmpty();
        float pulse = pulse();
        UUID hover = nodeAt(mx, my);
        for (Map.Entry<UUID, IQuestLineEntry> e : line.entrySet()) {
            UUID id = e.getKey();
            if (!shown.contains(id)) continue;
            IQuest q = qdb().get(id);
            if (q == null) continue;
            IQuestLineEntry en = e.getValue();
            double cx = sx(en.getPosX() + en.getSizeX() / 2.0, v), cy = sy(en.getPosY() + en.getSizeY() / 2.0, v);
            float hv = hover(id, id.equals(hover));
            double base = Math.max(en.getSizeX(), en.getSizeY()) * v[2];
            double s = base * (1 + 0.1 * hv);
            if (cx + s < mapLeft() || cx - s > width || cy + s < mapTop() || cy - s > height) continue;
            boolean main = Boolean.TRUE.equals(q.getProperty(NativeProps.MAIN));
            ResourceLocation[] t = shapeTex.get(main ? (Theme.betterQuestingTheme ? "castle" : "hexagon") : "circle");
            double x = cx - s / 2, y = cy - s / 2;
            EnumQuestState st = state(id);
            boolean locked = st == EnumQuestState.LOCKED;
            boolean unclaimed = st == EnumQuestState.UNCLAIMED;
            boolean active = st == EnumQuestState.UNLOCKED;
            int outline = locked ? 0xFF4A4F57 : unclaimed ? GOLD : done(st) ? (st == EnumQuestState.REPEATABLE ? 0xFF55FFFF : GREEN) : ACCENT;
            if (Theme.requiredHighlight && main && !unclaimed) outline = locked ? 0xFF6E5C1A : 0xFFE0B72E;
            int fill = locked ? 0xE023252A : done(st) ? 0xE01B3B22 : 0xE0272B32;

            if (unclaimed || active) {
                double gs = s + (5 + 5 * pulse) * Math.max(0.6, v[2]);
                tex(t[1], cx - gs / 2, cy - gs / 2, gs, gs, withAlpha(outline, (unclaimed ? 0.45f : 0.28f) * (0.4f + 0.6f * pulse)));
            }
            tex(t[0], x, y, s, s, fill);
            tex(t[1], x, y, s, s, outline);
            if (hv > 0) tex(t[1], x - 1, y - 1, s + 2, s + 2, withAlpha(0xFFFFFFFF, 0.5f * hv));
            item(iconOf(q), cx, cy, s * 0.68);
            if (locked) tex(t[0], x, y, s, s, 0x70000000);
            if (filtering) {
                if (matches.contains(id)) {
                    ring(cx, cy, s * 0.62, 2.2f, withAlpha(0xFFFFFFFF, 0.5f + 0.5f * pulse));
                } else {
                    tex(t[0], x, y, s, s, 0xB0101318);
                }
            }

            double br = Math.max(4.5, s * 0.2);
            double bx = x + s * 0.88, by = y + s * 0.88;
            if (locked) {
                double ls = Math.max(5, s * 0.3);
                disc(bx, by, ls * 0.5, 0xFF23262C);
                padlock(bx, by, ls * 0.72, 0xFFB0B6C2);
            } else if (unclaimed) {
                double ms = s * (0.34 + 0.03 * pulse);
                tex(MARKER_TEX, bx - ms * 0.55, by - ms * 0.7, ms, ms, 0xFFFFFFFF);
            } else if (st == EnumQuestState.COMPLETED) {
                double cs = s * 0.32;
                tex(CHECK_TEX, bx - cs * 0.5, by - cs * 0.6, cs, cs, 0xFFFFFFFF);
            } else if (st == EnumQuestState.REPEATABLE) {
                disc(bx, by, br, 0xFF000000);
                disc(bx, by, br - 1, 0xFF1B8C9A);
                check(bx - br * 0.55, by - br * 0.6, br * 1.15, 0xFFFFFFFF);
            }
            if (Theme.favourites.contains(id)) {
                double sr = Math.max(4, s * 0.17);
                star(x + s * 0.88, y + s * 0.12, sr + 1, true, 0xFF000000);
                star(x + s * 0.88, y + s * 0.12, sr, true, GOLD);
            }
        }
        unscissor();
    }

    /** {thumbY, thumbY, thumbHeight, trackHeight, maxScroll} */
    private int[] scrollGeom() {
        int listTop = HEADER_H, listBottom = height - FOOT_H;
        int max = Math.max(1, sideRows() * ROW_H - (listBottom - listTop - 4));
        int th = listBottom - listTop - 4;
        int bh = Math.max(14, th * th / (th + max));
        int by = listTop + 2 + (th - bh) * sideScroll / max;
        return new int[] { by, by, bh, th, max };
    }

    private void dragScrollTo(int my) {
        int[] g = scrollGeom();
        int free = g[3] - g[2];
        if (free <= 0) return;
        int pos = my - scrollGrab - (HEADER_H + 2);
        sideScroll = Math.max(0, Math.min(g[4], pos * g[4] / free));
    }

    private int sideRows() {
        return sideLines().size();
    }

    /** Sidebar chapters: favourites first, then the rest; chapters without visible quests are dropped when the setting is on. */
    private List<Map.Entry<UUID, IQuestLine>> sideLines() {
        List<Map.Entry<UUID, IQuestLine>> favs = new ArrayList<Map.Entry<UUID, IQuestLine>>();
        List<Map.Entry<UUID, IQuestLine>> rest = new ArrayList<Map.Entry<UUID, IQuestLine>>();
        for (Map.Entry<UUID, IQuestLine> l : ldb().getOrderedEntries()) {
            UUID id = l.getKey();
            if (Theme.hideUseless && !id.equals(curLine)) {
                int[] st = stats.get(id);
                if (st != null && st[1] == 0) continue;
            }
            (Theme.favLines.contains(id) ? favs : rest).add(l);
        }
        favs.addAll(rest);
        return favs;
    }

    private void drawSidebar(int mx, int my) {
        FontRenderer f = fontRendererObj;
        if (Theme.sideFolded) {
            drawRect(0, 0, SIDE_MIN, height, 0xE61A2030);
            drawRect(SIDE_MIN - 1, 0, SIDE_MIN, height, 0xFF0C0F16);
            drawRect(0, 0, SIDE_MIN - 1, HEADER_H, 0xFF141926);
            drawRect(0, HEADER_H - 1, SIDE_MIN - 1, HEADER_H, 0xFF0C0F16);
            f.drawStringWithShadow("\u00bb", 4, 9, in(sideToggleRect(), mx, my) ? 0xFFFFFF : 0x8C96AA);
            return;
        }
        drawRect(0, 0, SIDE_W, height, 0xE61A2030);
        drawRect(SIDE_W - 1, 0, SIDE_W, height, 0xFF0C0F16);

        int listTop = HEADER_H, listBottom = height - FOOT_H;
        int maxScroll = Math.max(0, sideRows() * ROW_H - (listBottom - listTop - 4));
        sideScroll = Math.max(0, Math.min(sideScroll, maxScroll));

        scissor(0, listTop, SIDE_W - 1, listBottom - listTop);
        int gut = maxScroll > 0 ? 9 : 0;
        int rowR = SIDE_W - 1 - gut;
        int y = listTop + 2 - sideScroll;
        for (Map.Entry<UUID, IQuestLine> l : sideLines()) {
            if (y + ROW_H >= listTop && y <= listBottom) {
                boolean sel = l.getKey().equals(curLine);
                boolean hov = mx >= 0 && mx < rowR && my >= Math.max(y, listTop) && my < Math.min(y + ROW_H, listBottom);
                if (sel) drawRect(0, y, rowR, y + ROW_H, 0x402F6FD0);
                else {
                    float ha = hover("row" + l.getKey(), hov);
                    if (ha > 0) drawRect(0, y, rowR, y + ROW_H, withAlpha(0x30FFFFFF, ha));
                }
                if (sel) drawRect(0, y, 2, y + ROW_H, ACCENT);
                item(iconOf(l.getValue()), 14, y + ROW_H / 2.0, 14);
                int[] st = stats.get(l.getKey());
                boolean all = st != null && st[1] > 0 && st[0] == st[1];
                int color = sel ? 0x55FFFF : all ? 0x55FF55 : 0xFFFFFF;
                GL11.glPushMatrix();
                GL11.glTranslatef(26, y + (st != null && st[1] > 0 ? 7 : 10), 0);
                GL11.glScalef(0.8f, 0.8f, 1f);
                f.drawStringWithShadow(f.trimStringToWidth(name(l.getValue()), (int) ((SIDE_W - 42 - gut) / 0.8f)), 0, 0, color);
                GL11.glPopMatrix();
                if (Theme.favLines.contains(l.getKey())) star(rowR - 7, y + ROW_H - 6, 4, true, GOLD);
                if (st != null && st[1] > 0) {
                    int bx = 26, bw = SIDE_W - 40 - gut;
                    drawRect(bx, y + 17, bx + bw, y + 21, 0xFF0C0F16);
                    drawRect(bx + 1, y + 18, bx + 1 + (bw - 2) * st[0] / st[1], y + 20, all ? GREEN : ACCENT);
                    if (st[2] > 0) drawRect(rowR - 8, y + 4, rowR - 3, y + 9, GOLD);
                }
            }
            y += ROW_H;
        }
        unscissor();

        if (maxScroll > 0) {
            int[] g = scrollGeom();
            boolean hot = scrollDrag || (mx >= SIDE_W - 9 && mx < SIDE_W - 1 && my >= g[1] && my < g[1] + g[2]);
            drawRect(SIDE_W - 6, listTop + 2, SIDE_W - 2, listTop + 2 + g[3], 0x40FFFFFF);
            drawRect(SIDE_W - 6, g[0], SIDE_W - 2, g[0] + g[2], hot ? 0xFFFFFFFF : 0xFFAAB4C8);
        }

        drawRect(0, 0, SIDE_W - 1, HEADER_H, 0xFF141926);
        drawRect(0, HEADER_H - 1, SIDE_W - 1, HEADER_H, 0xFF0C0F16);
        f.drawStringWithShadow("Quests", 8, 9, 0xFFFFFF);
        int[] tg = sideToggleRect();
        f.drawStringWithShadow("\u00ab", tg[0] + 5, 9, in(tg, mx, my) ? 0xFFFFFF : 0x8C96AA);

        drawRect(0, height - FOOT_H, SIDE_W - 1, height, 0xFF141926);
        drawRect(0, height - FOOT_H, SIDE_W - 1, height - FOOT_H + 1, 0xFF0C0F16);
        int[] wr = wheelRect();
        drawRect(wr[0], wr[1], wr[2], wr[3], in(wr, mx, my) ? 0xFFFFFFFF : 0xFF0C0F16);
        tex(SETTINGS_TEX, wr[0] + 3, wr[1] + 3, wr[2] - wr[0] - 6, wr[3] - wr[1] - 6, 0xFFFFFFFF);
        button(partyRect(), "Party", true, 0xFF2F6FD0, mx, my);
        button(classicRect(), "Classic", true, 0xFF3A4660, mx, my);
    }

    public static void drawRect(int a, int b, int c, int d, int col) {
        net.minecraft.client.gui.Gui.drawRect(a, b, c, d, Theme.t(col));
    }

    private int[] wheelRect() { return new int[] { 4, height - FOOT_H + 3, 4 + (FOOT_H - 6), height - 3 }; }

    int[] guideTargetScreen() {
        if (Guide.step() == Guide.PIN_FOLD) return PinHud.guideFoldTarget(mc);
        if (Guide.step() == Guide.FAVORITE_QUEST && modalId != null && modalId.equals(Theme.pinned)) {
            return PinHud.guideFavoriteTarget(mc);
        }
        int[] r = guideTargetLocal();
        if (r == null || !windowed()) return r;
        float k = winK();
        return new int[] {
                ox() + Math.round(r[0] * k), oy() + Math.round(r[1] * k),
                ox() + Math.round(r[2] * k), oy() + Math.round(r[3] * k)
        };
    }

    private int[] guideTargetLocal() {
        switch (Guide.step()) {
            case Guide.OPEN_SETTINGS:
                return wheelRect();
            case Guide.SELECT_CATEGORY:
                return guideCategoryRect(null);
            case Guide.FAVORITE_CATEGORY:
            case Guide.UNFAVORITE_CATEGORY:
                return guideCategoryRect(curLine);
            case Guide.SELECT_QUEST:
                return guideQuestRect();
            case Guide.PIN_QUEST:
                return modalQuest == null ? null : pinRect();
            case Guide.FAVORITE_QUEST:
                return modalQuest == null ? null : favRect();
            case Guide.SEARCH:
                return searchBtnRect();
            case Guide.SEARCH_INPUT:
                if (!searchOpen) return null;
                int[] p = panelRect();
                return new int[] { p[0] + 6, p[1] + 6, p[2] - 6, p[1] + 24 };
            case Guide.FAVORITES_FILTER:
                return favBtnRect();
            case Guide.PARTY:
                return Theme.sideFolded ? null : partyRect();
            default:
                return null;
        }
    }

    private int[] guideCategoryRect(UUID id) {
        if (Theme.sideFolded) return null;
        List<Map.Entry<UUID, IQuestLine>> lines = sideLines();
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (id == null || lines.get(i).getKey().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) return null;
        int top = HEADER_H + 2 + index * ROW_H - sideScroll;
        int bottom = height - FOOT_H;
        if (top >= bottom || top + ROW_H <= HEADER_H) return null;
        int maxScroll = Math.max(0, sideRows() * ROW_H - (bottom - HEADER_H - 4));
        int rowRight = SIDE_W - 1 - (maxScroll > 0 ? 9 : 0);
        return new int[] { 0, Math.max(HEADER_H, top), rowRight, Math.min(bottom, top + ROW_H) };
    }

    private int[] guideQuestRect() {
        if (curLine == null) return null;
        IQuestLine line = ldb().get(curLine);
        if (line == null) return null;
        if (guideQuestId == null || !curLine.equals(guideQuestLine)) {
            guideQuestId = null;
            guideQuestLine = curLine;
            for (Map.Entry<UUID, IQuestLineEntry> e : line.entrySet()) {
                if (!shown.contains(e.getKey()) || qdb().get(e.getKey()) == null) continue;
                guideQuestId = e.getKey();
                IQuestLineEntry entry = e.getValue();
                double[] v = view();
                v[0] = entry.getPosX() + entry.getSizeX() / 2.0;
                v[1] = entry.getPosY() + entry.getSizeY() / 2.0;
                v[2] = Math.max(0.7, Math.min(1.2, v[2]));
                break;
            }
        }
        IQuestLineEntry entry = guideQuestId == null ? null : line.get(guideQuestId);
        if (entry == null) return null;
        double[] v = view();
        double cx = sx(entry.getPosX() + entry.getSizeX() / 2.0, v);
        double cy = sy(entry.getPosY() + entry.getSizeY() / 2.0, v);
        return new int[] {
                (int) Math.floor(cx - entry.getSizeX() * v[2] / 2.0) - 2,
                (int) Math.floor(cy - entry.getSizeY() * v[2] / 2.0) - 2,
                (int) Math.ceil(cx + entry.getSizeX() * v[2] / 2.0) + 2,
                (int) Math.ceil(cy + entry.getSizeY() * v[2] / 2.0) + 2
        };
    }

    void guideCloseQuest() {
        closeModal();
        searchOpen = false;
    }

    void prepareGuideCategories() {
        sideScroll = 0;
    }

    private int[] partyRect() { return new int[] { 4 + FOOT_H - 6 + 3, height - FOOT_H + 3, 84, height - 3 }; }

    private int[] classicRect() { return new int[] { 87, height - FOOT_H + 3, SIDE_W - 5, height - 3 }; }

    // ---------------------------------------------------------------- quest window

    private boolean folded() { return pinMode && Theme.pinFolded; }

    private int mw() { return folded() ? PIN_FOLD_W : Math.min(470, width - 20); }

    private int mh() { return Math.min(320, height - 20); }

    private int m0x() { return (width - mw()) / 2; }

    private int m0y() { return (height - mh()) / 2; }

    private int bodyTop() { return m0y() + 30; }

    private int bodyBottom() { return m0y() + mh() - FOOT_H - 4; }

    private int leftW() { return folded() ? 0 : (mw() - 24) * 2 / 5; }

    private int rightX() { return m0x() + 12 + leftW() + (folded() ? 0 : 8); }

    private int rightW() { return m0x() + mw() - 10 - rightX(); }

    private static final int REW_ROW = 18;

    private int rewardAreaH() {
        return Math.min(Math.max(1, rewText.size()) * REW_ROW, (bodyBottom() - bodyTop()) * 40 / 100);
    }

    private int rewardTop() { return bodyBottom() - rewardAreaH(); }

    private int descH() { return rewardTop() - 14 - bodyTop(); }

    private static final PresetColor[] TEXT_KEYS = {
        PresetColor.TEXT_HEADER, PresetColor.TEXT_MAIN, PresetColor.TEXT_AUX_0, PresetColor.TEXT_AUX_1, PresetColor.TEXT_HIGHLIGHT
    };
    private static final int[] TEXT_LIGHT = { 0xFFFFFFFF, 0xFFE6EAF2, 0xFFB8C0D0, 0xFF9FA9BC, 0xFFFFD866 };

    /** Native BQ panels use dark text for BQ's light theme; swap in light colours while they build/draw. */
    private java.util.Map<PresetColor, IGuiColor> lightText() {
        java.util.Map<PresetColor, IGuiColor> saved = new java.util.HashMap<PresetColor, IGuiColor>();
        try {
            IGuiTheme th = ThemeRegistry.INSTANCE.getCurrentTheme();
            if (th instanceof ResourceTheme) {
                for (int i = 0; i < TEXT_KEYS.length; i++) {
                    saved.put(TEXT_KEYS[i], th.getColor(TEXT_KEYS[i].getKey()));
                    ((ResourceTheme) th).setColor(TEXT_KEYS[i].getKey(), new GuiColorStatic(TEXT_LIGHT[i]));
                }
            }
        } catch (Throwable ignored) {
            // text stays in the theme's own colours
        }
        return saved;
    }

    private void restoreText(java.util.Map<PresetColor, IGuiColor> saved) {
        try {
            IGuiTheme th = ThemeRegistry.INSTANCE.getCurrentTheme();
            if (th instanceof ResourceTheme) {
                for (java.util.Map.Entry<PresetColor, IGuiColor> e : saved.entrySet()) {
                    ((ResourceTheme) th).setColor(e.getKey().getKey(), e.getValue());
                }
            }
        } catch (Throwable ignored) {
            // nothing to restore
        }
    }

    private int[] closeRect() { return new int[] { m0x() + mw() - 20, m0y() + 6, m0x() + mw() - 6, m0y() + 20 }; }

    private int[] favRect() { return new int[] { m0x() + mw() - 40, m0y() + 6, m0x() + mw() - 22, m0y() + 22 }; }

    private int[] pinRect() { return new int[] { m0x() + mw() - 62, m0y() + 6, m0x() + mw() - 44, m0y() + 22 }; }

    private void pinIcon(int[] r, boolean on, float hv) {
        double cx = (r[0] + r[2]) / 2.0, cy = (r[1] + r[3]) / 2.0;
        int col = on ? 0xFFE05555 : mix(0xFF8C96AA, 0xFFFFFFFF, hv);
        disc(cx, cy - 2.5, 3.6, 0xFF000000);
        disc(cx, cy - 2.5, 2.6, col);
        line(cx - 3.2, cy + 0.8, cx + 3.2, cy + 0.8, 1.6f, col);
        line(cx, cy + 0.8, cx, cy + 6, 1.4f, col);
    }

    private int[] foldRect() { return new int[] { m0x() + mw() - 106, m0y() + 6, m0x() + mw() - 88, m0y() + 22 }; }

    int[] pinFoldScreenRect(int px, int py, float k) {
        return pinScreenRect(foldRect(), px, py, k);
    }

    int[] pinFavoriteScreenRect(int px, int py, float k) {
        return pinScreenRect(favRect(), px, py, k);
    }

    private int[] pinScreenRect(int[] r, int px, int py, float k) {
        return new int[] {
                px + Math.round((r[0] - m0x()) * k),
                py + Math.round((r[1] - m0y()) * k),
                px + Math.round((r[2] - m0x()) * k),
                py + Math.round((r[3] - m0y()) * k)
        };
    }

    private int[] shareRect() { return new int[] { m0x() + mw() - 84, m0y() + 6, m0x() + mw() - 66, m0y() + 22 }; }

    private void shareIcon(int[] r, boolean flash, float hv) {
        double cx = (r[0] + r[2]) / 2.0, cy = (r[1] + r[3]) / 2.0;
        int col = flash ? 0xFF55DD66 : mix(0xFF8C96AA, 0xFFFFFFFF, hv);
        flat();
        drawRect((int) cx - 5, (int) cy - 4, (int) cx + 5, (int) cy + 3, 0xFF000000);
        drawRect((int) cx - 4, (int) cy - 3, (int) cx + 4, (int) cy + 2, col);
        drawRect((int) cx - 3, (int) cy + 3, (int) cx - 1, (int) cy + 5, col);
        drawRect((int) cx - 3, (int) cy - 1, (int) cx + 3, (int) cy, 0xFF161B26);
    }

    private int[] claimRect() { return new int[] { m0x() + mw() - 110, m0y() + mh() - FOOT_H + 2, m0x() + mw() - 10, m0y() + mh() - 6 }; }

    private int[] checkRect() { return new int[] { m0x() + mw() - 190, m0y() + mh() - FOOT_H + 2, m0x() + mw() - 116, m0y() + mh() - 6 }; }

    private int[] classicQuestRect() { return new int[] { m0x() + 10, m0y() + mh() - FOOT_H + 2, m0x() + 100, m0y() + mh() - 6 }; }

    private static final int TASK_HEAD = 14;

    private int[] taskH = new int[0];

    private int taskAreaH() { return bodyBottom() - bodyTop(); }

    private int taskPanelH(int i) { return i < taskH.length ? taskH[i] : 40; }

    private int taskOff(int i) {
        int y = 0;
        for (int k = 0; k < i; k++) y += TASK_HEAD + taskPanelH(k) + 4;
        return y;
    }

    private int taskMax(int n) { return Math.max(0, taskOff(n) - 4 - taskAreaH()); }

    private int taskW(int n) { return rightW() - (taskMax(n) > 0 ? 7 : 0); }

    /** Estimated panel height: BQ panels don't report one, so size from the task's entry list. */
    private int estimateTaskH(ITask t, int n) {
        if (n <= 1) return taskAreaH() - TASK_HEAD - 2;
        int count = 0;
        try {
            Class<?> c = t.getClass();
            outer:
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field fl : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(fl.getModifiers())) continue;
                    fl.setAccessible(true);
                    Object o = fl.get(t);
                    if (o instanceof java.util.Collection) {
                        count = ((java.util.Collection<?>) o).size();
                        if (count > 0) break outer;
                    } else if (o instanceof java.util.Map) {
                        count = ((java.util.Map<?, ?>) o).size();
                        if (count > 0) break outer;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
            // fall back to the default size
        }
        if (count <= 0) return 48;
        String nm = t.getClass().getSimpleName();
        int extra = nm.contains("Crafting") ? 30 : 20;
        return Math.max(48, extra + count * 36);
    }

    private int taskTrackX() { return rightX() + rightW() - 4; }

    private void dragTaskTo(int my) {
        int n = modalQuest == null ? 0 : modalQuest.getTasks().size();
        int free = taskAreaH() - taskBar, max = taskMax(n);
        if (free <= 0 || max <= 0) return;
        taskScroll = Math.max(0, Math.min(max, (my - taskGrab - bodyTop()) * max / free));
    }

    private void showModal(UUID id, IQuest q) {
        modalId = id;
        modalQuest = q;
        taskScroll = 0;
        taskDrag = false;
        rewardScroll = 0;
        descScroll = 0;
        descPos = 0;
        descMax = 0;
        descDrag = false;
        buildModal();
    }

    private void closeModal() {
        modalId = null;
        modalQuest = null;
        taskCvs.clear();
        rewardCvs.clear();
    }

    private void buildModal() {
        lastModalBuild = System.currentTimeMillis();
        builtW = width;
        builtH = height;
        builtTaskScroll = taskScroll;
        taskCvs.clear();
        rewardCvs.clear();
        modalBroken = false;
        if (modalQuest == null) return;
        Map.Entry<UUID, IQuest> entry = new java.util.AbstractMap.SimpleEntry<UUID, IQuest>(modalId, modalQuest);
        rewIcons = new ArrayList<ItemStack>();
        rewText = rewardLines(modalQuest, rewIcons);
        java.util.Map<PresetColor, IGuiColor> saved = lightText();
        try {
            List<DBEntry<ITask>> tasks = modalQuest.getTasks().getEntries();
            int n = tasks.size();
            taskH = new int[n];
            for (int i = 0; i < n; i++) taskH[i] = estimateTaskH(tasks.get(i).getValue(), n);
            taskScroll = Math.max(0, Math.min(taskScroll, taskMax(n)));
            builtTaskScroll = taskScroll;
            for (int i = 0; i < n; i++) {
                int ty = bodyTop() + taskOff(i) + TASK_HEAD - taskScroll;
                CanvasEmpty cv = new CanvasEmpty(new GuiRectangle(rightX(), ty, taskW(n), taskPanelH(i)));
                IGuiPanel p = tasks.get(i).getValue().getTaskGui(
                        new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), entry);
                cv.addPanel(p != null ? p : new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0)));
                taskCvs.add(cv);
            }
        } catch (Throwable t) {
            BQModern.LOG.error("Failed to build quest panels", t);
            modalBroken = true;
        } finally {
            restoreText(saved);
        }
    }

    /** Draws a description line; text between the note markers (\u00a7d ... \u00a7r) uses the themed note colour. */
    private void drawNoteLine(FontRenderer f, String s, int x, int y) {
        int normal = 0xFFC8D0E0, note = Theme.t(0xFF7FC8FF);
        boolean on = false;
        StringBuilder seg = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                char k = Character.toLowerCase(s.charAt(i + 1));
                if (k == 'd' || k == 'r') {
                    if (seg.length() > 0) {
                        f.drawString(seg.toString(), x, y, on ? note : normal);
                        x += f.getStringWidth(seg.toString());
                        seg.setLength(0);
                    }
                    on = k == 'd';
                    i++;
                    continue;
                }
            }
            seg.append(c);
        }
        if (seg.length() > 0) f.drawString(seg.toString(), x, y, on ? note : normal);
    }

    private void drawModal(int mx, int my) {
        FontRenderer f = fontRendererObj;
        if (modalQuest == null) return;
        if (!Mouse.isButtonDown(0) && (System.currentTimeMillis() - lastModalBuild > 2000 || builtW != width || builtH != height)) buildModal();
        int x0 = m0x(), y0 = m0y(), w = mw(), h = mh();
        if (!pinMode) drawRect(0, 0, width, height, withAlpha(0x90000000, modalAnim));
        drawRect(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF3A4661);
        drawRect(x0, y0, x0 + w, y0 + h, 0xFA161B26);
        drawRect(x0, y0, x0 + w, y0 + 28, 0xFF1D2433);
        drawRect(x0, y0 + 28, x0 + w, y0 + 29, 0xFF3A4661);

        item(iconOf(modalQuest), x0 + 16, y0 + 14, 18);
        EnumQuestState st = state(modalId);
        f.drawStringWithShadow(f.trimStringToWidth(name(modalQuest), w - (pinMode ? 182 : 160)), x0 + 30, y0 + 6, 0xFFFFFF);
        f.drawString(stateLabel(st), x0 + 30, y0 + 17, 0xFFFFFF);
        int[] cr = closeRect();
        f.drawStringWithShadow("x", cr[0] + 4, cr[1] + 3, in(cr, mx, my) ? 0xFF5555 : 0xAAAAAA);
        int[] fr = favRect();
        boolean isFav = Theme.favourites.contains(modalId);
        float fh = hover("modal-fav", in(fr, mx, my));
        star((fr[0] + fr[2]) / 2.0, (fr[1] + fr[3]) / 2.0 + 0.5, 6 + 1.5 * fh, isFav, isFav ? GOLD : mix(0xFF8C96AA, 0xFFFFFFFF, fh));
        int[] pr = pinRect();
        pinIcon(pr, modalId.equals(Theme.pinned), hover("modal-pin", in(pr, mx, my)));
        int[] sr = shareRect();
        shareIcon(sr, System.currentTimeMillis() - sharedAt < 900, hover("modal-share", in(sr, mx, my)));
        if (pinMode) {
            int[] fo = foldRect();
            f.drawStringWithShadow(Theme.pinFolded ? "\u00bb" : "\u00ab", fo[0] + 5, fo[1] + 4, in(fo, mx, my) ? 0xFFFFFF : 0x8C96AA);
        }

        // description
        int dx = x0 + 12, dy = bodyTop(), dw = leftW(), dh = descH();
        if (folded()) {
            descMax = 0;
        } else {
        f.drawStringWithShadow("Description", dx, dy, 0xFF6FA8FF);
        String descRaw = text(modalQuest.getProperty(NativeProps.DESC))
                .replaceAll("(?i)\\[note\\]", "\u00a7d").replaceAll("(?i)\\[/note\\]", "\u00a7r");
        List<?> lines = f.listFormattedStringToWidth(descRaw, dw - 10);
        int maxDesc = Math.max(0, lines.size() * 10 - (dh - 12));
        descScroll = Math.max(0, Math.min(descScroll, maxDesc));
        descMax = maxDesc;
        if (Theme.animations && !descDrag) descPos += (descScroll - descPos) * Math.min(1f, dt * 16f);
        else descPos = descScroll;
        int pos = Math.round(descPos);
        scissor(dx, dy + 12, dw, dh - 12);
        int ly = dy + 13 - pos;
        for (Object o : lines) {
            if (ly > dy + 2 && ly < dy + dh) drawNoteLine(f, (String) o, dx, ly);
            ly += 10;
        }
        unscissor();
        if (maxDesc > 0) {
            int trackH = dh - 12;
            int bh = Math.max(14, trackH * trackH / (lines.size() * 10));
            int by = dy + 12 + (trackH - bh) * pos / maxDesc;
            descBar = bh;
            boolean hot = descDrag || (mx >= dx + dw - 8 && mx < dx + dw && my >= by && my < by + bh);
            drawRect(dx + dw - 5, dy + 12, dx + dw, dy + 12 + trackH, 0x30FFFFFF);
            drawRect(dx + dw - 5, by, dx + dw, by + bh, hot ? 0xFFFFFFFF : 0xFFAAB4C8);
        }

        // rewards: compact rows, icon + text
        f.drawStringWithShadow("Rewards", dx, rewardTop() - 12, 0xFFE0B030);
        if (rewText.isEmpty()) f.drawString("None", dx, rewardTop() + 4, 0xFF7C8599);
        int rh = rewardAreaH();
        int rewMax = Math.max(0, rewText.size() * REW_ROW - rh);
        rewardScroll = Math.max(0, Math.min(rewardScroll, rewMax));
        int rtw = dw - (rewMax > 0 ? 8 : 0);
        scissor(dx, rewardTop(), dw, rh);
        for (int i = 0; i < rewText.size(); i++) {
            int ry = rewardTop() + i * REW_ROW - rewardScroll;
            if (ry + REW_ROW < rewardTop() || ry > rewardTop() + rh) continue;
            drawRect(dx, ry, dx + rtw, ry + REW_ROW - 2, 0x30FFFFFF);
            ItemStack ic = i < rewIcons.size() ? rewIcons.get(i) : null;
            if (ic != null) item(ic, dx + 9, ry + 8, 14);
            f.drawString(f.trimStringToWidth(rewText.get(i), rtw - (ic != null ? 22 : 8)), dx + (ic != null ? 20 : 5), ry + 4, 0xFFC8D0E0);
        }
        unscissor();
        if (rewMax > 0) {
            int bh = Math.max(10, rh * rh / (rh + rewMax));
            int by = rewardTop() + (rh - bh) * rewardScroll / rewMax;
            drawRect(dx + dw - 4, rewardTop(), dx + dw, rewardTop() + rh, 0x30FFFFFF);
            drawRect(dx + dw - 4, by, dx + dw, by + bh, 0xFFAAB4C8);
        }
        }

        // tasks: stacked vertically, scrolled together
        List<DBEntry<ITask>> tasks = modalQuest.getTasks().getEntries();
        UUID uid = me();
        int nt = tasks.size();
        if (taskScroll != builtTaskScroll && !modalBroken) buildModal();
        int tTop = bodyTop(), tH = taskAreaH();
        if (nt == 0) f.drawString("No tasks", rightX(), bodyTop() + 4, 0xFF7C8599);
        for (int i = 0; i < nt; i++) {
            int hy = tTop + taskOff(i) - taskScroll;
            int pTop = hy + TASK_HEAD, pBot = pTop + taskPanelH(i);
            if (pBot < tTop || hy > tTop + tH) continue;
            ITask t = tasks.get(i).getValue();
            boolean dn = false;
            try {
                dn = t.isComplete(uid);
            } catch (Throwable ignored) {
                // leave as incomplete
            }
            scissor(rightX(), tTop, rightW(), tH);
            drawRect(rightX(), hy, rightX() + taskW(nt), hy + TASK_HEAD, 0xFF222838);
            drawRect(rightX(), hy, rightX() + 2, hy + TASK_HEAD, dn ? GREEN : 0xFF2F6FD0);
            String label = (i + 1) + ". " + text(t.getUnlocalisedName());
            f.drawStringWithShadow(f.trimStringToWidth(label, taskW(nt) - 12), rightX() + 6, hy + 3, dn ? 0x55FF55 : 0xFFFFFF);
            unscissor();
            int cTop = Math.max(pTop, tTop), cBot = Math.min(pBot, tTop + tH);
            if (cBot > cTop && i < taskCvs.size()) {
                scissor(rightX(), cTop, rightW(), cBot - cTop);
                boolean okDraw = safeDraw(taskCvs.get(i), mx, my);
                unscissor();
                if (!okDraw) break;
            }
        }
        int tMax = taskMax(nt);
        if (tMax > 0) {
            int trackH = tH;
            int bh = Math.max(14, trackH * trackH / (trackH + tMax));
            int by = tTop + (trackH - bh) * taskScroll / tMax;
            taskBar = bh;
            boolean hot = taskDrag || (mx >= taskTrackX() - 3 && mx < rightX() + rightW() && my >= by && my < by + bh);
            drawRect(taskTrackX(), tTop, taskTrackX() + 4, tTop + trackH, 0x30FFFFFF);
            drawRect(taskTrackX(), by, taskTrackX() + 4, by + bh, hot ? 0xFFFFFFFF : 0xFFAAB4C8);
        }
        if (modalBroken) {
            f.drawString("This quest needs the classic view.", rightX(), bodyTop() + 24, 0xFFFF8080);
        }

        // footer
        if (!pinMode) button(classicQuestRect(), "Classic view", true, 0xFF3A4660, mx, my);
        button(checkRect(), "Check", st != EnumQuestState.LOCKED, 0xFF2F6FD0, mx, my);
        boolean canClaim = false;
        try {
            canClaim = modalQuest.canClaim(player());
        } catch (Throwable ignored) {
            // treat as not claimable
        }
        button(claimRect(), "Claim rewards", canClaim, 0xFF2F9F55, mx, my);

        // tooltips from native panels
        try {
            List<String> tip = null;
            for (CanvasEmpty tc : taskCvs) {
                tip = tc.getTooltip(mx, my);
                if (tip != null && !tip.isEmpty()) break;
            }
            if (tip == null || tip.isEmpty()) {
                for (CanvasEmpty cv : rewardCvs) {
                    List<String> t = cv.getTooltip(mx, my);
                    if (t != null && !t.isEmpty()) {
                        tip = t;
                        break;
                    }
                }
            }
            if (tip != null && !tip.isEmpty()) {
                drawHoveringText(tip, mx, my, f);
                GL11.glDisable(GL11.GL_LIGHTING);
            }
        } catch (Throwable ignored) {
            // tooltip failure is cosmetic
        }
    }

    private boolean safeDraw(CanvasEmpty cv, int mx, int my) {
        java.util.Map<PresetColor, IGuiColor> saved = lightText();
        try {
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            cv.drawPanel(mx, my, 0f);
            GL11.glColor4f(1, 1, 1, 1);
            return true;
        } catch (Throwable t) {
            BQModern.LOG.error("Native panel failed to draw", t);
            modalBroken = true;
            taskCvs.clear();
            rewardCvs.clear();
            return false;
        } finally {
            restoreText(saved);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        if (modalQuest != null) {
            clickModal(mx, my, btn);
            return;
        }
        if (btn == 1 && !searchOpen && !Theme.sideFolded && mx >= 0 && mx < SIDE_W - 1 && my >= HEADER_H && my < height - FOOT_H) {
            int[] g = scrollGeom();
            if (!(g[4] > 1 && sideRows() * ROW_H > height - FOOT_H - HEADER_H - 4 && mx >= SIDE_W - 9)) {
                int idx = (my - HEADER_H - 2 + sideScroll) / ROW_H;
                List<Map.Entry<UUID, IQuestLine>> o = sideLines();
                if (idx >= 0 && idx < o.size()) {
                    UUID id = o.get(idx).getKey();
                    Theme.click();
                    boolean nowFavorite = Theme.toggleFavLine(id);
                    sideScroll = 0;
                    Guide.categoryToggled(id, nowFavorite);
                    return;
                }
            }
        }
        if (btn != 0) return;
        if (in(searchBtnRect(), mx, my) || in(favBtnRect(), mx, my)) {
            boolean fav = in(favBtnRect(), mx, my);
            Theme.click();
            if (searchOpen && favOnly == fav) closeSearch();
            else {
                openSearch(fav);
                Guide.searchOpened(fav);
            }
            return;
        }
        if (searchOpen) {
            if (in(panelRect(), mx, my)) {
                clickSearchPanel(mx, my);
                return;
            }
            closeSearch();
        }
        if (mx < sideW()) {
            if (in(sideToggleRect(), mx, my)) {
                Theme.click();
                Theme.sideFolded = !Theme.sideFolded;
                Theme.save();
                return;
            }
            if (Theme.sideFolded) return;
            if (in(classicRect(), mx, my)) {
                Theme.click();
                openClassic();
                return;
            }
            if (!hosted && in(wheelRect(), mx, my)) {
                Theme.click();
                Guide.settingsOpened();
                mc.displayGuiScreen(new ThemeScreen(this));
                return;
            }
            if (!hosted && in(partyRect(), mx, my)) {
                Theme.click();
                mc.displayGuiScreen(new PartyScreen(this));
                return;
            }
            if (my < HEADER_H || my >= height - FOOT_H) return;
            int[] g = scrollGeom();
            if (g[4] > 1 && sideRows() * ROW_H > height - FOOT_H - HEADER_H - 4 && mx >= SIDE_W - 9) {
                scrollDrag = true;
                if (my >= g[0] && my < g[0] + g[2]) scrollGrab = my - g[0];
                else {
                    scrollGrab = g[2] / 2;
                    dragScrollTo(my);
                }
                return;
            }
            int idx = (my - HEADER_H - 2 + sideScroll) / ROW_H;
            List<Map.Entry<UUID, IQuestLine>> o = sideLines();
            if (idx >= 0 && idx < o.size()) {
                UUID selected = o.get(idx).getKey();
                if (!selected.equals(curLine)) curLine = selected;
                guideQuestId = null;
                Guide.categorySelected(selected, Theme.favLines.contains(selected));
                Theme.click();
            }
            return;
        }
        if (my < mapTop()) return;
        dragging = true;
        dragDist = 0;
        lastMx = mx;
        lastMy = my;
    }

    private void openClassic() {
        if (hosted) return;
        Hooks.classic = true;
        mc.displayGuiScreen(new GuiHome(null));
    }

    private void dragDescTo(int my) {
        int trackH = descH() - 12;
        int free = trackH - descBar;
        if (free <= 0 || descMax <= 0) return;
        int pos = my - descGrab - (bodyTop() + 12);
        descScroll = Math.max(0, Math.min(descMax, pos * descMax / free));
    }

    private void clickModal(int mx, int my, int btn) {
        int x0 = m0x(), y0 = m0y(), w = mw(), h = mh();
        if (mx < x0 || mx >= x0 + w || my < y0 || my >= y0 + h) {
            if (btn == 0 && !pinMode) closeModal();
            return;
        }
        if (btn == 0 && descMax > 0 && !folded()) {
            int dx = m0x() + 12, dw = leftW(), top = bodyTop() + 12, trackH = descH() - 12;
            if (mx >= dx + dw - 9 && mx < dx + dw && my >= top && my < top + trackH) {
                int by = top + (trackH - descBar) * descScroll / descMax;
                descDrag = true;
                if (my >= by && my < by + descBar) descGrab = my - by;
                else {
                    descGrab = descBar / 2;
                    dragDescTo(my);
                }
                return;
            }
        }
        if (btn == 0) {
            if (in(closeRect(), mx, my)) {
                Theme.click();
                if (pinMode) PinHud.pin(null);
                else closeModal();
                return;
            }
            if (in(pinRect(), mx, my)) {
                boolean alreadyPinned = modalId.equals(Theme.pinned);
                if (Guide.step() == Guide.PIN_QUEST && alreadyPinned) {
                    Guide.questPinned();
                    return;
                }
                PinHud.pin(alreadyPinned ? null : modalId);
                Theme.click();
                if (modalId.equals(Theme.pinned)) Guide.questPinned();
                return;
            }
            if (pinMode && in(foldRect(), mx, my)) {
                Theme.pinFolded = !Theme.pinFolded;
                Theme.save();
                Theme.click();
                buildModal();
                Guide.pinFolded();
                return;
            }
            if (in(shareRect(), mx, my)) {
                Theme.click();
                sharedAt = System.currentTimeMillis();
                PinHud.share(mc, modalId);
                return;
            }
            if (in(favRect(), mx, my)) {
                Theme.toggleFavourite(modalId);
                Theme.click();
                Guide.questFavorited();
                return;
            }
            if (!pinMode && !hosted && in(classicQuestRect(), mx, my)) {
                Theme.click();
                Hooks.classic = true;
                mc.displayGuiScreen(new betterquesting.client.gui2.GuiQuest(this, modalId));
                return;
            }
            if (in(checkRect(), mx, my) && state(modalId) != EnumQuestState.LOCKED) {
                Theme.click();
                NetQuestAction.requestDetect(Collections.singletonList(modalId));
                return;
            }
            if (in(claimRect(), mx, my)) {
                boolean ok = false;
                try {
                    ok = modalQuest.canClaim(player());
                } catch (Throwable ignored) {
                    // not claimable
                }
                if (ok) {
                    Theme.click();
                    NetQuestAction.requestClaim(Collections.singletonList(modalId));
                }
                return;
            }
            if (mx >= taskTrackX() - 3 && mx < rightX() + rightW() && my >= bodyTop() && my < bodyBottom()) {
                int n = modalQuest.getTasks().size();
                if (taskMax(n) > 0) {
                    int by = bodyTop() + (taskAreaH() - taskBar) * taskScroll / taskMax(n);
                    taskDrag = true;
                    if (my >= by && my < by + taskBar) taskGrab = my - by;
                    else {
                        taskGrab = taskBar / 2;
                        dragTaskTo(my);
                    }
                    return;
                }
            }
        }
        try {
            if (my >= bodyTop() && my < bodyBottom()) {
                for (CanvasEmpty tc : taskCvs) tc.onMouseClick(mx, my, btn);
            }
            for (CanvasEmpty cv : rewardCvs) cv.onMouseClick(mx, my, btn);
        } catch (Throwable t) {
            BQModern.LOG.error("Native panel click failed", t);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (descDrag) {
            dragDescTo(my);
            return;
        }
        if (taskDrag) {
            dragTaskTo(my);
            return;
        }
        if (scrollDrag) {
            dragScrollTo(my);
            return;
        }
        if (!dragging || curLine == null || modalQuest != null) return;
        double[] v = view();
        v[0] -= (mx - lastMx) / v[2];
        v[1] -= (my - lastMy) / v[2];
        dragDist += Math.abs(mx - lastMx) + Math.abs(my - lastMy);
        lastMx = mx;
        lastMy = my;
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int btn) {
        if (descDrag && btn == 0) {
            descDrag = false;
            return;
        }
        if (taskDrag && btn == 0) {
            taskDrag = false;
            return;
        }
        if (modalQuest != null) {
            if (btn >= 0) {
                try {
                    for (CanvasEmpty tc : taskCvs) tc.onMouseRelease(mx, my, btn);
                    for (CanvasEmpty cv : rewardCvs) cv.onMouseRelease(mx, my, btn);
                } catch (Throwable t) {
                    BQModern.LOG.error("Native panel release failed", t);
                }
            }
            return;
        }
        if (scrollDrag && btn == 0) {
            scrollDrag = false;
            return;
        }
        if (dragging && btn == 0) {
            dragging = false;
            if (dragDist < 4) {
                UUID id = nodeAt(mx, my);
                if (id != null) {
                    IQuest q = qdb().get(id);
                    if (q != null) {
                        Theme.click();
                        showModal(id, q);
                        Guide.questSelected(id);
                    }
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        applyWindow();
        boolean tutorialTargetClick = false;
        if (!pinMode && Guide.active() && Mouse.getEventButtonState()) {
            int rx = Mouse.getEventX() * realW / mc.displayWidth;
            int ry = realH - Mouse.getEventY() * realH / mc.displayHeight - 1;
            int[] target = guideTargetScreen();
            if (Guide.click(fontRendererObj, realW, realH, rx, ry, Mouse.getEventButton(), target)) return;
            tutorialTargetClick = in(target, rx, ry);
        }
        boolean pinTarget = Guide.step() == Guide.PIN_FOLD
                || Guide.step() == Guide.FAVORITE_QUEST && modalId != null && modalId.equals(Theme.pinned);
        if ((!tutorialTargetClick || pinTarget) && PinHud.onMouse(mc)) return;
        mouseInput();
    }

    void mouseInput() {
        applyWindow();
        int rx = Mouse.getEventX() * realW / mc.displayWidth;
        int ry = realH - Mouse.getEventY() * realH / mc.displayHeight - 1;
        int btn = Mouse.getEventButton();
        float k = windowed() ? winK() : 1f;
        int mx = (int) Math.floor((rx - ox()) / k), my = (int) Math.floor((ry - oy()) / k);
        if (Mouse.getEventButtonState()) {
            heldBtn = btn;
            if (windowed() && windowPress(rx, ry, btn)) return;
            mouseClicked(mx, my, btn);
        } else if (btn != -1) {
            heldBtn = -1;
            if (winMode != 0) {
                winMode = 0;
                Theme.save();
                return;
            }
            mouseMovedOrUp(mx, my, btn);
        } else if (heldBtn != -1) {
            if (winMode != 0) windowDrag(rx, ry);
            else mouseClickMove(mx, my, heldBtn, 0L);
        }
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        if (windowed() && (mx < 0 || my < 0 || mx >= width || my >= height)) return;
        handleWheel(d, mx, my);
    }

    private void handleWheel(int d, int mx, int my) {
        if (!pinMode && Guide.active()) return;
        if (modalQuest != null) {
            boolean used = false;
            try {
                int sg = d > 0 ? 1 : -1;
                boolean inTasks = mx >= rightX() && my >= bodyTop() && my < bodyBottom();
                if (inTasks) {
                    for (CanvasEmpty tc : taskCvs) {
                        if (tc.onMouseScroll(mx, my, sg)) {
                            used = true;
                            break;
                        }
                    }
                }
                if (!used && inTasks) {
                    taskScroll = Math.max(0, taskScroll + (d > 0 ? -24 : 24));
                    used = true;
                }
                if (!used) {
                    for (CanvasEmpty cv : rewardCvs) {
                        if (cv.onMouseScroll(mx, my, sg)) {
                            used = true;
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                BQModern.LOG.error("Native panel scroll failed", t);
            }
            if (!used) {
                int step = d > 0 ? -20 : 20;
                if (mx >= m0x() + 12 && mx < m0x() + 12 + leftW()) {
                    if (my < rewardTop() - 12) descScroll += step;
                    else rewardScroll = Math.max(0, rewardScroll + step);
                }
            }
            return;
        }
        if (searchOpen && in(panelRect(), mx, my)) {
            resScroll += d > 0 ? -1 : 1;
            return;
        }
        if (mx < sideW()) {
            sideScroll -= d > 0 ? ROW_H : -ROW_H;
        } else if (curLine != null) {
            double[] v = view();
            double wx = v[0] + (mx - (mapLeft() + mapW() / 2.0)) / v[2];
            double wy = v[1] + (my - (mapTop() + mapH() / 2.0)) / v[2];
            v[2] = Math.max(0.25, Math.min(4, v[2] * (d > 0 ? 1.15 : 1 / 1.15)));
            v[0] = wx - (mx - (mapLeft() + mapW() / 2.0)) / v[2];
            v[1] = wy - (my - (mapTop() + mapH() / 2.0)) / v[2];
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (!pinMode && Guide.active()) {
            if (key == 1) {
                Guide.escape();
                return;
            }
            if (!Guide.acceptsKeyboard()) return;
        }
        if (modalQuest != null) {
            if (key == 1) {
                closeModal();
                return;
            }
            try {
                for (CanvasEmpty tc : taskCvs) {
                    if (tc.onKeyTyped(c, key)) return;
                }
            } catch (Throwable t) {
                BQModern.LOG.error("Native panel key failed", t);
            }
            return;
        }
        if (key == 33 && isCtrlKeyDown()) {
            openSearch(false);
            return;
        }
        if (searchOpen) {
            if (key == 1) {
                closeSearch();
            } else if (key == 28 || key == 156) {
                if (!results.isEmpty()) gotoQuest(results.get(0));
            } else if (key == 14) {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length() - 1);
                    rebuildResults();
                }
            } else if (ChatAllowedCharacters.isAllowedCharacter(c) && query.length() < 40) {
                query += c;
                rebuildResults();
            }
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
