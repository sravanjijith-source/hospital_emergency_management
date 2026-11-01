package hems.view;

import hems.model.Patient;
import hems.model.TreatmentRecord;
import hems.service.HospitalSystem;
import hems.util.AsyncExecutor;
import hems.util.DateTimeUtil;
import hems.util.UiUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Patient History View: Retrospectively queries all historical emergency visits
 * directly from MySQL for any selected patient.
 */
public class PatientHistoryView extends VBox {

    private final HospitalSystem hospitalSystem;
    private final MainView mainView;

    // Search bar
    private final TextField txtSearch = new TextField();
    private final Button btnSearch = new Button("Search");

    // Summary Card Labels
    private final Label lblPatientName = new Label("No patient selected");
    private final Label lblPatientId = new Label("-");
    private final Label lblPatientAge = new Label("-");
    private final Label lblRegistrationDate = new Label("-");
    private final Label lblTotalVisits = new Label("0");

    // History Table
    private final TableView<TreatmentRecord> historyTable = new TableView<>();

    public PatientHistoryView(HospitalSystem hospitalSystem, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.mainView = mainView;

        setSpacing(16);
        setPadding(new Insets(24));

        buildUI();
    }

    private void buildUI() {
        // Search Bar Card
        HBox searchCard = new HBox(12);
        searchCard.setAlignment(Pos.CENTER_LEFT);
        searchCard.getStyleClass().add("card");

        Label searchLbl = new Label("Lookup Patient:");
        searchLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #172B3A;");

        txtSearch.setPromptText("Enter Patient ID (e.g. P-1001) or full name...");
        HBox.setHgrow(txtSearch, Priority.ALWAYS);

        btnSearch.getStyleClass().add("btn-primary");
        btnSearch.setOnAction(e -> handleSearch());
        txtSearch.setOnAction(e -> handleSearch());

        searchCard.getChildren().addAll(searchLbl, txtSearch, btnSearch);

        // Patient Summary Header Card
        VBox summaryCard = new VBox(10);
        summaryCard.getStyleClass().add("card");

        lblPatientName.getStyleClass().add("card-title");
        lblPatientName.setStyle("-fx-font-size: 16px;");

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(20);
        metaGrid.setVgap(6);

        metaGrid.add(createMetaField("PATIENT ID", lblPatientId), 0, 0);
        metaGrid.add(createMetaField("AGE", lblPatientAge), 1, 0);
        metaGrid.add(createMetaField("REGISTERED ON", lblRegistrationDate), 2, 0);
        metaGrid.add(createMetaField("LIFETIME VISITS", lblTotalVisits), 3, 0);

        summaryCard.getChildren().addAll(lblPatientName, metaGrid);

        // History Table
        setupHistoryTable();
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        getChildren().addAll(searchCard, summaryCard, historyTable);
    }

    private VBox createMetaField(String label, Label valueLabel) {
        VBox box = new VBox(2);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #657583;");
        valueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #172B3A;");
        box.getChildren().addAll(lbl, valueLabel);
        return box;
    }

    @SuppressWarnings("unchecked")
    private void setupHistoryTable() {
        TableColumn<TreatmentRecord, String> colVisitId = new TableColumn<>("Visit ID");
        colVisitId.setMinWidth(75);
        colVisitId.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getVisitId()));

        TableColumn<TreatmentRecord, String> colDate = new TableColumn<>("Visit Date");
        colDate.setMinWidth(95);
        colDate.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDate(data.getValue().getArrivalTime())));

        TableColumn<TreatmentRecord, String> colSeverity = new TableColumn<>("Severity");
        colSeverity.setMinWidth(100);
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

        TableColumn<TreatmentRecord, String> colDoctor = new TableColumn<>("Attending Doctor");
        colDoctor.setMinWidth(160);
        colDoctor.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAssignedDoctorName()));

        TableColumn<TreatmentRecord, String> colArrival = new TableColumn<>("Arrival Timestamp");
        colArrival.setMinWidth(140);
        colArrival.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDateTime(data.getValue().getArrivalTime())));

        TableColumn<TreatmentRecord, String> colStart = new TableColumn<>("Treatment Start");
        colStart.setMinWidth(140);
        colStart.setCellValueFactory(data -> {
            if (data.getValue().getStartTime() == null) {
                return new SimpleStringProperty("-");
            }
            return new SimpleStringProperty(DateTimeUtil.formatDateTime(data.getValue().getStartTime()));
        });

        TableColumn<TreatmentRecord, String> colEnd = new TableColumn<>("Treatment End");
        colEnd.setMinWidth(140);
        colEnd.setCellValueFactory(data -> {
            if (data.getValue().getStatus() == hems.model.TreatmentStatus.WAITING) {
                return new SimpleStringProperty("-");
            }
            if (data.getValue().getStatus() == hems.model.TreatmentStatus.IN_TREATMENT) {
                return new SimpleStringProperty("In progress");
            }
            return new SimpleStringProperty(DateTimeUtil.formatDateTime(data.getValue().getEndTime()));
        });

        TableColumn<TreatmentRecord, String> colStatus = new TableColumn<>("Visit Status");
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

        historyTable.getColumns().addAll(colVisitId, colDate, colSeverity, colDoctor, colArrival, colStart, colEnd, colStatus);
        historyTable.setPlaceholder(new Label("Search for a patient to load complete medical triage history from MySQL."));
    }

    private void handleSearch() {
        String query = txtSearch.getText();
        if (query == null || query.trim().isEmpty()) {
            mainView.showError("Please enter a patient ID or name to search.");
            return;
        }

        AsyncExecutor.execute(() -> {
            Patient p = hospitalSystem.findPatientById(query.trim());
            if (p == null) {
                List<Patient> matches = hospitalSystem.findPatientsByName(query.trim());
                if (!matches.isEmpty()) {
                    p = matches.get(0);
                }
            }
            if (p == null) {
                return null;
            }
            List<TreatmentRecord> history = hospitalSystem.getPatientHistory(p.getId());
            return new PatientSearchData(p, history);
        }, res -> {
            if (res == null) {
                mainView.showError("No patient matching '" + query.trim() + "' was found.");
                return;
            }
            Patient p = res.patient;
            lblPatientName.setText(p.getName());
            lblPatientId.setText(p.getId());
            lblPatientAge.setText(p.getAge() + " years old");
            lblRegistrationDate.setText(DateTimeUtil.formatDateTime(p.getCreatedAt()));
            lblTotalVisits.setText(String.valueOf(res.history.size()));

            historyTable.getItems().setAll(res.history);
            mainView.showSuccess("Loaded " + res.history.size() + " visits from MySQL for " + p.getName() + ".");
        }, error -> {
            mainView.showError("Search failed: " + error.getMessage());
        }, btnSearch);
    }

    public void selectPatient(String patientId) {
        txtSearch.setText(patientId);
        handleSearch();
    }

    private static class PatientSearchData {
        final Patient patient;
        final List<TreatmentRecord> history;

        PatientSearchData(Patient patient, List<TreatmentRecord> history) {
            this.patient = patient;
            this.history = history;
        }
    }
}
