package hems.model;

/** Lifecycle of one emergency visit: WAITING -> IN_TREATMENT -> COMPLETED. */
public enum TreatmentStatus {
    WAITING(1),
    IN_TREATMENT(2),
    COMPLETED(3);

    private final int level;

    TreatmentStatus(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /** The only legal moves are WAITING -> IN_TREATMENT and IN_TREATMENT -> COMPLETED. */
    public boolean canMoveTo(TreatmentStatus next) {
        switch (this) {
            case WAITING:
                return next == IN_TREATMENT;
            case IN_TREATMENT:
                return next == COMPLETED;
            default:
                return false;
        }
    }

    /** A visit is "open" until it is COMPLETED. */
    public boolean isOpen() {
        return this != COMPLETED;
    }
}
