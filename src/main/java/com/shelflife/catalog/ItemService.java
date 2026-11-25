package com.shelflife.catalog;

import com.shelflife.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Item use cases. Every method takes the merchant id first and only touches that merchant's items. */
@Service
@Transactional
public class ItemService {

    private final ItemRepository items;

    public ItemService(ItemRepository items) {
        this.items = items;
    }

    public Item create(UUID merchantId, ItemRequest request) {
        Item item = new Item(merchantId, request.name());
        apply(item, request);
        return items.save(item);
    }

    @Transactional(readOnly = true)
    public Item get(UUID merchantId, UUID itemId) {
        return items.findByMerchantIdAndId(merchantId, itemId)
                .orElseThrow(() -> new NotFoundException("Item", itemId));
    }

    /** All items by name, or only those with this barcode when one is given (barcode scanning). */
    @Transactional(readOnly = true)
    public List<Item> list(UUID merchantId, String gtin) {
        if (gtin == null || gtin.isBlank()) {
            return items.findAllByMerchantIdOrderByNameAsc(merchantId);
        }
        return items.findAllByMerchantIdAndGtin(merchantId, Gtin.normalize(gtin));
    }

    public Item replace(UUID merchantId, UUID itemId, ItemRequest request) {
        Item item = get(merchantId, itemId);
        item.rename(request.name());
        apply(item, request);
        return item;
    }

    private static void apply(Item item, ItemRequest request) {
        item.changeSku(request.sku());
        item.changeGtin(request.gtin());
    }
}
