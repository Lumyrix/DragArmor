package dev.dragarmor;

import dev.lumyrix.lumlib.api.LumConfigContainer;
import dev.lumyrix.lumlib.api.LumLib;

/** Only loaded when LumLib is present (checked in DragArmor.onInitializeClient). */
final class DragArmorLumConfig {

    private DragArmorLumConfig() {
    }

    static void register() {
        LumConfigContainer c = LumLib.registerMod("dragarmor", "DragArmor");
        c.addBoolean("enabled", "Enabled", "Enables or disables DragArmor functionality.", true, v -> DragArmorConfig.enabled = v, () -> DragArmorConfig.enabled);
        c.build();
    }
}
