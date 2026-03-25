package id.bayu.web.crud;

import id.bayu.data.page.Page;
import id.bayu.data.page.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base CRUD service interface. Extend this for each entity.
 * @param <T> Entity type
 * @param <ID> Primary key type (Long, UUID, etc.)
 */
public interface BaseCrudService<T, ID> {

    List<T> findAll();

    Page<T> findAll(PageRequest pageRequest, Map<String, String> filters);

    Optional<T> findById(ID id);

    T save(T entity);

    void deleteById(ID id);

    long count();
}
