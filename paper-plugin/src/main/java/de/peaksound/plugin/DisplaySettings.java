package de.peaksound.plugin;

/** Einstellungen eines Display-Blocks: Blickrichtung ({@code DisplayWallLogic.Facing}-Ordinal) und die Karten-ID seiner Kachel (0 = noch keine). */
record DisplaySettings(int facing, int mapId) {
    static DisplaySettings defaults() { return new DisplaySettings(0, 0); }

    DisplaySettings sanitized() { return new DisplaySettings(Math.floorMod(facing, 4), Math.max(0, mapId)); }
}