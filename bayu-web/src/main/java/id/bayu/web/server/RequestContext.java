package id.bayu.web.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class RequestContext {

    private final HttpServerExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, String> queryParams;
    private final ObjectMapper objectMapper;
    private final byte[] bodyBytes;

    public RequestContext(HttpServerExchange exchange, Map<String, String> pathParams,
                          ObjectMapper objectMapper, byte[] bodyBytes) {
        this.exchange = exchange;
        this.pathParams = pathParams != null ? pathParams : Map.of();
        this.queryParams = parseQueryParams(exchange);
        this.objectMapper = objectMapper;
        this.bodyBytes = bodyBytes != null ? bodyBytes : new byte[0];
    }

    public String pathVariable(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Path variable '" + name + "' not found");
        }
        return value;
    }

    public String queryParam(String name) {
        return queryParams.get(name);
    }

    public String queryParam(String name, String defaultValue) {
        return queryParams.getOrDefault(name, defaultValue);
    }

    public <T> T body(Class<T> type) throws IOException {
        return objectMapper.readValue(bodyBytes, type);
    }

    public String bodyAsString() {
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

    public byte[] bodyBytes() {
        return bodyBytes;
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String method() {
        return exchange.getRequestMethod().toString();
    }

    public String path() {
        return exchange.getRequestPath();
    }

    public Map<String, String> pathParams() {
        return Map.copyOf(pathParams);
    }

    public Map<String, String> queryParams() {
        return Map.copyOf(queryParams);
    }

    private static Map<String, String> parseQueryParams(HttpServerExchange exchange) {
        Map<String, String> params = new HashMap<>();
        Map<String, Deque<String>> qp = exchange.getQueryParameters();
        for (Map.Entry<String, Deque<String>> entry : qp.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                params.put(entry.getKey(), entry.getValue().getFirst());
            }
        }
        return params;
    }
}
