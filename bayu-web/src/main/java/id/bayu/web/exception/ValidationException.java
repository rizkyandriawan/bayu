package id.bayu.web.exception;

import java.util.List;
import java.util.Map;

/**
 * Thrown when validation fails. Contains structured error details.
 */
public class ValidationException extends HttpException {

    private final List<Map<String, String>> errors;

    public ValidationException(List<Map<String, String>> errors) {
        super(400, "Validation failed");
        this.errors = errors;
    }

    public ValidationException(String field, String message) {
        this(List.of(Map.of("field", field, "message", message)));
    }

    public List<Map<String, String>> getErrors() { return errors; }
}
