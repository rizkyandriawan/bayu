package id.bayu.web.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ResponseWriter {

    private final HttpServerExchange exchange;
    private final ObjectMapper objectMapper;
    private int statusCode = 200;
    private boolean committed = false;

    public ResponseWriter(HttpServerExchange exchange, ObjectMapper objectMapper) {
        this.exchange = exchange;
        this.objectMapper = objectMapper;
    }

    public ResponseWriter status(int code) {
        this.statusCode = code;
        return this;
    }

    public ResponseWriter header(String name, String value) {
        exchange.getResponseHeaders().add(new io.undertow.util.HttpString(name), value);
        return this;
    }

    public void json(Object obj) throws IOException {
        if (committed) return;
        committed = true;

        byte[] body = objectMapper.writeValueAsBytes(obj);
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new String(body, StandardCharsets.UTF_8));
    }

    public void text(String body) throws IOException {
        if (committed) return;
        committed = true;

        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
        exchange.getResponseSender().send(body);
    }

    public void empty() throws IOException {
        if (committed) return;
        committed = true;

        exchange.setStatusCode(statusCode);
        exchange.endExchange();
    }

    public boolean isCommitted() {
        return committed;
    }

    void sendError(int code, String message) throws IOException {
        if (committed) return;
        committed = true;

        exchange.setStatusCode(code);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(
                "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
