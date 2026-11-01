package hems.service;

/**
 * Immutable value object holding real-time statistics snapshot for the dashboard.
 */
public class DashboardStats {
    private final int totalPatients;
    private final int totalVisits;
    private final int waitingVisits;
    private final int inTreatmentVisits;
    private final int completedVisits;

    private final int totalDoctors;
    private final int availableDoctors;
    private final int busyDoctors;
    private final int offDutyDoctors;

    public DashboardStats(int totalPatients, int totalVisits, int waitingVisits,
                          int inTreatmentVisits, int completedVisits,
                          int totalDoctors, int availableDoctors,
                          int busyDoctors, int offDutyDoctors) {
        this.totalPatients = totalPatients;
        this.totalVisits = totalVisits;
        this.waitingVisits = waitingVisits;
        this.inTreatmentVisits = inTreatmentVisits;
        this.completedVisits = completedVisits;
        this.totalDoctors = totalDoctors;
        this.availableDoctors = availableDoctors;
        this.busyDoctors = busyDoctors;
        this.offDutyDoctors = offDutyDoctors;
    }

    public int getTotalPatients() {
        return totalPatients;
    }

    public int getTotalVisits() {
        return totalVisits;
    }

    public int getWaitingVisits() {
        return waitingVisits;
    }

    public int getInTreatmentVisits() {
        return inTreatmentVisits;
    }

    public int getCompletedVisits() {
        return completedVisits;
    }

    public int getTotalDoctors() {
        return totalDoctors;
    }

    public int getAvailableDoctors() {
        return availableDoctors;
    }

    public int getBusyDoctors() {
        return busyDoctors;
    }

    public int getOffDutyDoctors() {
        return offDutyDoctors;
    }

    @Override
    public String toString() {
        return "DashboardStats{" +
                "patients=" + totalPatients +
                ", visits=" + totalVisits +
                ", waiting=" + waitingVisits +
                ", inTreatment=" + inTreatmentVisits +
                ", completed=" + completedVisits +
                ", doctors=" + totalDoctors +
                ", available=" + availableDoctors +
                ", busy=" + busyDoctors +
                ", offDuty=" + offDutyDoctors +
                '}';
    }
}
