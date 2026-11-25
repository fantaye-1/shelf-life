package com.shelflife.inventory;

import com.shelflife.web.CurrentMerchant;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Waste across all batches in a time window, e.g. one month, with the dollar total. Recording waste
 * happens on a batch: {@code POST /api/batches/{id}/waste}.
 */
@RestController
@RequestMapping("/api/waste")
public class WasteController {

    private final WasteService service;

    public WasteController(WasteService service) {
        this.service = service;
    }

    /** {@code from} and {@code to} are ISO-8601 instants, e.g. 2026-11-01T04:00:00Z; {@code to} is exclusive. */
    @GetMapping
    public WasteListResponse list(@CurrentMerchant UUID merchantId, @RequestParam Instant from,
            @RequestParam Instant to) {
        return service.listBetween(merchantId, from, to);
    }
}
