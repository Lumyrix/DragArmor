package dev.dragarmor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Called once per frame while a screen is open (mouseX/mouseY in GUI coordinates). */
@FunctionalInterface
public interface FrameCallback {
    void run(Minecraft mc, Screen screen, int mouseX, int mouseY);
}
