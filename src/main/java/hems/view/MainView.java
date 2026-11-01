package hems.view;

import hems.database.ConnectionProvider;
import hems.service.HospitalSystem;
import hems.util.VectorIcons;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * Main application window layout.
 * Houses the midnight-navy left navigation sidebar, top contextual header,
 * notification/alert banner, and central dynamic view area.
 */
public class MainView extends BorderPane {

    private final HospitalSystem hospitalSystem;
    private final ConnectionProvider connectionProvider;

    // Header labels
    private final Label lblViewTitle = new Label("Dashboard");
    private final Label lblViewSubtitle = new Label("Hospital operations and emergency triage overview");

    // Notification banner
    private final Label lblNotification = new Label();
    private final HBox notificationBanner = new HBox(lblNotification);

    // Navigation buttons and views map
    private final Map<String, Button> navButtons = new HashMap<>();
    private final Map<String, Node> viewCache = new HashMap<>();
    private String currentViewKey = "dashboard";

    private final StackPane contentArea = new StackPane();

    public MainView(HospitalSystem hospitalSystem, ConnectionProvider connectionProvider) {
        this.hospitalSystem = hospitalSystem;
        this.connectionProvider = connectionProvider;

        getStylesheets().add(getClass().getResource("/hems/view/css/application.css").toExternalForm());

        setLeft(buildSidebar());
        setTop(buildHeader());
        setCenter(contentArea);

        // Preload views
        viewCache.put("dashboard", new DashboardView(hospitalSystem, this));
        viewCache.put("patients", new PatientsView(hospitalSystem, this));
        viewCache.put("doctors", new DoctorsView(hospitalSystem, this));
        viewCache.put("queue", new EmergencyQueueView(hospitalSystem, this));
        viewCache.put("active", new ActiveTreatmentsView(hospitalSystem, this));
        viewCache.put("history", new PatientHistoryView(hospitalSystem, this));
        viewCache.put("settings", new SettingsView(hospitalSystem, connectionProvider, this));

        navigateTo("dashboard");
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // Header / Brand area
        VBox brandBox = new VBox(6);
        brandBox.getStyleClass().add("sidebar-header");

        HBox logoBox = new HBox(10);
        logoBox.setAlignment(Pos.CENTER_LEFT);

        Node crossMark = VectorIcons.medicalCross(22, Color.web("#167D83"));
        VBox titleBox = new VBox(2);
        Label brandTitle = new Label("HEMS");
        brandTitle.getStyleClass().add("sidebar-brand-title");
        Label brandSub = new Label("Emergency Management");
        brandSub.getStyleClass().add("sidebar-brand-subtitle");
        titleBox.getChildren().addAll(brandTitle, brandSub);

        logoBox.getChildren().addAll(crossMark, titleBox);
        brandBox.getChildren().add(logoBox);

        // Nav items
        VBox navBox = new VBox(4);
        navBox.setPadding(new Insets(16, 12, 16, 12));
        VBox.setVgrow(navBox, Priority.ALWAYS);

        addNavItem(navBox, "dashboard", "Dashboard", VectorIcons.dashboardIcon(Color.web("#C2CFD6")));
        addNavItem(navBox, "patients", "Patients", VectorIcons.patientsIcon(Color.web("#C2CFD6")));
        addNavItem(navBox, "doctors", "Physicians", VectorIcons.doctorsIcon(Color.web("#C2CFD6")));
        addNavItem(navBox, "queue", "Emergency Queue", VectorIcons.queueIcon(Color.web("#C2CFD6")));
        addNavItem(navBox, "active", "Active Treatments", VectorIcons.treatmentIcon(Color.web("#C2CFD6")));
        addNavItem(navBox, "history", "Patient History", VectorIcons.historyIcon(Color.web("#C2CFD6")));
        addNavItem(navBox, "settings", "Settings & DB", VectorIcons.settingsIcon(Color.web("#C2CFD6")));

        // Footer area
        VBox footerBox = new VBox(6);
        footerBox.getStyleClass().add("sidebar-footer");

        HBox statusPill = new HBox(6);
        statusPill.setAlignment(Pos.CENTER_LEFT);
        Label statusDot = new Label("\u25CF");
        statusDot.setStyle("-fx-text-fill: #167D83; -fx-font-size: 11px;");
        Label statusText = new Label("MySQL 8.0: Active");
        statusText.setStyle("-fx-text-fill: #8EA0AD; -fx-font-size: 11px;");
        statusPill.getChildren().addAll(statusDot, statusText);

        Label copy = new Label("B.Tech CS Engineering Project");
        copy.setStyle("-fx-text-fill: #556B7A; -fx-font-size: 10px;");

        footerBox.getChildren().addAll(statusPill, copy);

        sidebar.getChildren().addAll(brandBox, navBox, footerBox);
        return sidebar;
    }

    private void addNavItem(VBox container, String key, String label, Node icon) {
        Button btn = new Button(label);
        btn.getStyleClass().add("sidebar-nav-button");
        btn.setGraphic(icon);
        btn.setGraphicTextGap(12);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> navigateTo(key));

        navButtons.put(key, btn);
        container.getChildren().add(btn);
    }

    private VBox buildHeader() {
        VBox headerContainer = new VBox();

        HBox topBar = new HBox(16);
        topBar.getStyleClass().add("top-header");
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox titleGroup = new VBox(2);
        lblViewTitle.getStyleClass().add("view-title");
        lblViewSubtitle.getStyleClass().add("view-subtitle");
        titleGroup.getChildren().addAll(lblViewTitle, lblViewSubtitle);
        HBox.setHgrow(titleGroup, Priority.ALWAYS);

        Button btnRefresh = new Button("Sync & Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setGraphic(VectorIcons.refreshIcon(Color.web("#172B3A")));
        btnRefresh.setOnAction(e -> refreshAllViews());

        topBar.getChildren().addAll(titleGroup, btnRefresh);

        // Notification Banner
        notificationBanner.setAlignment(Pos.CENTER_LEFT);
        notificationBanner.setVisible(false);
        notificationBanner.setManaged(false);
        notificationBanner.setPadding(new Insets(8, 28, 8, 28));

        headerContainer.getChildren().addAll(topBar, notificationBanner);
        return headerContainer;
    }

    public void navigateTo(String key) {
        currentViewKey = key;

        // Update button styles
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            if (entry.getKey().equals(key)) {
                if (!entry.getValue().getStyleClass().contains("sidebar-nav-button-active")) {
                    entry.getValue().getStyleClass().add("sidebar-nav-button-active");
                }
            } else {
                entry.getValue().getStyleClass().remove("sidebar-nav-button-active");
            }
        }

        // Update titles
        switch (key) {
            case "dashboard":
                lblViewTitle.setText("Triage Dashboard");
                lblViewSubtitle.setText("Live hospital statistics, priority queue overview, and triage actions.");
                break;
            case "patients":
                lblViewTitle.setText("Patient Registry & Visits");
                lblViewSubtitle.setText("Register patients, record emergency visits, and inspect medical files.");
                break;
            case "doctors":
                lblViewTitle.setText("Medical Staff Roster");
                lblViewSubtitle.setText("Manage attending physicians and review real-time duty availability.");
                break;
            case "queue":
                lblViewTitle.setText("Emergency Waiting Queue");
                lblViewSubtitle.setText("Prioritized waiting visits ordered strictly by clinical severity and arrival time.");
                break;
            case "active":
                lblViewTitle.setText("Active Emergency Treatments");
                lblViewSubtitle.setText("Track ongoing emergency procedures and record treatment completion.");
                break;
            case "history":
                lblViewTitle.setText("Patient History & Archives");
                lblViewSubtitle.setText("Persistent emergency visit histories retrieved directly from MySQL.");
                break;
            case "settings":
                lblViewTitle.setText("System & Database Console");
                lblViewSubtitle.setText("JDBC connection diagnostics, schema verification, and recovery tools.");
                break;
        }

        Node view = viewCache.get(key);
        if (view != null) {
            contentArea.getChildren().setAll(view);
            refreshActiveView();
        }
    }

    public void refreshActiveView() {
        Node view = viewCache.get(currentViewKey);
        if (view instanceof DashboardView) {
            ((DashboardView) view).refreshData();
        } else if (view instanceof PatientsView) {
            ((PatientsView) view).refreshData();
        } else if (view instanceof DoctorsView) {
            ((DoctorsView) view).refreshData();
        } else if (view instanceof EmergencyQueueView) {
            ((EmergencyQueueView) view).refreshData();
        } else if (view instanceof ActiveTreatmentsView) {
            ((ActiveTreatmentsView) view).refreshData();
        } else if (view instanceof SettingsView) {
            ((SettingsView) view).refreshConnectionStatus();
        }
    }

    public void refreshAllViews() {
        for (Node view : viewCache.values()) {
            if (view instanceof DashboardView) ((DashboardView) view).refreshData();
            if (view instanceof PatientsView) ((PatientsView) view).refreshData();
            if (view instanceof DoctorsView) ((DoctorsView) view).refreshData();
            if (view instanceof EmergencyQueueView) ((EmergencyQueueView) view).refreshData();
            if (view instanceof ActiveTreatmentsView) ((ActiveTreatmentsView) view).refreshData();
            if (view instanceof SettingsView) ((SettingsView) view).refreshConnectionStatus();
        }
        showSuccess("Synchronized all hospital data with MySQL.");
    }

    public void showSuccess(String message) {
        showBanner(message, "banner-success");
    }

    public void showError(String message) {
        showBanner(message, "banner-error");
    }

    public void showInfo(String message) {
        showBanner(message, "banner-info");
    }

    private void showBanner(String message, String styleClass) {
        notificationBanner.getStyleClass().setAll(styleClass);
        lblNotification.setText(message);
        notificationBanner.setVisible(true);
        notificationBanner.setManaged(true);

        PauseTransition hideDelay = new PauseTransition(Duration.seconds(6));
        hideDelay.setOnFinished(e -> {
            notificationBanner.setVisible(false);
            notificationBanner.setManaged(false);
        });
        hideDelay.play();
    }
}
