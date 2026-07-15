package com.alnlabs.ridebuddy.mail;

/**
 * Placeholder mail integration. Production will send real messages;
 * the default implementation only logs / no-ops.
 */
public interface MailService {

    /**
     * Send a verification code for office email. Implementations may throw if delivery fails.
     */
    void sendOfficeEmailVerification(String toEmail, String code);
}
