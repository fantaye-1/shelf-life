package com.shelflife.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller parameter (a {@link java.util.UUID}) that receives the calling merchant's id.
 * Every API call is scoped to this merchant.
 *
 * <p>Until app login lands (M8) the id comes from the {@value CurrentMerchantResolver#HEADER} request
 * header. M8 replaces only {@link CurrentMerchantResolver}; controllers stay the same.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMerchant {
}
