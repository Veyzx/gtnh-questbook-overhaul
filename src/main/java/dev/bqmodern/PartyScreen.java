package dev.bqmodern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.network.handlers.NetPartyAction;
import betterquesting.questing.party.PartyInvitations;
import betterquesting.questing.party.PartyManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatAllowedCharacters;

/** Modern party manager; talks to BetterQuesting through its own party packets. */
public class PartyScreen extends GuiScreen {
    private static final int ROW = 22;

    private final GuiScreen parent;
    private String nameText = "";
    private String inviteText = "";
    private int focus; // 0 none, 1 name/create, 2 invite
    private int scroll;
    private int sf = 2;
    private String message = "";
    private long messageUntil;

    private final List<Object[]> hits = new ArrayList<Object[]>();

    public PartyScreen(GuiScreen parent) {
        this.parent = parent;
    }

    public static void drawRect(int a, int b, int c, int d, int col) {
        net.minecraft.client.gui.Gui.drawRect(a, b, c, d, Theme.t(col));
    }

    private UUID me() { return QuestingAPI.getQuestingUUID(mc.thePlayer); }

    private DBEntry<IParty> party() {
        try {
            return PartyManager.INSTANCE.getParty(me());
        } catch (Throwable t) {
            return null;
        }
    }

    private String nameOf(UUID id) {
        try {
            String n = QuestingAPI.getAPI(ApiReference.NAME_CACHE).getName(id);
            return n == null || n.isEmpty() ? id.toString().substring(0, 8) : n;
        } catch (Throwable t) {
            return id.toString().substring(0, 8);
        }
    }

    private static void send(int action, int partyId, String name, String user, boolean hasId) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("action", action);
        if (hasId) t.setInteger("partyID", partyId);
        if (name != null) t.setString("name", name);
        if (user != null) t.setString("username", user);
        if (action == 3) t.setLong("expiry", 300000L);
        NetPartyAction.sendAction(t);
    }

    private void say(String s) {
        message = s;
        messageUntil = System.currentTimeMillis() + 3000;
    }

    private int pw() { return Math.min(360, width - 20); }

    private int ph() { return Math.min(280, height - 20); }

    private int px() { return (width - pw()) / 2; }

    private int py() { return (height - ph()) / 2; }

    private void box(int x1, int y1, int x2, int y2, int fill, int border) {
        drawRect(x1, y1, x2, y2, border);
        drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 28), g = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void button(int x, int y, int w, int h, String label, int color, boolean enabled, int mx, int my, Object[] action) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        box(x, y, x + w, y + h, !enabled ? 0xFF2A2E35 : hov ? lighten(color) : color, 0xFF0C0F16);
        FontRenderer f = fontRendererObj;
        String s = f.trimStringToWidth(label, w - 4);
        f.drawStringWithShadow(s, x + (w - f.getStringWidth(s)) / 2, y + (h - 8) / 2, enabled ? 0xFFFFFF : 0x777777);
        if (enabled && action != null) hits.add(new Object[] { x, y, w, h, action });
    }

    private void field(int x, int y, int w, String text, boolean focused, int id, String placeholder) {
        box(x, y, x + w, y + 18, 0xFF0E1118, focused ? 0xFFFFFFFF : 0xFF5A6478);
        FontRenderer f = fontRendererObj;
        if (text.isEmpty() && !focused) {
            f.drawString(placeholder, x + 4, y + 5, 0xFF5A6478);
        } else {
            String s = text + (focused && (System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
            f.drawString(f.trimStringToWidth(s, w - 8, true), x + 4, y + 5, 0xFFFFFF);
        }
        hits.add(new Object[] { x, y, w, 18, Integer.valueOf(id) });
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        sf = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        hits.clear();
        FontRenderer f = fontRendererObj;
        drawRect(0, 0, width, height, 0xC0101318);
        int x0 = px(), y0 = py(), w = pw(), h = ph();
        box(x0, y0, x0 + w, y0 + h, 0xFA161B26, 0xFF3A4661);
        drawRect(x0 + 1, y0 + 1, x0 + w - 1, y0 + 28, 0xFF1D2433);
        drawRect(x0 + 1, y0 + 28, x0 + w - 1, y0 + 29, 0xFF3A4661);
        f.drawStringWithShadow("Party", x0 + 12, y0 + 10, 0xFFFFFF);
        button(x0 + w - 62, y0 + 6, 54, 16, "Back", 0xFF3A4660, true, mx, my, new Object[] { "back" });

        DBEntry<IParty> entry = party();
        int cy = y0 + 38;
        if (entry == null) drawNoParty(x0, cy, w, h - 38, mx, my);
        else drawParty(entry, x0, cy, w, h - 38, mx, my);

        if (System.currentTimeMillis() < messageUntil) {
            f.drawStringWithShadow(message, x0 + 12, y0 + h - 14, 0xFFFFE070);
        }
    }

    private void drawNoParty(int x0, int y, int w, int h, int mx, int my) {
        FontRenderer f = fontRendererObj;
        f.drawStringWithShadow("You are not in a party", x0 + 12, y, 0xFFAAB4C8);
        f.drawString("Parties share quest progress with their members.", x0 + 12, y + 12, 0xFF7C8599);

        f.drawStringWithShadow("Create a party", x0 + 12, y + 32, 0xFF6FA8FF);
        field(x0 + 12, y + 46, w - 24 - 80, nameText, focus == 1, 1, "Party name");
        boolean ok = !nameText.trim().isEmpty();
        button(x0 + w - 12 - 74, y + 46, 74, 18, "Create", 0xFF2F9F55, ok, mx, my, new Object[] { "create" });

        f.drawStringWithShadow("Invitations", x0 + 12, y + 76, 0xFFE0B030);
        List<Map.Entry<Integer, Long>> inv = new ArrayList<Map.Entry<Integer, Long>>();
        try {
            inv.addAll(PartyInvitations.INSTANCE.getPartyInvites(me()));
        } catch (Throwable ignored) {
            // no invites available yet
        }
        if (inv.isEmpty()) f.drawString("No pending invitations", x0 + 12, y + 92, 0xFF7C8599);
        int top = y + 90, bottom = y + h - 18;
        int max = Math.max(0, inv.size() * ROW - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, max));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, mc.displayHeight - bottom * sf, mc.displayWidth, Math.max(0, (bottom - top) * sf));
        int ry = top - scroll;
        for (Map.Entry<Integer, Long> e : inv) {
            IParty p = PartyManager.INSTANCE.getValue(e.getKey());
            String n = p == null ? "Party " + e.getKey() : p.getProperties().getProperty(NativeProps.NAME);
            box(x0 + 12, ry, x0 + w - 12, ry + ROW - 2, 0xFF1E2433, 0xFF2C3446);
            f.drawStringWithShadow(f.trimStringToWidth(n, w - 130), x0 + 18, ry + 7, 0xFFFFFF);
            boolean inside = ry >= top - ROW && ry < bottom;
            button(x0 + w - 12 - 70, ry + 2, 66, ROW - 6, "Accept", 0xFF2F9F55, inside, mx, my, new Object[] { "accept", e.getKey() });
            ry += ROW;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawParty(DBEntry<IParty> entry, int x0, int y, int w, int h, int mx, int my) {
        FontRenderer f = fontRendererObj;
        IParty p = entry.getValue();
        int id = entry.getID();
        EnumPartyStatus mine = p.getStatus(me());
        boolean admin = mine == EnumPartyStatus.OWNER || mine == EnumPartyStatus.ADMIN;
        boolean owner = mine == EnumPartyStatus.OWNER;

        String pname = p.getProperties().getProperty(NativeProps.NAME);
        f.drawStringWithShadow(pname, x0 + 12, y, 0xFF55FFFF);
        List<UUID> members = p.getMembers();
        f.drawString(members.size() + " member" + (members.size() == 1 ? "" : "s"), x0 + 12, y + 12, 0xFF7C8599);
        if (owner) {
            button(x0 + w - 12 - 80, y - 2, 80, 18, "Disband", 0xFF8A3A3A, true, mx, my, new Object[] { "disband", id });
        } else {
            button(x0 + w - 12 - 80, y - 2, 80, 18, "Leave party", 0xFF8A3A3A, true, mx, my, new Object[] { "leave", id });
        }

        int bottomReserve = admin ? 40 : 14;
        int top = y + 28, bottom = y + h - bottomReserve;
        int max = Math.max(0, members.size() * ROW - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, max));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, mc.displayHeight - bottom * sf, mc.displayWidth, Math.max(0, (bottom - top) * sf));
        int ry = top - scroll;
        UUID self = me();
        for (UUID m : members) {
            EnumPartyStatus st = p.getStatus(m);
            box(x0 + 12, ry, x0 + w - 12, ry + ROW - 2, 0xFF1E2433, 0xFF2C3446);
            String role = st == EnumPartyStatus.OWNER ? "Owner" : st == EnumPartyStatus.ADMIN ? "Admin" : "Member";
            int rc = st == EnumPartyStatus.OWNER ? 0xFFC832 : st == EnumPartyStatus.ADMIN ? 0x6FA8FF : 0x8C96AA;
            f.drawStringWithShadow(f.trimStringToWidth(nameOf(m), w - 150), x0 + 18, ry + 7, m.equals(self) ? 0x55FF55 : 0xFFFFFF);
            f.drawString(role, x0 + w - 12 - 118 + (m.equals(self) || !canKick(mine, st) ? 66 : 0), ry + 7, rc);
            if (!m.equals(self) && canKick(mine, st)) {
                boolean inside = ry >= top - ROW && ry < bottom;
                button(x0 + w - 12 - 50, ry + 2, 46, ROW - 6, "Kick", 0xFF8A3A3A, inside, mx, my, new Object[] { "kick", nameOf(m), id });
            }
            ry += ROW;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (max > 0) {
            int th = bottom - top;
            int bh = Math.max(10, th * th / (members.size() * ROW));
            int by = top + (th - bh) * scroll / max;
            drawRect(x0 + w - 7, top, x0 + w - 5, bottom, 0x40FFFFFF);
            drawRect(x0 + w - 7, by, x0 + w - 5, by + bh, 0xFFAAB4C8);
        }

        if (admin) {
            f.drawStringWithShadow("Invite player", x0 + 12, y + h - 38, 0xFFE0B030);
            field(x0 + 12, y + h - 28, w - 24 - 80, inviteText, focus == 2, 2, "Player name");
            button(x0 + w - 12 - 74, y + h - 28, 74, 18, "Invite", 0xFF2F6FD0, !inviteText.trim().isEmpty(), mx, my, new Object[] { "invite", id });
        }
    }

    private static boolean canKick(EnumPartyStatus mine, EnumPartyStatus target) {
        if (mine == EnumPartyStatus.OWNER) return true;
        return mine == EnumPartyStatus.ADMIN && target == EnumPartyStatus.MEMBER;
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        if (btn != 0) return;
        focus = 0;
        for (Object[] h : hits) {
            int x = (Integer) h[0], y = (Integer) h[1], w = (Integer) h[2], hh = (Integer) h[3];
            if (mx < x || mx >= x + w || my < y || my >= y + hh) continue;
            Object a = h[4];
            if (a instanceof Integer) {
                focus = (Integer) a;
                return;
            }
            act((Object[]) a);
            return;
        }
    }

    private void act(Object[] a) {
        String k = (String) a[0];
        if ("back".equals(k)) {
            mc.displayGuiScreen(parent);
        } else if ("create".equals(k)) {
            send(0, 0, nameText.trim(), null, false);
            nameText = "";
            say("Creating party...");
        } else if ("accept".equals(k)) {
            send(4, (Integer) a[1], null, null, true);
            say("Joining party...");
        } else if ("disband".equals(k)) {
            send(1, (Integer) a[1], null, null, true);
            say("Party disbanded");
        } else if ("leave".equals(k)) {
            send(5, (Integer) a[1], null, mc.thePlayer.getCommandSenderName(), true);
            say("Left the party");
        } else if ("kick".equals(k)) {
            send(5, (Integer) a[2], null, (String) a[1], true);
            say("Kicked " + a[1]);
        } else if ("invite".equals(k)) {
            send(3, (Integer) a[1], null, inviteText.trim(), true);
            say("Invited " + inviteText.trim());
            inviteText = "";
        }
    }

    @Override
    protected void keyTyped(char c, int k) {
        if (k == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (focus == 0) return;
        String cur = focus == 1 ? nameText : inviteText;
        if (k == Keyboard.KEY_BACK) {
            if (!cur.isEmpty()) cur = GuiScreen.isCtrlKeyDown() ? "" : cur.substring(0, cur.length() - 1);
        } else if (k == Keyboard.KEY_RETURN) {
            if (focus == 1 && !cur.trim().isEmpty()) act(new Object[] { "create" });
            else if (focus == 2 && !cur.trim().isEmpty()) {
                DBEntry<IParty> e = party();
                if (e != null) act(new Object[] { "invite", e.getID() });
            }
            return;
        } else if (GuiScreen.isCtrlKeyDown() && k == Keyboard.KEY_V) {
            String clip = GuiScreen.getClipboardString();
            if (clip != null && cur.length() + clip.length() < 40) cur += clip.replace("\r", "").replace("\n", "");
        } else if (ChatAllowedCharacters.isAllowedCharacter(c) && cur.length() < 40) {
            cur += c;
        }
        if (focus == 1) nameText = cur;
        else inviteText = cur;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scroll -= d > 0 ? ROW : -ROW;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
