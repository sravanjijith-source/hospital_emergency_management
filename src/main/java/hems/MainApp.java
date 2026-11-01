package hems;

import hems.dao.DoctorDao;
import hems.dao.PatientDao;
import hems.dao.TreatmentRecordDao;
import hems.database.ConnectionProvider;
import hems.database.DatabaseConfig;
import hems.database.SchemaInitializer;
import hems.service.HospitalSystem;
import hems.util.DatabaseException;
import hems.view.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main Application Entry Point for the Hospital Emergency Management System (HEMS).
 * Opens a native resizable desktop window with the clinical editorial theme.
 *
 * Enforces fail-fast persistence: If MySQL is unreachable, displays a clear, secret-free
 * error screen detailing how to resolve it, and never silently falls back to memory.
 */
public class MainApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    public static final String APP_TITLE = "Hospital Emergency Management System (HEMS)";
    public static final int DEFAULT_WIDTH = 1180;
    public static final int DEFAULT_HEIGHT = 740;
    public static final int MIN_WIDTH = 1050;
    public static final int MIN_HEIGHT = 680;

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle(APP_TITLE);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        initializeApplication();
    }

    private void initializeApplication() {
        try {
            // 1. Resolve database configuration
            DatabaseConfig config = DatabaseConfig.load();
            ConnectionProvider connectionProvider = new ConnectionProvider(config);

            // 2. Validate connectivity immediately
            if (!connectionProvider.testConnection()) {
                throw new DatabaseException("Could not establish a connection to MySQL server at " + config.getUrl()
                        + ". Please ensure MySQL is running locally and credentials are valid.");
            }

            // 3. Initialize schema idempotently without destructive DDL
            SchemaInitializer schemaInitializer = new SchemaInitializer(connectionProvider);
            schemaInitializer.initialize();

            // 4. Instantiate persistence DAOs
            PatientDao patientDao = new PatientDao();
            DoctorDao doctorDao = new DoctorDao();
            TreatmentRecordDao treatmentRecordDao = new TreatmentRecordDao();
            hems.dao.SequenceDao sequenceDao = new hems.dao.SequenceDao();

            // 5. Initialize authoritative service layer
            HospitalSystem hospitalSystem = new HospitalSystem(patientDao, doctorDao, treatmentRecordDao, sequenceDao, connectionProvider);

            // 6. Reload from MySQL and reconcile in-memory priority queue and doctor states
            hospitalSystem.reloadFromDatabase();

            // 7. Render primary desktop interface
            MainView mainView = new MainView(hospitalSystem, connectionProvider);
            Scene scene = new Scene(mainView, DEFAULT_WIDTH, DEFAULT_HEIGHT);

            primaryStage.setScene(scene);
            primaryStage.show();

            LOGGER.info("HEMS Desktop Application started successfully.");
        } catch (Throwable ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize application against database", ex);
            showStartupDatabaseErrorScreen(ex);
        }
    }

    /**
     * Renders a friendly, secret-free error console when MySQL is unreachable.
     */
    private void showStartupDatabaseErrorScreen(Throwable error) {
        VBox root = new VBox(18);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #F5F4EF; -fx-font-family: 'Segoe UI', sans-serif;");

        VBox card = new VBox(16);
        card.setMaxWidth(620);
        card.setPadding(new Insets(28));
        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #F4BEB9; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label badge = new Label("DATABASE CONNECTION FAILURE");
        badge.setStyle("-fx-background-color: #FBEAE8; -fx-text-fill: #D94F45; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");

        Label title = new Label("Unable to Reach MySQL Database");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #172B3A;");

        String displayMessage = error instanceof DatabaseException ? error.getMessage() :
                "A connection error occurred while connecting to the MySQL database server.";

        Label message = new Label(displayMessage);
        message.setWrapText(true);
        message.setStyle("-fx-font-size: 13px; -fx-text-fill: #465561;");

        Label instructionsTitle = new Label("How to fix this issue:");
        instructionsTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #172B3A;");

        Label instructions = new Label(
                "1. Verify that MySQL 8.0 is running on your computer:\n" +
                "     Run in PowerShell: Get-Service MySQL80\n" +
                "     If stopped, start it: Start-Service MySQL80\n\n" +
                "2. Verify environment variables are configured:\n" +
                "     HEMS_DB_URL = jdbc:mysql://localhost:3306/hems\n" +
                "     HEMS_DB_USER = hems_user\n" +
                "     HEMS_DB_PASSWORD = [your-password]\n\n" +
                "3. Or provide config/db.properties (see config/db.properties.example).\n\n" +
                "HEMS strictly refuses to run in memory-only mode to prevent silent data loss."
        );
        instructions.setWrapText(true);
        instructions.setStyle("-fx-font-size: 12px; -fx-text-fill: #556B7A; -fx-line-spacing: 3px; -fx-background-color: #F8F9FA; -fx-padding: 12px; -fx-background-radius: 4px;");

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button btnRetry = new Button("Retry Connection");
        btnRetry.setStyle("-fx-background-color: #167D83; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-background-radius: 4px; -fx-cursor: hand;");
        btnRetry.setOnAction(e -> initializeApplication());

        Button btnExit = new Button("Exit");
        btnExit.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #DDE3E5; -fx-text-fill: #172B3A; -fx-padding: 8px 18px; -fx-background-radius: 4px; -fx-cursor: hand;");
        btnExit.setOnAction(e -> Platform.exit());

        actions.getChildren().addAll(btnExit, btnRetry);

        card.getChildren().addAll(badge, title, message, instructionsTitle, instructions, actions);
        root.getChildren().add(card);

        Scene scene = new Scene(root, 780, 560);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
