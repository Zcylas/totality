package zcylas.totality.util.mc;

import com.google.gson.*;
import net.minecraft.nbt.*;

import java.util.Map;

/**
 * Utilities for working with {@link CompoundTag} and converting Gson JSON to NBT.
 *
 * <p>Highlights:
 * <ul>
 *   <li>{@link #mergeNotOverwrite(CompoundTag, CompoundTag)} — merges defaults into an existing
 *       tag without clobbering keys that already exist. Useful for save data migration and
 *       providing default values to player components.</li>
 *   <li>{@link #of(JsonObject)} — converts a {@link JsonObject} to a {@link CompoundTag},
 *       choosing the narrowest NBT numeric type that fits each JSON number. Handy for
 *       data-driven content loaded from JSON files.</li>
 * </ul>
 *
 * Ported from CreativeCore {@code NBTUtils} (team.creative.creativecore).
 */
public final class NBTUtils {

    private NBTUtils() {}

    // -------------------------------------------------------------------------
    // Merging
    // -------------------------------------------------------------------------

    /**
     * Copies all keys from {@code toInsert} into {@code base} that do not already
     * exist in {@code base}. Recurses into nested {@link CompoundTag}s.
     *
     * <p>This is the opposite of {@link CompoundTag#merge}: that method overwrites
     * existing keys, whereas this method preserves them.
     *
     * @return {@code base} (mutated in place)
     */
    public static CompoundTag mergeNotOverwrite(CompoundTag base, CompoundTag toInsert) {
        for (String key : toInsert.keySet()) {
            Tag insertEntry = toInsert.get(key);
            if (insertEntry == null) continue;

            Tag existing = base.get(key);
            if (existing instanceof CompoundTag existingCompound
                    && insertEntry instanceof CompoundTag insertCompound) {
                mergeNotOverwrite(existingCompound, insertCompound);
            } else if (existing == null) {
                base.put(key, insertEntry);
            }
        }
        return base;
    }

    // -------------------------------------------------------------------------
    // JSON → NBT conversion
    // -------------------------------------------------------------------------

    /**
     * Converts a {@link JsonObject} to a {@link CompoundTag}.
     * JSON numbers are mapped to the narrowest fitting NBT numeric type.
     */
    public static CompoundTag of(JsonObject obj) {
        return of(obj, new CompoundTag());
    }

    /**
     * Converts a {@link JsonObject} into {@code compound}, writing all entries.
     *
     * @return {@code compound} (mutated in place)
     */
    public static CompoundTag of(JsonObject obj, CompoundTag compound) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet())
            compound.put(entry.getKey(), of(entry.getValue()));
        return compound;
    }

    /** Recursively converts a {@link JsonElement} to the appropriate {@link Tag}. */
    public static Tag of(JsonElement element) {
        if (element instanceof JsonPrimitive p) {
            if (p.isBoolean())
                return ByteTag.valueOf(p.getAsBoolean());

            if (p.isNumber()) {
                // Gson's getAsNumber() always returns a LazilyParsedNumber — never Double, Float etc.
                // instanceof checks would always fall through to IntTag, silently truncating decimals.
                // Instead, check the raw string to distinguish floats from integers.
                String raw = p.getAsString();
                boolean isDecimal = raw.contains(".") || raw.contains("e") || raw.contains("E");
                if (isDecimal) {
                    return DoubleTag.valueOf(p.getAsDouble());
                }
                long lv = p.getAsLong();
                if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE)
                    return IntTag.valueOf((int) lv);
                return LongTag.valueOf(lv);
            }
            return StringTag.valueOf(p.getAsString());
        }

        if (element instanceof JsonArray arr) {
            ListTag list = new ListTag();
            for (int i = 0; i < arr.size(); i++)
                list.add(of(arr.get(i)));

            if (!list.isEmpty()) {
                // Promote homogeneous primitive lists to the compact array types
                if (list.getFirst() instanceof ByteTag) {
                    byte[] bytes = new byte[list.size()];
                    for (int i = 0; i < bytes.length; i++)
                        bytes[i] = ((ByteTag) list.get(i)).byteValue();
                    return new ByteArrayTag(bytes);
                }
                if (list.getFirst() instanceof IntTag) {
                    int[] ints = new int[list.size()];
                    for (int i = 0; i < ints.length; i++)
                        ints[i] = ((IntTag) list.get(i)).intValue();
                    return new IntArrayTag(ints);
                }
                if (list.getFirst() instanceof LongTag) {
                    long[] longs = new long[list.size()];
                    for (int i = 0; i < longs.length; i++)
                        longs[i] = ((LongTag) list.get(i)).longValue();
                    return new LongArrayTag(longs);
                }
            }
            return list;
        }

        if (element instanceof JsonObject obj)
            return of(obj);

        // JsonNull or anything unexpected → empty string tag
        return StringTag.valueOf("");
    }
}