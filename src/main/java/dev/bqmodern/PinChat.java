package dev.bqmodern;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;

/** Vanilla chat that lets the pinned quest be dragged and scrolled. */
public class PinChat extends GuiChat {
    public PinChat(String text) {
        super(text);
    }

    static String initialText(GuiChat g) {
        String[] names = { "defaultInputFieldText", "field_146409_v" };
        for (String n : names) {
            try {
                Field f = GuiChat.class.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(g);
                if (v instanceof String) return (String) v;
            } catch (Throwable ignored) {
                // try the other name
            }
        }
        return "";
    }

    @Override
    public void handleMouseInput() {
        if (PinHud.onMouse(Minecraft.getMinecraft())) return;
        super.handleMouseInput();
    }
}
