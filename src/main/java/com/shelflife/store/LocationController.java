package com.shelflife.store;

import com.shelflife.web.CurrentMerchant;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Locations API. There is no delete on purpose: batches point at locations, and once Square is
 * connected (M6) locations come from the Square account.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    public List<LocationResponse> list(@CurrentMerchant UUID merchantId) {
        return service.list(merchantId).stream().map(LocationResponse::of).toList();
    }

    @GetMapping("/{id}")
    public LocationResponse get(@CurrentMerchant UUID merchantId, @PathVariable UUID id) {
        return LocationResponse.of(service.get(merchantId, id));
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(@CurrentMerchant UUID merchantId,
            @Valid @RequestBody LocationRequest request) {
        Location location = service.create(merchantId, request);
        return ResponseEntity.created(URI.create("/api/locations/" + location.getId()))
                .body(LocationResponse.of(location));
    }

    @PutMapping("/{id}")
    public LocationResponse replace(@CurrentMerchant UUID merchantId, @PathVariable UUID id,
            @Valid @RequestBody LocationRequest request) {
        return LocationResponse.of(service.replace(merchantId, id, request));
    }
}
