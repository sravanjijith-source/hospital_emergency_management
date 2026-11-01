package hems.util;

/**
 * An operation is not allowed in the current state, for example completing a
 * WAITING visit, completing a visit twice, or setting a BUSY doctor off duty.
 */
public class InvalidStateException extends HospitalException {
    private static final long serialVersionUID = 1L;

    public InvalidStateException(String message) {
        super(message);
    }
}
