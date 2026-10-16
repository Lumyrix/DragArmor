package dev.dragarmor;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Mostra a janela "falta LumLib" antes do Minecraft abrir. */
public class LumLibPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        LumLibMissingDialog.checkAndShow("DragArmor");
    }
}
