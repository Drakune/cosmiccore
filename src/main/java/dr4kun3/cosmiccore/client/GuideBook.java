package dr4kun3.cosmiccore.client;

import net.minecraft.client.Minecraft;

/** Oeffnet das kosmische Handbuch (eigene Doppelseite mit Inhaltsverzeichnis und 3D-Strukturen). */
public final class GuideBook {
    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    private GuideBook() {}
}
