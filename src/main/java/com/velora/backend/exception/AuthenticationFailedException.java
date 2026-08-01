package com.velora.backend.exception;

/**
 * A 401 that carries its own explanation.
 * <p>
 * Distinct from Spring's {@code BadCredentialsException}, which the handler answers with
 * a deliberately vague "Invalid email or password" - correct for login, where naming the
 * wrong half would let an attacker enumerate accounts, but actively unhelpful anywhere
 * else. A client whose refresh token expired needs to know that's what happened so it can
 * send the user to log in again, rather than being told its email was wrong.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
