package dev.yuroom.api;

/** A request parameter is present but unusable. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String parameter;

    public BadRequestException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }
}
