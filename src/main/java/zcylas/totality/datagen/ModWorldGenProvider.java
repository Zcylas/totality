package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class ModWorldGenProvider extends FabricDynamicRegistryProvider {
    public ModWorldGenProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void configure(HolderLookup.@NonNull Provider provider, @NonNull Entries entries) {

    }

    @Override
    public @NonNull String getName() {
        return "Totality World Gen";
    }
}
