package id.bayu.security.interceptor;

import id.bayu.web.server.RequestContext;
import id.bayu.web.server.ResponseWriter;

public interface BayuInterceptor {

    default boolean preHandle(RequestContext ctx, ResponseWriter res) throws Exception {
        return true;
    }

    default void postHandle(RequestContext ctx, ResponseWriter res, Object result) throws Exception {
    }
}
