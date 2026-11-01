package hems.util;

/** A patient or doctor with the same ID already exists. */
public class DuplicateIdException extends HospitalException {
    private static final long serialVersionUID = 1L;

    public DuplicateIdException(String message) {
        super(message);
    }
}
