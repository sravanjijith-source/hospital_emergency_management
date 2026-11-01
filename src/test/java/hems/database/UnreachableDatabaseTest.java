package hems.database;

import hems.dao.DoctorDao;
import hems.dao.PatientDao;
import hems.dao.TreatmentRecordDao;
import hems.service.HospitalSystem;
import hems.util.DatabaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fail-Fast Database Unreachable Tests")
class UnreachableDatabaseTest {

    @Test
    @DisplayName("Throws secret-free DatabaseException when MySQL server is unreachable")
    void testUnreachableDatabase() {
        // Points to an invalid port with short connect timeout to simulate MySQL being stopped
        String badUrl = "jdbc:mysql://127.0.0.1:59999/hems?connectTimeout=1000&socketTimeout=1000";
        DatabaseConfig badConfig = new DatabaseConfig(badUrl, "hems_user", "super_secret_password_123");

        ConnectionProvider provider = new ConnectionProvider(badConfig);

        // Verify testConnection returns false cleanly without throwing unhandled exceptions
        assertFalse(provider.testConnection(), "Unreachable MySQL should return false on testConnection()");

        // Verify getConnection throws DatabaseException
        DatabaseException ex = assertThrows(DatabaseException.class, provider::getConnection);
        assertNotNull(ex.getMessage());

        // CRITICAL SECURITY ASSERTION: Password must NEVER be exposed in message
        assertFalse(ex.getMessage().contains("super_secret_password_123"), "Password must never appear in exception message");

        // Verify HospitalSystem reload throws DatabaseException
        HospitalSystem system = new HospitalSystem(new PatientDao(), new DoctorDao(), new TreatmentRecordDao(), provider);
        DatabaseException reloadEx = assertThrows(DatabaseException.class, system::reloadFromDatabase);
        assertFalse(reloadEx.getMessage().contains("super_secret_password_123"));
    }
}
