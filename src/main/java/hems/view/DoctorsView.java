package hems.view;

import hems.model.Doctor;
import hems.model.DoctorStatus;
import hems.service.HospitalSystem;
import hems.util.AsyncExecutor;
import hems.util.UiUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Doctors View: Physician roster management, registration, search, and availability toggles.
 */
public class DoctorsView extends HBox {

    private final HospitalSystem hospitalSystem;
    private final MainView mainView;

    // Search controls
    private final TextField txtSearch = new TextField();
    private final Button btnSearch = new Button("Search");
    private final Button btnClearSearch = new Button("Reset");

    // Table
    private final TableView<Doctor> doctorTable = new TableView<>();

    // Registration Form
    private final TextField txtNewName = new TextField();
    private final Spinner<Integer> spnNewAge = new Spinner<>(18, 90, 40);
    private final TextField txtNewSpec = new TextField();
    private final ComboBox<DoctorStatus> cmbNewStatus = new ComboBox<>();
    private final Button btnRegister = new Button("Register Doctor");

    // Status Toggle Panel
    private final Label lblSelectedDoctor = new Label("No doctor selected");
    private final HBox statusBadgeBox = new HBox(8);
    private final Button btnSetAvailable = new Button("Set AVAILABLE");
    private final Button btnSetOffDuty = new Button("Set OFF_DUTY");

    public DoctorsView(HospitalSystem hospitalSystem, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.mainView = mainView;

        setSpacing(16);
        setPadding(new Insets(20));

        buildUI();
        refreshData();
    }

    private void buildUI() {
        // Left Column: Search & Doctor List
        VBox leftCol = new VBox(12);
        leftCol.setPrefWidth(550);
        HBox.setHgrow(leftCol, Priority.ALWAYS);

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        txtSearch.setPromptText("Search by Doctor ID or Name...");
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

        setupDoctorTable();
        VBox.setVgrow(doctorTable, Priority.ALWAYS);

        leftCol.getChildren().addAll(searchBox, doctorTable);

        // Right Column: Registration & Availability Controls
        VBox rightCol = new VBox(14);
        rightCol.setPrefWidth(400);
        rightCol.setMinWidth(360);

        rightCol.getChildren().addAll(buildRegisterCard(), buildStatusControlCard());

        getChildren().addAll(leftCol, rightCol);

        doctorTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateSelectedDoctorControls(newVal);
        });
    }

    @SuppressWarnings("unchecked")
    private void setupDoctorTable() {
        TableColumn<Doctor, String> colId = new TableColumn<>("Doctor ID");
        colId.setMinWidth(90);
        colId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));

        TableColumn<Doctor, String> colName = new TableColumn<>("Physician Name");
        colName.setMinWidth(150);
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        TableColumn<Doctor, String> colAge = new TableColumn<>("Age");
        colAge.setMinWidth(55);
        colAge.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getAge())));

        TableColumn<Doctor, String> colSpec = new TableColumn<>("Specialization");
        colSpec.setMinWidth(130);
        colSpec.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSpecialization()));

        TableColumn<Doctor, String> colStatus = new TableColumn<>("Status");
        colStatus.setMinWidth(100);
        colStatus.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(UiUtil.createDoctorStatusBadge(getTableRow().getItem().getStatus()));
                    setText(null);
                }
            }
        });

        doctorTable.getColumns().addAll(colId, colName, colAge, colSpec, colStatus);
        doctorTable.setPlaceholder(new Label("No physicians registered."));
    }

    private VBox buildRegisterCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("Register New Physician");
        title.getStyleClass().add("card-title");

        Label lblIdHint = new Label("ID is assigned automatically (e.g. D-001)");
        lblIdHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #167D83; -fx-padding: 2px 0 6px 0; -fx-font-style: italic;");

        txtNewName.setPromptText("e.g. Dr. Derek Shepherd");
        txtNewSpec.setPromptText("e.g. Emergency Care / Trauma");

        spnNewAge.setEditable(true);
        cmbNewStatus.getItems().setAll(DoctorStatus.AVAILABLE, DoctorStatus.OFF_DUTY);
        cmbNewStatus.setValue(DoctorStatus.AVAILABLE);

        btnRegister.getStyleClass().add("btn-primary");
        btnRegister.setMaxWidth(Double.MAX_VALUE);
        btnRegister.setOnAction(e -> handleRegisterDoctor());

        card.getChildren().addAll(
                title,
                lblIdHint,
                new Label("Full Name:"), txtNewName,
                new Label("Age (18-90):"), spnNewAge,
                new Label("Specialization:"), txtNewSpec,
                new Label("Initial Availability:"), cmbNewStatus,
                btnRegister
        );
        return card;
    }

    private VBox buildStatusControlCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("Manage Availability");
        title.getStyleClass().add("card-title");

        lblSelectedDoctor.getStyleClass().add("card-subtitle");

        statusBadgeBox.setAlignment(Pos.CENTER_LEFT);

        btnSetAvailable.getStyleClass().add("btn-primary");
        btnSetOffDuty.getStyleClass().add("btn-secondary");

        btnSetAvailable.setOnAction(e -> handleUpdateStatus(DoctorStatus.AVAILABLE));
        btnSetOffDuty.setOnAction(e -> handleUpdateStatus(DoctorStatus.OFF_DUTY));

        HBox btnBox = new HBox(10);
        btnBox.getChildren().addAll(btnSetAvailable, btnSetOffDuty);

        Label notice = new Label("Rule: Only AVAILABLE <-> OFF_DUTY is manual. BUSY is set exclusively by patient assignment.");
        notice.setWrapText(true);
        notice.setStyle("-fx-font-size: 11px; -fx-text-fill: #657583;");

        card.getChildren().addAll(title, lblSelectedDoctor, statusBadgeBox, btnBox, notice);
        return card;
    }

    private void updateSelectedDoctorControls(Doctor doctor) {
        if (doctor == null) {
            lblSelectedDoctor.setText("Select a physician from the roster to adjust availability.");
            statusBadgeBox.getChildren().clear();
            btnSetAvailable.setDisable(true);
            btnSetOffDuty.setDisable(true);
            return;
        }

        lblSelectedDoctor.setText(doctor.getName() + " (" + doctor.getId() + ")");
        statusBadgeBox.getChildren().setAll(UiUtil.createDoctorStatusBadge(doctor.getStatus()));

        if (doctor.getStatus() == DoctorStatus.BUSY) {
            btnSetAvailable.setDisable(true);
            btnSetOffDuty.setDisable(true);
        } else if (doctor.getStatus() == DoctorStatus.AVAILABLE) {
            btnSetAvailable.setDisable(true);
            btnSetOffDuty.setDisable(false);
        } else {
            btnSetAvailable.setDisable(false);
            btnSetOffDuty.setDisable(true);
        }
    }

    private void handleRegisterDoctor() {
        String name = txtNewName.getText();
        int age = spnNewAge.getValue();
        String spec = txtNewSpec.getText();
        DoctorStatus status = cmbNewStatus.getValue();

        AsyncExecutor.execute(() -> {
            return hospitalSystem.registerDoctor(name, age, spec, status);
        }, doctor -> {
            mainView.showSuccess("Physician " + name + " registered with ID " + doctor.getId() + " (" + spec + ").");
            txtNewName.clear();
            txtNewSpec.clear();
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError(error.getMessage());
        }, btnRegister);
    }

    private void handleUpdateStatus(DoctorStatus target) {
        Doctor selected = doctorTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        AsyncExecutor.executeAction(() -> {
            hospitalSystem.updateDoctorStatus(selected.getId(), target);
        }, () -> {
            mainView.showSuccess("Updated " + selected.getName() + " status to " + target + ".");
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError(error.getMessage());
        }, btnSetAvailable, btnSetOffDuty);
    }

    private void handleSearch() {
        String query = txtSearch.getText();
        AsyncExecutor.execute(() -> {
            if (query == null || query.trim().isEmpty()) {
                return hospitalSystem.getAllDoctors();
            }
            Doctor exact = hospitalSystem.findDoctorById(query.trim());
            if (exact != null) {
                return List.of(exact);
            }
            return hospitalSystem.findDoctorsByName(query.trim());
        }, doctors -> {
            doctorTable.getItems().setAll(doctors);
        }, error -> {
            mainView.showError(error.getMessage());
        });
    }

    public void refreshData() {
        AsyncExecutor.execute(hospitalSystem::getAllDoctors,
                doctors -> {
                    doctorTable.getItems().setAll(doctors);
                    if (!doctorTable.getItems().isEmpty() && doctorTable.getSelectionModel().getSelectedItem() == null) {
                        doctorTable.getSelectionModel().selectFirst();
                    }
                },
                error -> mainView.showError("Failed to load doctors: " + error.getMessage()));
    }
}
