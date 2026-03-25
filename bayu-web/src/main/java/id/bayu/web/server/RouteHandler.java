package id.bayu.web.server;

@FunctionalInterface
public interface RouteHandler {
    void handle(RequestContext ctx, ResponseWriter res) throws Exception;
}
