package hems.view;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Active Treatments View: Real-time monitoring of patients currently receiving medical care,
 * with completion action triggered with confirmation dialog.
 */
public class ActiveTreatmentsView extends VBox {

    private final HospitalSystem hospitalSystem;
    private final MainView mainView;

    private final TableView<TreatmentRecord> table = new TableView<>();
    private final Button btnCompleteTreatment = new Button("Complete Selected Treatment");
    private final Label lblActiveCount = new Label("0 Treatments in Progress");

    public ActiveTreatmentsView(HospitalSystem hospitalSystem, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.mainView = mainView;

        setSpacing(16);
        setPadding(new Insets(24));

        buildUI();
        refreshData();
    }

    private void buildUI() {
        // Top Header Card
        HBox topCard = new HBox(16);
        topCard.setAlignment(Pos.CENTER_LEFT);
        topCard.getStyleClass().add("card");

        VBox titleBox = new VBox(4);
        lblActiveCount.getStyleClass().add("card-title");
        Label subtitle = new Label("Select an ongoing procedure to record completion and return physician to available pool.");
        subtitle.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(lblActiveCount, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        btnCompleteTreatment.getStyleClass().add("btn-primary");
        btnCompleteTreatment.setDisable(true);
        btnCompleteTreatment.setOnAction(e -> handleCompleteTreatment());

        topCard.getChildren().addAll(titleBox, btnCompleteTreatment);

        setupTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnCompleteTreatment.setDisable(newVal == null);
        });

        getChildren().addAll(topCard, table);
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        TableColumn<TreatmentRecord, String> colVisitId = new TableColumn<>("Visit ID");
        colVisitId.setMinWidth(80);
        colVisitId.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getVisitId()));

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

        TableColumn<TreatmentRecord, String> colPatient = new TableColumn<>("Patient Name & ID");
        colPatient.setMinWidth(220);
        colPatient.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPatientName() + " (" + data.getValue().getPatientId() + ")"));

        TableColumn<TreatmentRecord, String> colDoctor = new TableColumn<>("Attending Physician");
        colDoctor.setMinWidth(200);
        colDoctor.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAssignedDoctorName() +
                        (data.getValue().getAssignedDoctorId() != null ? " (" + data.getValue().getAssignedDoctorId() + ")" : "")));

        TableColumn<TreatmentRecord, String> colStart = new TableColumn<>("Treatment Started");
        colStart.setMinWidth(140);
        colStart.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDateTime(data.getValue().getStartTime())));

        TableColumn<TreatmentRecord, String> colDuration = new TableColumn<>("Elapsed Time");
        colDuration.setMinWidth(110);
        colDuration.setCellValueFactory(data ->
                new SimpleStringProperty(DateTimeUtil.formatDuration(data.getValue().getStartTime(), null)));

        TableColumn<TreatmentRecord, String> colStatus = new TableColumn<>("Status");
        colStatus.setMinWidth(110);
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

        table.getColumns().addAll(colVisitId, colSeverity, colPatient, colDoctor, colStart, colDuration, colStatus);
        table.setPlaceholder(new Label("No patients currently undergoing treatment."));
    }

    private void handleCompleteTreatment() {
        TreatmentRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        boolean confirm = UiUtil.showConfirmationDialog(
                "Confirm Treatment Completion",
                "Complete visit #" + selected.getVisitId() + " for " + selected.getPatientName() + "?",
                "This will conclude emergency treatment, record the final end time, and free Dr. "
                        + selected.getAssignedDoctorName() + " back to AVAILABLE."
        );

        if (!confirm) return;

        AsyncExecutor.execute(() -> {
            return hospitalSystem.completeTreatment(selected.getVisitId());
        }, completedVisit -> {
            mainView.showSuccess("Concluded treatment for visit #" + completedVisit.getVisitId()
                    + " (" + completedVisit.getPatientName() + "). Dr. "
                    + completedVisit.getAssignedDoctorName() + " is now AVAILABLE.");
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError(error.getMessage());
        }, btnCompleteTreatment);
    }

    public void refreshData() {
        AsyncExecutor.execute(hospitalSystem::getActiveTreatments, list -> {
            table.getItems().setAll(list);
            lblActiveCount.setText(list.size() + " Active Treatment" + (list.size() == 1 ? "" : "s") + " in Progress");
            btnCompleteTreatment.setDisable(table.getSelectionModel().getSelectedItem() == null);
        }, error -> {
            mainView.showError("Failed to refresh active treatments: " + error.getMessage());
        });
    }
}
