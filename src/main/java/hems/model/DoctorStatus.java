package hems.model;

/** Whether a doctor can take a patient right now. */
public enum DoctorStatus {
    AVAILABLE,
    BUSY,
    OFF_DUTY;

    /**
     * Can staff switch to this status by hand?
     * Only AVAILABLE &lt;-&gt; OFF_DUTY is manual. BUSY is set only by assigning a
     * patient, and BUSY goes back to AVAILABLE only by completing treatment.
     */
    public boolean canChangeManuallyTo(DoctorStatus target) {
        return (this == AVAILABLE && target == OFF_DUTY)
                || (this == OFF_DUTY && target == AVAILABLE);
    }
}
