package zcylas.totality.mixin;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.component.ComponentContainer;
import zcylas.totality.api.component.ComponentProvider;
import zcylas.totality.api.component.PlayerComponentEvents;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer implements ComponentProvider {

    @Unique
    private ComponentContainer totality$componentContainer;

    // Initialize in constructor like CCA does — guarantees container exists before any save/load
    @Inject(method = "<init>", at = @At("RETURN"))
    private void totality$initComponents(CallbackInfo ci) {
        this.totality$componentContainer = new ComponentContainer();
        PlayerComponentEvents.attachComponentsTo((ServerPlayer) (Object) this);
    }

    @Override
    public ComponentContainer getComponentContainer() {
        return totality$componentContainer;
    }

    @Override
    public Iterable<ServerPlayer> getComponentSyncRecipients() {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) {
            Deque<ServerPlayer> watchers = new ArrayDeque<>(PlayerLookup.tracking(self));
            ServerPlayer player = (ServerPlayer) (Object) this;
            if (player.connection != null) {
                watchers.addFirst(player);
            }
            return watchers;
        }
        return List.of();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void totality$writeComponents(ValueOutput output, CallbackInfo ci) {
        for (var entry : totality$componentContainer.entries()) {
            String key = entry.getKey().getId().toString().replace(':', '.');
            ValueOutput sub = output.child(key);
            entry.getValue().writeData(sub);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void totality$readComponents(ValueInput input, CallbackInfo ci) {
        for (var entry : totality$componentContainer.entries()) {
            String key = entry.getKey().getId().toString().replace(':', '.');
            input.child(key).ifPresent(sub -> entry.getValue().readData(sub));
        }
    }
}