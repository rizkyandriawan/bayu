package id.bayu.web.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.bayu.data.page.Page;
import id.bayu.data.page.PageRequest;
import id.bayu.web.server.Router;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers standard CRUD routes for a given path and service.
 * Provides: GET list (paginated), GET by id, POST create, PUT update, DELETE.
 */
public class CrudRouteRegistrar {

    private final Router router;
    private final ObjectMapper objectMapper;

    public CrudRouteRegistrar(Router router, ObjectMapper objectMapper) {
        this.router = router;
        this.objectMapper = objectMapper;
    }

    public <T, ID> void register(String basePath, BaseCrudService<T, ID> service,
                                  Class<T> entityClass, IdParser<ID> idParser) {
        // GET /basePath - list with pagination
        router.addRoute("GET", basePath, (ctx, res) -> {
            String pageStr = ctx.queryParam("page", "0");
            String sizeStr = ctx.queryParam("size", "20");
            String sortBy = ctx.queryParam("sort");
            String sortDir = ctx.queryParam("dir", "ASC");

            int page = Integer.parseInt(pageStr);
            int size = Integer.parseInt(sizeStr);

            // Collect filter params (everything except page, size, sort, dir)
            Map<String, String> filters = new HashMap<>(ctx.queryParams());
            filters.remove("page");
            filters.remove("size");
            filters.remove("sort");
            filters.remove("dir");

            Page<T> result = service.findAll(PageRequest.of(page, size, sortBy, sortDir), filters);
            res.json(result);
        });

        // GET /basePath/{id}
        router.addRoute("GET", basePath + "/{id}", (ctx, res) -> {
            ID id = idParser.parse(ctx.pathVariable("id"));
            var entity = service.findById(id);
            if (entity.isPresent()) {
                res.json(entity.get());
            } else {
                res.status(404).json(Map.of("error", "Not found"));
            }
        });

        // POST /basePath
        router.addRoute("POST", basePath, (ctx, res) -> {
            T entity = ctx.body(entityClass);
            T saved = service.save(entity);
            res.status(201).json(saved);
        });

        // PUT /basePath/{id}
        router.addRoute("PUT", basePath + "/{id}", (ctx, res) -> {
            T entity = ctx.body(entityClass);
            T saved = service.save(entity);
            res.json(saved);
        });

        // DELETE /basePath/{id}
        router.addRoute("DELETE", basePath + "/{id}", (ctx, res) -> {
            ID id = idParser.parse(ctx.pathVariable("id"));
            service.deleteById(id);
            res.status(204).empty();
        });
    }

    @FunctionalInterface
    public interface IdParser<ID> {
        ID parse(String raw);
    }
}
