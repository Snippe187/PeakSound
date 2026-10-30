package de.peaksound.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Schuetzt die Kompatibilitaet mit der Alpha-Version: Name, Befehle und Rechte duerfen sich nicht aendern, sonst gehen bestehende Rechte-Einstellungen
 * und Daten des Servers verloren. Die plugin.yml muss ausserdem gueltiges YAML sein und PeakCore als feste Abhaengigkeit nennen.
 */
class PluginYmlTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() throws Exception {
        try (InputStream in = PluginYmlTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml fehlt");
            return new Yaml().load(in);
        }
    }

    @Test
    void nameStaysPeakSoundSoDataFolderAndPermissionsSurviveTheUpdate() throws Exception {
        assertEquals("PeakSound", load().get("name"));
    }

    @Test
    void peakCoreIsAHardDependency() throws Exception {
        Object depend = load().get("depend");
        assertTrue(depend instanceof List<?> list && list.contains("PeakCore"), "depend: " + depend);
        Object soft = load().get("softdepend");
        assertTrue(soft instanceof List<?> list && list.contains("voicechat"), "Simple Voice Chat bleibt optional");
    }

    @Test
    void commandsOfTheAlphaVersionAreStillThere() throws Exception {
        Map<?, ?> commands = (Map<?, ?>) load().get("commands");
        assertTrue(commands.containsKey("peaksound"));
        assertTrue(commands.containsKey("peaksoundvote"));
    }

    @Test
    void permissionNodesOfTheAlphaVersionAreStillThere() throws Exception {
        Map<?, ?> permissions = (Map<?, ?>) load().get("permissions");
        for (String node : List.of("peaksound.upload", "peaksound.library", "peaksound.library.manage.own", "peaksound.library.manage.any",
                "peaksound.queue", "peaksound.vote", "peaksound.place", "peaksound.radio", "peaksound.limit.unlimited", "peaksound.admin")) {
            assertTrue(permissions.containsKey(node), "Recht fehlt: " + node);
        }
    }

    @Test
    void mainClassExists() throws Exception {
        String main = (String) load().get("main");
        assertNotNull(PluginYmlTest.class.getResource("/" + main.replace('.', '/') + ".class"), "Hauptklasse nicht gefunden: " + main);
    }
}
