package id.bayu.example.interceptor;

import id.bayu.security.annotation.Interceptor;
import id.bayu.security.interceptor.BayuInterceptor;
import id.bayu.security.interceptor.SecurityContext;
import id.bayu.web.server.RequestContext;
import id.bayu.web.server.ResponseWriter;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

@Interceptor(order = 1)
public class AuthInterceptor implements BayuInterceptor {

    // Simple token-based auth for demo purposes
    // In production, this would validate JWT, session, etc.
    private static final Map<String, UserInfo> TOKENS = Map.of(
            "token-admin", new UserInfo("admin", Set.of("ROLE_ADMIN", "ROLE_USER")),
            "token-user", new UserInfo("user", Set.of("ROLE_USER"))
    );

    @Override
    public boolean preHandle(RequestContext ctx, ResponseWriter res) throws Exception {
        String authHeader = ctx.header("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            UserInfo user = TOKENS.get(token);
            if (user != null) {
                SecurityContext.set(
                        (Principal) () -> user.username(),
                        user.roles()
                );
                return true;
            }
        }

        // No auth or invalid token - still allow request (public endpoints)
        // @Secured annotation will block unauthorized access
        SecurityContext.clear();
        return true;
    }

    @Override
    public void postHandle(RequestContext ctx, ResponseWriter res, Object result) throws Exception {
        SecurityContext.clear();
    }

    private record UserInfo(String username, Set<String> roles) {}
}
