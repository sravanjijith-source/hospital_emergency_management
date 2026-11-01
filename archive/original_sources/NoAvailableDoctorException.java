package hems.util;

/** Assignment was requested but every doctor is BUSY or OFF_DUTY. */
public class NoAvailableDoctorException extends HospitalException {
    private static final long serialVersionUID = 1L;

    public NoAvailableDoctorException() {
        super("No doctor is available right now. The patient stays in the queue.");
    }
}
