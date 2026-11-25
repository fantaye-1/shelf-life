package com.shelflife.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default sender for development: writes the email to the log instead of sending it. Active
 * when {@code shelflife.mail.provider} is {@code log} or unset. A real provider is added before
 * launch (M10) under another value of that property.
 */
@Component
@ConditionalOnProperty(name = "shelflife.mail.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Email (not sent, mail provider is 'log')\nTo: {}\nSubject: {}\n\n{}",
                message.to(), message.subject(), message.body());
    }
}
