package hems.view;

import hems.model.Patient;
import hems.model.Severity;
import hems.model.TreatmentRecord;
import hems.service.HospitalSystem;
import hems.util.AsyncExecutor;
import hems.util.DateTimeUtil;
import hems.util.UiUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Patients View: Patient registration with initial visit triage, creation of subsequent
 * visits for returning patients, patient lookup, and detail/history inspection.
 */
public class PatientsView extends HBox {

    private final HospitalSystem hospitalSystem;
    private final MainView mainView;

    // Search controls
    private final TextField txtSearch = new TextField();
    private final Button btnSearch = new Button("Search");
    private final Button btnClearSearch = new Button("Reset");

    // Patients table
    private final TableView<Patient> patientTable = new TableView<>();

    // Form: New Patient Registration
    private final TextField txtNewName = new TextField();
    private final Spinner<Integer> spnNewAge = new Spinner<>(0, 120, 30);
    private final ComboBox<Severity> cmbNewSeverity = new ComboBox<>();
    private final Button btnRegister = new Button("Register & Queue Patient");

    // Form: New Visit for Existing Patient
    private final TextField txtExistingPatientId = new TextField();
    private final ComboBox<Severity> cmbReturnSeverity = new ComboBox<>();
    private final Button btnCreateVisit = new Button("Create Emergency Visit");

    // Detail Panel
    private final Label lblDetailName = new Label("No patient selected");
    private final Label lblDetailId = new Label("-");
    private final Label lblDetailAge = new Label("-");
    private final TableView<TreatmentRecord> historyMiniTable = new TableView<>();

    public PatientsView(HospitalSystem hospitalSystem, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.mainView = mainView;

        setSpacing(16);
        setPadding(new Insets(20));

        buildUI();
        refreshData();
    }

    private void buildUI() {
        // Left Column: Search + Patient List
        VBox leftCol = new VBox(12);
        leftCol.setPrefWidth(550);
        HBox.setHgrow(leftCol, Priority.ALWAYS);

        // Search bar
        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        txtSearch.setPromptText("Search by Patient ID or Name...");
        HBox.setHgrow(txtSearch, Priority.ALWAYS);
        btnSearch.getStyleClass().add("btn-secondary");
        btnClearSearch.getStyleClass().add("btn-secondary");

        btnSearch.setOnAction(e -> handleSearch());
        txtSearch.setOnAction(e -> handleSearch());
        btnClearSearch.setOnAction(e -> {
            txtSearch.clear();
            refreshData();
        });

        searchBox.getChildren().addAll(txtSearch, btnSearch, btnClearSearch);

        setupPatientTable();
        VBox.setVgrow(patientTable, Priority.ALWAYS);

        leftCol.getChildren().addAll(searchBox, patientTable);

        // Right Column: TabPane (Register, New Visit, Detail)
        VBox rightCol = new VBox(12);
        rightCol.setPrefWidth(420);
        rightCol.setMinWidth(380);

        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("card");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Tab tabRegister = new Tab("New Patient", buildRegisterTab());
        Tab tabNewVisit = new Tab("Returning Visit", buildReturnVisitTab());
        Tab tabDetails = new Tab("Details & History", buildDetailsTab());

        tabPane.getTabs().addAll(tabRegister, tabNewVisit, tabDetails);

        rightCol.getChildren().add(tabPane);

        getChildren().addAll(leftCol, rightCol);

        // Selection listener to update existing patient tab and details tab
        patientTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                txtExistingPatientId.setText(newVal.getId());
                updateDetailsPanel(newVal);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void setupPatientTable() {
        TableColumn<Patient, String> colId = new TableColumn<>("Patient ID");
        colId.setMinWidth(90);
        colId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));

        TableColumn<Patient, String> colName = new TableColumn<>("Full Name");
        colName.setMinWidth(160);
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        TableColumn<Patient, String> colAge = new TableColumn<>("Age");
        colAge.setMinWidth(60);
        colAge.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getAge())));

        TableColumn<Patient, String> colRegistered = new TableColumn<>("Registered On");
        colRegistered.setMinWidth(140);
        colRegistered.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDateTime(data.getValue().getCreatedAt())));

        patientTable.getColumns().addAll(colId, colName, colAge, colRegistered);
        patientTable.setPlaceholder(new Label("No patients registered."));
    }

    private VBox buildRegisterTab() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(16));

        Label lblTitle = new Label("Register New Patient");
        lblTitle.getStyleClass().add("card-title");

        Label lblIdHint = new Label("ID is assigned automatically (e.g. P-0001)");
        lblIdHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #167D83; -fx-padding: 2px 0 6px 0; -fx-font-style: italic;");

        txtNewName.setPromptText("e.g. Sarah Connor");

        spnNewAge.setEditable(true);
        cmbNewSeverity.getItems().setAll(Severity.values());
        cmbNewSeverity.setValue(Severity.MEDIUM);

        btnRegister.getStyleClass().add("btn-primary");
        btnRegister.setMaxWidth(Double.MAX_VALUE);
        btnRegister.setOnAction(e -> handleRegister());

        form.getChildren().addAll(
                lblTitle,
                lblIdHint,
                new Label("Full Name:"), txtNewName,
                new Label("Age (0-120):"), spnNewAge,
                new Label("Initial Severity:"), cmbNewSeverity,
                btnRegister
        );
        return form;
    }

    private VBox buildReturnVisitTab() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(16));

        Label lblTitle = new Label("Create Emergency Visit");
        lblTitle.getStyleClass().add("card-title");

        Label lblSubtitle = new Label("For an existing patient returning to the emergency department.");
        lblSubtitle.getStyleClass().add("card-subtitle");

        txtExistingPatientId.setPromptText("Enter Patient ID (or select from table)");
        cmbReturnSeverity.getItems().setAll(Severity.values());
        cmbReturnSeverity.setValue(Severity.HIGH);

        btnCreateVisit.getStyleClass().add("btn-primary");
        btnCreateVisit.setMaxWidth(Double.MAX_VALUE);
        btnCreateVisit.setOnAction(e -> handleCreateReturnVisit());

        form.getChildren().addAll(
                lblTitle,
                lblSubtitle,
                new Label("Patient ID:"), txtExistingPatientId,
                new Label("Emergency Severity:"), cmbReturnSeverity,
                btnCreateVisit
        );
        return form;
    }

    @SuppressWarnings("unchecked")
    private VBox buildDetailsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));

        lblDetailName.getStyleClass().add("card-title");

        HBox meta = new HBox(12);
        meta.getChildren().addAll(new Label("ID:"), lblDetailId, new Label("Age:"), lblDetailAge);

        Label lblHistoryTitle = new Label("Visit History:");
        lblHistoryTitle.setStyle("-fx-font-weight: bold; -fx-padding: 8px 0 2px 0;");

        // History mini table
        TableColumn<TreatmentRecord, String> colVisitId = new TableColumn<>("Visit");
        colVisitId.setMinWidth(55);
        colVisitId.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getVisitId()));

        TableColumn<TreatmentRecord, String> colSev = new TableColumn<>("Severity");
        colSev.setMinWidth(80);
        colSev.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
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

        TableColumn<TreatmentRecord, String> colDoc = new TableColumn<>("Doctor");
        colDoc.setMinWidth(100);
        colDoc.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAssignedDoctorName()));

        TableColumn<TreatmentRecord, String> colStatus = new TableColumn<>("Status");
        colStatus.setMinWidth(90);
        colStatus.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
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

        historyMiniTable.getColumns().addAll(colVisitId, colSev, colDoc, colStatus);
        historyMiniTable.setPlaceholder(new Label("No visit history."));
        VBox.setVgrow(historyMiniTable, Priority.ALWAYS);

        box.getChildren().addAll(lblDetailName, meta, lblHistoryTitle, historyMiniTable);
        return box;
    }

    private void handleRegister() {
        String name = txtNewName.getText();
        int age = spnNewAge.getValue();
        Severity severity = cmbNewSeverity.getValue();

        AsyncExecutor.execute(() -> {
            return hospitalSystem.registerPatient(name, age, severity);
        }, visit -> {
            mainView.showSuccess("Patient " + name + " registered with ID " + visit.getPatientId() + " (Visit #" + visit.getVisitId() + " queued).");
            txtNewName.clear();
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError(error.getMessage());
        }, btnRegister);
    }

    private void handleCreateReturnVisit() {
        String patientId = txtExistingPatientId.getText();
        Severity severity = cmbReturnSeverity.getValue();

        AsyncExecutor.execute(() -> {
            return hospitalSystem.createEmergencyVisit(patientId, severity);
        }, visit -> {
            mainView.showSuccess("New emergency visit #" + visit.getVisitId() + " created for " + patientId + ".");
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError(error.getMessage());
        }, btnCreateVisit);
    }

    private void handleSearch() {
        String query = txtSearch.getText();
        AsyncExecutor.execute(() -> {
            if (query == null || query.trim().isEmpty()) {
                return hospitalSystem.getAllPatients();
            }
            // Check exact ID match first
            Patient exact = hospitalSystem.findPatientById(query.trim());
            if (exact != null) {
                return List.of(exact);
            }
            return hospitalSystem.findPatientsByName(query.trim());
        }, patients -> {
            patientTable.getItems().setAll(patients);
        }, error -> {
            mainView.showError(error.getMessage());
        });
    }

    private void updateDetailsPanel(Patient patient) {
        lblDetailName.setText(patient.getName());
        lblDetailId.setText(patient.getId());
        lblDetailAge.setText(String.valueOf(patient.getAge()));

        AsyncExecutor.execute(() -> {
            return hospitalSystem.getPatientHistory(patient.getId());
        }, visits -> {
            historyMiniTable.getItems().setAll(visits);
        }, error -> {
            mainView.showError("Could not load visit history: " + error.getMessage());
        });
    }

    public void refreshData() {
        AsyncExecutor.execute(hospitalSystem::getAllPatients,
                patients -> {
                    patientTable.getItems().setAll(patients);
                    if (!patientTable.getItems().isEmpty() && patientTable.getSelectionModel().getSelectedItem() == null) {
                        patientTable.getSelectionModel().selectFirst();
                    }
                },
                error -> mainView.showError("Failed to load patients: " + error.getMessage()));
    }
}
