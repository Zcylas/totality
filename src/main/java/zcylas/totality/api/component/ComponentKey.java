package zcylas.totality.api.component;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A typed key used to get and sync a component on a provider.
 * Obtain instances via ComponentRegistry.getOrCreate().
 */
public final class ComponentKey<C extends TotalityComponent> {

    private final Identifier id;
    private final Class<C> componentClass;

    ComponentKey(Identifier id, Class<C> componentClass) {
        this.id = id;
        this.componentClass = componentClass;
    }

    public Identifier getId() {
        return id;
    }

    public Class<C> getComponentClass() {
        return componentClass;
    }

    /**
     * Gets the component from a provider, throwing if absent.
     */
    public C get(ComponentProvider provider) {
        C component = provider.getComponentContainer().get(this);
        if (component == null) {
            throw new IllegalStateException(
                    provider + " has no component for key: " + id
            );
        }
        return component;
    }

    /**
     * Gets the component from a provider, returning empty if absent.
     */
    public Optional<C> maybeGet(ComponentProvider provider) {
        return Optional.ofNullable(provider.getComponentContainer().get(this));
    }

    public boolean isProvidedBy(ComponentProvider provider) {
        return provider.getComponentContainer().get(this) != null;
    }

    /**
     * Syncs this component to all relevant players via the provider.
     */
    public void sync(ComponentProvider provider) {
        C component = get(provider);
        if (component instanceof SyncedComponent synced) {
            @SuppressWarnings("unchecked")
            ComponentKey<SyncedComponent> syncedKey = (ComponentKey<SyncedComponent>) (ComponentKey<?>) this;
            provider.syncComponent(syncedKey, synced);
        }
    }

    @Override
    public String toString() {
        return "ComponentKey[" + id + "]";
    }
}