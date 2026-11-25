package com.shelflife.notify;

/**
 * Sends email. The app talks only to this interface, so the provider (SMTP, SES, Postmark, ...)
 * can be chosen at launch without touching the digest code. Implementations throw on failure so
 * the caller can retry.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
