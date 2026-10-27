package de.peaksound.mod.client;

import de.peaksound.protocol.ReverbPreset;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * Raumhall ueber OpenAL-Effekte (EFX): Je Preset gibt es einen Effekt und einen Effekt-Slot, an den die Quellen der
 * Lautsprecher mit diesem Preset ihr Signal schicken. Unterstuetzt das Audiogeraet kein EFX, bleibt der Hall aus
 * (der Ton selbst ist dann unveraendert).
 */
final class ReverbFx {
    /** Pro Preset: {Effekt, Slot}. */
    private static final Map<Integer, int[]> slots = new HashMap<>();
    private static Boolean supported;

    private ReverbFx() {}

    static boolean supported() {
        if (supported == null) {
            long context = ALC10.alcGetCurrentContext();
            if (context == 0) return false; // Sound noch nicht bereit: spaeter erneut pruefen
            long device = ALC10.alcGetContextsDevice(context);
            supported = ALC.createCapabilities(device).ALC_EXT_EFX;
        }
        return supported;
    }

    /** Schickt die Quelle in den Hall des Presets (0 = aus). Fehler schalten den Hall nur ab. */
    static void attach(int source, int presetOrdinal) {
        if (presetOrdinal <= 0 || !supported()) return;
        try {
            int[] slot = slots.computeIfAbsent(presetOrdinal, ReverbFx::create);
            if (slot[1] == 0) return;
            AL10.alGetError();
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot[1], 0, EXTEfx.AL_FILTER_NULL);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) supported = false;
        } catch (RuntimeException e) {
            supported = false;
        }
    }

    private static int[] create(int presetOrdinal) {
        ReverbPreset preset = ReverbPreset.values()[presetOrdinal];
        AL10.alGetError();
        int effect = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, preset.decaySeconds());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_HFRATIO, preset.hfRatio());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, Math.min(1f, preset.lateGain()));
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, 1.26f);
        int slot = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            supported = false;
            return new int[] {effect, 0};
        }
        return new int[] {effect, slot};
    }

    /** Effekte freigeben (Verbindungswechsel, Spiel beenden). */
    static void clear() {
        if (!slots.isEmpty() && ALC10.alcGetCurrentContext() != 0) {
            for (int[] s : slots.values()) {
                if (s[1] != 0) EXTEfx.alDeleteAuxiliaryEffectSlots(s[1]);
                EXTEfx.alDeleteEffects(s[0]);
            }
        }
        slots.clear();
        supported = null;
    }
}