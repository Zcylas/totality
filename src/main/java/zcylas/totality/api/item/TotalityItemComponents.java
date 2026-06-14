package zcylas.totality.api.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.bleach.zanpakuto.ZanpakutoType;

import java.util.UUID;

/**
 * Custom item DataComponentTypes for Totality.
 *
 * ATTUNED_TO          — UUID of the player this item is attuned to. Null = unattuned.
 * IDENTIFICATION_STATUS — {@link IdentificationStatus} of this item stack.
 */
public final class TotalityItemComponents {

    // ── Components ────────────────────────────────────────────────────────────

    /** UUID of the player this stack is attuned to. Absent = unattuned. */
    public static final DataComponentType<UUID> ATTUNED_TO =
            DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build();

    /** Current evolution stage of a Zanpakutō stack. Absent = ASAUCHI. */
    public static final DataComponentType<ZanpakutoType> ZANPAKUTO_TYPE =
            DataComponentType.<ZanpakutoType>builder()
                    .persistent(Codec.STRING.xmap(
                            s -> { try { return ZanpakutoType.valueOf(s); } catch (Exception e) { return ZanpakutoType.ASAUCHI; } },
                            ZanpakutoType::name))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
                            s -> { try { return ZanpakutoType.valueOf(s); } catch (Exception e) { return ZanpakutoType.ASAUCHI; } },
                            ZanpakutoType::name))
                    .build();

    /** Identification state of this item stack. Defaults to UNIDENTIFIED if absent. */
    public static final DataComponentType<IdentificationStatus> IDENTIFICATION_STATUS =
            DataComponentType.<IdentificationStatus>builder()
                    .persistent(Codec.STRING.xmap(
                            s -> {
                                try { return IdentificationStatus.valueOf(s); }
                                catch (Exception e) { return IdentificationStatus.UNIDENTIFIED; }
                            },
                            IdentificationStatus::name
                    ))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
                            s -> {
                                try { return IdentificationStatus.valueOf(s); }
                                catch (Exception e) { return IdentificationStatus.UNIDENTIFIED; }
                            },
                            IdentificationStatus::name
                    ))
                    .build();

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "attuned_to"),
                ATTUNED_TO);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "zanpakuto_type"),
                ZANPAKUTO_TYPE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "identification_status"),
                IDENTIFICATION_STATUS);
    }

    private TotalityItemComponents() {}
}