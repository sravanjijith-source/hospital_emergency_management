package hems.util;

import hems.model.DoctorStatus;
import hems.model.Severity;
import hems.model.TreatmentStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Reusable UI widgets and component builders adhering to the clinical editorial style.
 */
public final class UiUtil {

    private UiUtil() {}

    public static Label createSeverityBadge(Severity severity) {
        if (severity == null) {
            Label badge = new Label("-");
            badge.getStyleClass().add("badge");
            return badge;
        }
        Label badge = new Label(severity.name());
        badge.getStyleClass().add("badge");
        switch (severity) {
            case CRITICAL:
                badge.getStyleClass().add("badge-critical");
                break;
            case HIGH:
                badge.getStyleClass().add("badge-high");
                break;
            case MEDIUM:
                badge.getStyleClass().add("badge-medium");
                break;
            case LOW:
                badge.getStyleClass().add("badge-low");
                break;
        }
        return badge;
    }

    public static Label createDoctorStatusBadge(DoctorStatus status) {
        if (status == null) {
            Label badge = new Label("-");
            badge.getStyleClass().add("badge");
            return badge;
        }
        Label badge = new Label(status.name());
        badge.getStyleClass().add("badge");
        switch (status) {
            case AVAILABLE:
                badge.getStyleClass().add("badge-available");
                break;
            case BUSY:
                badge.getStyleClass().add("badge-busy");
                break;
            case OFF_DUTY:
                badge.getStyleClass().add("badge-off-duty");
                break;
        }
        return badge;
    }

    public static Label createTreatmentStatusBadge(TreatmentStatus status) {
        if (status == null) {
            Label badge = new Label("-");
            badge.getStyleClass().add("badge");
            return badge;
        }
        Label badge = new Label(status.name());
        badge.getStyleClass().add("badge");
        switch (status) {
            case WAITING:
                badge.getStyleClass().add("badge-waiting");
                break;
            case IN_TREATMENT:
                badge.getStyleClass().add("badge-in-treatment");
                break;
            case COMPLETED:
                badge.getStyleClass().add("badge-completed");
                break;
        }
        return badge;
    }

    public static VBox createStatCard(String label, String initialValue, String valueStyleClass) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("stat-label");

        Label val = new Label(initialValue);
        val.getStyleClass().add(valueStyleClass != null ? valueStyleClass : "stat-value");

        card.getChildren().addAll(lbl, val);
        return card;
    }

    public static boolean showConfirmationDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
