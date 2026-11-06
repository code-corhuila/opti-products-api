package co.edu.corhuila.opti.products.domain.model;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** Reusable field rules. Every failure names the field so the client can show it next to the input. */
public final class Validation {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private Validation() {
    }

    public static String text(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw DomainException.validation(field, min == 0
                    ? "Debe tener como máximo " + max + " caracteres"
                    : "Debe tener entre " + min + " y " + max + " caracteres");
        }
        return trimmed;
    }

    /** The {@code Idempotency-Key} header of a creation: 8 to 128 characters. */
    public static String idempotencyKey(String value) {
        if (value == null || value.length() < 8 || value.length() > 128) {
            throw validation("Idempotency-Key", "Este campo requiere de 8 a 128 caracteres");
        }
        return value;
    }

    private static DomainException validation(String field, String message) {
        return DomainException.validation(field, message);
    }

    public static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return text(value, field, 1, max);
    }

    public static String matching(String value, String field, Pattern pattern, String hint) {
        String trimmed = value == null ? "" : value.trim();
        if (!pattern.matcher(trimmed).matches()) {
            throw DomainException.validation(field, hint);
        }
        return trimmed;
    }

    public static String optionalEmail(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 160 || !EMAIL.matcher(trimmed).matches()) {
            throw DomainException.validation(field, "Escribe un correo electrónico válido");
        }
        return trimmed.toLowerCase();
    }

    public static long positive(long value, String field) {
        if (value <= 0) {
            throw DomainException.validation(field, "Debe ser mayor que cero");
        }
        return value;
    }

    public static long nonNegative(long value, String field) {
        if (value < 0) {
            throw DomainException.validation(field, "No puede ser negativo");
        }
        return value;
    }

    public static int intBetween(int value, String field, int min, int max) {
        if (value < min || value > max) {
            throw DomainException.validation(field, "Debe estar entre " + min + " y " + max);
        }
        return value;
    }

    public static LocalDate pastOrToday(LocalDate value, String field, LocalDate today) {
        if (value != null && value.isAfter(today)) {
            throw DomainException.validation(field, "No puede ser una fecha futura");
        }
        return value;
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw DomainException.validation(field, "Este campo es obligatorio");
        }
        return value;
    }
}
