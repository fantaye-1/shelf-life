package com.shelflife.inventory;

import com.shelflife.web.CurrentMerchant;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batches API: log a delivery with its expiry date, then act on it from the shelf. Actions are
 * separate endpoints (not a free-form PUT) so the status rules in {@link Batch} can't be skipped.
 * There is no delete: batch history feeds the waste report.
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService service;
    private final WasteService waste;

    public BatchController(BatchService service, WasteService waste) {
        this.service = service;
        this.waste = waste;
    }

    /** Batches at one store, soonest expiry first. Repeat {@code status} to filter; default is open batches. */
    @GetMapping
    public List<BatchResponse> list(@CurrentMerchant UUID merchantId, @RequestParam UUID locationId,
            @RequestParam(required = false) List<BatchStatus> status) {
        return service.listAtLocation(merchantId, locationId, status);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@CurrentMerchant UUID merchantId, @PathVariable UUID id) {
        return service.get(merchantId, id);
    }

    @PostMapping
    public ResponseEntity<BatchResponse> log(@CurrentMerchant UUID merchantId,
            @Valid @RequestBody BatchRequest request) {
        BatchResponse batch = service.log(merchantId, request);
        return ResponseEntity.created(URI.create("/api/batches/" + batch.id())).body(batch);
    }

    @PostMapping("/{id}/mark-down")
    public BatchResponse markDown(@CurrentMerchant UUID merchantId, @PathVariable UUID id) {
        return service.markDown(merchantId, id);
    }

    @PostMapping("/{id}/sales")
    public BatchResponse recordSale(@CurrentMerchant UUID merchantId, @PathVariable UUID id,
            @Valid @RequestBody QuantityRequest request) {
        return service.recordSale(merchantId, id, request.quantity());
    }

    @PostMapping("/{id}/sold-out")
    public BatchResponse markSoldOut(@CurrentMerchant UUID merchantId, @PathVariable UUID id) {
        return service.markSoldOut(merchantId, id);
    }

    @PutMapping("/{id}/expires-on")
    public BatchResponse correctExpiry(@CurrentMerchant UUID merchantId, @PathVariable UUID id,
            @Valid @RequestBody ExpiryRequest request) {
        return service.correctExpiry(merchantId, id, request.expiresOn());
    }

    /** Throws units away. Records the dollar loss; wasting the last unit closes the batch as wasted. */
    @PostMapping("/{id}/waste")
    public ResponseEntity<WasteRecordedResponse> recordWaste(@CurrentMerchant UUID merchantId,
            @PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waste.record(merchantId, id, request.quantity()));
    }

    /** Waste records of one batch, oldest first. */
    @GetMapping("/{id}/waste")
    public List<WasteResponse> listWaste(@CurrentMerchant UUID merchantId, @PathVariable UUID id) {
        return waste.listForBatch(merchantId, id);
    }
}
