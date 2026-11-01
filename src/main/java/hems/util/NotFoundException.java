package hems.util;

/** A patient, doctor or visit with the requested ID does not exist. */
public class NotFoundException extends HospitalException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
