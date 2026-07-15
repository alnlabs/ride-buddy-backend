package com.alnlabs.ridebuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder mailer — no SMTP yet. Logs verification codes for local/dev use.
 */
@Service
public class PlaceholderMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderMailService.class);

    @Override
    public void sendOfficeEmailVerification(String toEmail, String code) {
        log.info(
                "[mail-placeholder] Office email verification for {} — code {} (email service not wired yet)",
                toEmail,
                code
        );
    }
}
