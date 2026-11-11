package aerocore.gds.adapter;

import aerocore.gds.model.GdsType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for resolving GDS Adapters based on airline code or GdsType.
 */
public class GdsAdapterFactory {
    private final Map<GdsType, GdsAdapter> registry = new EnumMap<>(GdsType.class);

    public GdsAdapterFactory() {
        registry.put(GdsType.TOPAS, new TopasGdsAdapter());
        registry.put(GdsType.AMADEUS, new AmadeusGdsAdapter());
    }

    public void registerAdapter(GdsAdapter adapter) {
        registry.put(adapter.getSupportedGds(), adapter);
    }

    public GdsAdapter getAdapter(GdsType type) {
        GdsAdapter adapter = registry.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported GDS type: " + type);
        }
        return adapter;
    }

    /**
     * Resolves the appropriate GDS adapter for a given flight number.
     * e.g., KE (Korean Air) -> TOPAS, OZ (Asiana) -> AMADEUS, LH/AF -> AMADEUS
     */
    public GdsAdapter getAdapterForFlight(String flightNumber) {
        if (flightNumber == null || flightNumber.isBlank()) {
            return getAdapter(GdsType.TOPAS);
        }
        String prefix = flightNumber.trim().toUpperCase();
        if (prefix.startsWith("KE")) {
            return getAdapter(GdsType.TOPAS);
        } else if (prefix.startsWith("OZ") || prefix.startsWith("LH") || prefix.startsWith("AF")) {
            return getAdapter(GdsType.AMADEUS);
        }
        return getAdapter(GdsType.TOPAS);
    }
}
