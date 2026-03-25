package id.bayu.web.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Deque;

public class BayuHttpServer {

    private final Undertow.Builder builder;
    private Undertow server;
    private final Router router;
    private final ObjectMapper objectMapper;
    private final int port;
    private CorsConfig corsConfig;

    // WebSocket: path -> handler
    private final Map<String, WebSocketEndpoint> wsEndpoints = new ConcurrentHashMap<>();

    public BayuHttpServer(int port) {
        this.port = port;
        this.router = new Router();
        this.objectMapper = createObjectMapper();
        this.builder = Undertow.builder()
                .addHttpListener(port, "0.0.0.0");
    }

    public void setCorsConfig(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ---- WebSocket API ----

    public void addWebSocket(String path, WebSocketListener listener) {
        WebSocketEndpoint endpoint = new WebSocketEndpoint(listener);
        wsEndpoints.put(path, endpoint);
    }

    public Set<WebSocketChannel> getWebSocketSessions(String path) {
        WebSocketEndpoint ep = wsEndpoints.get(path);
        return ep != null ? Collections.unmodifiableSet(ep.sessions) : Set.of();
    }

    public void broadcastWebSocket(String path, String message) {
        WebSocketEndpoint ep = wsEndpoints.get(path);
        if (ep != null) {
            for (WebSocketChannel ch : ep.sessions) {
                WebSockets.sendText(message, ch, null);
            }
        }
    }

    // ---- Lifecycle ----

    public void start() {
        HttpHandler rootHandler = this::dispatch;

        // If we have WebSocket endpoints, wrap with path routing
        if (!wsEndpoints.isEmpty()) {
            HttpHandler httpHandler = rootHandler;
            // Build a path-checking handler that delegates to WS or HTTP
            rootHandler = exchange -> {
                String path = exchange.getRequestPath();
                WebSocketEndpoint wsEp = wsEndpoints.get(path);
                if (wsEp != null) {
                    // Upgrade to WebSocket
                    new WebSocketProtocolHandshakeHandler(wsEp.callback).handleRequest(exchange);
                } else {
                    httpHandler.handleRequest(exchange);
                }
            };
        }

        this.server = builder.setHandler(rootHandler).build();
        server.start();
        System.out.println("Bayu server started on port " + port + " (Undertow)");
        if (!wsEndpoints.isEmpty()) {
            wsEndpoints.keySet().forEach(p -> System.out.println("  WebSocket: ws://localhost:" + port + p));
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            System.out.println("Bayu server stopped");
        }
    }

    // ---- HTTP dispatch ----

    private void dispatch(HttpServerExchange exchange) {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this::dispatch);
            return;
        }

        String method = exchange.getRequestMethod().toString();
        String path = exchange.getRequestPath();

        // CORS
        applyCorsHeaders(exchange);

        if ("OPTIONS".equals(method)) {
            exchange.setStatusCode(204);
            exchange.endExchange();
            return;
        }

        Router.MatchResult match = router.match(method, path);

        if (match == null) {
            sendError(exchange, 404, "Not Found: " + method + " " + path);
            return;
        }

        if (match.isMethodNotAllowed()) {
            exchange.getResponseHeaders().put(new HttpString("Allow"),
                    String.join(", ", match.allowedMethods()));
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        // Read body
        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            RequestContext ctx = new RequestContext(ex, match.pathParams(), objectMapper, data);
            ResponseWriter res = new ResponseWriter(ex, objectMapper);

            try {
                match.handler().handle(ctx, res);
                if (!res.isCommitted()) {
                    res.empty();
                }
            } catch (Exception e) {
                if (!res.isCommitted()) {
                    System.err.println("Error handling " + method + " " + path + ": " + e.getMessage());
                    e.printStackTrace();
                    sendError(ex, 500, "Internal Server Error");
                }
            }
        });
    }

    private void applyCorsHeaders(HttpServerExchange exchange) {
        if (corsConfig == null) return;

        HeaderMap reqHeaders = exchange.getRequestHeaders();
        String origin = reqHeaders.getFirst("Origin");

        if (origin != null && corsConfig.isOriginAllowed(origin)) {
            HeaderMap resHeaders = exchange.getResponseHeaders();
            resHeaders.put(new HttpString("Access-Control-Allow-Origin"), origin);
            resHeaders.put(new HttpString("Access-Control-Allow-Methods"),
                    String.join(", ", corsConfig.getAllowedMethods()));

            String reqHeader = reqHeaders.getFirst("Access-Control-Request-Headers");
            if (corsConfig.getAllowedHeaders().contains("*") && reqHeader != null) {
                resHeaders.put(new HttpString("Access-Control-Allow-Headers"), reqHeader);
            } else {
                resHeaders.put(new HttpString("Access-Control-Allow-Headers"),
                        String.join(", ", corsConfig.getAllowedHeaders()));
            }
            if (corsConfig.isAllowCredentials()) {
                resHeaders.put(new HttpString("Access-Control-Allow-Credentials"), "true");
            }
            resHeaders.put(new HttpString("Access-Control-Max-Age"),
                    String.valueOf(corsConfig.getMaxAge()));
        }
    }

    private void sendError(HttpServerExchange exchange, int code, String message) {
        exchange.setStatusCode(code);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(
                "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }

    public Router getRouter() { return router; }
    public ObjectMapper getObjectMapper() { return objectMapper; }
    public int getPort() { return port; }

    // ---- WebSocket internals ----

    private static class WebSocketEndpoint {
        final WebSocketListener listener;
        final Set<WebSocketChannel> sessions = new CopyOnWriteArraySet<>();
        final WebSocketConnectionCallback callback;

        WebSocketEndpoint(WebSocketListener listener) {
            this.listener = listener;
            this.callback = (exchange, channel) -> {
                sessions.add(channel);
                listener.onOpen(channel);

                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage msg) {
                        listener.onMessage(ch, msg.getData());
                    }

                    @Override
                    protected void onError(WebSocketChannel ch, Throwable error) {
                        listener.onError(ch, error);
                    }

                    @Override
                    protected void onCloseMessage(CloseMessage cm, WebSocketChannel ch) {
                        sessions.remove(ch);
                        listener.onClose(ch, cm.getCode(), cm.getReason());
                    }
                });
                channel.resumeReceives();

                channel.addCloseTask(ch -> {
                    sessions.remove(ch);
                });
            };
        }
    }

    /**
     * Listener interface for WebSocket events.
     */
    public interface WebSocketListener {
        default void onOpen(WebSocketChannel channel) {}
        void onMessage(WebSocketChannel channel, String message);
        default void onClose(WebSocketChannel channel, int code, String reason) {}
        default void onError(WebSocketChannel channel, Throwable error) {
            System.err.println("WebSocket error: " + error.getMessage());
        }
    }
}
