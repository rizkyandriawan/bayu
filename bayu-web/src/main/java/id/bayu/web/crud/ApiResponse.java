package id.bayu.web.crud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiResponse {

    public static Map<String, Object> ok(Object body) {
        var m = new LinkedHashMap<String, Object>();
        m.put("statusCode", 200);
        m.put("body", body);
        return m;
    }

    public static Map<String, Object> created(Object body) {
        var m = new LinkedHashMap<String, Object>();
        m.put("statusCode", 201);
        m.put("body", body);
        return m;
    }

    public static <T> Map<String, Object> list(List<T> items) {
        var m = new LinkedHashMap<String, Object>();
        m.put("statusCode", 200);
        m.put("body", Map.of("items", items));
        return m;
    }

    public static Map<String, Object> error(int status, String message) {
        var m = new LinkedHashMap<String, Object>();
        m.put("error", status >= 500 ? "Internal Server Error" : "Bad Request");
        m.put("message", message);
        m.put("status", status);
        return m;
    }
}
