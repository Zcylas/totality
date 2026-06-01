package zcylas.totality.client.hud.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SecondaryResourceRegistry {

    private static final List<ISecondaryResource> RESOURCES = new ArrayList<>();

    public static void register(ISecondaryResource resource) {
        RESOURCES.add(resource);
    }

    public static List<ISecondaryResource> all() {
        return Collections.unmodifiableList(RESOURCES);
    }

    private SecondaryResourceRegistry() {}
}