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
import hems.model.TreatmentStatus;
import hems.service.DashboardStats;
import hems.service.HospitalSystem;
import hems.util.InvalidStateException;
import hems.util.NoAvailableDoctorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("db")
@DisplayName("Phase 5 Full Lifecycle Integration Test (hems_test)")
class FullLifecycleIntegrationTest {

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
        if (user == null || user.trim().isEmpty()) user = "hems_user";
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
    @DisplayName("Complete end-to-end clinical triage lifecycle")
    void testFullTriageLifecycle() throws InterruptedException {
        // Step 1: Register doctors with mixed availability (IDs auto-assigned: D-001, D-002, D-003)
        Doctor docGrey = system.registerDoctor("Dr. Meredith Grey", 38, "Trauma Surgery", DoctorStatus.AVAILABLE);
        Doctor docShepherd = system.registerDoctor("Dr. Derek Shepherd", 45, "Neurosurgery", DoctorStatus.AVAILABLE);
        Doctor docKarev = system.registerDoctor("Dr. Alex Karev", 36, "Pediatric Emergency", DoctorStatus.OFF_DUTY);

        assertEquals("D-001", docGrey.getId());
        assertEquals("D-002", docShepherd.getId());
        assertEquals("D-003", docKarev.getId());

        // Step 2: Register patients with mixed severities and distinct arrival order (P-0001 to P-0005)
        TreatmentRecord vLow = system.registerPatient("John Low", 25, Severity.LOW);
        Thread.sleep(15);
        TreatmentRecord vMed = system.registerPatient("Jane Med", 32, Severity.MEDIUM);
        Thread.sleep(15);
        TreatmentRecord vHigh1 = system.registerPatient("Bob High 1", 45, Severity.HIGH);
        Thread.sleep(15);
        TreatmentRecord vCrit = system.registerPatient("Alice Crit 1", 50, Severity.CRITICAL);
        Thread.sleep(15);
        TreatmentRecord vHigh2 = system.registerPatient("Charlie High 2", 29, Severity.HIGH);

        String idCrit = vCrit.getPatientId();
        String idHigh1 = vHigh1.getPatientId();
        String idHigh2 = vHigh2.getPatientId();
        String idMed = vMed.getPatientId();
        String idLow = vLow.getPatientId();

        // Step 3: Confirm deterministic priority queue ordering
        List<TreatmentRecord> queue = system.getWaitingQueueSnapshot();
        assertEquals(5, queue.size());
        assertEquals(idCrit, queue.get(0).getPatientId(), "CRITICAL patient must be #1 in queue");
        assertEquals(idHigh1, queue.get(1).getPatientId(), "First HIGH arrival must be #2");
        assertEquals(idHigh2, queue.get(2).getPatientId(), "Second HIGH arrival must be #3");
        assertEquals(idMed, queue.get(3).getPatientId(), "MEDIUM patient must be #4");
        assertEquals(idLow, queue.get(4).getPatientId(), "LOW patient must be #5");

        // Step 4: Assign first patient (CRITICAL)
        TreatmentRecord assigned1 = system.assignNextPatient();
        assertEquals(idCrit, assigned1.getPatientId());
        assertEquals(TreatmentStatus.IN_TREATMENT, assigned1.getStatus());
        String doc1Id = assigned1.getAssignedDoctorId();
        assertNotNull(doc1Id);
        assertEquals(DoctorStatus.BUSY, system.findDoctorById(doc1Id).getStatus());

        // Step 5: Assign second patient (HIGH)
        TreatmentRecord assigned2 = system.assignNextPatient();
        assertEquals(idHigh1, assigned2.getPatientId());
        assertEquals(TreatmentStatus.IN_TREATMENT, assigned2.getStatus());
        String doc2Id = assigned2.getAssignedDoctorId();
        assertNotNull(doc2Id);
        assertNotEquals(doc1Id, doc2Id, "Should be assigned to the second available doctor");
        assertEquals(DoctorStatus.BUSY, system.findDoctorById(doc2Id).getStatus());

        // Step 6: Try assigning with no available doctors (D-001 and D-002 are BUSY, D-003 is OFF_DUTY)
        assertThrows(NoAvailableDoctorException.class, () -> system.assignNextPatient());

        // Verify queue is untouched (High2, Med, Low still waiting in order)
        List<TreatmentRecord> queueAfterFailedAssign = system.getWaitingQueueSnapshot();
        assertEquals(3, queueAfterFailedAssign.size());
        assertEquals(idHigh2, queueAfterFailedAssign.get(0).getPatientId());
        assertEquals(idMed, queueAfterFailedAssign.get(1).getPatientId());
        assertEquals(idLow, queueAfterFailedAssign.get(2).getPatientId());

        // Step 7: Complete treatment for first assigned patient (CRITICAL)
        TreatmentRecord completed1 = system.completeTreatment(assigned1.getVisitId());
        assertEquals(TreatmentStatus.COMPLETED, completed1.getStatus());
        assertNotNull(completed1.getEndTime());
        assertEquals(DoctorStatus.AVAILABLE, system.findDoctorById(doc1Id).getStatus(),
                "Doctor must return to AVAILABLE status after completion");

        // Step 8: Assign next patient now that doc1 is available
        TreatmentRecord assigned3 = system.assignNextPatient();
        assertEquals(idHigh2, assigned3.getPatientId());
        assertEquals(doc1Id, assigned3.getAssignedDoctorId());
        assertEquals(DoctorStatus.BUSY, system.findDoctorById(doc1Id).getStatus());

        // Step 9: Returning patient visit for critical patient (previous visit is COMPLETED)
        TreatmentRecord returnVisit = system.createEmergencyVisit(idCrit, Severity.CRITICAL);
        assertNotNull(returnVisit);
        assertEquals(TreatmentStatus.WAITING, returnVisit.getStatus());

        // Attempting to create another visit while returnVisit is open must fail
        assertThrows(InvalidStateException.class, () ->
                system.createEmergencyVisit(idCrit, Severity.LOW));

        // Step 10: View patient history from MySQL
        List<TreatmentRecord> critHistory = system.getPatientHistory(idCrit);
        assertEquals(2, critHistory.size());
        assertEquals(returnVisit.getVisitId(), critHistory.get(0).getVisitId());
        assertEquals(TreatmentStatus.WAITING, critHistory.get(0).getStatus());
        assertNull(critHistory.get(0).getEndTime());

        assertEquals(assigned1.getVisitId(), critHistory.get(1).getVisitId());
        assertEquals(TreatmentStatus.COMPLETED, critHistory.get(1).getStatus());
        assertNotNull(critHistory.get(1).getEndTime());

        // Step 11: Close and reopen the app (simulate application restart)
        HospitalSystem restarted = new HospitalSystem(patientDao, doctorDao, treatmentRecordDao, sequenceDao, testConnectionProvider);
        restarted.reloadFromDatabase();

        // Verify restarted queue: returnVisit (CRITICAL) must be first!
        List<TreatmentRecord> restartedQueue = restarted.getWaitingQueueSnapshot();
        assertEquals(3, restartedQueue.size()); // returnVisit (CRIT), Med, Low
        assertEquals(idCrit, restartedQueue.get(0).getPatientId());
        assertEquals(Severity.CRITICAL, restartedQueue.get(0).getSeverity());
        assertEquals(idMed, restartedQueue.get(1).getPatientId());
        assertEquals(Severity.MEDIUM, restartedQueue.get(1).getSeverity());
        assertEquals(idLow, restartedQueue.get(2).getPatientId());
        assertEquals(Severity.LOW, restartedQueue.get(2).getSeverity());

        // Verify restarted active treatments
        List<TreatmentRecord> active = restarted.getActiveTreatments();
        assertEquals(2, active.size()); // idHigh1 and idHigh2
        assertTrue(active.stream().anyMatch(v -> v.getPatientId().equals(idHigh1)));
        assertTrue(active.stream().anyMatch(v -> v.getPatientId().equals(idHigh2)));

        // Verify restarted dashboard counts
        DashboardStats stats = restarted.getDashboardStats();
        assertEquals(5, stats.getTotalPatients());
        assertEquals(6, stats.getTotalVisits()); // 5 initial + 1 return visit
        assertEquals(3, stats.getWaitingVisits());
        assertEquals(2, stats.getInTreatmentVisits());
        assertEquals(1, stats.getCompletedVisits());
        assertEquals(3, stats.getTotalDoctors());
        assertEquals(0, stats.getAvailableDoctors());
        assertEquals(2, stats.getBusyDoctors());
        assertEquals(1, stats.getOffDutyDoctors());
    }
}
