package dev.bqmodern;

import java.lang.reflect.Field;
import java.util.UUID;

import betterquesting.client.gui2.GuiHome;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.client.gui2.GuiQuestLines;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;

/** Swaps BetterQuesting's home / quest line / quest screens for the modern one. */
public class Hooks {
    /** True while the user deliberately went to the original BetterQuesting screens. */
    public static boolean classic;

    /** The quest book left on screen after its GUI closed (window mode + "keep open"). */
    public static ModernScreen kept;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onQuestShareChat(ClientChatReceivedEvent e) {
        if (!(e.message instanceof ChatComponentTranslation)) return;
        ChatComponentTranslation msg = (ChatComponentTranslation) e.message;
        if (!"betterquesting.msg.share_quest".equals(msg.getKey())) return;

        Object[] args = msg.getFormatArgs();
        if (args == null || args.length == 0) return;

        String questName = null;
        IChatComponent questComponent = null;
        for (Object arg : args) {
            if (arg instanceof IChatComponent) {
                IChatComponent component = (IChatComponent) arg;
                String candidate = component.getUnformattedText();
                if (candidate != null && !candidate.trim().isEmpty()) {
                    if (!looksLikeQuestId(candidate)) {
                        questName = candidate;
                        questComponent = component;
                        break;
                    }
                }
            } else if (arg instanceof String) {
                String candidate = ((String) arg).trim();
                if (!candidate.isEmpty() && !looksLikeQuestId(candidate)) {
                    questName = candidate;
                    break;
                }
            }
        }
        if (questName == null || questName.trim().isEmpty()) return;

        ChatComponentText link = new ChatComponentText("[" + questName + "]");
        if (questComponent != null && questComponent.getChatStyle() != null) {
            link.setChatStyle(questComponent.getChatStyle().createShallowCopy());
        } else if (msg.getChatStyle() != null) {
            link.setChatStyle(msg.getChatStyle().createShallowCopy());
        }

        e.message = new ChatComponentText("Shared a Quest: ").appendSibling(link);
    }

    private static boolean looksLikeQuestId(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.matches("[0-9a-zA-Z+/=]{8,}")) return true;
        if (trimmed.matches("#?\\d+")) return true;
        return false;
    }

    @SubscribeEvent
    public void onOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Post e) {
        if (e.type != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.gameSettings.hideGUI) return;
        PinHud.draw(mc, -1, -1);
    }

    @SubscribeEvent
    public void onScreenDraw(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post e) {
        if (ours(e.gui) || pauseScreen(e.gui)) return;
        PinHud.draw(net.minecraft.client.Minecraft.getMinecraft(), e.mouseX, e.mouseY);
    }

    private static boolean pauseScreen(GuiScreen g) {
        return g instanceof net.minecraft.client.gui.GuiIngameMenu || g instanceof net.minecraft.client.gui.GuiOptions
                || g instanceof net.minecraft.client.gui.GuiVideoSettings || g instanceof net.minecraft.client.gui.GuiControls
                || g instanceof net.minecraft.client.gui.GuiScreenOptionsSounds || g instanceof net.minecraft.client.gui.GuiLanguage
                || g instanceof net.minecraft.client.gui.GuiSnooper || g instanceof net.minecraft.client.gui.GuiScreenResourcePacks;
    }


    @SubscribeEvent
    public void onOpen(GuiOpenEvent e) {
        if (e.gui != null && e.gui.getClass() == net.minecraft.client.gui.GuiChat.class && (Theme.pinned != null || kept != null)) {
            e.gui = new PinChat(PinChat.initialText((net.minecraft.client.gui.GuiChat) e.gui));
        }
        swap(e);
        syncScale(e.gui);
    }

    static boolean ours(GuiScreen g) {
        return g instanceof ModernScreen || g instanceof PartyScreen || g instanceof ThemeScreen;
    }

    private static int savedScale = -1;

    /** Applies the chosen UI scale while our screens are open and restores the player's own setting otherwise. */
    public static void syncScale(GuiScreen next) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (ours(next) && Theme.uiScale > 0) {
            if (savedScale < 0) savedScale = mc.gameSettings.guiScale;
            mc.gameSettings.guiScale = Theme.uiScale;
        } else if (savedScale >= 0) {
            mc.gameSettings.guiScale = savedScale;
            savedScale = -1;
        }
    }

    private void swap(GuiOpenEvent e) {
        GuiScreen g = e.gui;
        if (g == null || ours(g)) {
            if (g == null) classic = false;
            return;
        }
        boolean bq = g.getClass().getName().startsWith("betterquesting.");
        if (!bq) {
            classic = false;
            return;
        }
        if (classic) return;
        try {
            if (g instanceof GuiHome || g instanceof GuiQuestLines) {
                e.gui = new ModernScreen(null);
            } else if (g instanceof GuiQuest) {
                ModernScreen s = new ModernScreen(null);
                s.openQuest(findUuid(g));
                e.gui = s;
            }
        } catch (Throwable t) {
            BQModern.LOG.error("Could not open modern screen, falling back to BetterQuesting", t);
        }
    }

    private static UUID findUuid(GuiScreen g) {
        for (Field f : g.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object v = f.get(g);
                if (v instanceof UUID) return (UUID) v;
                if (v instanceof java.util.Map.Entry && ((java.util.Map.Entry<?, ?>) v).getKey() instanceof UUID) {
                    return (UUID) ((java.util.Map.Entry<?, ?>) v).getKey();
                }
            } catch (Throwable ignored) {
                // try next field
            }
        }
        return null;
    }
}
