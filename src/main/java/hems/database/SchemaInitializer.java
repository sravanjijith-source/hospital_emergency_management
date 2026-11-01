package hems.database;

import hems.util.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initializes database tables using idempotent CREATE TABLE IF NOT EXISTS.
 * Never executes destructive operations (no DROP or TRUNCATE).
 */
public class SchemaInitializer {
    private static final Logger LOGGER = Logger.getLogger(SchemaInitializer.class.getName());

    private static final String DDL_PATIENTS =
            "CREATE TABLE IF NOT EXISTS patients (" +
            "  patient_id  VARCHAR(20)  PRIMARY KEY," +
            "  full_name   VARCHAR(100) NOT NULL," +
            "  age         INT          NOT NULL CHECK (age BETWEEN 0 AND 120)," +
            "  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  INDEX idx_patients_name (full_name)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    private static final String DDL_DOCTORS =
            "CREATE TABLE IF NOT EXISTS doctors (" +
            "  doctor_id      VARCHAR(20)  PRIMARY KEY," +
            "  full_name      VARCHAR(100) NOT NULL," +
            "  age            INT          NOT NULL CHECK (age BETWEEN 18 AND 90)," +
            "  specialization VARCHAR(80)  NOT NULL," +
            "  availability   ENUM('AVAILABLE','BUSY','OFF_DUTY') NOT NULL DEFAULT 'AVAILABLE'," +
            "  INDEX idx_doctors_name (full_name)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    private static final String DDL_SEQUENCES =
            "CREATE TABLE IF NOT EXISTS id_sequences (" +
            "  `sequence_name` VARCHAR(20) PRIMARY KEY," +
            "  `last_value`    BIGINT NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    private static final String DDL_VISITS =
            "CREATE TABLE IF NOT EXISTS emergency_visits (" +
            "  visit_id     BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  patient_id   VARCHAR(20) NOT NULL," +
            "  doctor_id    VARCHAR(20) NULL," +
            "  severity     ENUM('CRITICAL','HIGH','MEDIUM','LOW') NOT NULL," +
            "  status       ENUM('WAITING','IN_TREATMENT','COMPLETED') NOT NULL DEFAULT 'WAITING'," +
            "  visit_date   DATE NOT NULL," +
            "  arrival_time DATETIME(3) NOT NULL," +
            "  start_time   DATETIME(3) NULL," +
            "  end_time     DATETIME(3) NULL," +
            "  active_doctor_id VARCHAR(20) GENERATED ALWAYS AS (IF(status='IN_TREATMENT', doctor_id, NULL)) STORED," +
            "  open_patient_id  VARCHAR(20) GENERATED ALWAYS AS (IF(status<>'COMPLETED', patient_id, NULL)) STORED," +
            "  CONSTRAINT fk_visit_patient FOREIGN KEY (patient_id) REFERENCES patients(patient_id)," +
            "  CONSTRAINT fk_visit_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors(doctor_id)," +
            "  CONSTRAINT chk_visit_times CHECK (" +
            "    (status='WAITING'      AND doctor_id IS NULL     AND start_time IS NULL AND end_time IS NULL) OR" +
            "    (status='IN_TREATMENT' AND doctor_id IS NOT NULL AND start_time IS NOT NULL AND end_time IS NULL) OR" +
            "    (status='COMPLETED'    AND doctor_id IS NOT NULL AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time >= start_time))," +
            "  UNIQUE KEY uq_active_doctor (active_doctor_id)," +
            "  UNIQUE KEY uq_open_patient  (open_patient_id)," +
            "  INDEX idx_visits_status_priority (status, arrival_time)," +
            "  INDEX idx_visits_patient (patient_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    private final ConnectionProvider connectionProvider;

    public SchemaInitializer(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        LOGGER.info("Verifying and initializing database schema...");
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DDL_PATIENTS);
            stmt.execute(DDL_DOCTORS);
            stmt.execute(DDL_VISITS);
            stmt.execute(DDL_SEQUENCES);

            new hems.dao.SequenceDao().ensureSeeded(conn);

            LOGGER.info("Database schema initialized successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database schema", e);
            throw new DatabaseException("Failed to initialize database schema. Please check MySQL permissions.", e);
        }
    }
}
