package id.bayu.web.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Router {

    private final TrieNode root = new TrieNode();

    public void addRoute(String method, String path, RouteHandler handler) {
        String[] segments = splitPath(path);
        TrieNode current = root;
        for (String segment : segments) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                // Parameter segment
                if (current.paramChild == null) {
                    current.paramChild = new TrieNode();
                    current.paramName = segment.substring(1, segment.length() - 1);
                }
                current = current.paramChild;
            } else {
                current = current.children.computeIfAbsent(segment, k -> new TrieNode());
            }
        }
        current.handlers.put(method.toUpperCase(), handler);
    }

    public MatchResult match(String method, String path) {
        String[] segments = splitPath(path);
        Map<String, String> pathParams = new HashMap<>();
        TrieNode current = root;

        for (String segment : segments) {
            // Try exact match first
            TrieNode next = current.children.get(segment);
            if (next != null) {
                current = next;
            } else if (current.paramChild != null) {
                // Try parameter match
                pathParams.put(current.paramName, segment);
                current = current.paramChild;
            } else {
                return null; // No match
            }
        }

        RouteHandler handler = current.handlers.get(method.toUpperCase());
        if (handler == null) {
            // Check if path exists but method is wrong (for 405 response)
            if (!current.handlers.isEmpty()) {
                return new MatchResult(null, pathParams, new ArrayList<>(current.handlers.keySet()));
            }
            return null;
        }

        return new MatchResult(handler, pathParams, null);
    }

    private String[] splitPath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return new String[0];
        }
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("/");
    }

    private static class TrieNode {
        final Map<String, TrieNode> children = new HashMap<>();
        final Map<String, RouteHandler> handlers = new HashMap<>();
        TrieNode paramChild;
        String paramName;
    }

    public record MatchResult(RouteHandler handler, Map<String, String> pathParams, List<String> allowedMethods) {
        public boolean isMethodNotAllowed() {
            return handler == null && allowedMethods != null && !allowedMethods.isEmpty();
        }
    }
}
