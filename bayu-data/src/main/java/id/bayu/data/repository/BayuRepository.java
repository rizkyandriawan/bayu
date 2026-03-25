package id.bayu.data.repository;

import java.util.List;
import java.util.Optional;

public interface BayuRepository<T, ID> {

    Optional<T> findById(ID id);

    List<T> findAll();

    T save(T entity);

    void deleteById(ID id);

    long count();

    boolean existsById(ID id);
}
