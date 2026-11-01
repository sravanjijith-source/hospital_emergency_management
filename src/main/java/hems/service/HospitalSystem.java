package hems.service;

import hems.dao.DoctorDao;
import hems.dao.PatientDao;
import hems.dao.SequenceDao;
import hems.dao.TreatmentRecordDao;
import hems.database.ConnectionProvider;
import hems.model.Doctor;
import hems.model.DoctorStatus;
import hems.model.Patient;
import hems.model.Severity;
import hems.model.TreatmentRecord;
import hems.model.TreatmentStatus;
import hems.util.DatabaseException;
import hems.util.HospitalException;
import hems.util.InvalidStateException;
import hems.util.NoAvailableDoctorException;
import hems.util.NotFoundException;
import hems.util.ValidationException;
import hems.util.Validator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core service layer orchestrating domain logic, the in-memory PriorityQueue,
 * and JDBC persistence.
 *
 * All state modifications are guarded by a fair ReentrantLock to prevent race
 * conditions between concurrent assignments, completions, or registrations.
 */
public class HospitalSystem {
    private static final Logger LOGGER = Logger.getLogger(HospitalSystem.class.getName());

    private final ReentrantLock lock = new ReentrantLock(true);

    // In-memory state and caches
    private final Map<String, Patient> patientMap = new HashMap<>();
    private final Map<String, Doctor> doctorMap = new HashMap<>();
    private final Map<Long, TreatmentRecord> visitMap = new HashMap<>();
    private final PriorityQueue<TreatmentRecord> waitingQueue =
            new PriorityQueue<>(TreatmentRecord.PRIORITY_ORDER);

    // In-memory visit ID generator for standalone unit test mode
    private final AtomicLong memoryVisitIdSequence = new AtomicLong(1000);
    private final AtomicLong memoryPatientSeq = new AtomicLong(0);
    private final AtomicLong memoryDoctorSeq = new AtomicLong(0);

    // Optional JDBC DAOs and connection provider (null in pure-logic test mode)
    private final PatientDao patientDao;
    private final DoctorDao doctorDao;
    private final TreatmentRecordDao treatmentRecordDao;
    private final SequenceDao sequenceDao;
    private final ConnectionProvider connectionProvider;

    /**
     * In-memory constructor for pure-logic unit testing without MySQL.
     */
    public HospitalSystem() {
        this(null, null, null, null, null);
    }

    /**
     * Database-backed constructor for production and DB integration tests.
     */
    public HospitalSystem(PatientDao patientDao, DoctorDao doctorDao,
                          TreatmentRecordDao treatmentRecordDao,
                          ConnectionProvider connectionProvider) {
        this(patientDao, doctorDao, treatmentRecordDao, new SequenceDao(), connectionProvider);
    }

    public HospitalSystem(PatientDao patientDao, DoctorDao doctorDao,
                          TreatmentRecordDao treatmentRecordDao,
                          SequenceDao sequenceDao,
                          ConnectionProvider connectionProvider) {
        this.patientDao = patientDao;
        this.doctorDao = doctorDao;
        this.treatmentRecordDao = treatmentRecordDao;
        this.sequenceDao = (sequenceDao != null) ? sequenceDao : new SequenceDao();
        this.connectionProvider = connectionProvider;
    }

    public boolean isDatabaseBacked() {
        return connectionProvider != null && patientDao != null;
    }

    // =========================================================================
    // PATIENT MANAGEMENT
    // =========================================================================

    /**
     * Registers a new patient with an auto-generated ID (P-0001, P-0002...)
     * and creates their initial WAITING emergency visit inside a single transaction.
     */
    public TreatmentRecord registerPatient(String name, int age, Severity initialSeverity) {
        lock.lock();
        try {
            String cleanName = Validator.validateName(name);
            int cleanAge = Validator.validateAge(age, Patient.MIN_AGE, Patient.MAX_AGE);
            if (initialSeverity == null) {
                throw new ValidationException("Initial emergency severity is required.");
            }

            String cleanId;
            Patient patient;
            TreatmentRecord visit;

            if (isDatabaseBacked()) {
                Connection conn = null;
                try {
                    conn = connectionProvider.getConnection();
                    conn.setAutoCommit(false);

                    // Generate next unique patient ID race-free inside the transaction
                    cleanId = sequenceDao.generateNextPatientId(conn, patientDao);

                    patient = new Patient(cleanId, cleanName, cleanAge);
                    visit = TreatmentRecord.newWaitingVisit(cleanId, initialSeverity);
                    visit.setPatientName(cleanName);

                    patientDao.insert(conn, patient);
                    treatmentRecordDao.insert(conn, visit);

                    conn.commit();
                } catch (Exception e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Rollback failed", ex); }
                    }
                    if (e instanceof HospitalException) {
                        throw (HospitalException) e;
                    }
                    throw new DatabaseException("Failed to register patient in database.", e);
                } finally {
                    closeConnectionSafely(conn);
                }
            } else {
                do {
                    cleanId = String.format("P-%04d", memoryPatientSeq.incrementAndGet());
                } while (patientMap.containsKey(cleanId));

                patient = new Patient(cleanId, cleanName, cleanAge);
                visit = TreatmentRecord.newWaitingVisit(cleanId, initialSeverity);
                visit.setPatientName(cleanName);
                visit.assignVisitId(memoryVisitIdSequence.incrementAndGet());
            }

            // Commit to memory
            patientMap.put(cleanId, patient);
            visitMap.put(visit.getVisitId(), visit);
            waitingQueue.add(visit);

            final String loggedId = cleanId;
            LOGGER.info(() -> "Registered patient " + loggedId + " with visit #" + visit.getVisitId() + " (" + initialSeverity + ")");
            return visit;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new emergency visit for an existing registered patient.
     * Rejects if the patient already has an open (WAITING or IN_TREATMENT) visit.
     */
    public TreatmentRecord createEmergencyVisit(String patientId, Severity severity) {
        lock.lock();
        try {
            String cleanId = Validator.validateId(patientId, "Patient ID");
            if (severity == null) {
                throw new ValidationException("Emergency severity is required.");
            }

            Patient patient = patientMap.get(cleanId);
            if (patient == null) {
                if (isDatabaseBacked()) {
                    patient = loadPatientFromDb(cleanId);
                }
                if (patient == null) {
                    throw new NotFoundException("Patient with ID '" + cleanId + "' was not found.");
                }
                patientMap.put(cleanId, patient);
            }

            // Check if patient already has an open visit
            boolean hasOpenVisit = visitMap.values().stream()
                    .anyMatch(v -> v.getPatientId().equals(cleanId) && v.getStatus().isOpen());
            if (hasOpenVisit) {
                throw new InvalidStateException("Patient " + cleanId + " already has an open emergency visit.");
            }

            TreatmentRecord visit = TreatmentRecord.newWaitingVisit(cleanId, severity);
            visit.setPatientName(patient.getName());

            if (isDatabaseBacked()) {
                Connection conn = null;
                try {
                    conn = connectionProvider.getConnection();
                    conn.setAutoCommit(false);

                    if (treatmentRecordDao.hasOpenVisit(conn, cleanId)) {
                        throw new InvalidStateException("Patient " + cleanId + " already has an open emergency visit in database.");
                    }

                    treatmentRecordDao.insert(conn, visit);
                    conn.commit();
                } catch (Exception e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Rollback failed", ex); }
                    }
                    if (e instanceof hems.util.HospitalException) {
                        throw (hems.util.HospitalException) e;
                    }
                    throw new DatabaseException("Failed to create emergency visit.", e);
                } finally {
                    closeConnectionSafely(conn);
                }
            } else {
                visit.assignVisitId(memoryVisitIdSequence.incrementAndGet());
            }

            visitMap.put(visit.getVisitId(), visit);
            waitingQueue.add(visit);

            LOGGER.info(() -> "Created new visit #" + visit.getVisitId() + " for patient " + cleanId);
            return visit;
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // DOCTOR MANAGEMENT
    // =========================================================================

    /**
     * Registers a new doctor with an auto-generated ID (D-001, D-002...) and starting status.
     */
    public Doctor registerDoctor(String name, int age, String specialization, DoctorStatus status) {
        lock.lock();
        try {
            String cleanName = Validator.validateName(name);
            int cleanAge = Validator.validateAge(age, Doctor.MIN_AGE, Doctor.MAX_AGE);
            String cleanSpec = Validator.validateSpecialization(specialization);
            if (status == null) {
                throw new ValidationException("Doctor status is required.");
            }
            DoctorStatus initialStatus = status;

            String cleanId;
            Doctor doctor;

            if (isDatabaseBacked()) {
                Connection conn = null;
                try {
                    conn = connectionProvider.getConnection();
                    conn.setAutoCommit(false);

                    // Generate next unique doctor ID race-free inside transaction
                    cleanId = sequenceDao.generateNextDoctorId(conn, doctorDao);

                    doctor = new Doctor(cleanId, cleanName, cleanAge, cleanSpec, initialStatus);

                    doctorDao.insert(conn, doctor);
                    conn.commit();
                } catch (Exception e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Rollback failed", ex); }
                    }
                    if (e instanceof HospitalException) {
                        throw (HospitalException) e;
                    }
                    throw new DatabaseException("Failed to register doctor in database.", e);
                } finally {
                    closeConnectionSafely(conn);
                }
            } else {
                do {
                    cleanId = String.format("D-%03d", memoryDoctorSeq.incrementAndGet());
                } while (doctorMap.containsKey(cleanId));

                doctor = new Doctor(cleanId, cleanName, cleanAge, cleanSpec, initialStatus);
            }

            doctorMap.put(cleanId, doctor);
            final String loggedDocId = cleanId;
            LOGGER.info(() -> "Registered doctor " + loggedDocId + " (" + cleanName + ", " + cleanSpec + ")");
            return doctor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates doctor availability manually (only AVAILABLE <-> OFF_DUTY).
     * Rejects changing BUSY doctors or setting an active doctor OFF_DUTY.
     */
    public void updateDoctorStatus(String doctorId, DoctorStatus targetStatus) {
        lock.lock();
        try {
            String cleanId = Validator.validateId(doctorId, "Doctor ID");
            if (targetStatus == null) {
                throw new ValidationException("Target status is required.");
            }

            Doctor doctor = doctorMap.get(cleanId);
            if (doctor == null) {
                throw new NotFoundException("Doctor with ID '" + cleanId + "' was not found.");
            }

            if (doctor.getStatus() == DoctorStatus.BUSY) {
                throw new InvalidStateException("Doctor " + doctor.getName()
                        + " is currently BUSY treating a patient. Status can only be changed by completing the treatment.");
            }

            if (!doctor.getStatus().canChangeManuallyTo(targetStatus)) {
                throw new InvalidStateException("Illegal status transition from "
                        + doctor.getStatus() + " to " + targetStatus + ".");
            }

            if (isDatabaseBacked()) {
                Connection conn = null;
                try {
                    conn = connectionProvider.getConnection();
                    conn.setAutoCommit(false);

                    doctorDao.updateStatus(conn, cleanId, targetStatus);
                    conn.commit();
                } catch (Exception e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Rollback failed", ex); }
                    }
                    if (e instanceof hems.util.HospitalException) {
                        throw (hems.util.HospitalException) e;
                    }
                    throw new DatabaseException("Failed to update doctor status.", e);
                } finally {
                    closeConnectionSafely(conn);
                }
            }

            doctor.setStatus(targetStatus);
            LOGGER.info(() -> "Doctor " + cleanId + " status updated to " + targetStatus);
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // TRIAGE & TREATMENT WORKFLOW
    // =========================================================================

    /**
     * Atomically assigns the highest-priority waiting patient to an AVAILABLE doctor.
     * Follows Section 4 rules:
     * 1. Empty queue check.
     * 2. Find AVAILABLE doctor. If none, keep queue untouched and throw NoAvailableDoctorException.
     * 3. Take highest-priority waiting visit.
     * 4. In one DB transaction: lock visit and doctor (SELECT ... FOR UPDATE),
     *    set visit IN_TREATMENT, set start time, set doctor BUSY.
     * 5. Only after commit, update in-memory state.
     */
    public TreatmentRecord assignNextPatient() {
        lock.lock();
        try {
            if (waitingQueue.isEmpty()) {
                throw new InvalidStateException("The emergency waiting queue is currently empty.");
            }

            // Peek at highest priority waiting visit
            TreatmentRecord nextVisit = waitingQueue.peek();

            // Find an AVAILABLE doctor (lowest ID for deterministic assignment)
            Doctor availableDoctor = doctorMap.values().stream()
                    .filter(Doctor::isAvailable)
                    .min(java.util.Comparator.comparing(Doctor::getId))
                    .orElse(null);

            if (availableDoctor == null) {
                // Queue is kept untouched!
                throw new NoAvailableDoctorException();
            }

            LocalDateTime startTime = TreatmentRecord.now();

            if (isDatabaseBacked()) {
                Connection conn = null;
                try {
                    conn = connectionProvider.getConnection();
                    conn.setAutoCommit(false);

                    // Re-verify and lock visit and doctor in DB
                    treatmentRecordDao.assignDoctorAndStart(conn, nextVisit.getVisitId(),
                            availableDoctor.getId(), startTime);
                    doctorDao.updateStatus(conn, availableDoctor.getId(), DoctorStatus.BUSY);

                    conn.commit();
                } catch (Exception e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Rollback failed", ex); }
                    }
                    if (e instanceof hems.util.HospitalException) {
                        throw (hems.util.HospitalException) e;
                    }
                    throw new DatabaseException("Database transaction failed during assignment.", e);
                } finally {
                    closeConnectionSafely(conn);
                }
            }

            // Successfully committed! Update in-memory objects and queue
            waitingQueue.poll(); // Now remove from queue
            nextVisit.startTreatment(availableDoctor, startTime);
            availableDoctor.setStatus(DoctorStatus.BUSY);

            LOGGER.info(() -> "Assigned visit #" + nextVisit.getVisitId() + " (Patient "
                    + nextVisit.getPatientId() + ") to Dr. " + availableDoctor.getName());
            return nextVisit;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically completes treatment for an active visit.
     * Sets end time, marks COMPLETED, and sets doctor back to AVAILABLE.
     */
    public TreatmentRecord completeTreatment(long visitId) {
        lock.lock();
        try {
            TreatmentRecord visit = visitMap.get(visitId);
            if (visit == null) {
                if (isDatabaseBacked()) {
                    visit = loadVisitFromDb(visitId);
                }
                if (visit == null) {
                    throw new NotFoundException("Treatment visit #" + visitId + " was not found.");
                }
                visitMap.put(visitId, visit);
            }

            if (visit.getStatus() != TreatmentStatus.IN_TREATMENT) {
                throw new InvalidStateException("Visit #" + visitId + " is currently "
                        + visit.getStatus() + ". Only visits that are IN_TREATMENT can be completed.");
            }

            Doctor doctor = visit.getAssignedDoctor();
            if (doctor == null && visit.getAssignedDoctorId() != null) {
                doctor = doctorMap.get(visit.getAssignedDoctorId());
            }

            if (doctor == null) {
                throw new InvalidStateException("Visit #" + visitId + " has no assigned doctor.");
            }

            LocalDateTime endTime = TreatmentRecord.now();
            if (endTime.isBefore(visit.getStartTime())) {
                endTime = visit.getStartTime().plusSeconds(1);
            }

            if (isDatabaseBacked()) {
                Connection conn = null;
                try {
                    conn = connectionProvider.getConnection();
                    conn.setAutoCommit(false);

                    treatmentRecordDao.completeTreatment(conn, visitId, endTime);
                    doctorDao.updateStatus(conn, doctor.getId(), DoctorStatus.AVAILABLE);

                    conn.commit();
                } catch (Exception e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Rollback failed", ex); }
                    }
                    if (e instanceof hems.util.HospitalException) {
                        throw (hems.util.HospitalException) e;
                    }
                    throw new DatabaseException("Database transaction failed during treatment completion.", e);
                } finally {
                    closeConnectionSafely(conn);
                }
            }

            // Commit memory changes
            visit.completeTreatment(endTime);
            doctor.setStatus(DoctorStatus.AVAILABLE);

            final TreatmentRecord finalVisit = visit;
            final Doctor finalDoctor = doctor;
            LOGGER.info(() -> "Completed treatment for visit #" + finalVisit.getVisitId()
                    + " with Dr. " + finalDoctor.getName());
            return visit;
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // QUERIES, SNAPSHOTS & SEARCH
    // =========================================================================

    /**
     * Returns a sorted copy snapshot of waiting visits in true priority order.
     * Never iterates PriorityQueue directly for display.
     */
    public List<TreatmentRecord> getWaitingQueueSnapshot() {
        lock.lock();
        try {
            List<TreatmentRecord> snapshot = new ArrayList<>(waitingQueue);
            snapshot.sort(TreatmentRecord.PRIORITY_ORDER);
            return Collections.unmodifiableList(snapshot);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all visits currently IN_TREATMENT.
     */
    public List<TreatmentRecord> getActiveTreatments() {
        lock.lock();
        try {
            List<TreatmentRecord> active = new ArrayList<>();
            for (TreatmentRecord v : visitMap.values()) {
                if (v.getStatus() == TreatmentStatus.IN_TREATMENT) {
                    active.add(v);
                }
            }
            // Sort by start time (earliest started first)
            active.sort((a, b) -> {
                if (a.getStartTime() == null || b.getStartTime() == null) return 0;
                return a.getStartTime().compareTo(b.getStartTime());
            });
            return Collections.unmodifiableList(active);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Searches patient by exact ID (case-insensitive).
     */
    public Patient findPatientById(String id) {
        lock.lock();
        try {
            if (id == null || id.trim().isEmpty()) {
                return null;
            }
            String cleanId = id.trim();
            Patient p = null;
            for (Map.Entry<String, Patient> entry : patientMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cleanId)) {
                    p = entry.getValue();
                    break;
                }
            }
            if (p == null && isDatabaseBacked()) {
                p = loadPatientFromDb(cleanId);
                if (p != null) {
                    patientMap.put(p.getId(), p);
                }
            }
            return p;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Searches patients by partial name (case-insensitive).
     */
    public List<Patient> findPatientsByName(String nameQuery) {
        lock.lock();
        try {
            if (nameQuery == null || nameQuery.trim().isEmpty()) {
                return getAllPatients();
            }
            String term = nameQuery.trim().toLowerCase(Locale.ROOT);
            if (isDatabaseBacked()) {
                try (Connection conn = connectionProvider.getConnection()) {
                    return patientDao.findByNameLike(conn, term);
                } catch (SQLException e) {
                    throw new DatabaseException("Failed to search patients by name.", e);
                }
            } else {
                List<Patient> matches = new ArrayList<>();
                for (Patient p : patientMap.values()) {
                    if (p.getName().toLowerCase(Locale.ROOT).contains(term)) {
                        matches.add(p);
                    }
                }
                return matches;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Searches doctor by exact ID (case-insensitive).
     */
    public Doctor findDoctorById(String id) {
        lock.lock();
        try {
            if (id == null || id.trim().isEmpty()) {
                return null;
            }
            String cleanId = id.trim();
            for (Map.Entry<String, Doctor> entry : doctorMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cleanId)) {
                    return entry.getValue();
                }
            }
            if (isDatabaseBacked()) {
                Doctor d = loadDoctorFromDb(cleanId);
                if (d != null) {
                    doctorMap.put(d.getId(), d);
                }
                return d;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Searches doctors by partial name (case-insensitive).
     */
    public List<Doctor> findDoctorsByName(String nameQuery) {
        lock.lock();
        try {
            if (nameQuery == null || nameQuery.trim().isEmpty()) {
                return getAllDoctors();
            }
            String term = nameQuery.trim().toLowerCase(Locale.ROOT);
            List<Doctor> matches = new ArrayList<>();
            for (Doctor d : doctorMap.values()) {
                if (d.getName().toLowerCase(Locale.ROOT).contains(term)) {
                    matches.add(d);
                }
            }
            return matches;
        } finally {
            lock.unlock();
        }
    }

    public List<Patient> getAllPatients() {
        lock.lock();
        try {
            List<Patient> list = new ArrayList<>(patientMap.values());
            list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return Collections.unmodifiableList(list);
        } finally {
            lock.unlock();
        }
    }

    public List<Doctor> getAllDoctors() {
        lock.lock();
        try {
            List<Doctor> list = new ArrayList<>(doctorMap.values());
            list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return Collections.unmodifiableList(list);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves full emergency visit history for a patient.
     * In database mode, this reads directly from MySQL.
     */
    public List<TreatmentRecord> getPatientHistory(String patientId) {
        lock.lock();
        try {
            String cleanId = Validator.validateId(patientId, "Patient ID");
            if (isDatabaseBacked()) {
                try (Connection conn = connectionProvider.getConnection()) {
                    List<TreatmentRecord> visits = treatmentRecordDao.findByPatientId(conn, cleanId, doctorMap);
                    Patient p = patientMap.get(cleanId);
                    if (p != null) {
                        p.replaceHistoryCache(visits);
                    }
                    return visits;
                } catch (SQLException e) {
                    throw new DatabaseException("Failed to load patient history from database.", e);
                }
            } else {
                List<TreatmentRecord> history = new ArrayList<>();
                for (TreatmentRecord v : visitMap.values()) {
                    if (v.getPatientId().equals(cleanId)) {
                        history.add(v);
                    }
                }
                history.sort((a, b) -> b.getArrivalTime().compareTo(a.getArrivalTime()));
                return history;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Calculates fresh dashboard counts.
     */
    public DashboardStats getDashboardStats() {
        lock.lock();
        try {
            int totalPatients = patientMap.size();
            int totalVisits = visitMap.size();
            int waiting = waitingQueue.size();
            int inTreatment = 0;
            int completed = 0;

            for (TreatmentRecord v : visitMap.values()) {
                if (v.getStatus() == TreatmentStatus.IN_TREATMENT) {
                    inTreatment++;
                } else if (v.getStatus() == TreatmentStatus.COMPLETED) {
                    completed++;
                }
            }

            int totalDoctors = doctorMap.size();
            int availableDocs = 0;
            int busyDocs = 0;
            int offDutyDocs = 0;

            for (Doctor d : doctorMap.values()) {
                switch (d.getStatus()) {
                    case AVAILABLE: availableDocs++; break;
                    case BUSY: busyDocs++; break;
                    case OFF_DUTY: offDutyDocs++; break;
                }
            }

            return new DashboardStats(totalPatients, totalVisits, waiting, inTreatment, completed,
                    totalDoctors, availableDocs, busyDocs, offDutyDocs);
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // RESTART RECOVERY & RECONCILIATION
    // =========================================================================

    /**
     * Rebuilds in-memory caches and priority queue from MySQL and safely reconciles inconsistencies.
     */
    public void reloadFromDatabase() {
        if (!isDatabaseBacked()) {
            return;
        }

        lock.lock();
        try {
            LOGGER.info("Starting reload and reconciliation from MySQL...");
            patientMap.clear();
            doctorMap.clear();
            visitMap.clear();
            waitingQueue.clear();

            try (Connection conn = connectionProvider.getConnection()) {
                // 1. Load all patients
                List<Patient> patients = patientDao.findAll(conn);
                for (Patient p : patients) {
                    patientMap.put(p.getId(), p);
                }

                // 2. Load all doctors
                List<Doctor> doctors = doctorDao.findAll(conn);
                for (Doctor d : doctors) {
                    doctorMap.put(d.getId(), d);
                }

                // 3. Load all visits
                List<TreatmentRecord> allVisits = treatmentRecordDao.findAll(conn, doctorMap);
                for (TreatmentRecord v : allVisits) {
                    // Populate patient name
                    Patient p = patientMap.get(v.getPatientId());
                    if (p != null) {
                        v.setPatientName(p.getName());
                    }
                    if (v.getAssignedDoctor() == null && v.getAssignedDoctorId() != null) {
                        Doctor doc = doctorMap.get(v.getAssignedDoctorId());
                        if (doc != null) {
                            v.setAssignedDoctor(doc);
                        }
                    }
                    visitMap.put(v.getVisitId(), v);
                    if (v.getStatus() == TreatmentStatus.WAITING) {
                        waitingQueue.add(v);
                    }
                }

                // 4. Reconcile doctor states
                // A BUSY doctor with no active visit becomes AVAILABLE (log warning)
                for (Doctor doc : doctorMap.values()) {
                    if (doc.getStatus() == DoctorStatus.BUSY) {
                        boolean hasActiveVisit = visitMap.values().stream()
                                .anyMatch(v -> v.getStatus() == TreatmentStatus.IN_TREATMENT
                                        && doc.getId().equals(v.getAssignedDoctorId()));
                        if (!hasActiveVisit) {
                            LOGGER.warning("Reconciliation: Doctor " + doc.getId()
                                    + " was BUSY in DB with no active treatment. Resetting to AVAILABLE.");
                            doc.setStatus(DoctorStatus.AVAILABLE);
                            doctorDao.updateStatus(conn, doc.getId(), DoctorStatus.AVAILABLE);
                        }
                    }
                }

                // 5. Flag any IN_TREATMENT visits with no doctor
                for (TreatmentRecord v : visitMap.values()) {
                    if (v.getStatus() == TreatmentStatus.IN_TREATMENT && v.getAssignedDoctor() == null) {
                        LOGGER.warning("Reconciliation warning: Visit #" + v.getVisitId()
                                + " is IN_TREATMENT but has no assigned doctor.");
                    }
                }

                LOGGER.info(() -> "Reload complete: " + patientMap.size() + " patients, "
                        + doctorMap.size() + " doctors, " + waitingQueue.size() + " waiting visits.");
            } catch (SQLException e) {
                throw new DatabaseException("Failed to reload data from MySQL.", e);
            }
        } finally {
            lock.unlock();
        }
    }

    private Patient loadPatientFromDb(String id) {
        if (!isDatabaseBacked()) return null;
        try (Connection conn = connectionProvider.getConnection()) {
            return patientDao.findById(conn, id);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load patient " + id, e);
            return null;
        }
    }

    private Doctor loadDoctorFromDb(String id) {
        if (!isDatabaseBacked()) return null;
        try (Connection conn = connectionProvider.getConnection()) {
            return doctorDao.findById(conn, id);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load doctor " + id, e);
            return null;
        }
    }

    private TreatmentRecord loadVisitFromDb(long visitId) {
        if (!isDatabaseBacked()) return null;
        try (Connection conn = connectionProvider.getConnection()) {
            return treatmentRecordDao.findById(conn, visitId, doctorMap);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load visit " + visitId, e);
            return null;
        }
    }

    private void closeConnectionSafely(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to close connection cleanly", e);
            }
        }
    }
}
