package hems.util;

import java.util.regex.Pattern;

/**
 * The single place where input rules live, so they are never duplicated.
 * Every method returns the cleaned (trimmed) value or throws ValidationException.
 */
public final class Validator {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_ID_LENGTH = 20;
    public static final int MAX_SPECIALIZATION_LENGTH = 80;

    // Starts with a letter/digit, then letters, digits, '-' or '_'. Examples: P-1001, D7, ER_12
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    private Validator() {
        // utility class, no instances
    }

    public static String validateName(String name) {
        return requireText(name, "Name", MAX_NAME_LENGTH);
    }

    public static String validateSpecialization(String specialization) {
        return requireText(specialization, "Specialization", MAX_SPECIALIZATION_LENGTH);
    }

    public static String validateId(String id, String label) {
        String value = requireText(id, label, MAX_ID_LENGTH);
        if (!ID_PATTERN.matcher(value).matches()) {
            throw new ValidationException(label
                    + " may contain only letters, digits, '-' and '_' (for example P-1001).");
        }
        return value;
    }

    public static int validateAge(int age, int min, int max) {
        if (age < min || age > max) {
            throw new ValidationException("Age must be between " + min + " and " + max + ".");
        }
        return age;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(label + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }
}
