package id.bayu.example.ws;

import id.bayu.web.annotation.WebSocket;
import id.bayu.web.server.BayuHttpServer.WebSocketListener;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@WebSocket("/ws/chat")
public class ChatWebSocket implements WebSocketListener {

    private final Set<WebSocketChannel> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void onOpen(WebSocketChannel channel) {
        sessions.add(channel);
        broadcast("System: new user joined (" + sessions.size() + " online)");
    }

    @Override
    public void onMessage(WebSocketChannel channel, String message) {
        broadcast("User: " + message);
    }

    @Override
    public void onClose(WebSocketChannel channel, int code, String reason) {
        sessions.remove(channel);
        broadcast("System: user left (" + sessions.size() + " online)");
    }

    private void broadcast(String message) {
        for (WebSocketChannel session : sessions) {
            WebSockets.sendText(message, session, null);
        }
    }
}
