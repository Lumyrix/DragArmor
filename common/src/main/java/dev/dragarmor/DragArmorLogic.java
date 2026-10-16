package dev.dragarmor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dragarmor.compat.Compat;
import dev.dragarmor.mixin.AbstractContainerScreenAccessor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DragArmorLogic {

    private static final int FEET = 36;
    private static final int LEGS = 37;
    private static final int CHEST = 38;
    private static final int HEAD = 39;
    private static final int OFFHAND = 40;
    private static final int PATH_STEP = 4;
    private static final long ACTION_INTERVAL_NS = 120_000_000L;
    private static long lastActionNs;

    private static final InputConstants.Key LEFT_SHIFT = InputConstants.getKey("key.keyboard.left.shift");
    private static final InputConstants.Key RIGHT_SHIFT = InputConstants.getKey("key.keyboard.right.shift");

    private static MethodHandle rebindCheck;
    private static boolean resolvedRebindCheck;
    private static KeyMapping rebindQuickSwap;
    private static boolean searchedRebind;

    private static Screen lastScreen;
    private static int lastX;
    private static int lastY;

    private DragArmorLogic() {
    }

    /** Runs every frame while a screen is open and visits every slot the mouse crossed since the previous frame. */
    public static void frame(Minecraft mc, Screen current, int mouseX, int mouseY) {
        boolean sameScreen = current == lastScreen;
        int fromX = sameScreen ? lastX : mouseX;
        int fromY = sameScreen ? lastY : mouseY;
        lastScreen = current;
        lastX = mouseX;
        lastY = mouseY;

        if ((DragArmor.disabled || !DragArmorConfig.enabled)) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        if (current instanceof MerchantScreen merchant) {
            MerchantTrading.frame(mc, merchant, mouseX, mouseY);
            return;
        }
        if (!(current instanceof AbstractContainerScreen<?> screen)) return;
        if (screen instanceof CreativeModeInventoryScreen) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) return;

        if (!quickSwapHeld(mc)) return;
        if (!Compat.isDown(mc, mc.options.keyAttack) && !Compat.isDown(mc, mc.options.keyUse)) return;

        AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) (Object) screen;
        Set<Slot> crossed = new LinkedHashSet<>();
        int steps = Math.max(Math.abs(mouseX - fromX), Math.abs(mouseY - fromY)) / PATH_STEP + 1;
        for (int i = 0; i <= steps; i++) {
            int x = fromX + (mouseX - fromX) * i / steps;
            int y = fromY + (mouseY - fromY) * i / steps;
            Slot slot = slotAt(menu, access, x, y);
            if (slot != null) crossed.add(slot);
        }
        Slot hovered = access.dragarmor$getHoveredSlot();
        if (hovered != null) crossed.add(hovered);

        if (!menu.getCarried().isEmpty()) return;
        long now = System.nanoTime();
        if (now - lastActionNs < ACTION_INTERVAL_NS) return;
        for (Slot slot : crossed) {
            if (!slot.hasItem() || !isActionable(player, menu, slot)) continue;
            // QUICK_MOVE is the normal single-packet inventory action. Never synthesize
            // a source/destination PICKUP pair every rendered frame.
            Compat.quickMove(mc, player, slot.index);
            lastActionNs = now;
            return;
        }
    }

    private static Slot slotAt(AbstractContainerMenu menu, AbstractContainerScreenAccessor access, int x, int y) {
        int relX = x - access.dragarmor$getLeftPos();
        int relY = y - access.dragarmor$getTopPos();
        for (Slot slot : menu.slots) {
            if (slot.isActive() && relX >= slot.x && relX < slot.x + 16 && relY >= slot.y && relY < slot.y + 16) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isActionable(LocalPlayer player, AbstractContainerMenu menu, Slot slot) {
        if (slot.container != player.getInventory()) {
            return !(slot.container instanceof ResultContainer) && isWearable(player, slot.getItem());
        }
        if (menu != player.inventoryMenu) return false;
        int containerSlot = slot.getContainerSlot();
        if (containerSlot >= FEET && containerSlot <= OFFHAND) return true;
        return containerSlot >= 0 && containerSlot < FEET
                && (slot.getItem().is(Items.SHIELD) || Compat.equipSlot(player, slot.getItem()) != null);
    }

    private static boolean isWearable(LocalPlayer player, ItemStack stack) {
        if (stack.is(Items.SHIELD)) return true;
        EquipmentSlot type = Compat.equipSlot(player, stack);
        return type != null && targetFor(type) >= 0;
    }

    private static int targetFor(EquipmentSlot type) {
        return switch (type) {
            case HEAD -> HEAD;
            case CHEST -> CHEST;
            case LEGS -> LEGS;
            case FEET -> FEET;
            default -> -1;
        };
    }

    /** Uses RebindQuickSwap's own method (if installed), which already reads the key the player bound. */
    private static Boolean rebindModHeld() {
        if (!resolvedRebindCheck) {
            resolvedRebindCheck = true;
            try {
                Class<?> modClass = Class.forName("walksy.quickswaprebinder.RebindQuickSwapMod");
                rebindCheck = MethodHandles.publicLookup()
                        .findStatic(modClass, "shouldQuickSwap", MethodType.methodType(boolean.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                rebindCheck = null;
            }
        }
        if (rebindCheck == null) return null;
        try {
            return (boolean) rebindCheck.invoke();
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean quickSwapHeld(Minecraft mc) {
        Boolean fromMod = rebindModHeld();
        if (fromMod != null) return fromMod;
        KeyMapping rebind = rebindQuickSwap(mc);
        if (rebind != null) return Compat.isDown(mc, rebind);
        return Boolean.TRUE.equals(Compat.keyDown(mc, LEFT_SHIFT))
                || Boolean.TRUE.equals(Compat.keyDown(mc, RIGHT_SHIFT));
    }

    private static KeyMapping rebindQuickSwap(Minecraft mc) {
        if (!searchedRebind) {
            searchedRebind = true;
            for (KeyMapping mapping : mc.options.keyMappings) {
                String name = mapping.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains("quick swap")) {
                    rebindQuickSwap = mapping;
                    break;
                }
            }
        }
        return rebindQuickSwap;
    }
}
