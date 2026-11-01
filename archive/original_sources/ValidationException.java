package hems.util;

/** Input is missing or invalid (empty name, age out of range, bad ID format...). */
public class ValidationException extends HospitalException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
