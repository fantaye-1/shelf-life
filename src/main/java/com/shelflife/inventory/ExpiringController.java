package com.shelflife.inventory;

import com.shelflife.web.CurrentMerchant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The expiring-soon list: what to pull, mark down or sell first at one store. */
@RestController
@RequestMapping("/api/expiring")
public class ExpiringController {

    private final ExpiringService service;

    public ExpiringController(ExpiringService service) {
        this.service = service;
    }

    @GetMapping
    public ExpiringReport report(@CurrentMerchant UUID merchantId, @RequestParam UUID locationId,
            @RequestParam(defaultValue = "" + ExpiringService.DEFAULT_WITHIN_DAYS) int withinDays) {
        return service.report(merchantId, locationId, withinDays);
    }
}
