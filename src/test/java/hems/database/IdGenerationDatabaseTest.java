package hems.database;

import hems.dao.DoctorDao;
import hems.dao.PatientDao;
import hems.dao.SequenceDao;
import hems.dao.TreatmentRecordDao;
import hems.model.Doctor;
import hems.model.DoctorStatus;
import hems.model.Patient;
import hems.model.Severity;
import hems.model.TreatmentRecord;
import hems.service.HospitalSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the auto-generated patient and doctor ID requirement.
 * Labelled as MySQL-dependent, targeting hems_test only.
 */
@Tag("db")
@DisplayName("MySQL-Dependent Auto-Generated ID Verification (hems_test)")
class IdGenerationDatabaseTest {

    private static ConnectionProvider testConnectionProvider;
    private static PatientDao patientDao;
    private static DoctorDao doctorDao;
    private static TreatmentRecordDao treatmentRecordDao;
    private static SequenceDao sequenceDao;

    private HospitalSystem system;

    @BeforeAll
    static void initDatabase() {
        String envUrl = System.getenv(DatabaseConfig.ENV_URL);
        String testUrl = (envUrl != null && envUrl.contains("/hems"))
                ? envUrl.replace("/hems", "/hems_test")
                : "jdbc:mysql://localhost:3306/hems_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        String user = System.getenv(DatabaseConfig.ENV_USER);
        if (user == null || user.trim().isEmpty()) {
            user = "hems_user";
        }
        String password = System.getenv(DatabaseConfig.ENV_PASSWORD);

        DatabaseConfig testConfig = new DatabaseConfig(testUrl, user, password);
        testConnectionProvider = new ConnectionProvider(testConfig);

        patientDao = new PatientDao();
        doctorDao = new DoctorDao();
        treatmentRecordDao = new TreatmentRecordDao();
        sequenceDao = new SequenceDao();

        SchemaInitializer initializer = new SchemaInitializer(testConnectionProvider);
        initializer.initialize();
    }

    @BeforeEach
    void setUp() throws SQLException {
        cleanTestData();
        system = new HospitalSystem(patientDao, doctorDao, treatmentRecordDao, sequenceDao, testConnectionProvider);
    }

    @AfterEach
    void tearDown() throws SQLException {
        cleanTestData();
    }

    private void cleanTestData() throws SQLException {
        try (Connection conn = testConnectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM emergency_visits");
            stmt.execute("DELETE FROM doctors");
            stmt.execute("DELETE FROM patients");
            stmt.execute("DELETE FROM id_sequences");
        }
    }

    @Test
    @DisplayName("a) First patient gets P-0001 and first doctor gets D-001 on a clean test database")
    void testFirstPatientAndDoctorOnCleanDatabase() {
        TreatmentRecord patientVisit = system.registerPatient("First Patient", 30, Severity.HIGH);
        assertNotNull(patientVisit);
        assertEquals("P-0001", patientVisit.getPatientId(), "Clean DB first patient must receive ID P-0001");

        Doctor doctor = system.registerDoctor("First Doctor", 42, "Emergency", DoctorStatus.AVAILABLE);
        assertNotNull(doctor);
        assertEquals("D-001", doctor.getId(), "Clean DB first doctor must receive ID D-001");
    }

    @Test
    @DisplayName("b) IDs increment correctly and are unique across many registrations")
    void testIncrementalAndUniqueIdsAcrossManyRegistrations() {
        List<String> patientIds = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            TreatmentRecord rec = system.registerPatient("Patient " + i, 20 + i, Severity.MEDIUM);
            patientIds.add(rec.getPatientId());
            String expectedId = String.format("P-%04d", i);
            assertEquals(expectedId, rec.getPatientId());
        }

        List<String> doctorIds = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Doctor doc = system.registerDoctor("Doctor " + i, 30 + i, "Spec " + i, DoctorStatus.AVAILABLE);
            doctorIds.add(doc.getId());
            String expectedId = String.format("D-%03d", i);
            assertEquals(expectedId, doc.getId());
        }

        // Verify uniqueness
        Set<String> uniquePatientIds = new HashSet<>(patientIds);
        assertEquals(15, uniquePatientIds.size(), "All patient IDs must be distinct");

        Set<String> uniqueDoctorIds = new HashSet<>(doctorIds);
        assertEquals(10, uniqueDoctorIds.size(), "All doctor IDs must be distinct");
    }

    @Test
    @DisplayName("c) Two threads registering at once never get the same ID")
    void testConcurrentRegistrationsNeverDuplicateId() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Set<String> generatedPatientIds = Collections.synchronizedSet(new HashSet<>());
        Set<String> generatedDoctorIds = Collections.synchronizedSet(new HashSet<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to hit registration simultaneously
                    TreatmentRecord rec = system.registerPatient("Concurrent Pat " + index, 25, Severity.LOW);
                    generatedPatientIds.add(rec.getPatientId());

                    Doctor doc = system.registerDoctor("Concurrent Doc " + index, 40, "General", DoctorStatus.AVAILABLE);
                    generatedDoctorIds.add(doc.getId());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release threads
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent registrations should finish within timeout");
        assertTrue(errors.isEmpty(), "No thread should fail: " + errors);

        // Crucial race-free assertion: every thread must obtain a distinct ID
        assertEquals(threadCount, generatedPatientIds.size(),
                "Race-free counter must produce 10 unique patient IDs across concurrent threads");
        assertEquals(threadCount, generatedDoctorIds.size(),
                "Race-free counter must produce 10 unique doctor IDs across concurrent threads");
    }

    @Test
    @DisplayName("d) A registration that fails and rolls back does not consume a number")
    void testRollbackDoesNotConsumeSequenceNumber() throws SQLException {
        // Step 1: Register initial patient -> consumes P-0001
        TreatmentRecord first = system.registerPatient("Patient One", 30, Severity.LOW);
        assertEquals("P-0001", first.getPatientId());

        // Step 2: Open an explicit transaction, increment sequence to P-0002, but ROLLBACK without committing
        try (Connection conn = testConnectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            String nextId = sequenceDao.generateNextPatientId(conn, patientDao);
            assertEquals("P-0002", nextId, "Next candidate ID should have been P-0002");
            // Deliberate rollback to simulate failure
            conn.rollback();
        }

        // Step 3: Register through HospitalSystem in a new transaction
        // Since previous transaction rolled back, the counter row rolled back to 1.
        // The new registration must receive P-0002 (not P-0003)!
        TreatmentRecord afterRollback = system.registerPatient("Patient Two", 40, Severity.HIGH);
        assertEquals("P-0002", afterRollback.getPatientId(),
                "Rolled-back transaction must NOT consume or waste sequence numbers");
    }

    @Test
    @DisplayName("e) After creating a new service instance (simulated restart), the next ID continues from the last one")
    void testSimulatedRestartContinuesNextId() {
        TreatmentRecord p1 = system.registerPatient("Pat 1", 30, Severity.LOW);
        TreatmentRecord p2 = system.registerPatient("Pat 2", 35, Severity.MEDIUM);
        assertEquals("P-0001", p1.getPatientId());
        assertEquals("P-0002", p2.getPatientId());

        Doctor d1 = system.registerDoctor("Doc 1", 40, "ER", DoctorStatus.AVAILABLE);
        assertEquals("D-001", d1.getId());

        // Simulated application restart: brand new HospitalSystem instance reading from MySQL
        HospitalSystem restartedSystem = new HospitalSystem(patientDao, doctorDao, treatmentRecordDao, sequenceDao, testConnectionProvider);
        restartedSystem.reloadFromDatabase();

        // Register on restarted system -> must seamlessly continue sequence
        TreatmentRecord p3 = restartedSystem.registerPatient("Pat 3", 40, Severity.CRITICAL);
        assertEquals("P-0003", p3.getPatientId(), "Restarted system must continue patient ID sequence at P-0003");

        Doctor d2 = restartedSystem.registerDoctor("Doc 2", 45, "Surgery", DoctorStatus.AVAILABLE);
        assertEquals("D-002", d2.getId(), "Restarted system must continue doctor ID sequence at D-002");
    }

    @Test
    @DisplayName("f) Legacy IDs already in the table are preserved and the counter continues correctly after them")
    void testLegacyIdsPreservedAndCounterContinues() throws SQLException {
        // Insert legacy patients with older/manual format and a highest matching format ID "P-0007"
        try (Connection conn = testConnectionProvider.getConnection();
             PreparedStatement patStmt = conn.prepareStatement("INSERT INTO patients (patient_id, full_name, age) VALUES (?, ?, ?)");
             PreparedStatement docStmt = conn.prepareStatement("INSERT INTO doctors (doctor_id, full_name, age, specialization, availability) VALUES (?, ?, ?, ?, ?)")) {

            // Legacy IDs: purely numeric, alpha-numeric, and one matching format
            patStmt.setString(1, "1000");
            patStmt.setString(2, "Old Manual Patient");
            patStmt.setInt(3, 50);
            patStmt.executeUpdate();

            patStmt.setString(1, "P-0007");
            patStmt.setString(2, "Existing Pattern Patient");
            patStmt.setInt(3, 40);
            patStmt.executeUpdate();

            // Legacy Doctors
            docStmt.setString(1, "DOC-LEGACY");
            docStmt.setString(2, "Old Doctor");
            docStmt.setInt(3, 60);
            docStmt.setString(4, "Radiology");
            docStmt.setString(5, "AVAILABLE");
            docStmt.executeUpdate();

            docStmt.setString(1, "D-005");
            docStmt.setString(2, "Pattern Doctor");
            docStmt.setInt(3, 52);
            docStmt.setString(4, "Cardiology");
            docStmt.setString(5, "AVAILABLE");
            docStmt.executeUpdate();
        }

        // Initialize a new system instance which triggers ensureSeeded
        HospitalSystem freshSystem = new HospitalSystem(patientDao, doctorDao, treatmentRecordDao, sequenceDao, testConnectionProvider);
        freshSystem.reloadFromDatabase();

        // 1. Verify legacy IDs are preserved and findable
        Patient legacy1 = freshSystem.findPatientById("1000");
        assertNotNull(legacy1, "Legacy patient 1000 must be preserved");
        assertEquals("Old Manual Patient", legacy1.getName());

        Patient legacyPattern = freshSystem.findPatientById("P-0007");
        assertNotNull(legacyPattern, "Legacy pattern patient P-0007 must be preserved");

        Doctor legacyDoc = freshSystem.findDoctorById("DOC-LEGACY");
        assertNotNull(legacyDoc, "Legacy doctor DOC-LEGACY must be preserved");

        Doctor legacyPatternDoc = freshSystem.findDoctorById("D-005");
        assertNotNull(legacyPatternDoc, "Legacy pattern doctor D-005 must be preserved");

        // 2. Next registrations must start AFTER the highest existing pattern ID
        TreatmentRecord nextPat = freshSystem.registerPatient("New Patient", 28, Severity.HIGH);
        assertEquals("P-0008", nextPat.getPatientId(),
                "Sequence counter must seed from highest existing format ID (P-0007) and yield P-0008");

        Doctor nextDoc = freshSystem.registerDoctor("New Doctor", 38, "Neurology", DoctorStatus.AVAILABLE);
        assertEquals("D-006", nextDoc.getId(),
                "Sequence counter must seed from highest existing format ID (D-005) and yield D-006");

        // 3. Collision skip test:
        // If someone manually inserted P-0009 before the sequence reached it,
        // the sequence generator must skip P-0009 and assign P-0010 without failing.
        try (Connection conn = testConnectionProvider.getConnection();
             PreparedStatement patStmt = conn.prepareStatement("INSERT INTO patients (patient_id, full_name, age) VALUES (?, ?, ?)")) {
            patStmt.setString(1, "P-0009");
            patStmt.setString(2, "Pre-inserted collision candidate");
            patStmt.setInt(3, 33);
            patStmt.executeUpdate();
        }

        TreatmentRecord skippedPat = freshSystem.registerPatient("Collision Skipped Patient", 35, Severity.LOW);
        assertEquals("P-0010", skippedPat.getPatientId(),
                "Generator must automatically skip existing colliding ID (P-0009) to P-0010");
    }

    @Test
    @DisplayName("g) Search by generated ID and by name still works (including lower-case ID)")
    void testSearchByGeneratedIdAndName() {
        TreatmentRecord visit = system.registerPatient("Ravi Kumar", 34, Severity.HIGH);
        String patientId = visit.getPatientId();
        assertEquals("P-0001", patientId);

        Doctor doctor = system.registerDoctor("Dr. Ananya Sharma", 41, "Emergency Pediatrics", DoctorStatus.AVAILABLE);
        String docId = doctor.getId();
        assertEquals("D-001", docId);

        // 1. Search by exact ID
        assertNotNull(system.findPatientById("P-0001"));
        assertNotNull(system.findDoctorById("D-001"));

        // 2. Search by lowercase ID (e.g. "p-0001", "d-001")
        Patient lowerPatient = system.findPatientById("p-0001");
        assertNotNull(lowerPatient, "Lower-case ID 'p-0001' must find the patient");
        assertEquals("Ravi Kumar", lowerPatient.getName());

        Doctor lowerDoctor = system.findDoctorById("d-001");
        assertNotNull(lowerDoctor, "Lower-case ID 'd-001' must find the doctor");
        assertEquals("Dr. Ananya Sharma", lowerDoctor.getName());

        // 3. Search by partial, case-insensitive name
        List<Patient> patResults = system.findPatientsByName("ravi");
        assertEquals(1, patResults.size());
        assertEquals("P-0001", patResults.get(0).getId());

        List<Patient> patResultsUpper = system.findPatientsByName("KUMAR");
        assertEquals(1, patResultsUpper.size());
        assertEquals("P-0001", patResultsUpper.get(0).getId());

        List<Doctor> docResults = system.findDoctorsByName("ananya");
        assertEquals(1, docResults.size());
        assertEquals("D-001", docResults.get(0).getId());

        List<Doctor> docResultsUpper = system.findDoctorsByName("SHARMA");
        assertEquals(1, docResultsUpper.size());
        assertEquals("D-001", docResultsUpper.get(0).getId());
    }
}
