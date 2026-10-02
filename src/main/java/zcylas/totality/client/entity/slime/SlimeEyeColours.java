package zcylas.totality.client.entity.slime;

/**
 * Element eye colours (0xRRGGBB) for the Small Slime's existing eye texels, measured from the Genshin Small Slime
 * references (Small Slimes V1 UV decision report §2): the dark rim becomes {@link #rim}, the lower shading the
 * {@link #glow} ring colour, the light interior {@link #interior}. The eye's pixel pattern is unchanged.
 */
public record SlimeEyeColours(int rim, int glow, int interior) {
}
