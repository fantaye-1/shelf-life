package com.shelflife.catalog;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Items API. There is no delete on purpose: batches and waste history point at items, and once
 * Square is connected (M6) the item list comes from the Square catalog.
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<ItemResponse> list(@CurrentMerchant UUID merchantId,
            @RequestParam(required = false) String gtin) {
        return service.list(merchantId, gtin).stream().map(ItemResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ItemResponse get(@CurrentMerchant UUID merchantId, @PathVariable UUID id) {
        return ItemResponse.of(service.get(merchantId, id));
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@CurrentMerchant UUID merchantId,
            @Valid @RequestBody ItemRequest request) {
        Item item = service.create(merchantId, request);
        return ResponseEntity.created(URI.create("/api/items/" + item.getId())).body(ItemResponse.of(item));
    }

    @PutMapping("/{id}")
    public ItemResponse replace(@CurrentMerchant UUID merchantId, @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request) {
        return ItemResponse.of(service.replace(merchantId, id, request));
    }
}
