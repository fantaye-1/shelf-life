package com.shelflife.web;

import java.util.UUID;

/**
 * A record doesn't exist for this merchant. Records owned by another merchant also get this,
 * so the API never reveals that another store's id exists.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, UUID id) {
        super(what + " " + id + " not found");
    }
}
