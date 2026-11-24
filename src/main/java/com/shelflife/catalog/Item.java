package com.shelflife.catalog;

import com.shelflife.common.BaseEntity;
import com.shelflife.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** A product the store sells; maps to one Square item variation once Square is connected. */
@Entity
@Table(name = "item")
public class Item extends BaseEntity {

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "square_variation_id", length = 64)
    private String squareVariationId;

    @Column(nullable = false)
    private String name;

    @Column(length = 64)
    private String sku;

    @Column(length = 14)
    private String gtin;

    protected Item() {
    }

    public Item(UUID merchantId, String name) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId is required");
        rename(name);
    }

    public void rename(String newName) {
        this.name = Text.requireNonBlank(newName, "name");
    }

    public void changeSku(String newSku) {
        this.sku = Text.blankToNull(newSku);
    }

    /** Stores the barcode digits only; rejects barcodes with a wrong check digit. */
    public void changeGtin(String newGtin) {
        this.gtin = Gtin.normalize(newGtin);
    }

    public void linkSquare(String squareVariationId) {
        this.squareVariationId = Text.requireNonBlank(squareVariationId, "squareVariationId");
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getSquareVariationId() {
        return squareVariationId;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public String getGtin() {
        return gtin;
    }
}
