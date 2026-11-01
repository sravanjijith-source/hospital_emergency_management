package hems.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A patient is a permanent identity. It does NOT hold severity, status or an
 * assigned doctor: those belong to each emergency visit (TreatmentRecord), because
 * the same patient can come back many times.
 */
public class Patient extends Person {
    public static final int MIN_AGE = 0;
    public static final int MAX_AGE = 120;

    private final LocalDateTime createdAt;

    // In-memory CACHE of this patient's visits. MySQL is the source of truth;
    // the history screen must read from the database, not from this list.
    private final List<TreatmentRecord> historyCache = new ArrayList<>();

    /** For registering a new patient (created now). */
    public Patient(String id, String name, int age) {
        this(id, name, age, LocalDateTime.now());
    }

    /** For rebuilding a patient loaded from the database. */
    public Patient(String id, String name, int age, LocalDateTime createdAt) {
        super(id, name, age, MIN_AGE, MAX_AGE);
        this.createdAt = createdAt;
    }

    @Override
    public String getRole() {
        return "Patient";
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<TreatmentRecord> getHistoryCache() {
        return Collections.unmodifiableList(historyCache);
    }

    public void replaceHistoryCache(List<TreatmentRecord> visits) {
        historyCache.clear();
        historyCache.addAll(visits);
    }
}
