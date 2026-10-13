package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.ModSounds;
import net.minecraft.data.PackOutput;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

/** sounds.json: jedes Ereignis zeigt auf seine OGG-Datei (erzeugt von tools/generate_sounds.py) samt Untertitel. */
public class ModSoundProvider extends SoundDefinitionsProvider {
    public ModSoundProvider(PackOutput output, ExistingFileHelper files) {
        super(output, CosmicCore.MODID, files);
    }

    @Override
    public void registerSounds() {
        for (var entry : ModSounds.SOUNDS.getEntries()) {
            SoundEvent event = entry.value();
            String name = entry.getId().getPath();
            add(event, definition().subtitle("subtitles." + CosmicCore.MODID + "." + name).with(sound(CosmicCore.id(name))));
        }
    }
}
