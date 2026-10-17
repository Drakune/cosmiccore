package dr4kun3.cosmiccore.client;

import net.minecraft.client.Minecraft;

/** Oeffnet das kosmische Handbuch (eigene Doppelseite mit Inhaltsverzeichnis und 3D-Strukturen). */
public final class GuideBook {
    /** Oeffnet das Kosmische Handbuch (nur auf dem Client aufrufen). */
    public static void open() {
        Minecraft.getInstance().gui.setScreen(new GuideScreen());
    }

    /** Nur statische Hilfsfunktion - keine Instanzen. */
    private GuideBook() {}
}
