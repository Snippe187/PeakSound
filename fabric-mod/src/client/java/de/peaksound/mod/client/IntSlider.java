package de.peaksound.mod.client;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Ganzzahliger Schieberegler mit uebersetzbarer Beschriftung ("Reichweite: %d Bloecke"). */
final class IntSlider extends AbstractSliderButton {
    private final String key;
    private final int min;
    private final int max;
    private final IntConsumer onChange;
    private boolean silent;

    IntSlider(int x, int y, int w, String key, int min, int max, int value, IntConsumer onChange) {
        super(x, y, w, 20, Component.empty(), (double) (value - min) / (max - min));
        this.key = key;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        updateMessage();
    }

    int intValue() { return min + (int) Math.round(value * (max - min)); }

    /** Wert von aussen setzen (z. B. durch den Ketten-Schalter), ohne erneut zu melden. */
    void set(int v) {
        silent = true;
        this.value = (double) (Math.max(min, Math.min(max, v)) - min) / (max - min);
        updateMessage();
        silent = false;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable(key, intValue()));
    }

    @Override
    protected void applyValue() {
        if (!silent) onChange.accept(intValue());
    }
}
