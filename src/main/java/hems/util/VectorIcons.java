package hems.util;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Creates crisp, lightweight vector SVG icons styled to match the clinical theme.
 * Avoids heavy icon libraries and ensures crisp rendering on high-DPI displays.
 */
public final class VectorIcons {

    private VectorIcons() {}

    // SVG path definitions (standard 24x24 or 16x16 viewbox)
    private static final String SVG_CROSS =
            "M9 3h6v6h6v6h-6v6H9v-6H3V9h6V3z";

    private static final String SVG_DASHBOARD =
            "M3 3h8v8H3V3zm10 0h8v5h-8V3zm0 7h8v11h-8V10zM3 13h8v8H3v-8z";

    private static final String SVG_PATIENTS =
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";

    private static final String SVG_DOCTORS =
            "M19 8h-2v3h-3v2h3v3h2v-3h3v-2h-3V8zm-9 4c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";

    private static final String SVG_QUEUE =
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z";

    private static final String SVG_TREATMENT =
            "M3 13h4l2-6 4 12 2-8 2 4h4v-2h-3l-2-4-2 8-4-12-3 8H3v2z";

    private static final String SVG_HISTORY =
            "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z";

    private static final String SVG_SETTINGS =
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";

    private static final String SVG_ASSIGN =
            "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";

    private static final String SVG_REFRESH =
            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";

    private static SVGPath createIcon(String pathContent, double scale, Color fill) {
        SVGPath path = new SVGPath();
        path.setContent(pathContent);
        path.setFill(fill);
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }

    public static Node medicalCross(double size, Color fill) {
        return createIcon(SVG_CROSS, size / 24.0, fill);
    }

    public static Node dashboardIcon(Color fill) {
        return createIcon(SVG_DASHBOARD, 0.75, fill);
    }

    public static Node patientsIcon(Color fill) {
        return createIcon(SVG_PATIENTS, 0.75, fill);
    }

    public static Node doctorsIcon(Color fill) {
        return createIcon(SVG_DOCTORS, 0.75, fill);
    }

    public static Node queueIcon(Color fill) {
        return createIcon(SVG_QUEUE, 0.75, fill);
    }

    public static Node treatmentIcon(Color fill) {
        return createIcon(SVG_TREATMENT, 0.75, fill);
    }

    public static Node historyIcon(Color fill) {
        return createIcon(SVG_HISTORY, 0.75, fill);
    }

    public static Node settingsIcon(Color fill) {
        return createIcon(SVG_SETTINGS, 0.75, fill);
    }

    public static Node assignIcon(Color fill) {
        return createIcon(SVG_ASSIGN, 0.75, fill);
    }

    public static Node refreshIcon(Color fill) {
        return createIcon(SVG_REFRESH, 0.75, fill);
    }
}
