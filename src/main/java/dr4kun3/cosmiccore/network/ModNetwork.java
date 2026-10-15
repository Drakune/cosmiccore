package dr4kun3.cosmiccore.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Der Handler laeuft nur auf dem Client; die Client-Klasse wird erst beim Aufruf geladen.
        registrar.playToClient(AwakeningPayload.TYPE, AwakeningPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dr4kun3.cosmiccore.client.TitanCutscene.start(payload.pos())));
        registrar.playToServer(SetCoreColorsPayload.TYPE, SetCoreColorsPayload.CODEC, SetCoreColorsPayload::handle);
    }
}
