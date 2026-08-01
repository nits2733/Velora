package com.velora.backend.service;

import com.velora.backend.entity.User;

/**
 * How a reset token reaches its owner.
 * <p>
 * Deliberately an interface with a logging implementation for now: the recovery flow -
 * token generation, hashing, expiry, single use, session revocation - is the part with
 * security consequences, and it is finished. Delivery is a swap of one bean once an
 * email provider is chosen, and nothing above this line changes when it happens.
 */
public interface PasswordResetNotifier {

    void sendResetToken(User user, String rawToken);
}
