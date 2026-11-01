package hems.dao;

import hems.model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the 'patients' table.
 * All SQL executes using PreparedStatements.
 */
public class PatientDao {

    public void insert(Connection conn, Patient patient) throws SQLException {
        String sql = "INSERT INTO patients (patient_id, full_name, age, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patient.getId());
            stmt.setString(2, patient.getName());
            stmt.setInt(3, patient.getAge());
            stmt.setTimestamp(4, Timestamp.valueOf(patient.getCreatedAt()));
            stmt.executeUpdate();
        }
    }

    public boolean existsById(Connection conn, String patientId) throws SQLException {
        String sql = "SELECT 1 FROM patients WHERE LOWER(patient_id) = LOWER(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patientId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Patient findById(Connection conn, String patientId) throws SQLException {
        String sql = "SELECT patient_id, full_name, age, created_at FROM patients WHERE LOWER(patient_id) = LOWER(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patientId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Patient> findByNameLike(Connection conn, String nameQuery) throws SQLException {
        String sql = "SELECT patient_id, full_name, age, created_at FROM patients " +
                "WHERE LOWER(full_name) LIKE ? ORDER BY full_name ASC";
        List<Patient> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + nameQuery.toLowerCase() + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Patient> findAll(Connection conn) throws SQLException {
        String sql = "SELECT patient_id, full_name, age, created_at FROM patients ORDER BY full_name ASC";
        List<Patient> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int count(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private Patient mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("patient_id");
        String name = rs.getString("full_name");
        int age = rs.getInt("age");
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();
        return new Patient(id, name, age, createdAt);
    }
}
