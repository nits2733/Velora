package com.velora.backend.exception;

/**
 * Thrown when an authenticated user attempts an action on a resource they do not own
 * or are not permitted to act on (e.g. cancelling another customer's booking).
 */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
