package dev.dragarmor.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dragarmor.ControlPayload;
import dev.dragarmor.FrameCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.world.inventory.ClickType;
import org.lwjgl.glfw.GLFW;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Places where the API changes between Minecraft versions. */
public final class Compat {

    private Compat() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    /** Calls the callback every frame drawn for any screen. */
    public static void registerFrame(FrameCallback callback) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) ->
                        callback.run(client, s, mouseX, mouseY)));
    }

    public static CustomPacketPayload.Type<ControlPayload> controlType() {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("dragarmor", "control"));
    }

    public static void registerPayload() {
        PayloadTypeRegistry.playS2C().register(ControlPayload.TYPE, ControlPayload.CODEC);
    }

    /** swap = true uses a SWAP click (button = hotbar slot or 40 for offhand); false uses PICKUP. */
    public static void click(Minecraft mc, LocalPlayer player, int slot, int button, boolean swap) {
        int containerId = player.containerMenu.containerId;
        mc.gameMode.handleInventoryMouseClick(containerId, slot, button,
                swap ? ClickType.SWAP : ClickType.PICKUP, player);
    }

    /** Shift-click (QUICK_MOVE) on the slot. */
    public static void quickMove(Minecraft mc, LocalPlayer player, int slot) {
        mc.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slot, 0, ClickType.QUICK_MOVE, player);
    }

    /** Equipment slot of the item, or null if it is not equippable. */
    public static EquipmentSlot equipSlot(LocalPlayer player, ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    public static InputConstants.Key boundKey(KeyMapping mapping) {
        return KeyBindingHelper.getBoundKeyOf(mapping);
    }

    /** true/false se a tecla pode ser lida direto; null se nao da pra ler (scancode). */
    public static Boolean keyDown(Minecraft mc, InputConstants.Key key) {
        if (key == null || key.equals(InputConstants.UNKNOWN)) return Boolean.FALSE;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        }
        return null;
    }

    public static boolean isDown(Minecraft mc, KeyMapping mapping) {
        Boolean down = keyDown(mc, boundKey(mapping));
        return down != null ? down : mapping.isDown();
    }
}
