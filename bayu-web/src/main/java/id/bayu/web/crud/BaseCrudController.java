package id.bayu.web.crud;

import id.bayu.web.annotation.*;

import java.util.Map;
import java.util.UUID;

public abstract class BaseCrudController<T> {

    protected abstract BaseCrudService<T, UUID> getService();

    @GetMapping
    public Map<String, Object> findAll() {
        return ApiResponse.list(getService().findAll());
    }

    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable UUID id) {
        return getService().findById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error(404, "Not found"));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody T entity) {
        return ApiResponse.created(getService().save(entity));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody T entity) {
        return ApiResponse.ok(getService().save(entity));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        getService().deleteById(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
