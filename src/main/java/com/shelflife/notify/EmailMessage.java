package com.shelflife.notify;

import java.util.Objects;

/** A plain-text email. HTML mail can come later; plain text reads fine on a phone. */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to, "to is required");
        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(body, "body is required");
    }
}
