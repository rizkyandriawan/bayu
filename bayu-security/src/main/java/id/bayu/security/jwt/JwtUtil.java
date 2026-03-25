package id.bayu.security.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight JWT implementation using HMAC-SHA256.
 * No external dependencies - pure JDK.
 */
public class JwtUtil {

    private final String secret;
    private final long expirationMs;
    private final String issuer;

    public JwtUtil(String secret, long expirationMs, String issuer) {
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generateToken(String subject, Map<String, String> claims) {
        long now = Instant.now().toEpochMilli();
        long exp = now + expirationMs;

        // Header
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

        // Payload
        StringBuilder payload = new StringBuilder("{");
        payload.append("\"sub\":\"").append(escape(subject)).append("\"");
        payload.append(",\"iss\":\"").append(escape(issuer)).append("\"");
        payload.append(",\"iat\":").append(now / 1000);
        payload.append(",\"exp\":").append(exp / 1000);
        for (Map.Entry<String, String> entry : claims.entrySet()) {
            payload.append(",\"").append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append("\"");
        }
        payload.append("}");

        String encodedPayload = base64Url(payload.toString());
        String signingInput = header + "." + encodedPayload;
        String signature = sign(signingInput);

        return signingInput + "." + signature;
    }

    public JwtClaims validateToken(String token) {
        if (token == null || token.isEmpty()) return null;

        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        // Verify signature
        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = sign(signingInput);
        if (!expectedSig.equals(parts[2])) return null;

        // Decode payload
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        // Simple JSON parsing (no library needed for flat claims)
        Map<String, String> claimsMap = parseSimpleJson(payloadJson);

        // Check expiration
        String expStr = claimsMap.get("exp");
        if (expStr != null) {
            long exp = Long.parseLong(expStr);
            if (Instant.now().getEpochSecond() > exp) return null; // Expired
        }

        return new JwtClaims(
                claimsMap.get("sub"),
                claimsMap.get("iss"),
                claimsMap
        );
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    private String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        int i = 0;
        while (i < json.length()) {
            // Find key
            int keyStart = json.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            String key = json.substring(keyStart + 1, keyEnd);

            // Find colon
            int colon = json.indexOf(':', keyEnd + 1);

            // Find value
            int valStart = colon + 1;
            while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;

            String value;
            if (valStart < json.length() && json.charAt(valStart) == '"') {
                int valEnd = json.indexOf('"', valStart + 1);
                value = json.substring(valStart + 1, valEnd);
                i = valEnd + 1;
            } else {
                int valEnd = json.indexOf(',', valStart);
                if (valEnd == -1) valEnd = json.length();
                value = json.substring(valStart, valEnd).trim();
                i = valEnd;
            }

            map.put(key, value);
            // Skip comma
            if (i < json.length() && json.charAt(i) == ',') i++;
        }
        return map;
    }

    public record JwtClaims(String subject, String issuer, Map<String, String> claims) {
        public String get(String key) {
            return claims.get(key);
        }
    }
}
