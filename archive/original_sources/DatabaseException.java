package hems.util;

/**
 * Wraps any SQLException or connection/config problem.
 *
 * Pass a friendly message for the user and the original exception as the cause.
 * Log the cause; never show it (it can contain SQL or connection details).
 */
public class DatabaseException extends HospitalException {
    private static final long serialVersionUID = 1L;

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(String message) {
        super(message);
    }
}
