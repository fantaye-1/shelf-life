package com.shelflife.store;

import com.shelflife.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Location use cases. Every method takes the merchant id first and only touches that merchant's locations. */
@Service
@Transactional
public class LocationService {

    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    public Location create(UUID merchantId, LocationRequest request) {
        Location location = new Location(merchantId, request.name(), request.timezone());
        location.changeDigestEmail(request.digestEmail());
        return locations.save(location);
    }

    @Transactional(readOnly = true)
    public Location get(UUID merchantId, UUID locationId) {
        return locations.findByMerchantIdAndId(merchantId, locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
    }

    @Transactional(readOnly = true)
    public List<Location> list(UUID merchantId) {
        return locations.findAllByMerchantIdOrderByNameAsc(merchantId);
    }

    /**
     * Renames the location and changes its time zone and digest recipient. The Square link is kept;
     * only Square sync sets it.
     */
    public Location replace(UUID merchantId, UUID locationId, LocationRequest request) {
        Location location = get(merchantId, locationId);
        location.rename(request.name());
        location.changeTimezone(request.timezone());
        location.changeDigestEmail(request.digestEmail());
        return location;
    }
}
