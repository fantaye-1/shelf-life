package com.shelflife.web;

/** The caller didn't say which merchant it is, or named one that doesn't exist. Answered with 401. */
public class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(String message) {
        super(message);
    }
}
