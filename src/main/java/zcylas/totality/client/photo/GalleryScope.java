package zcylas.totality.client.photo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Which Gallery a photograph belongs to: one per (singleplayer world folder or multiplayer server address) and player
 * identity. Each scope is its own directory, so photographs can never leak between worlds, servers or players.
 *
 * <p>Directory: {@code <root>/<kind>-<readable name>-<hash>/<player uuid>/}. The readable part is sanitised for every
 * file system; the hash of the exact original name keeps two names that sanitise alike ("My World", "My_World") apart.
 *
 * @param kind     {@code "world"} (singleplayer save folder) or {@code "server"} (multiplayer address)
 * @param source   the original world folder name or normalised server address (recorded in {@code context.json})
 * @param player   the player's profile UUID
 */
public record GalleryScope(String kind, String source, UUID player) {

    public static final int DEFAULT_PORT = 25565;
    private static final int MAX_READABLE = 32;

    public GalleryScope {
        if (!kind.equals("world") && !kind.equals("server")) throw new IllegalArgumentException("unknown gallery kind " + kind);
        if (player == null) throw new IllegalArgumentException("no player identity");
    }

    public static GalleryScope world(String saveFolder, UUID player) {
        return new GalleryScope("world", saveFolder, player);
    }

    public static GalleryScope server(String address, UUID player) {
        return new GalleryScope("server", normaliseAddress(address), player);
    }

    /** Lower case, trimmed, without the default port: "Play.Example.net:25565" and "play.example.net" are one server. */
    public static String normaliseAddress(String address) {
        String a = address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
        if (a.endsWith(":" + DEFAULT_PORT)) a = a.substring(0, a.length() - (":" + DEFAULT_PORT).length());
        return a.isEmpty() ? "unknown" : a;
    }

    /** The directory name of this world or server: {@code kind-readable-hash}. */
    public String key() {
        return kind + "-" + readable(source) + "-" + hash(source);
    }

    public Path directory(Path root) {
        return root.resolve(key()).resolve(player.toString());
    }

    /** Letters, digits, '-' and '_' only; at most 32 characters; never empty. */
    static String readable(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') b.append(c);
            else if (b.length() > 0 && b.charAt(b.length() - 1) != '_') b.append('_');
            if (b.length() >= MAX_READABLE) break;
        }
        String r = b.toString().replaceAll("^_+|_+$", "");
        return r.isEmpty() ? "unnamed" : r;
    }

    static String hash(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08x", crc.getValue());
    }
}
