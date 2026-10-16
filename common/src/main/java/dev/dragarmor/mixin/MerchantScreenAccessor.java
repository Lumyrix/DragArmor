package dev.dragarmor.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {

    @Accessor("scrollOff")
    int dragarmor$getScrollOff();

    @Accessor("shopItem")
    void dragarmor$setShopItem(int index);

    @Invoker("postButtonClick")
    void dragarmor$postButtonClick();
}
