package hems.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Common formatting for dates, times, and elapsed durations.
 */
public final class DateTimeUtil {

    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateTimeUtil() {
        // utility class
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    public static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(TIME_FORMATTER);
    }

    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Calculates human-readable duration between from and to (or now if to is null).
     * Example: "45m", "1h 15m", "< 1m".
     */
    public static String formatDuration(LocalDateTime from, LocalDateTime to) {
        if (from == null) {
            return "-";
        }
        LocalDateTime end = (to != null) ? to : LocalDateTime.now();
        Duration duration = Duration.between(from, end);
        if (duration.isNegative()) {
            return "0m";
        }
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "< 1m";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours == 0) {
            return minutes + "m";
        }
        return hours + "h " + remainingMinutes + "m";
    }
}
