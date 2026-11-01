package hems.dao;

import hems.model.Doctor;
import hems.model.Severity;
import hems.model.TreatmentRecord;
import hems.model.TreatmentStatus;
import hems.util.InvalidStateException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for the 'emergency_visits' table.
 */
public class TreatmentRecordDao {

    public void insert(Connection conn, TreatmentRecord visit) throws SQLException {
        String sql = "INSERT INTO emergency_visits (patient_id, doctor_id, severity, status, visit_date, arrival_time, start_time, end_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, visit.getPatientId());
            stmt.setString(2, visit.getAssignedDoctorId());
            stmt.setString(3, visit.getSeverity().name());
            stmt.setString(4, visit.getStatus().name());
            stmt.setDate(5, Date.valueOf(visit.getVisitDate()));
            stmt.setTimestamp(6, Timestamp.valueOf(visit.getArrivalTime()));
            stmt.setTimestamp(7, visit.getStartTime() != null ? Timestamp.valueOf(visit.getStartTime()) : null);
            stmt.setTimestamp(8, visit.getEndTime() != null ? Timestamp.valueOf(visit.getEndTime()) : null);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long generatedId = rs.getLong(1);
                    visit.assignVisitId(generatedId);
                }
            }
        }
    }

    public boolean hasOpenVisit(Connection conn, String patientId) throws SQLException {
        String sql = "SELECT 1 FROM emergency_visits WHERE patient_id = ? AND status <> 'COMPLETED' LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patientId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public TreatmentRecord findById(Connection conn, long visitId, Map<String, Doctor> doctors) throws SQLException {
        String sql = "SELECT visit_id, patient_id, doctor_id, severity, status, arrival_time, start_time, end_time " +
                "FROM emergency_visits WHERE visit_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, visitId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs, doctors);
                }
            }
        }
        return null;
    }

    public List<TreatmentRecord> findByPatientId(Connection conn, String patientId, Map<String, Doctor> doctors) throws SQLException {
        String sql = "SELECT visit_id, patient_id, doctor_id, severity, status, arrival_time, start_time, end_time " +
                "FROM emergency_visits WHERE patient_id = ? ORDER BY arrival_time DESC";
        List<TreatmentRecord> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patientId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs, doctors));
                }
            }
        }
        return list;
    }

    public List<TreatmentRecord> findAll(Connection conn, Map<String, Doctor> doctors) throws SQLException {
        String sql = "SELECT visit_id, patient_id, doctor_id, severity, status, arrival_time, start_time, end_time " +
                "FROM emergency_visits ORDER BY arrival_time ASC";
        List<TreatmentRecord> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs, doctors));
            }
        }
        return list;
    }

    public List<TreatmentRecord> findWaitingVisits(Connection conn, Map<String, Doctor> doctors) throws SQLException {
        String sql = "SELECT visit_id, patient_id, doctor_id, severity, status, arrival_time, start_time, end_time " +
                "FROM emergency_visits WHERE status = 'WAITING' ORDER BY severity ASC, arrival_time ASC, visit_id ASC";
        List<TreatmentRecord> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs, doctors));
            }
        }
        return list;
    }

    public List<TreatmentRecord> findActiveTreatments(Connection conn, Map<String, Doctor> doctors) throws SQLException {
        String sql = "SELECT visit_id, patient_id, doctor_id, severity, status, arrival_time, start_time, end_time " +
                "FROM emergency_visits WHERE status = 'IN_TREATMENT' ORDER BY start_time ASC";
        List<TreatmentRecord> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs, doctors));
            }
        }
        return list;
    }

    /**
     * Atomically locks and transitions a waiting visit to IN_TREATMENT.
     */
    public void assignDoctorAndStart(Connection conn, long visitId, String doctorId, LocalDateTime startTime)
            throws SQLException {
        // Re-verify visit is WAITING and lock row
        String lockSql = "SELECT status FROM emergency_visits WHERE visit_id = ? FOR UPDATE";
        try (PreparedStatement lockStmt = conn.prepareStatement(lockSql)) {
            lockStmt.setLong(1, visitId);
            try (ResultSet rs = lockStmt.executeQuery()) {
                if (!rs.next()) {
                    throw new InvalidStateException("Visit #" + visitId + " does not exist.");
                }
                String currentStatus = rs.getString("status");
                if (!"WAITING".equals(currentStatus)) {
                    throw new InvalidStateException("Visit #" + visitId + " is no longer WAITING (current: " + currentStatus + ").");
                }
            }
        }

        // Lock doctor
        String lockDocSql = "SELECT availability FROM doctors WHERE doctor_id = ? FOR UPDATE";
        try (PreparedStatement lockDocStmt = conn.prepareStatement(lockDocSql)) {
            lockDocStmt.setString(1, doctorId);
            try (ResultSet rs = lockDocStmt.executeQuery()) {
                if (!rs.next()) {
                    throw new InvalidStateException("Doctor " + doctorId + " does not exist.");
                }
                String docAvail = rs.getString("availability");
                if (!"AVAILABLE".equals(docAvail)) {
                    throw new InvalidStateException("Doctor " + doctorId + " is not AVAILABLE (current: " + docAvail + ").");
                }
            }
        }

        String updateSql = "UPDATE emergency_visits SET status = 'IN_TREATMENT', doctor_id = ?, start_time = ? WHERE visit_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, doctorId);
            stmt.setTimestamp(2, Timestamp.valueOf(startTime));
            stmt.setLong(3, visitId);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically locks and transitions an active visit to COMPLETED.
     */
    public void completeTreatment(Connection conn, long visitId, LocalDateTime endTime)
            throws SQLException {
        String lockSql = "SELECT status, doctor_id, start_time FROM emergency_visits WHERE visit_id = ? FOR UPDATE";
        try (PreparedStatement lockStmt = conn.prepareStatement(lockSql)) {
            lockStmt.setLong(1, visitId);
            try (ResultSet rs = lockStmt.executeQuery()) {
                if (!rs.next()) {
                    throw new InvalidStateException("Visit #" + visitId + " does not exist.");
                }
                String status = rs.getString("status");
                if (!"IN_TREATMENT".equals(status)) {
                    throw new InvalidStateException("Visit #" + visitId + " is not IN_TREATMENT (current: " + status + ").");
                }
            }
        }

        String updateSql = "UPDATE emergency_visits SET status = 'COMPLETED', end_time = ? WHERE visit_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(endTime));
            stmt.setLong(2, visitId);
            stmt.executeUpdate();
        }
    }

    public int countVisits(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM emergency_visits";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public int countByStatus(Connection conn, TreatmentStatus status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM emergency_visits WHERE status = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private TreatmentRecord mapRow(ResultSet rs, Map<String, Doctor> doctors) throws SQLException {
        long id = rs.getLong("visit_id");
        String patientId = rs.getString("patient_id");
        String doctorId = rs.getString("doctor_id");
        Severity severity = Severity.valueOf(rs.getString("severity"));
        TreatmentStatus status = TreatmentStatus.valueOf(rs.getString("status"));
        LocalDateTime arrivalTime = rs.getTimestamp("arrival_time").toLocalDateTime();

        Timestamp startTs = rs.getTimestamp("start_time");
        LocalDateTime startTime = (startTs != null) ? startTs.toLocalDateTime() : null;

        Timestamp endTs = rs.getTimestamp("end_time");
        LocalDateTime endTime = (endTs != null) ? endTs.toLocalDateTime() : null;

        Doctor doc = null;
        if (doctorId != null && doctors != null) {
            doc = doctors.get(doctorId);
        }

        return new TreatmentRecord(id, patientId, severity, arrivalTime, status, doctorId, doc, startTime, endTime);
    }
}
