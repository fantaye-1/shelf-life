package com.shelflife.store;

import com.shelflife.common.BaseEntity;
import com.shelflife.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** A store owner's business: the tenant. Everything else belongs to exactly one merchant. */
@Entity
@Table(name = "merchant")
public class Merchant extends BaseEntity {

    @Column(name = "square_merchant_id", unique = true, length = 64)
    private String squareMerchantId;

    @Column(nullable = false)
    private String name;

    // Set by the Square connect flow (M6); values are already encrypted when they get here.
    @Column(name = "encrypted_access_token", length = 4096)
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", length = 4096)
    private String encryptedRefreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    protected Merchant() {
    }

    public Merchant(String name) {
        rename(name);
    }

    public void rename(String newName) {
        this.name = Text.requireNonBlank(newName, "name");
    }

    public void linkSquare(String squareMerchantId) {
        this.squareMerchantId = Text.requireNonBlank(squareMerchantId, "squareMerchantId");
    }

    public String getName() {
        return name;
    }

    public String getSquareMerchantId() {
        return squareMerchantId;
    }

    public boolean isSquareConnected() {
        return squareMerchantId != null && encryptedAccessToken != null;
    }

    @Override
    public String toString() {
        // Never print tokens, even encrypted ones.
        return "Merchant[id=" + getId() + ", name=" + name + "]";
    }
}
