package zcylas.totality.util.data;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * A generic {@link SavedData} wrapper that serializes/deserializes its payload
 * using any {@link Codec}. Eliminates the boilerplate of writing
 * {@code save}/{@code load} overrides by hand.
 *
 * <p>Ported from ResourcefulLib (TeamResourceful).</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // Declare a factory once (e.g. as a static field)
 * private static final CodecSavedData.Factory<BloodMoonState> DATA =
 *         CodecSavedData.create(BloodMoonState.CODEC, Totality.id("blood_moon"))
 *                       .defaultValue(BloodMoonState::new)
 *                       .global();           // store in overworld, not per-dimension
 *
 * // Load/create in a server-side system
 * CodecSavedData<BloodMoonState> saved = DATA.create(level);
 * BloodMoonState state = saved.get();
 * saved.set(state.withNextEvent(tick));
 * }</pre>
 */
public final class CodecSavedData<T> extends SavedData implements Supplier<T> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Factory<T> factory;
    private T data;

    private CodecSavedData(Factory<T> factory, T data) {
        this.factory = factory;
        this.data    = data;
    }

    private CodecSavedData(Factory<T> factory) {
        this.factory = factory;
        this.data    = factory.defaultValue.get();
    }

    @Override
    public boolean isDirty() {
        return this.factory.alwaysDirty || super.isDirty();
    }

    /** @return the stored data payload. */
    @Override
    public T get() {
        return this.data;
    }

    /** Replace the stored data payload and mark dirty so it will be saved. */
    public void set(T data) {
        this.data = data;
        this.setDirty();
    }

    // -----------------------------------------------------------------------
    // Factory builder
    // -----------------------------------------------------------------------

    /**
     * Start building a factory for a {@code CodecSavedData} keyed by {@code id}.
     *
     * @param codec the codec used to read/write the payload
     * @param id    the identifier used for the saved-data file name
     */
    public static <T> Factory<T> create(Codec<T> codec, Identifier id) {
        return new Factory<>(codec, id);
    }

    // -----------------------------------------------------------------------

    public static final class Factory<T> {

        private final Codec<T> codec;
        private final Identifier id;

        private Supplier<T> defaultValue = () -> null;
        private boolean alwaysDirty     = false;
        private boolean global          = false;

        private SavedDataType<CodecSavedData<T>> type;

        private Factory(Codec<T> codec, Identifier id) {
            this.codec = codec;
            this.id    = id;
        }

        /**
         * Set a supplier for the initial value when no saved data exists yet.
         */
        public Factory<T> defaultValue(Supplier<T> defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Force this saved data to be written to disk on every save cycle,
         * even when it has not been marked dirty explicitly.
         */
        public Factory<T> alwaysDirty() {
            this.alwaysDirty = true;
            return this;
        }

        /**
         * Store the data in the overworld's global data storage rather than
         * the per-dimension storage of whichever level is passed to {@link #create}.
         */
        public Factory<T> global() {
            this.global = true;
            return this;
        }

        /**
         * Retrieve (or create) the saved data from the given level.
         * If {@link #global()} was called, the overworld storage is used regardless
         * of which level is passed in.
         */
        public CodecSavedData<T> create(ServerLevel level) {
            var storage = this.global
                    ? level.getServer().overworld().getDataStorage()
                    : level.getDataStorage();

            if (this.type == null) {
                this.type = new SavedDataType<>(
                        this.id,
                        () -> new CodecSavedData<>(this),
                        codec.xmap(
                                data -> new CodecSavedData<>(this, data),
                                data -> data.data
                        ),
                        null
                );
            }
            return storage.computeIfAbsent(this.type);
        }
    }
}
