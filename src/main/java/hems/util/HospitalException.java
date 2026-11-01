package hems.util;

/**
 * Base class for all application-specific errors.
 *
 * getMessage() is always a friendly, secret-free sentence that the UI may show
 * to the user. Technical detail (SQL errors, stack traces) lives in getCause()
 * and must only be written to the log, never displayed.
 */
public class HospitalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HospitalException(String message) {
        super(message);
    }

    public HospitalException(String message, Throwable cause) {
        super(message, cause);
    }
}
