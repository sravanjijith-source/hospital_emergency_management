package hems.view;

import hems.database.ConnectionProvider;
import hems.database.DatabaseConfig;
import hems.model.Doctor;
import hems.model.DoctorStatus;
import hems.model.Patient;
import hems.model.Severity;
import hems.model.TreatmentRecord;
import hems.service.HospitalSystem;
import hems.util.AsyncExecutor;
import hems.util.UiUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Settings & Database Health Console:
 * Inspects JDBC connection parameters, validates database schema integrity,
 * triggers database reloads & reconciliation, and provides optional development data seeding.
 */
public class SettingsView extends VBox {

    private final HospitalSystem hospitalSystem;
    private final ConnectionProvider connectionProvider;
    private final MainView mainView;

    private final Label lblConnStatus = new Label("Checking...");
    private final Label lblDbUrl = new Label("-");
    private final Label lblDbUser = new Label("-");
    private final Label lblDbCatalog = new Label("-");
    private final Label lblSchemaTables = new Label("-");

    private final Button btnTestConnection = new Button("Test Connection");
    private final Button btnReloadDb = new Button("Reload from Database");
    private final Button btnLoadSampleData = new Button("Load Sample Clinical Data");

    public SettingsView(HospitalSystem hospitalSystem, ConnectionProvider connectionProvider, MainView mainView) {
        this.hospitalSystem = hospitalSystem;
        this.connectionProvider = connectionProvider;
        this.mainView = mainView;

        setSpacing(16);
        setPadding(new Insets(24));

        buildUI();
        refreshConnectionStatus();
    }

    private void buildUI() {
        // Card 1: Connection Health
        VBox connCard = new VBox(12);
        connCard.getStyleClass().add("card");

        HBox cardHeader = new HBox(12);
        cardHeader.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("MySQL Database Connection");
        title.getStyleClass().add("card-title");
        lblConnStatus.getStyleClass().add("badge");
        HBox.setHgrow(title, Priority.ALWAYS);
        cardHeader.getChildren().addAll(title, lblConnStatus);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);

        grid.add(new Label("JDBC URL:"), 0, 0);
        grid.add(lblDbUrl, 1, 0);

        grid.add(new Label("Database User:"), 0, 1);
        grid.add(lblDbUser, 1, 1);

        grid.add(new Label("Database Name:"), 0, 2);
        grid.add(lblDbCatalog, 1, 2);

        grid.add(new Label("Verified Tables:"), 0, 3);
        grid.add(lblSchemaTables, 1, 3);

        HBox btnBox = new HBox(12);
        btnTestConnection.getStyleClass().add("btn-primary");
        btnReloadDb.getStyleClass().add("btn-secondary");

        btnTestConnection.setOnAction(e -> testConnection());
        btnReloadDb.setOnAction(e -> handleReloadFromDatabase());

        btnBox.getChildren().addAll(btnTestConnection, btnReloadDb);

        connCard.getChildren().addAll(cardHeader, grid, btnBox);

        // Card 2: Development / Evaluation Data Loader
        VBox demoCard = new VBox(12);
        demoCard.getStyleClass().add("card");

        Label demoTitle = new Label("Development & Viva Demonstration Tools");
        demoTitle.getStyleClass().add("card-title");

        Label demoDesc = new Label("Optionally seed the database with realistic sample emergency physicians, patients, and triage visits for academic evaluation. Never executed automatically.");
        demoDesc.setWrapText(true);
        demoDesc.getStyleClass().add("card-subtitle");

        btnLoadSampleData.getStyleClass().add("btn-secondary");
        btnLoadSampleData.setOnAction(e -> handleLoadSampleData());

        demoCard.getChildren().addAll(demoTitle, demoDesc, btnLoadSampleData);

        // Card 3: Architecture Notes
        VBox archCard = new VBox(8);
        archCard.getStyleClass().add("card");

        Label archTitle = new Label("Persistence & Recovery Architectural Guarantees");
        archTitle.getStyleClass().add("card-title");

        Label archText = new Label(
                "\u2022 Atomic Transactions: Both 'Assign Next Patient' and 'Complete Treatment' execute inside a single MySQL transaction using 'SELECT ... FOR UPDATE' row-level locks.\n" +
                "\u2022 Priority Ordering: The waiting queue is ordered in memory strictly by Severity (CRITICAL first) \u2192 Arrival Timestamp \u2192 Visit ID.\n" +
                "\u2022 Safe Recovery: On application startup or manual reload, all WAITING visits are fetched from MySQL into the PriorityQueue, and inconsistent doctor states (e.g. BUSY without active visit) are automatically reconciled.\n" +
                "\u2022 Security: Passwords and sensitive JDBC connection parameters are never displayed in the interface or output to end-user error dialogs."
        );
        archText.setWrapText(true);
        archText.setStyle("-fx-font-size: 12px; -fx-text-fill: #465561; -fx-line-spacing: 4px;");

        archCard.getChildren().addAll(archTitle, archText);

        getChildren().addAll(connCard, demoCard, archCard);
    }

    private void testConnection() {
        AsyncExecutor.execute(() -> {
            if (connectionProvider == null) {
                return false;
            }
            return connectionProvider.testConnection();
        }, connected -> {
            if (Boolean.TRUE.equals(connected)) {
                mainView.showSuccess("MySQL database connection verified successfully.");
            } else {
                mainView.showError("Failed to reach MySQL database. Check credentials and server status.");
            }
            refreshConnectionStatus();
        }, error -> {
            mainView.showError("Connection test error: " + error.getMessage());
            refreshConnectionStatus();
        }, btnTestConnection);
    }

    private void handleReloadFromDatabase() {
        AsyncExecutor.executeAction(() -> {
            hospitalSystem.reloadFromDatabase();
        }, () -> {
            mainView.showSuccess("Reloaded all patient, physician, and emergency queue records from MySQL.");
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError("Reload failed: " + error.getMessage());
        }, btnReloadDb);
    }

    private void handleLoadSampleData() {
        boolean confirm = UiUtil.showConfirmationDialog(
                "Confirm Sample Data Loading",
                "Load sample triage dataset into the active database?",
                "This will insert sample physicians and emergency triage patients with diverse severity levels (CRITICAL, HIGH, MEDIUM, LOW) for demonstration and testing."
        );

        if (!confirm) return;

        AsyncExecutor.executeAction(() -> {
            // Seed Doctors if not already present
            seedDoctor("Dr. Meredith Grey", 38, "General Surgery", DoctorStatus.AVAILABLE);
            seedDoctor("Dr. Derek Shepherd", 45, "Neurosurgery", DoctorStatus.AVAILABLE);
            seedDoctor("Dr. Miranda Bailey", 44, "Trauma & Critical Care", DoctorStatus.AVAILABLE);
            seedDoctor("Dr. Alex Karev", 36, "Pediatric Emergency", DoctorStatus.OFF_DUTY);

            // Seed Patients with varying severities
            seedPatient("Arthur Dent", 42, Severity.MEDIUM);
            seedPatient("Elena Rostova", 28, Severity.CRITICAL);
            seedPatient("Marcus Aurelius", 58, Severity.HIGH);
            seedPatient("Chloe Sullivan", 22, Severity.LOW);
            seedPatient("David Copperfield", 35, Severity.HIGH);
        }, () -> {
            mainView.showSuccess("Sample clinical dataset successfully seeded into MySQL.");
            mainView.refreshAllViews();
        }, error -> {
            mainView.showError("Failed to seed sample data: " + error.getMessage());
        }, btnLoadSampleData);
    }

    private void seedDoctor(String name, int age, String spec, DoctorStatus status) {
        try {
            if (hospitalSystem.findDoctorsByName(name).isEmpty()) {
                hospitalSystem.registerDoctor(name, age, spec, status);
            }
        } catch (Exception ignored) {}
    }

    private void seedPatient(String name, int age, Severity severity) {
        try {
            if (hospitalSystem.findPatientsByName(name).isEmpty()) {
                hospitalSystem.registerPatient(name, age, severity);
            }
        } catch (Exception ignored) {}
    }

    public void refreshConnectionStatus() {
        if (connectionProvider == null) {
            lblConnStatus.setText("IN-MEMORY MODE");
            lblConnStatus.getStyleClass().setAll("badge", "badge-high");
            lblDbUrl.setText("None (Running decoupled pure-logic mode)");
            lblDbUser.setText("None");
            lblDbCatalog.setText("None");
            lblSchemaTables.setText("In-Memory Collections");
            return;
        }

        DatabaseConfig config = connectionProvider.getConfig();
        lblDbUrl.setText(config.getUrl());
        lblDbUser.setText(config.getUser());

        AsyncExecutor.execute(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                String catalog = conn.getCatalog();
                String tables = "";
                try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(rs.getString("TABLE_NAME"));
                    }
                    tables = sb.toString();
                }
                return new ConnInfo(true, catalog, tables);
            } catch (SQLException e) {
                return new ConnInfo(false, "-", "Connection unreachable");
            }
        }, info -> {
            if (info.connected) {
                lblConnStatus.setText("CONNECTED");
                lblConnStatus.getStyleClass().setAll("badge", "badge-available");
                lblDbCatalog.setText(info.catalog != null ? info.catalog : "-");
                lblSchemaTables.setText(info.tables.isEmpty() ? "No tables found" : info.tables);
            } else {
                lblConnStatus.setText("DISCONNECTED");
                lblConnStatus.getStyleClass().setAll("badge", "badge-critical");
                lblDbCatalog.setText("-");
                lblSchemaTables.setText("-");
            }
        }, err -> {
            lblConnStatus.setText("ERROR");
            lblConnStatus.getStyleClass().setAll("badge", "badge-critical");
        });
    }

    private static class ConnInfo {
        final boolean connected;
        final String catalog;
        final String tables;

        ConnInfo(boolean connected, String catalog, String tables) {
            this.connected = connected;
            this.catalog = catalog;
            this.tables = tables;
        }
    }
}
