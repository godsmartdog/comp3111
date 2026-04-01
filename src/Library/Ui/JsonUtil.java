package Library.Ui;

public final class JsonUtil {
    private JsonUtil() {
    }

    public static String escape(String input) {
        if (input == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char ch : input.toCharArray()) {
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }
}
