package hems.model;

import hems.util.ValidationException;
import hems.util.Validator;

/**
 * A doctor and current availability. The status setter does not check transition
 * rules; HospitalSystem (service layer) enforces them so the rules live in one place.
 */
public class Doctor extends Person {
    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 90;

    private final String specialization;
    private DoctorStatus status;

    /** New doctors start AVAILABLE. */
    public Doctor(String id, String name, int age, String specialization) {
        this(id, name, age, specialization, DoctorStatus.AVAILABLE);
    }

    public Doctor(String id, String name, int age, String specialization, DoctorStatus status) {
        super(id, name, age, MIN_AGE, MAX_AGE);
        this.specialization = Validator.validateSpecialization(specialization);
        if (status == null) {
            throw new ValidationException("Doctor status is required.");
        }
        this.status = status;
    }

    @Override
    public String getRole() {
        return "Doctor";
    }

    public String getSpecialization() {
        return specialization;
    }

    public DoctorStatus getStatus() {
        return status;
    }

    public void setStatus(DoctorStatus status) {
        if (status == null) {
            throw new ValidationException("Doctor status is required.");
        }
        this.status = status;
    }

    public boolean isAvailable() {
        return status == DoctorStatus.AVAILABLE;
    }

    @Override
    public String toString() {
        return super.toString() + " | " + specialization + " | " + status;
    }
}
