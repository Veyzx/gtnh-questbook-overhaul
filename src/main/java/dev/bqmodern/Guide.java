package dev.bqmodern;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** First-time tutorial shown once per world and player. */
public final class Guide {
    public static final int WELCOME = 1;
    public static final int OPEN_SETTINGS = 2;
    public static final int COLOR = 3;
    public static final int OPACITY = 4;
    public static final int HIDE_CATEGORIES = 5;
    public static final int SELECT_CATEGORY = 6;
    public static final int FAVORITE_CATEGORY = 7;
    public static final int UNFAVORITE_CATEGORY = 8;
    public static final int SELECT_QUEST = 9;
    public static final int PIN_QUEST = 10;
    public static final int PIN_INFO = 11;
    public static final int PIN_FOLD = 12;
    public static final int FAVORITE_QUEST = 13;
    public static final int SEARCH = 14;
    public static final int SEARCH_INPUT = 15;
    public static final int FAVORITES_FILTER = 16;
    public static final int PARTY = 17;
    public static final int FINISH = 18;

    private static final int W = 270, YELLOW = 0xFFFFC832;
    private static final int BTN_W = 70, BTN_H = 18;
    private static int step;
    private static boolean confirmSkip;
    private static boolean closeSettingsRequested;
    private static String key;
    private static UUID categoryId;
    private static boolean categoryFavorite;
    private static boolean categoryFavAdded;
    private static boolean categoryFavRemoved;
    private static ModernScreen mainScreen;

    private Guide() {}

    public static void begin(ModernScreen screen) {
        mainScreen = screen;
        key = currentKey();
        step = 0;
        confirmSkip = false;
        closeSettingsRequested = false;
        if (key == null || Theme.guideDone.contains(key)) return;
        step = WELCOME;
    }

    public static void startAgain(ModernScreen screen) {
        mainScreen = screen;
        key = currentKey();
        if (key != null) Theme.guideDone.remove(key);
        Theme.save();
        step = WELCOME;
        categoryId = null;
        categoryFavAdded = false;
        categoryFavRemoved = false;
        confirmSkip = false;
        closeSettingsRequested = false;
    }

    public static boolean active() {
        return step > 0;
    }

    public static int step() {
        return step;
    }

    public static boolean acceptsKeyboard() {
        return step == SEARCH_INPUT;
    }

    public static boolean isMainStep() {
        return active() && !isSettingsStep();
    }

    public static boolean isSettingsStep() {
        return step >= COLOR && step <= HIDE_CATEGORIES;
    }

    public static boolean takeCloseSettingsRequest() {
        boolean requested = closeSettingsRequested;
        closeSettingsRequested = false;
        return requested;
    }

    private static String currentKey() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        String world;
        if (mc.getIntegratedServer() != null) world = "sp:" + mc.getIntegratedServer().getFolderName();
        else if (mc.func_147104_D() != null) world = "mp:" + mc.func_147104_D().serverIP;
        else world = "unknown";
        return world + "|" + mc.thePlayer.getUniqueID();
    }

    private static void finish() {
        if (key != null) Theme.guideDone.add(key);
        Theme.save();
        step = 0;
        confirmSkip = false;
        categoryId = null;
    }

    public static void settingsOpened() {
        if (step == OPEN_SETTINGS) step = COLOR;
    }

    public static void categorySelected(UUID id, boolean alreadyFavorite) {
        if (step != SELECT_CATEGORY) return;
        categoryId = id;
        categoryFavorite = alreadyFavorite;
        categoryFavAdded = false;
        categoryFavRemoved = false;
        step = alreadyFavorite ? UNFAVORITE_CATEGORY : FAVORITE_CATEGORY;
    }

    public static void categoryToggled(UUID id, boolean nowFavorite) {
        if (categoryId == null || !categoryId.equals(id)) return;
        if (nowFavorite) categoryFavAdded = true;
        else categoryFavRemoved = true;
        categoryFavorite = nowFavorite;
        if (categoryFavAdded && categoryFavRemoved) step = SELECT_QUEST;
        else step = nowFavorite ? UNFAVORITE_CATEGORY : FAVORITE_CATEGORY;
    }

    public static void questSelected(UUID id) {
        if (step == SELECT_QUEST) step = PIN_QUEST;
    }

    public static void questPinned() {
        if (step == PIN_QUEST) step = PIN_INFO;
    }

    public static void pinFolded() {
        if (step == PIN_FOLD) step = FAVORITE_QUEST;
    }

    public static void questFavorited() {
        if (step == FAVORITE_QUEST) {
            if (mainScreen != null) mainScreen.guideCloseQuest();
            step = SEARCH;
        }
    }

    public static void searchOpened(boolean favorites) {
        if (step == SEARCH && !favorites) step = SEARCH_INPUT;
        else if (step == FAVORITES_FILTER && favorites) enterParty();
    }

    private static void enterParty() {
        if (mainScreen != null) mainScreen.guideCloseQuest();
        step = PARTY;
    }

    private static String title() {
        if (confirmSkip) return "Skip the tutorial?";
        switch (step) {
            case WELCOME: return "Welcome to the Quest Book!";
            case OPEN_SETTINGS: return "Settings";
            case COLOR: return "Choose a book color";
            case OPACITY: return "Background opacity";
            case HIDE_CATEGORIES: return "Hide empty categories";
            case SELECT_CATEGORY: return "Choose a category";
            case FAVORITE_CATEGORY:
            case UNFAVORITE_CATEGORY: return "Category favorites";
            case SELECT_QUEST: return "Open a quest";
            case PIN_QUEST:
            case PIN_INFO:
            case PIN_FOLD: return "Pin a quest";
            case FAVORITE_QUEST: return "Quest favorites";
            case SEARCH: return "Search the quest book";
            case SEARCH_INPUT: return "Search quests";
            case FAVORITES_FILTER: return "Show favorite quests";
            case PARTY: return "Party";
            default: return "You're ready!";
        }
    }

    private static String body(int[] target) {
        if (confirmSkip) return "Are you sure you want to skip the tutorial? You can start it again from Settings.";
        switch (step) {
            case WELCOME:
                return "This book has a few custom features. This short tour will show you where they are.";
            case OPEN_SETTINGS:
                return "Click the highlighted gear to open the book settings.";
            case COLOR:
                return "Use the color wheel or hex field to change the book's theme color. Click Next when you're ready.";
            case OPACITY:
                return "Drag the highlighted slider to change how transparent the quest map background is.";
            case HIDE_CATEGORIES:
                return "I enabled this option for you. It hides categories with no quests visible to you. Click Next to close Settings.";
            case SELECT_CATEGORY:
                if (target == null) return "There are no visible categories to choose from. Click Next to continue the tour.";
                return "Click the first category in the sidebar to view its quests.";
            case FAVORITE_CATEGORY:
                return categoryFavorite
                        ? "This category is already a favorite. Right-click it to remove the favorite, then add it again."
                        : "Right-click the selected category to favorite it. It moves to the top; right-click again to undo.";
            case UNFAVORITE_CATEGORY:
                return "Right-click the selected category to remove its favorite and return it to the normal list.";
            case SELECT_QUEST:
                if (target == null) return "This category has no visible quests to open. Click Next to continue the tour.";
                return "Click the highlighted quest on the map to open its details.";
            case PIN_QUEST:
                return "Click the pin icon to keep this quest visible on your screen.";
            case PIN_INFO:
                if (Theme.pinned == null) return "Pin a quest to keep it on screen after closing the book. Click Next to continue.";
                return "The pinned quest stays on screen even after you close the book. Next, try minimizing it.";
            case PIN_FOLD:
                return Theme.pinFolded
                        ? "This quest is already minimized. Click the chevron to expand it, then you can minimize it again later."
                        : "Click the highlighted chevron to minimize the pinned quest. Click it again later to expand it.";
            case FAVORITE_QUEST:
                return "Click the star to favorite this quest. Use the star in the book header to find favorites later.";
            case SEARCH:
                return "Click the magnifying glass to search quest names and descriptions.";
            case SEARCH_INPUT:
                return "Type to search. Press Next when you're done; you can clear or change the search any time.";
            case FAVORITES_FILTER:
                return "Click the highlighted star to show only your favorite quests.";
            case PARTY:
                return "This is the Party button. Open it to invite your friends... if you have any. Click Next to continue.";
            default:
                return "That's the tour. You can replay it from the Settings menu at any time.";
        }
    }

    private static String shortcutHint() {
        return !confirmSkip && (step == SEARCH || step == SEARCH_INPUT)
                ? "Tip: Press Ctrl+F to open search." : null;
    }

    private static int panelWidth(int sw) {
        return Math.min(W, Math.max(180, sw - 20));
    }

    private static int[] card(FontRenderer f, int sw, int sh, int[] target) {
        int w = panelWidth(sw);
        int lines = f.listFormattedStringToWidth(body(target), w - 24).size();
        String hint = shortcutHint();
        int hintLines = hint == null ? 0 : f.listFormattedStringToWidth(hint, w - 24).size();
        int h = 12 + 10 + 6 + (lines + hintLines) * 10 + 12 + BTN_H + 12;
        int x = (sw - w) / 2;
        int y = step == WELCOME || confirmSkip ? (sh - h) / 2 : Math.max(8, sh - h - 8);
        return new int[] { x, y, x + w, y + h };
    }

    private static int[] primary(int[] b) {
        return new int[] { b[2] - 12 - BTN_W, b[3] - 12 - BTN_H, b[2] - 12, b[3] - 12 };
    }

    private static int[] secondary(int[] b) {
        return new int[] { b[0] + 12, b[3] - 12 - BTN_H, b[0] + 12 + BTN_W, b[3] - 12 };
    }

    private static boolean in(int[] r, int x, int y) {
        return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    private static void button(FontRenderer f, int[] r, String label, boolean hot) {
        Gui.drawRect(r[0], r[1], r[2], r[3], YELLOW);
        Gui.drawRect(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, hot ? 0xFF3A3210 : 0xFF000000);
        f.drawStringWithShadow(label, (r[0] + r[2] - f.getStringWidth(label)) / 2, r[1] + 5, hot ? 0xFFFFFF : YELLOW);
    }

    @SuppressWarnings("unchecked")
    public static void draw(FontRenderer f, int sw, int sh, int mx, int my, int[] target) {
        if (step == 0) return;
        Gui.drawRect(0, 0, sw, sh, 0x88000000);
        if (!confirmSkip && target != null) {
            double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0);
            int edge = (int) (70 + 130 * pulse) << 24 | 0x33FF33;
            int fill = (int) (20 + 60 * pulse) << 24 | 0x33FF33;
            int x0 = target[0] - 3, y0 = target[1] - 3, x1 = target[2] + 3, y1 = target[3] + 3;
            Gui.drawRect(x0, y0, x1, y0 + 2, edge);
            Gui.drawRect(x0, y1 - 2, x1, y1, edge);
            Gui.drawRect(x0, y0 + 2, x0 + 2, y1 - 2, edge);
            Gui.drawRect(x1 - 2, y0 + 2, x1, y1 - 2, edge);
            Gui.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, fill);
        }
        int[] b = card(f, sw, sh, target);
        Gui.drawRect(b[0] - 2, b[1] - 2, b[2] + 2, b[3] + 2, YELLOW);
        Gui.drawRect(b[0], b[1], b[2], b[3], 0xFF000000);
        String heading = title();
        f.drawStringWithShadow(heading, b[0] + 12, b[1] + 12, YELLOW);
        if (!confirmSkip) {
            String progress = step + "/18";
            f.drawString(progress, b[2] - 12 - f.getStringWidth(progress), b[1] + 12, 0xFF8C96AA);
        }
        int y = b[1] + 12 + 10 + 6;
        for (String line : (List<String>) f.listFormattedStringToWidth(body(target), b[2] - b[0] - 24)) {
            f.drawString(line, b[0] + 12, y, 0xFFE6EAF2);
            y += 10;
        }
        String hint = shortcutHint();
        if (hint != null) {
            for (String line : (List<String>) f.listFormattedStringToWidth(hint, b[2] - b[0] - 24)) {
                f.drawString(line, b[0] + 12, y, 0xFFFF5555);
                y += 10;
            }
        }
        int[] p = primary(b), s = secondary(b);
        String next = step == HIDE_CATEGORIES ? "Close" : step == FINISH ? "Finish" : confirmSkip ? "Yes, skip" : "Next";
        button(f, p, next, in(p, mx, my));
        button(f, s, confirmSkip ? "Go back" : "Skip", in(s, mx, my));
    }

    /** Handles clicks on the tutorial and consumes every click outside the highlighted target. */
    public static boolean click(FontRenderer f, int sw, int sh, int mx, int my, int btn, int[] target) {
        if (step == 0) return false;
        if (btn != 0) return !(!confirmSkip && targetClick(btn, target, mx, my));
        int[] b = card(f, sw, sh, target);
        if (in(primary(b), mx, my)) {
            Theme.click();
            next(target);
        } else if (in(secondary(b), mx, my)) {
            Theme.click();
            if (confirmSkip) confirmSkip = false;
            else confirmSkip = true;
        } else if (!confirmSkip && targetClick(btn, target, mx, my)) {
            return false;
        }
        return true;
    }

    private static boolean targetClick(int btn, int[] target, int mx, int my) {
        if (!targetStep() || !in(target, mx, my)) return false;
        if (step == FAVORITE_CATEGORY || step == UNFAVORITE_CATEGORY) return btn == 1;
        return btn == 0;
    }

    private static boolean targetStep() {
        return step == OPEN_SETTINGS || step == COLOR || step == OPACITY || step == SELECT_CATEGORY
                || step == FAVORITE_CATEGORY || step == UNFAVORITE_CATEGORY || step == SELECT_QUEST
                || step == PIN_QUEST || step == PIN_FOLD || step == FAVORITE_QUEST
                || step == SEARCH || step == SEARCH_INPUT || step == FAVORITES_FILTER;
    }

    private static void next(int[] target) {
        if (confirmSkip) {
            finish();
            return;
        }
        switch (step) {
            case WELCOME:
                step = OPEN_SETTINGS;
                break;
            case COLOR:
                step = OPACITY;
                break;
            case OPACITY:
                Theme.hideUseless = true;
                Theme.save();
                step = HIDE_CATEGORIES;
                break;
            case HIDE_CATEGORIES:
                if (mainScreen != null) mainScreen.prepareGuideCategories();
                step = SELECT_CATEGORY;
                closeSettingsRequested = true;
                break;
            case SELECT_CATEGORY:
                step = target == null ? SEARCH : SELECT_QUEST;
                break;
            case FAVORITE_CATEGORY:
            case UNFAVORITE_CATEGORY:
                step = SELECT_QUEST;
                break;
            case SELECT_QUEST:
                step = target == null ? SEARCH : PIN_QUEST;
                break;
            case PIN_QUEST:
                step = PIN_INFO;
                break;
            case PIN_INFO:
                step = Theme.pinned == null ? FAVORITE_QUEST : PIN_FOLD;
                break;
            case PIN_FOLD:
                step = FAVORITE_QUEST;
                break;
            case FAVORITE_QUEST:
            case SEARCH:
                step = step == FAVORITE_QUEST ? SEARCH : SEARCH_INPUT;
                break;
            case SEARCH_INPUT:
                step = FAVORITES_FILTER;
                break;
            case FAVORITES_FILTER:
                enterParty();
                break;
            case PARTY:
                step = FINISH;
                break;
            default:
                finish();
                break;
        }
    }

    public static void escape() {
        if (step > 0) confirmSkip = !confirmSkip;
    }
}
