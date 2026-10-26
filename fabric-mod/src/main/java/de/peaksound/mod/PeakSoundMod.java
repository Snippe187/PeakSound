package de.peaksound.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Gemeinsamer Einstiegspunkt: registriert die Netzwerk-Payloads in beide Richtungen. */
public final class PeakSoundMod implements ModInitializer {
    public static final String VERSION =
            net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("peaksound")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(RawPayload.TYPE, RawPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RawPayload.TYPE, RawPayload.CODEC);
    }
}
