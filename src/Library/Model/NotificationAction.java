package Library.Model;

import java.io.Serializable;

public class NotificationAction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String type;
    private final String label;
    private final String target;
    private final String method;

    public NotificationAction(String type, String label, String target, String method) {
        this.type = safe(type);
        this.label = safe(label);
        this.target = safe(target);
        this.method = safe(method);
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public String getTarget() {
        return target;
    }

    public String getMethod() {
        return method;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}