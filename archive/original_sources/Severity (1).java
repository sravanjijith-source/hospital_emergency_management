package hems.model;

/**
 * Emergency level assigned when a visit is created.
 * A LOWER level number means HIGHER priority (CRITICAL = 1 is treated first).
 * The level is only used to order the queue; it is not a medical assessment.
 */
public enum Severity {
    CRITICAL(1),
    HIGH(2),
    MEDIUM(3),
    LOW(4);

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
