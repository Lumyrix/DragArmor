package dev.dragarmor;

import dev.dragarmor.compat.Compat;
import dev.dragarmor.mixin.MerchantScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;

/** Quick swap + two clicks on a villager trade: keeps buying the trade until the stock runs out. */
public final class MerchantTrading {

    private static final long MIN_GAP_MS = 100;
    private static final long MAX_GAP_MS = 1500;
    private static final int RESULT_SLOT = 2;
    private static final int SELECT_WAIT_TICKS = 4;
    private static final int STUCK_TICKS = 12;

    private static Screen lastScreen;
    private static boolean wasDown;
    private static boolean wasBoth;
    private static int pendingTrade = -1;
    private static long pendingMs;

    private static MerchantScreen buyingScreen;
    private static int buyingTrade;
    private static int stuckTicks;
    private static int ticksSinceSelect;
    private static String lastSignature = "";

    private MerchantTrading() {
    }

    /** Called every frame while the villager screen is open. */
    public static void frame(Minecraft mc, MerchantScreen screen, int mouseX, int mouseY) {
        if (screen != lastScreen) {
            lastScreen = screen;
            wasDown = false;
            wasBoth = false;
            pendingTrade = -1;
        }
        boolean down = Compat.isDown(mc, mc.options.keyAttack);
        boolean pressed = down && !wasDown;
        wasDown = down;
        boolean both = down && Compat.isDown(mc, mc.options.keyUse);
        boolean bothPressed = both && !wasBoth;
        wasBoth = both;
        if (!pressed && !bothPressed) return;

        if (!DragArmorLogic.quickSwapHeld(mc)) {
            pendingTrade = -1;
            return;
        }
        int trade = tradeUnder(screen, mouseX, mouseY);
        if (trade < 0) {
            pendingTrade = -1;
            return;
        }

        if (bothPressed) {
            // left + right at the same time on the trade: buy right away
            pendingTrade = -1;
            startBuying(screen, trade);
            return;
        }

        long now = System.currentTimeMillis();
        if (trade == pendingTrade) {
            long gap = now - pendingMs;
            if (gap < MIN_GAP_MS) return; // double click acidental: ignora
            if (gap <= MAX_GAP_MS) {
                pendingTrade = -1;
                startBuying(screen, trade);
                return;
            }
        }
        pendingTrade = trade;
        pendingMs = now;
    }

    private static void startBuying(MerchantScreen screen, int trade) {
        buyingScreen = screen;
        buyingTrade = trade;
        stuckTicks = 0;
        ticksSinceSelect = SELECT_WAIT_TICKS;
        lastSignature = "";
    }

    /** One action per tick: at most one packet per tick to the server. */
    public static void tick(Minecraft mc) {
        if (buyingScreen == null) return;

        LocalPlayer player = mc.player;
        if ((DragArmor.disabled || !DragArmorConfig.enabled) || player == null || mc.gameMode == null
                || Compat.screen(mc) != buyingScreen
                || !(player.containerMenu instanceof MerchantMenu menu)
                || !menu.getCarried().isEmpty()) {
            buyingScreen = null;
            return;
        }

        String signature = signature(menu);
        if (signature.equals(lastSignature)) {
            if (++stuckTicks >= STUCK_TICKS) {
                buyingScreen = null;
                return;
            }
        } else {
            stuckTicks = 0;
            lastSignature = signature;
        }

        ticksSinceSelect++;
        if (menu.getSlot(RESULT_SLOT).hasItem()) {
            Compat.quickMove(mc, player, RESULT_SLOT);
        } else if (ticksSinceSelect >= SELECT_WAIT_TICKS) {
            ticksSinceSelect = 0;
            MerchantScreenAccessor access = (MerchantScreenAccessor) (Object) buyingScreen;
            access.dragarmor$setShopItem(buyingTrade);
            access.dragarmor$postButtonClick();
        }
    }

    private static int tradeUnder(MerchantScreen screen, int mouseX, int mouseY) {
        int row = 0;
        for (var child : screen.children()) {
            if (child instanceof Button button) {
                if (button.isMouseOver(mouseX, mouseY)) {
                    return row + ((MerchantScreenAccessor) (Object) screen).dragarmor$getScrollOff();
                }
                row++;
            }
        }
        return -1;
    }

    private static String signature(MerchantMenu menu) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= RESULT_SLOT; i++) {
            Slot slot = menu.getSlot(i);
            builder.append(slot.hasItem()
                    ? slot.getItem().getItem().hashCode() + "x" + slot.getItem().getCount()
                    : "-").append('|');
        }
        return builder.toString();
    }
}
