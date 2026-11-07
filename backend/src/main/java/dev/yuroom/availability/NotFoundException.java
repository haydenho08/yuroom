package dev.yuroom.availability;

/** A requested resource (room, building) doesn't exist. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
