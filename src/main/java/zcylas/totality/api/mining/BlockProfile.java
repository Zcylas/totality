package zcylas.totality.api.mining;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One layer of a Block/Material Profile (Block Breaking V2, Pass 1). Every field is nullable: a null field
 * defers to the next layer of {@link BlockProfileResolver} (exact state override, exact block, material + form,
 * material default, compatibility fallback). Pure data, no Minecraft types, so the resolution rules are
 * unit-testable.
 *
 * <p>Block Durability here is ONLY the mining Integrity budget. It is deliberately not Structural Strength,
 * density or any other Architecture/Totality Core material property, and no form value is derived from a
 * geometric formula: every form's value is authored.
 *
 * @param maxDurability maximum Block Durability (only meaningful for {@link Classification#ORDINARY})
 * @param tools         effective tool(s), or an explicit tool-neutral affinity
 * @param requiredTier  required Mining Tier; the fallback keeps the vanilla-derived value when none is authored
 * @param ownership     which position owns the Integrity record of a multi-position assembly
 */
public record BlockProfile(@Nullable Material material, @Nullable Form form, @Nullable Classification classification,
                           @Nullable Float maxDurability, @Nullable ToolAffinity tools, @Nullable Integer requiredTier,
                           @Nullable Ownership ownership) {

    public static final BlockProfile EMPTY = new BlockProfile(null, null, null, null, null, null, null);

    public static BlockProfile durability(float maxDurability) { return EMPTY.withMaxDurability(maxDurability); }

    public BlockProfile withMaterial(Material m, Form f) { return new BlockProfile(m, f, classification, maxDurability, tools, requiredTier, ownership); }
    public BlockProfile withClassification(Classification c) { return new BlockProfile(material, form, c, maxDurability, tools, requiredTier, ownership); }
    public BlockProfile withMaxDurability(float max) { return new BlockProfile(material, form, classification, max, tools, requiredTier, ownership); }
    public BlockProfile withTools(ToolAffinity t) { return new BlockProfile(material, form, classification, maxDurability, t, requiredTier, ownership); }
    public BlockProfile withRequiredTier(int tier) { return new BlockProfile(material, form, classification, maxDurability, tools, tier, ownership); }
    public BlockProfile withOwnership(Ownership o) { return new BlockProfile(material, form, classification, maxDurability, tools, requiredTier, o); }

    /** Material/family identity, e.g. {@code totality:overworld_log}. */
    public record Material(String id) {}

    /** Authored form of a material (full block, slab, ...). Values are never scaled geometrically. */
    public record Form(String id) {
        public static final Form FULL_BLOCK = new Form("full_block");
    }

    /**
     * How Totality mining treats the block. SPECIAL and UNBREAKABLE are explicit behaviour classes, never
     * numerical HP shortcuts; neither ever holds a finite Integrity record.
     */
    public enum Classification {
        /** Finite Block Durability, struck and damaged by Totality mining. */
        ORDINARY,
        /** Existing special or vanilla behaviour stays authoritative (e.g. vanilla instant-break blocks). */
        SPECIAL,
        /** Cannot be broken by mining. */
        UNBREAKABLE,
        /** Not a minable block at all (air, fluids). */
        NOT_APPLICABLE
    }

    /** Which position owns the Integrity of a block that is part of a multi-position assembly. */
    public enum Ownership {
        /** Every position is independent (ordinary blocks, and each half of a double chest). */
        POSITION,
        /** Both halves of a door share the LOWER half's record. */
        DOOR_LOWER_HALF,
        /** Both halves of a bed share the HEAD's record. */
        BED_HEAD,
        /** An extended piston's base and head share the BASE's record. */
        PISTON_BASE
    }

    /** Conventional tool categories whose preferred-tool effectiveness is resolved. */
    public enum Tool { PICKAXE, AXE, SHOVEL, HOE }

    /**
     * Effective tool(s) of a block. {@code neutral} means no tool is preferred and none is penalised; an empty,
     * non-neutral set means no conventional tool is preferred (every profiled tool is a wrong tool).
     */
    public record ToolAffinity(Set<Tool> tools, boolean neutral) {
        public static final ToolAffinity NEUTRAL = new ToolAffinity(Set.of(), true);
        public static final ToolAffinity NONE = new ToolAffinity(Set.of(), false);

        public ToolAffinity {
            tools = tools.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(tools));   // enum order
        }

        public static ToolAffinity of(Tool... tools) {
            return new ToolAffinity(tools.length == 0 ? Set.of() : EnumSet.of(tools[0], tools), false);
        }

        public boolean effective(Tool tool) { return neutral || tools.contains(tool); }
    }

    /** Resolution layer a value came from, highest precedence first. */
    public enum Layer { STATE_OVERRIDE, BLOCK, MATERIAL_FORM, MATERIAL, FALLBACK }

    /**
     * A fully resolved profile: every field has a value. {@code material}/{@code form} stay null for a block that
     * has not been authored into a family yet.
     *
     * @param durabilityLayer     where {@code maxDurability} came from (provenance for tests and reports)
     * @param classificationLayer where {@code classification} came from
     * @param toolsLayer          where {@code tools} came from
     */
    public record Resolved(@Nullable Material material, @Nullable Form form, Classification classification,
                           float maxDurability, ToolAffinity tools, int requiredTier, Ownership ownership,
                           Layer durabilityLayer, Layer classificationLayer, Layer toolsLayer) {
        public boolean ordinary() { return classification == Classification.ORDINARY; }
    }
}
