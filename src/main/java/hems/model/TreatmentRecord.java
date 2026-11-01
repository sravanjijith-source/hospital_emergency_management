package hems.model;

import hems.util.InvalidStateException;
import hems.util.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * One emergency visit of one patient (maps to a row of the emergency_visits table).
 * The same patient can have many records over time, but only one open at once.
 *
 * Life cycle: WAITING -> startTreatment() -> IN_TREATMENT -> completeTreatment() -> COMPLETED.
 * The state methods only update THIS object. The service must call them AFTER the
 * database transaction has committed, so memory never gets ahead of the database.
 */
public class TreatmentRecord {

    /**
     * Queue order: most severe first, then earliest arrival, then lowest visit ID
     * (final tie-break so the order is always deterministic).
     * Patient ID is never used for priority.
     */
    public static final Comparator<TreatmentRecord> PRIORITY_ORDER =
            Comparator.comparingInt((TreatmentRecord v) -> v.getSeverity().getLevel())
                    .thenComparing(TreatmentRecord::getArrivalTime)
                    .thenComparingLong(TreatmentRecord::getVisitId);

    private long visitId; // 0 until the DAO inserts the row and assigns the real ID
    private final String patientId;
    private String patientName; // Display cache for UI convenience
    private final Severity severity;
    private final LocalDateTime arrivalTime;
    private TreatmentStatus status;
    private String assignedDoctorId;   // Preserved from DB even if Doctor object cache is absent
    private Doctor assignedDoctor;     // null until treatment starts
    private LocalDateTime startTime;   // null until treatment starts
    private LocalDateTime endTime;     // null until treatment completes

    /** Creates a brand-new WAITING visit that arrives now. */
    public static TreatmentRecord newWaitingVisit(String patientId, Severity severity) {
        return newWaitingVisit(patientId, severity, now());
    }

    /** Same, with an explicit arrival time (useful for tests). */
    public static TreatmentRecord newWaitingVisit(String patientId, Severity severity,
                                                  LocalDateTime arrivalTime) {
        return new TreatmentRecord(0, patientId, severity, arrivalTime,
                TreatmentStatus.WAITING, null, null, null, null);
    }

    /** Constructor used when Doctor entity object is available. */
    public TreatmentRecord(long visitId, String patientId, Severity severity,
                           LocalDateTime arrivalTime, TreatmentStatus status,
                           Doctor assignedDoctor, LocalDateTime startTime, LocalDateTime endTime) {
        this(visitId, patientId, severity, arrivalTime, status,
                assignedDoctor != null ? assignedDoctor.getId() : null,
                assignedDoctor, startTime, endTime);
    }

    /** Full constructor used by the DAO to rebuild a record read from MySQL. */
    public TreatmentRecord(long visitId, String patientId, Severity severity,
                           LocalDateTime arrivalTime, TreatmentStatus status,
                           String assignedDoctorId, Doctor assignedDoctor,
                           LocalDateTime startTime, LocalDateTime endTime) {
        if (patientId == null || severity == null || arrivalTime == null || status == null) {
            throw new ValidationException(
                    "A visit needs a patient ID, severity, arrival time and status.");
        }
        this.visitId = visitId;
        this.patientId = patientId;
        this.severity = severity;
        this.arrivalTime = arrivalTime;
        this.status = status;
        this.assignedDoctorId = (assignedDoctor != null) ? assignedDoctor.getId() : assignedDoctorId;
        this.assignedDoctor = assignedDoctor;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ---------- state changes (call only after the DB commit succeeded) ----------

    /** WAITING -> IN_TREATMENT with the given doctor and start time. */
    public void startTreatment(Doctor doctor, LocalDateTime when) {
        if (doctor == null || when == null) {
            throw new ValidationException("A doctor and a start time are required.");
        }
        if (!status.canMoveTo(TreatmentStatus.IN_TREATMENT)) {
            throw new InvalidStateException("Visit " + visitId + " is " + status
                    + ". Only a WAITING visit can start treatment.");
        }
        this.assignedDoctor = doctor;
        this.assignedDoctorId = doctor.getId();
        this.startTime = when;
        this.status = TreatmentStatus.IN_TREATMENT;
    }

    /** IN_TREATMENT -> COMPLETED with the given end time. */
    public void completeTreatment(LocalDateTime when) {
        if (when == null) {
            throw new ValidationException("An end time is required.");
        }
        if (!status.canMoveTo(TreatmentStatus.COMPLETED)) {
            throw new InvalidStateException("Visit " + visitId + " is " + status
                    + ". Only a visit that is IN_TREATMENT can be completed.");
        }
        if ((assignedDoctor == null && assignedDoctorId == null) || startTime == null) {
            throw new InvalidStateException("Visit " + visitId
                    + " has no doctor or start time, so it cannot be completed.");
        }
        if (when.isBefore(startTime)) {
            throw new InvalidStateException("End time cannot be before start time.");
        }
        this.endTime = when;
        this.status = TreatmentStatus.COMPLETED;
    }

    /** Called once by the DAO after the INSERT returns the generated ID. */
    public void assignVisitId(long id) {
        if (visitId != 0) {
            throw new IllegalStateException("Visit ID is already set.");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("Visit ID must be positive.");
        }
        this.visitId = id;
    }

    /** Timestamps are cut to milliseconds so they match MySQL DATETIME(3) exactly. */
    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // ---------- getters and setters ----------

    public long getVisitId() {
        return visitId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName != null ? patientName : patientId;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    /** The visit date is simply the date part of the arrival time. */
    public LocalDate getVisitDate() {
        return arrivalTime.toLocalDate();
    }

    public TreatmentStatus getStatus() {
        return status;
    }

    public Doctor getAssignedDoctor() {
        return assignedDoctor;
    }

    public void setAssignedDoctor(Doctor assignedDoctor) {
        this.assignedDoctor = assignedDoctor;
        if (assignedDoctor != null) {
            this.assignedDoctorId = assignedDoctor.getId();
        }
    }

    public String getAssignedDoctorId() {
        if (assignedDoctor != null) {
            return assignedDoctor.getId();
        }
        return assignedDoctorId;
    }

    public String getAssignedDoctorName() {
        if (assignedDoctor != null) {
            return assignedDoctor.getName();
        }
        if (assignedDoctorId != null) {
            return assignedDoctorId;
        }
        return "-";
    }

    /** null until treatment starts; the UI should show "-" or "Not started". */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** null until treatment completes; the UI should show "In progress". */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    @Override
    public String toString() {
        return "Visit #" + visitId + " [patient " + patientId + ", " + severity + ", " + status + "]";
    }
}
