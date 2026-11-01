package hems.view;

import hems.model.Doctor;
import hems.model.Severity;
import hems.model.TreatmentRecord;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Emergency Queue View: Live inspection of the priority-ordered waiting queue and
 * triage assignment execution.
 */
public class EmergencyQueueView extends VBox {

    private final HospitalSystem hospitalSystem;
    private final MainView mainView;

    private final TableView<TreatmentRecord> queueTable = new TableView<>();
    private final Button btnAssignNext = new Button("Assign Next Patient");

    private final Label lblQueueCount = new Label("0 patients waiting");
    private final Label lblCritCount = new Label("Critical: 0");
    private final Label lblHighCount = new Label("High: 0");
    private final Label lblMedCount = new Label("Medium: 0");
    private final Label lblLowCount = new Label("Low: 0");

    public EmergencyQueueView(HospitalSystem hospitalSystem, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.mainView = mainView;

        setSpacing(16);
        setPadding(new Insets(24));

        buildUI();
        refreshData();
    }

    private void buildUI() {
        // Top Header Card with Summary & Assign Action
        HBox topCard = new HBox(16);
        topCard.setAlignment(Pos.CENTER_LEFT);
        topCard.getStyleClass().add("card");

        VBox statsBox = new VBox(4);
        lblQueueCount.getStyleClass().add("card-title");

        HBox badges = new HBox(10);
        lblCritCount.setStyle("-fx-font-weight: bold; -fx-text-fill: #D94F45;");
        lblHighCount.setStyle("-fx-font-weight: bold; -fx-text-fill: #D89B32;");
        lblMedCount.setStyle("-fx-font-weight: bold; -fx-text-fill: #167D83;");
        lblLowCount.setStyle("-fx-font-weight: bold; -fx-text-fill: #657583;");
        badges.getChildren().addAll(lblCritCount, lblHighCount, lblMedCount, lblLowCount);

        statsBox.getChildren().addAll(lblQueueCount, badges);
        HBox.setHgrow(statsBox, Priority.ALWAYS);

        btnAssignNext.getStyleClass().add("btn-primary");
        btnAssignNext.setGraphic(VectorIcons.assignIcon(Color.WHITE));
        btnAssignNext.setOnAction(e -> handleAssignNext());

        topCard.getChildren().addAll(statsBox, btnAssignNext);

        // Queue Table
        setupQueueTable();
        VBox.setVgrow(queueTable, Priority.ALWAYS);

        // Bottom informational note
        Label footerNote = new Label("Deterministic Priority: 1. Severity Level (CRITICAL first) \u2192 2. Arrival Timestamp \u2192 3. Visit ID. Patient ID is never used for priority.");
        footerNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #657583; -fx-padding: 4px 8px;");

        getChildren().addAll(topCard, queueTable, footerNote);
    }

    @SuppressWarnings("unchecked")
    private void setupQueueTable() {
        TableColumn<TreatmentRecord, String> colPos = new TableColumn<>("Position");
        colPos.setMinWidth(70);
        colPos.setMaxWidth(80);
        colPos.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0) {
                    setText(null);
                } else {
                    setText("#" + (getIndex() + 1));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #167D83;");
                }
            }
        });

        TableColumn<TreatmentRecord, String> colSeverity = new TableColumn<>("Severity");
        colSeverity.setMinWidth(110);
        colSeverity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(UiUtil.createSeverityBadge(getTableRow().getItem().getSeverity()));
                    setText(null);
                }
            }
        });

        TableColumn<TreatmentRecord, String> colPatient = new TableColumn<>("Patient Name & ID");
        colPatient.setMinWidth(220);
        colPatient.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPatientName() + " (" + data.getValue().getPatientId() + ")"));

        TableColumn<TreatmentRecord, String> colVisitId = new TableColumn<>("Visit ID");
        colVisitId.setMinWidth(80);
        colVisitId.setCellValueFactory(data ->
                new SimpleStringProperty("Visit #" + data.getValue().getVisitId()));

        TableColumn<TreatmentRecord, String> colArrival = new TableColumn<>("Arrival Time");
        colArrival.setMinWidth(130);
        colArrival.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatTime(data.getValue().getArrivalTime())));

        TableColumn<TreatmentRecord, String> colWait = new TableColumn<>("Wait Duration");
        colWait.setMinWidth(110);
        colWait.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDuration(data.getValue().getArrivalTime(), null)));

        TableColumn<TreatmentRecord, String> colStatus = new TableColumn<>("Status");
        colStatus.setMinWidth(100);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(UiUtil.createTreatmentStatusBadge(getTableRow().getItem().getStatus()));
                    setText(null);
                }
            }
        });

        queueTable.getColumns().addAll(colPos, colSeverity, colPatient, colVisitId, colArrival, colWait, colStatus);
        queueTable.setPlaceholder(new Label("Queue is empty. No patients currently waiting for triage assignment."));
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

    public void refreshData() {
        AsyncExecutor.execute(hospitalSystem::getWaitingQueueSnapshot, queue -> {
            queueTable.getItems().setAll(queue);

            lblQueueCount.setText(queue.size() + " Patient" + (queue.size() == 1 ? "" : "s") + " Waiting");

            int crit = 0, high = 0, med = 0, low = 0;
            for (TreatmentRecord r : queue) {
                switch (r.getSeverity()) {
                    case CRITICAL: crit++; break;
                    case HIGH: high++; break;
                    case MEDIUM: med++; break;
                    case LOW: low++; break;
                }
            }
            lblCritCount.setText("Critical: " + crit);
            lblHighCount.setText("High: " + high);
            lblMedCount.setText("Medium: " + med);
            lblLowCount.setText("Low: " + low);

            btnAssignNext.setDisable(queue.isEmpty());
        }, error -> {
            mainView.showError("Failed to refresh emergency queue: " + error.getMessage());
        });
    }
}
