package de.peaksound.mod.client;

import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;

/** Von der Mixin aufgerufen: macht aus der Standard-Fortschrittsansicht die PeakVerse-Ansicht (alle anderen Bildschirme bleiben). */
public final class PeakAdvancementsOpener {
    private static Field advancementsField;

    private PeakAdvancementsOpener() {}

    public static Screen replace(Screen screen) {
        if (screen == null || screen.getClass() != AdvancementsScreen.class) return screen;
        try {
            if (advancementsField == null) {
                for (Field f : AdvancementsScreen.class.getDeclaredFields()) {
                    if (f.getType() == ClientAdvancements.class) { f.setAccessible(true); advancementsField = f; break; }
                }
            }
            if (advancementsField == null) return screen;
            return new PeakAdvancementsScreen((ClientAdvancements) advancementsField.get(screen));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return screen;   // im Zweifel die Standard-Ansicht behalten
        }
    }
}
