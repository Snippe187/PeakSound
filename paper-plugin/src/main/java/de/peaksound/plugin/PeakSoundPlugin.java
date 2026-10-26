package de.peaksound.plugin;

import de.peaksound.protocol.PeakIds;
import org.bukkit.plugin.java.JavaPlugin;

/** Einstiegspunkt des PeakSound-Plugins. Die Logik liegt in den einzelnen Klassen dieses Pakets. */
public final class PeakSoundPlugin extends JavaPlugin {
    private BlockRegistry blocks;
    private PeakRecipes recipes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PeakItems items = new PeakItems();

        blocks = new BlockRegistry(this);
        blocks.load();
        blocks.startAutosave();

        recipes = new PeakRecipes(this, items);
        recipes.register();

        ClientHandshake handshake = new ClientHandshake(this, recipes);
        getServer().getMessenger().registerIncomingPluginChannel(this, PeakIds.CHANNEL, handshake);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PeakIds.CHANNEL);

        getServer().getPluginManager().registerEvents(handshake, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this, items, recipes), this);
    }

    @Override
    public void onDisable() {
        if (recipes != null) recipes.unregister();
        if (blocks != null) blocks.saveNow();
    }
}
