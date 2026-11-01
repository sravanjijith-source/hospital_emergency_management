package hems.dao;

import hems.model.Doctor;
import hems.model.DoctorStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the 'doctors' table.
 */
public class DoctorDao {

    public void insert(Connection conn, Doctor doctor) throws SQLException {
        String sql = "INSERT INTO doctors (doctor_id, full_name, age, specialization, availability) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doctor.getId());
            stmt.setString(2, doctor.getName());
            stmt.setInt(3, doctor.getAge());
            stmt.setString(4, doctor.getSpecialization());
            stmt.setString(5, doctor.getStatus().name());
            stmt.executeUpdate();
        }
    }

    public boolean existsById(Connection conn, String doctorId) throws SQLException {
        String sql = "SELECT 1 FROM doctors WHERE LOWER(doctor_id) = LOWER(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doctorId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Doctor findById(Connection conn, String doctorId) throws SQLException {
        String sql = "SELECT doctor_id, full_name, age, specialization, availability FROM doctors WHERE LOWER(doctor_id) = LOWER(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doctorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Doctor> findByNameLike(Connection conn, String nameQuery) throws SQLException {
        String sql = "SELECT doctor_id, full_name, age, specialization, availability FROM doctors " +
                "WHERE LOWER(full_name) LIKE ? ORDER BY full_name ASC";
        List<Doctor> list = new ArrayList<>();
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

    public List<Doctor> findAll(Connection conn) throws SQLException {
        String sql = "SELECT doctor_id, full_name, age, specialization, availability FROM doctors ORDER BY full_name ASC";
        List<Doctor> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void updateStatus(Connection conn, String doctorId, DoctorStatus status) throws SQLException {
        String sql = "UPDATE doctors SET availability = ? WHERE doctor_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, doctorId);
            stmt.executeUpdate();
        }
    }

    public int count(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM doctors";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public int countByStatus(Connection conn, DoctorStatus status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM doctors WHERE availability = ?";
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

    private Doctor mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("doctor_id");
        String name = rs.getString("full_name");
        int age = rs.getInt("age");
        String spec = rs.getString("specialization");
        DoctorStatus status = DoctorStatus.valueOf(rs.getString("availability"));
        return new Doctor(id, name, age, spec, status);
    }
}
