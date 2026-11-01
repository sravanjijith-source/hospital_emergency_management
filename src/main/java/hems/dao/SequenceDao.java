package hems.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Data Access Object for atomic, race-free ID sequence generation using the id_sequences table.
 *
 * Sequence values are fetched and locked within active transactions using SELECT ... FOR UPDATE,
 * ensuring strict consistency, zero duplicate IDs, and automatic rollback on transaction failure.
 */
public class SequenceDao {

    public static final String SEQ_PATIENT = "PATIENT";
    public static final String SEQ_DOCTOR = "DOCTOR";

    public static final String DDL_SEQUENCES =
            "CREATE TABLE IF NOT EXISTS id_sequences (" +
            "  `sequence_name` VARCHAR(20) PRIMARY KEY," +
            "  `last_value`    BIGINT NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    public void initTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(DDL_SEQUENCES);
        }
    }

    /**
     * Seeds initial sequence counters from existing legacy IDs if not already present.
     * Starts at 0 (so the first generated ID will be 1) if no matching records exist.
     */
    public void ensureSeeded(Connection conn) throws SQLException {
        initTable(conn);
        seedSequenceIfMissing(conn, SEQ_PATIENT, findMaxPatientNumericSuffix(conn));
        seedSequenceIfMissing(conn, SEQ_DOCTOR, findMaxDoctorNumericSuffix(conn));
    }

    private void seedSequenceIfMissing(Connection conn, String seqName, long seedValue) throws SQLException {
        String sql = "INSERT IGNORE INTO id_sequences (`sequence_name`, `last_value`) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seqName);
            stmt.setLong(2, seedValue);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically locks the sequence row, increments it, formats the ID (P-0001, P-0002...),
     * skips any number that already exists (collision prevention), and updates last_value.
     */
    public String generateNextPatientId(Connection conn, PatientDao patientDao) throws SQLException {
        long current = lockAndGetLastValue(conn, SEQ_PATIENT, () -> findMaxPatientNumericSuffix(conn));
        String generatedId;
        do {
            current++;
            generatedId = String.format("P-%04d", current);
        } while (patientDao.existsById(conn, generatedId));

        updateLastValue(conn, SEQ_PATIENT, current);
        return generatedId;
    }

    /**
     * Atomically locks the sequence row, increments it, formats the ID (D-001, D-002...),
     * skips any number that already exists (collision prevention), and updates last_value.
     */
    public String generateNextDoctorId(Connection conn, DoctorDao doctorDao) throws SQLException {
        long current = lockAndGetLastValue(conn, SEQ_DOCTOR, () -> findMaxDoctorNumericSuffix(conn));
        String generatedId;
        do {
            current++;
            generatedId = String.format("D-%03d", current);
        } while (doctorDao.existsById(conn, generatedId));

        updateLastValue(conn, SEQ_DOCTOR, current);
        return generatedId;
    }

    private long lockAndGetLastValue(Connection conn, String sequenceName, SeedSupplier seedSupplier) throws SQLException {
        String selectSql = "SELECT `last_value` FROM id_sequences WHERE `sequence_name` = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, sequenceName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        // Row was not found; seed it and lock
        long seed = seedSupplier.getSeed();
        seedSequenceIfMissing(conn, sequenceName, seed);

        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, sequenceName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    private void updateLastValue(Connection conn, String sequenceName, long newValue) throws SQLException {
        String updateSql = "UPDATE id_sequences SET `last_value` = ? WHERE `sequence_name` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setLong(1, newValue);
            stmt.setString(2, sequenceName);
            stmt.executeUpdate();
        }
    }

    public long findMaxPatientNumericSuffix(Connection conn) throws SQLException {
        long max = 0;
        String sql = "SELECT patient_id FROM patients WHERE patient_id LIKE 'P-%' OR patient_id LIKE 'p-%'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id != null && (id.startsWith("P-") || id.startsWith("p-"))) {
                    String numPart = id.substring(2);
                    try {
                        long val = Long.parseLong(numPart);
                        if (val > max) max = val;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    public long findMaxDoctorNumericSuffix(Connection conn) throws SQLException {
        long max = 0;
        String sql = "SELECT doctor_id FROM doctors WHERE doctor_id LIKE 'D-%' OR doctor_id LIKE 'd-%'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id != null && (id.startsWith("D-") || id.startsWith("d-"))) {
                    String numPart = id.substring(2);
                    try {
                        long val = Long.parseLong(numPart);
                        if (val > max) max = val;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    @FunctionalInterface
    private interface SeedSupplier {
        long getSeed() throws SQLException;
    }
}
