package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import de.peaksound.protocol.LimitLogic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Web-Schnittstelle von PeakSound ({@code /api/peaksound/...}): Songs ansehen, anhoeren, umbenennen, freigeben, loeschen und die Upload-Limits einstellen.
 * Dieselben Regeln wie in der Song-Verwaltung im Spiel; jede Aktion steht im Web-Protokoll und im Server-Log.
 */
final class SongWeb {
    private static final Gson GSON = new Gson();

    private final WebRegistry web;
    private final SongStore songs;
    private final AdminLimits limits;
    private final AnalysisStore analysis;
    private final FavoriteStore favorites;
    private final PlaybackManager playback;

    SongWeb(WebRegistry web, SongStore songs, AdminLimits limits, AnalysisStore analysis, FavoriteStore favorites, PlaybackManager playback) {
        this.web = web;
        this.songs = songs;
        this.limits = limits;
        this.analysis = analysis;
        this.favorites = favorites;
        this.playback = playback;
    }

    void register(String script) {
        web.panel("PeakSound", "PeakSound", script);
        web.route("meta", r -> web.onMain(() -> WebResponse.json(meta())));
        web.route("songs", r -> WebResponse.json(list()));
        web.route("songs/*", this::song);
        web.route("cover/*", this::cover);
        web.route("audio/*", this::audio);
        web.route("limits", this::setLimits);
        web.route("players/*", this::setPlayerLimit);
    }

    // ---- Lesen ----

    private String meta() {
        JsonObject o = new JsonObject();
        List<Song> all = songs.all();
        o.addProperty("count", all.size());
        o.addProperty("pending", all.stream().filter(s -> s.status() == Song.Status.PENDING).count());
        o.addProperty("totalBytes", songs.totalBytes());
        o.addProperty("maxBytes", limits.maxStorageBytes());
        o.addProperty("defaultMax", limits.defaultMaxSongs());
        o.addProperty("useDefault", LimitLogic.USE_DEFAULT);
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, Integer> counts = new HashMap<>();
        for (Song s : all) {
            names.putIfAbsent(s.uploader(), s.uploaderName());
            counts.merge(s.uploader(), 1, Integer::sum);
        }
        limits.overrides().keySet().forEach(k -> {
            try {
                UUID id = UUID.fromString(k);
                String n = Bukkit.getOfflinePlayer(id).getName();
                names.putIfAbsent(id, n != null ? n : k);
            } catch (IllegalArgumentException ignored) { /* kaputter Eintrag */ }
        });
        JsonArray players = new JsonArray();
        names.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).forEach(e -> {
            JsonObject p = new JsonObject();
            p.addProperty("uuid", e.getKey().toString());
            p.addProperty("name", e.getValue());
            p.addProperty("count", counts.getOrDefault(e.getKey(), 0));
            p.addProperty("override", limits.overrideFor(e.getKey()));
            players.add(p);
        });
        o.add("players", players);
        return GSON.toJson(o);
    }

    private String list() {
        JsonArray a = new JsonArray();
        songs.all().stream().sorted(Comparator.comparingLong(Song::uploadedAt).reversed()).forEach(s -> {
            JsonObject o = new JsonObject();
            o.addProperty("hash", s.hash());
            o.addProperty("title", s.title());
            o.addProperty("artist", s.artist());
            o.addProperty("uploader", s.uploaderName());
            o.addProperty("channels", s.channels());
            o.addProperty("durationMs", s.durationMs());
            o.addProperty("sizeBytes", s.sizeBytes());
            o.addProperty("format", s.format());
            o.addProperty("pending", s.status() == Song.Status.PENDING);
            o.addProperty("uploadedAt", s.uploadedAt());
            o.addProperty("hasCover", s.hasCover());
            o.addProperty("plays", s.plays());
            a.add(o);
        });
        return GSON.toJson(a);
    }

    private WebResponse cover(WebRequest r) throws Exception {
        String hash = r.path().substring("cover/".length());
        if (!SongStore.validHash(hash)) return WebResponse.error(404, "Kein Bild");
        Path file = songs.coverPath(hash);
        if (!Files.isRegularFile(file)) return WebResponse.error(404, "Kein Bild");
        byte[] data = Files.readAllBytes(file);
        boolean png = data.length > 3 && (data[0] & 0xff) == 0x89 && data[1] == 'P';
        return WebResponse.bytes(png ? "image/png" : "image/jpeg", data);
    }

    private WebResponse audio(WebRequest r) throws Exception {
        String hash = r.path().substring("audio/".length());
        Song song = SongStore.validHash(hash) ? songs.get(hash).orElse(null) : null;
        if (song == null || !Files.isRegularFile(songs.audioPath(song))) return WebResponse.error(404, "Kein Song");
        String type = switch (song.format()) {
            case "ogg" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
        return WebResponse.bytes(type, Files.readAllBytes(songs.audioPath(song)));
    }

    // ---- Aendern ----

    private WebResponse song(WebRequest r) throws Exception {
        String[] p = r.path().split("/"); // songs/<hash>[/<aktion>]
        if (p.length < 2 || !SongStore.validHash(p[1])) return WebResponse.error(400, "Ungueltige Song-Kennung");
        String hash = p[1];
        if (p.length == 2 && r.method().equals("DELETE")) return remove(r, hash);
        if (p.length == 3 && r.method().equals("POST")) {
            switch (p[2]) {
                case "rename" -> {
                    String title = de.peaksound.audio.AudioInspector.clean(GSON.fromJson(r.text(), JsonObject.class).get("title").getAsString());
                    if (title.isEmpty()) throw new IllegalArgumentException("Der Titel darf nicht leer sein.");
                    Song old = songs.get(hash).orElseThrow(() -> new IllegalArgumentException("Diesen Song gibt es nicht mehr."));
                    songs.rename(hash, title);
                    return done(r, "Song umbenannt: \"" + old.title() + "\" -> \"" + title + "\"");
                }
                case "approve" -> {
                    Song old = songs.get(hash).orElseThrow(() -> new IllegalArgumentException("Diesen Song gibt es nicht mehr."));
                    if (old.status() != Song.Status.PENDING) throw new IllegalArgumentException("Der Song wartet nicht auf Freigabe.");
                    songs.setStatus(hash, Song.Status.OK);
                    return done(r, "Song freigegeben: \"" + old.title() + "\" von " + old.uploaderName());
                }
                default -> {
                    return WebResponse.error(404, "Unbekannte Aktion");
                }
            }
        }
        return WebResponse.error(404, "Unbekannte Adresse");
    }

    private WebResponse remove(WebRequest r, String hash) throws Exception {
        Song old = songs.get(hash).orElseThrow(() -> new IllegalArgumentException("Diesen Song gibt es nicht mehr."));
        songs.remove(hash); // loescht die Dateien (synchronisiert)
        web.onMain(() -> {
            analysis.forget(hash);
            favorites.forget(hash);
            playback.onSongRemoved(hash);
            return null;
        });
        return done(r, "Song geloescht: \"" + old.title() + "\" (" + hash.substring(0, 8) + ") von " + old.uploaderName());
    }

    private WebResponse setLimits(WebRequest r) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
        if (o.has("defaultMax")) {
            long v = o.get("defaultMax").getAsLong();
            if (v < 0) throw new IllegalArgumentException("Das Limit darf nicht negativ sein.");
            limits.setDefaultMaxSongs(v);
        }
        if (o.has("storageMb")) {
            long v = o.get("storageMb").getAsLong();
            if (v < 0) throw new IllegalArgumentException("Das Limit darf nicht negativ sein.");
            limits.setStorageMb(v);
        }
        return done(r, "Limits geaendert: Standard " + limits.defaultMaxSongs() + " Songs, Speicher " + limits.maxStorageBytes() / (1024 * 1024) + " MB");
    }

    private WebResponse setPlayerLimit(WebRequest r) throws Exception {
        String[] p = r.path().split("/"); // players/<uuid>/limit
        if (p.length != 3 || !p[2].equals("limit") || !r.method().equals("POST")) return WebResponse.error(404, "Unbekannte Adresse");
        UUID id;
        try {
            id = UUID.fromString(p[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltige Spieler-Kennung");
        }
        long v = GSON.fromJson(r.text(), JsonObject.class).get("value").getAsLong();
        if (v < 0 && v != LimitLogic.USE_DEFAULT) throw new IllegalArgumentException("Das Limit darf nicht negativ sein.");
        limits.setPlayerLimit(id, v);
        return done(r, "Songlimit von " + id + " = " + (v == LimitLogic.USE_DEFAULT ? "Standard" : v));
    }

    /** Protokollieren, im Server-Log vermerken, andere Seiten benachrichtigen und die neue Uebersicht liefern. */
    private WebResponse done(WebRequest r, String what) throws Exception {
        web.log(r.admin(), what);
        Bukkit.getLogger().info("[PeakSound/Web] " + r.admin() + ": " + what);
        web.changed("songs", r.admin());
        return web.onMain(() -> WebResponse.json(meta()));
    }
}
