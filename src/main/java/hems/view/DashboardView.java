package hems.view;

import hems.model.Doctor;
import hems.model.Severity;
import hems.model.TreatmentRecord;
import hems.model.TreatmentStatus;
import hems.service.DashboardStats;
import hems.service.HospitalSystem;
import hems.util.AsyncExecutor;
import hems.util.DateTimeUtil;
import hems.util.UiUtil;
import hems.util.VectorIcons;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Dashboard View: Overview of live triage statistics, queue preview, active treatments,
 * and quick assignment action.
 */
public class DashboardView extends VBox {

    private final HospitalSystem hospitalSystem;
    private final MainView mainView;

    // Stat card value labels
    private final Label lblPatients = new Label("0");
    private final Label lblVisits = new Label("0");
    private final Label lblWaiting = new Label("0");
    private final Label lblInTreatment = new Label("0");
    private final Label lblCompleted = new Label("0");
    private final Label lblDoctors = new Label("0");
    private final Label lblAvailableDocs = new Label("0");
    private final Label lblBusyDocs = new Label("0");
    private final Label lblOffDutyDocs = new Label("0");

    // Action button
    private final Button btnAssignNext = new Button("Assign Next Patient");

    // Preview tables
    private final TableView<TreatmentRecord> queuePreviewTable = new TableView<>();
    private final TableView<TreatmentRecord> activeTreatmentsTable = new TableView<>();

    public DashboardView(HospitalSystem hospitalSystem, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.mainView = mainView;

        setSpacing(20);
        setPadding(new Insets(24));

        buildUI();
        refreshData();
    }

    private void buildUI() {
        // Top action bar with prominent "Assign Next Patient"
        HBox actionBanner = new HBox(16);
        actionBanner.setAlignment(Pos.CENTER_LEFT);
        actionBanner.getStyleClass().add("card");

        VBox bannerText = new VBox(4);
        Label bannerTitle = new Label("Emergency Triage Operations");
        bannerTitle.getStyleClass().add("card-title");
        Label bannerDesc = new Label("Monitor real-time patient queue, physician assignments, and triage status.");
        bannerDesc.getStyleClass().add("card-subtitle");
        bannerText.getChildren().addAll(bannerTitle, bannerDesc);
        HBox.setHgrow(bannerText, Priority.ALWAYS);

        btnAssignNext.getStyleClass().add("btn-primary");
        btnAssignNext.setGraphic(VectorIcons.assignIcon(Color.WHITE));
        btnAssignNext.setOnAction(e -> handleAssignNext());

        actionBanner.getChildren().addAll(bannerText, btnAssignNext);

        // Stat Cards Grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(12);
        statsGrid.setVgap(12);

        // Patients & Visits
        statsGrid.add(createCard("Total Patients", lblPatients, "stat-value"), 0, 0);
        statsGrid.add(createCard("Total Visits", lblVisits, "stat-value"), 1, 0);
        statsGrid.add(createCard("Waiting Queue", lblWaiting, "stat-value-warning"), 2, 0);
        statsGrid.add(createCard("In Treatment", lblInTreatment, "stat-value-accent"), 3, 0);
        statsGrid.add(createCard("Completed Visits", lblCompleted, "stat-value"), 4, 0);

        // Doctors
        statsGrid.add(createCard("Total Doctors", lblDoctors, "stat-value"), 0, 1);
        statsGrid.add(createCard("Available Doctors", lblAvailableDocs, "stat-value-accent"), 1, 1);
        statsGrid.add(createCard("Busy Doctors", lblBusyDocs, "stat-value-warning"), 2, 1);
        statsGrid.add(createCard("Off Duty Doctors", lblOffDutyDocs, "stat-value"), 3, 1);

        // Tables Section: Side-by-side
        HBox tablesBox = new HBox(16);
        VBox.setVgrow(tablesBox, Priority.ALWAYS);

        // Queue preview box
        VBox queueBox = new VBox(10);
        queueBox.getStyleClass().add("card");
        HBox.setHgrow(queueBox, Priority.ALWAYS);

        Label queueTitle = new Label("Waiting Queue Preview (Priority Ordered)");
        queueTitle.getStyleClass().add("card-title");
        setupQueuePreviewTable();
        queueBox.getChildren().addAll(queueTitle, queuePreviewTable);
        VBox.setVgrow(queuePreviewTable, Priority.ALWAYS);

        // Active treatment box
        VBox activeBox = new VBox(10);
        activeBox.getStyleClass().add("card");
        HBox.setHgrow(activeBox, Priority.ALWAYS);

        Label activeTitle = new Label("Active Treatments in Progress");
        activeTitle.getStyleClass().add("card-title");
        setupActiveTreatmentsTable();
        activeBox.getChildren().addAll(activeTitle, activeTreatmentsTable);
        VBox.setVgrow(activeTreatmentsTable, Priority.ALWAYS);

        tablesBox.getChildren().addAll(queueBox, activeBox);

        getChildren().addAll(actionBanner, statsGrid, tablesBox);
    }

    private VBox createCard(String labelText, Label valLabel, String styleClass) {
        VBox card = new VBox(4);
        card.getStyleClass().add("stat-card");
        Label lbl = new Label(labelText.toUpperCase());
        lbl.getStyleClass().add("stat-label");
        valLabel.getStyleClass().setAll(styleClass);
        card.getChildren().addAll(lbl, valLabel);
        return card;
    }

    @SuppressWarnings("unchecked")
    private void setupQueuePreviewTable() {
        TableColumn<TreatmentRecord, String> colSeverity = new TableColumn<>("Severity");
        colSeverity.setMinWidth(95);
        colSeverity.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    TreatmentRecord record = getTableRow().getItem();
                    setGraphic(UiUtil.createSeverityBadge(record.getSeverity()));
                    setText(null);
                }
            }
        });

        TableColumn<TreatmentRecord, String> colPatient = new TableColumn<>("Patient");
        colPatient.setMinWidth(130);
        colPatient.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPatientName() + " (" + data.getValue().getPatientId() + ")"));

        TableColumn<TreatmentRecord, String> colArrival = new TableColumn<>("Arrival Time");
        colArrival.setMinWidth(110);
        colArrival.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatTime(data.getValue().getArrivalTime())));

        TableColumn<TreatmentRecord, String> colWait = new TableColumn<>("Wait Time");
        colWait.setMinWidth(80);
        colWait.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDuration(data.getValue().getArrivalTime(), null)));

        queuePreviewTable.getColumns().addAll(colSeverity, colPatient, colArrival, colWait);
        queuePreviewTable.setPlaceholder(new Label("No patients currently waiting in queue."));
    }

    @SuppressWarnings("unchecked")
    private void setupActiveTreatmentsTable() {
        TableColumn<TreatmentRecord, String> colPatient = new TableColumn<>("Patient");
        colPatient.setMinWidth(130);
        colPatient.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPatientName() + " (" + data.getValue().getPatientId() + ")"));

        TableColumn<TreatmentRecord, String> colDoctor = new TableColumn<>("Assigned Doctor");
        colDoctor.setMinWidth(130);
        colDoctor.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAssignedDoctorName()));

        TableColumn<TreatmentRecord, String> colStart = new TableColumn<>("Start Time");
        colStart.setMinWidth(90);
        colStart.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatTime(data.getValue().getStartTime())));

        TableColumn<TreatmentRecord, String> colDuration = new TableColumn<>("Elapsed");
        colDuration.setMinWidth(75);
        colDuration.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDuration(data.getValue().getStartTime(), null)));

        activeTreatmentsTable.getColumns().addAll(colPatient, colDoctor, colStart, colDuration);
        activeTreatmentsTable.setPlaceholder(new Label("No patients currently undergoing treatment."));
    }

    public void refreshData() {
        AsyncExecutor.execute(() -> {
            DashboardStats stats = hospitalSystem.getDashboardStats();
            List<TreatmentRecord> queueSnapshot = hospitalSystem.getWaitingQueueSnapshot();
            List<TreatmentRecord> activeList = hospitalSystem.getActiveTreatments();
            return new DashboardData(stats, queueSnapshot, activeList);
        }, data -> {
            // Update Stat numbers
            DashboardStats s = data.stats;
            lblPatients.setText(String.valueOf(s.getTotalPatients()));
            lblVisits.setText(String.valueOf(s.getTotalVisits()));
            lblWaiting.setText(String.valueOf(s.getWaitingVisits()));
            lblInTreatment.setText(String.valueOf(s.getInTreatmentVisits()));
            lblCompleted.setText(String.valueOf(s.getCompletedVisits()));

            lblDoctors.setText(String.valueOf(s.getTotalDoctors()));
            lblAvailableDocs.setText(String.valueOf(s.getAvailableDoctors()));
            lblBusyDocs.setText(String.valueOf(s.getBusyDoctors()));
            lblOffDutyDocs.setText(String.valueOf(s.getOffDutyDoctors()));

            // Update Tables
            queuePreviewTable.getItems().setAll(data.queue);
            activeTreatmentsTable.getItems().setAll(data.activeTreatments);

            btnAssignNext.setDisable(data.queue.isEmpty() || s.getAvailableDoctors() == 0);
        }, err -> {
            mainView.showError("Failed to refresh dashboard: " + err.getMessage());
        });
    }

    private void handleAssignNext() {
        AsyncExecutor.execute(() -> {
            return hospitalSystem.assignNextPatient();
        }, assignedVisit -> {
            mainView.showSuccess("Assigned visit #" + assignedVisit.getVisitId()
                    + " (" + assignedVisit.getPatientName() + ") to Dr. "
                    + assignedVisit.getAssignedDoctorName() + ".");
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError(error.getMessage());
        }, btnAssignNext);
    }

    private static class DashboardData {
        final DashboardStats stats;
        final List<TreatmentRecord> queue;
        final List<TreatmentRecord> activeTreatments;

        DashboardData(DashboardStats stats, List<TreatmentRecord> queue, List<TreatmentRecord> activeTreatments) {
            this.stats = stats;
            this.queue = queue;
            this.activeTreatments = activeTreatments;
        }
    }
}
