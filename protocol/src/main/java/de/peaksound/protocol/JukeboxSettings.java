package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Alle Einstellungen einer PeakSound-Jukebox (Menue). Der Server prueft jeden Wert mit {@link #sanitized()};
 * der Client wird nie als vertrauenswuerdig behandelt.
 *
 * @param name             frei waehlbarer Name (max. {@link #MAX_NAME} Zeichen, ohne Steuerzeichen)
 * @param range            Reichweite in Bloecken (1-30)
 * @param volume           Lautstaerke in Prozent (0-100)
 * @param chain            Kette: Reichweite und Lautstaerke bewegen sich proportional mit
 * @param particles        Noten-Partikel im Takt
 * @param particleAmount   Partikel pro Takt (1-10)
 * @param particleDensity  Dichte (1-10): hoeher = enger um die Jukebox gebuendelt
 * @param particleHeight   Aufsteigehoehe in Bloecken (0-5)
 * @param rainbow          bunte Noten (sonst alle in einer Farbe)
 * @param showNowPlaying   "Jetzt laeuft: Titel - Interpret" fuer Spieler in Reichweite anzeigen
 * @param loop             Wiederholmodus der Playlist
 * @param shuffle          Playlist in zufaelliger Reihenfolge spielen
 * @param upmix            Stereo-/Mono-Songs auf alle angeschlossenen Lautsprecher hochmischen (sonst nur vorne links/rechts)
 */
public record JukeboxSettings(String name, int range, int volume, boolean chain, boolean particles,
                              int particleAmount, int particleDensity, int particleHeight, boolean rainbow,
                              boolean showNowPlaying, LoopMode loop, boolean shuffle, boolean upmix) {
    public static final int MAX_NAME = 32;

    public static JukeboxSettings defaults(int range, int volume) {
        return new JukeboxSettings("", range, volume, false, true, 3, 5, 2, true, true, LoopMode.OFF, false, false).sanitized();
    }

    /** Alle Werte in den erlaubten Bereich bringen und den Namen bereinigen. */
    public JukeboxSettings sanitized() {
        String clean = name == null ? "" : name.replaceAll("\\p{Cntrl}", " ").trim();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        return new JukeboxSettings(clean,
                clamp(range, Limits.MIN_RANGE, Limits.MAX_RANGE), clamp(volume, 0, 100), chain, particles,
                clamp(particleAmount, 1, 10), clamp(particleDensity, 1, 10), clamp(particleHeight, 0, 5),
                rainbow, showNowPlaying, loop == null ? LoopMode.OFF : loop, shuffle, upmix);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public JukeboxSettings withName(String v) {
        return new JukeboxSettings(v, range, volume, chain, particles, particleAmount, particleDensity, particleHeight,
                rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withRangeAndVolume(int newRange, int newVolume) {
        return new JukeboxSettings(name, newRange, newVolume, chain, particles, particleAmount, particleDensity,
                particleHeight, rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withChain(boolean v) {
        return new JukeboxSettings(name, range, volume, v, particles, particleAmount, particleDensity, particleHeight,
                rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withParticles(boolean v) {
        return new JukeboxSettings(name, range, volume, chain, v, particleAmount, particleDensity, particleHeight,
                rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withParticleAmount(int v) {
        return new JukeboxSettings(name, range, volume, chain, particles, v, particleDensity, particleHeight,
                rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withParticleDensity(int v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, v, particleHeight,
                rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withParticleHeight(int v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, particleDensity, v,
                rainbow, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withRainbow(boolean v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, particleDensity, particleHeight,
                v, showNowPlaying, loop, shuffle, upmix);
    }

    public JukeboxSettings withShowNowPlaying(boolean v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, particleDensity, particleHeight,
                rainbow, v, loop, shuffle, upmix);
    }

    public JukeboxSettings withLoop(LoopMode v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, particleDensity, particleHeight,
                rainbow, showNowPlaying, v, shuffle, upmix);
    }

    public JukeboxSettings withShuffle(boolean v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, particleDensity, particleHeight,
                rainbow, showNowPlaying, loop, v, upmix);
    }

    public JukeboxSettings withUpmix(boolean v) {
        return new JukeboxSettings(name, range, volume, chain, particles, particleAmount, particleDensity, particleHeight,
                rainbow, showNowPlaying, loop, shuffle, v);
    }

    void write(PacketIO.Writer w) {
        w.writeString(name, MAX_NAME).writeByte(range).writeByte(volume).writeBoolean(chain).writeBoolean(particles)
                .writeByte(particleAmount).writeByte(particleDensity).writeByte(particleHeight)
                .writeBoolean(rainbow).writeBoolean(showNowPlaying).writeByte(loop.ordinal()).writeBoolean(shuffle).writeBoolean(upmix);
    }

    /** Liest und prueft: Werte ausserhalb des Bereichs machen das Paket ungueltig. */
    static JukeboxSettings read(ByteBuffer in) {
        String name = PacketIO.readString(in, MAX_NAME);
        int range = PacketIO.readByte(in);
        int volume = PacketIO.readByte(in);
        boolean chain = PacketIO.readBoolean(in);
        boolean particles = PacketIO.readBoolean(in);
        int amount = PacketIO.readByte(in);
        int density = PacketIO.readByte(in);
        int height = PacketIO.readByte(in);
        boolean rainbow = PacketIO.readBoolean(in);
        boolean now = PacketIO.readBoolean(in);
        LoopMode loop = LoopMode.byOrdinal(PacketIO.readByte(in));
        boolean shuffle = PacketIO.readBoolean(in);
        boolean upmix = PacketIO.readBoolean(in);
        JukeboxSettings s = new JukeboxSettings(name, range, volume, chain, particles, amount, density, height,
                rainbow, now, loop, shuffle, upmix);
        if (!s.equals(s.sanitized().withName(name))) throw new MalformedPacketException("Einstellungen ausserhalb des Bereichs");
        return s;
    }
}
