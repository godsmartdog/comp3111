package Library.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RequestFilters {
    private RequestFilters() {
    }

    public static String getTrimmed(Map<String, String> values, String key, String defaultValue) {
        String raw = values.getOrDefault(key, defaultValue);
        return raw == null ? "" : raw.trim();
    }

    public static int parseIntInRange(Map<String, String> values,
                                      String key,
                                      int defaultValue,
                                      int min,
                                      int max) {
        String raw = getTrimmed(values, key, Integer.toString(defaultValue));
        final int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric value for " + key + ".");
        }

        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ".");
        }
        return parsed;
    }

    public static List<String> parseCsv(Map<String, String> values, String key) {
        return splitByDelimiter(values.getOrDefault(key, ""), ",");
    }

    public static List<String> parseNewlineList(Map<String, String> values, String key) {
        return splitByDelimiter(values.getOrDefault(key, ""), "\\n");
    }

    private static List<String> splitByDelimiter(String raw, String delimiterRegex) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        String[] parts = raw.split(delimiterRegex);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}