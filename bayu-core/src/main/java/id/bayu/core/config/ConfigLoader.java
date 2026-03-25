package id.bayu.core.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    private final Map<String, String> properties = new HashMap<>();

    public void load(String... args) {
        // 1. Load application.yml from classpath
        loadYaml("application.yml");

        // 2. Check for active profile
        String profile = resolveProfile(args);
        if (profile != null && !profile.isEmpty()) {
            loadYaml("application-" + profile + ".yml");
        }

        // 3. Environment variable overrides: BAYU_SERVER_PORT -> server.port
        applyEnvironmentOverrides();

        // 4. Command line args: --server.port=9090
        applyCommandLineArgs(args);
    }

    private String resolveProfile(String[] args) {
        // Check command line first: --bayu.profile=dev
        for (String arg : args) {
            if (arg.startsWith("--bayu.profile=")) {
                return arg.substring("--bayu.profile=".length());
            }
        }
        // Then env var
        String envProfile = System.getenv("BAYU_PROFILE");
        if (envProfile != null) return envProfile;
        // Then config
        return properties.get("bayu.profile");
    }

    private void loadYaml(String resource) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (is == null) return;

        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(is);
        if (data != null) {
            flatten("", data);
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<String, Object>) value);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        flatten(key + "[" + i + "]", (Map<String, Object>) item);
                    } else {
                        properties.put(key + "[" + i + "]", String.valueOf(item));
                    }
                }
            } else {
                properties.put(key, value == null ? "" : String.valueOf(value));
            }
        }
    }

    private void applyEnvironmentOverrides() {
        // BAYU_SERVER_PORT -> server.port
        System.getenv().forEach((envKey, envValue) -> {
            if (envKey.startsWith("BAYU_")) {
                String configKey = envKey.substring(5).toLowerCase().replace('_', '.');
                properties.put(configKey, envValue);
            }
        });
    }

    private void applyCommandLineArgs(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String key = arg.substring(2, arg.indexOf('='));
                String value = arg.substring(arg.indexOf('=') + 1);
                properties.put(key, value);
            }
        }
    }

    public String get(String key) {
        return resolveValue(properties.get(key));
    }

    public String get(String key, String defaultValue) {
        String value = properties.get(key);
        return value != null ? resolveValue(value) : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public Map<String, String> getAll() {
        return Map.copyOf(properties);
    }

    private String resolveValue(String value) {
        if (value == null) return null;
        // Resolve ${ENV_VAR:-default} patterns
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (i + 1 < value.length() && value.charAt(i) == '$' && value.charAt(i + 1) == '{') {
                int end = value.indexOf('}', i + 2);
                if (end != -1) {
                    String expr = value.substring(i + 2, end);
                    String resolved = resolveExpression(expr);
                    result.append(resolved);
                    i = end + 1;
                    continue;
                }
            }
            result.append(value.charAt(i));
            i++;
        }
        return result.toString();
    }

    private String resolveExpression(String expr) {
        // Handle ${KEY:-default} or ${KEY:default} or ${KEY}
        int defaultSep = expr.indexOf(":-");
        String key;
        String defaultValue;
        if (defaultSep != -1) {
            key = expr.substring(0, defaultSep);
            defaultValue = expr.substring(defaultSep + 2);
        } else {
            int colonSep = expr.indexOf(':');
            if (colonSep != -1) {
                key = expr.substring(0, colonSep);
                defaultValue = expr.substring(colonSep + 1);
            } else {
                key = expr;
                defaultValue = "";
            }
        }

        // Try env var first, then config property
        String envValue = System.getenv(key);
        if (envValue != null) return envValue;

        String propValue = properties.get(key);
        if (propValue != null) return propValue;

        return defaultValue;
    }
}
