package id.bayu.security.interceptor;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

public final class SecurityContext {

    private static final ThreadLocal<SecurityInfo> current = new ThreadLocal<>();

    private SecurityContext() {}

    public static void set(Principal principal, Set<String> roles) {
        current.set(new SecurityInfo(principal, roles));
    }

    public static void clear() {
        current.remove();
    }

    public static Principal getPrincipal() {
        SecurityInfo info = current.get();
        return info != null ? info.principal() : null;
    }

    public static Set<String> getRoles() {
        SecurityInfo info = current.get();
        return info != null ? info.roles() : Collections.emptySet();
    }

    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    public static boolean isAuthenticated() {
        return current.get() != null;
    }

    public record SecurityInfo(Principal principal, Set<String> roles) {}
}
