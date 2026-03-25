package id.bayu.example.repository;

import id.bayu.data.annotation.Repository;
import id.bayu.data.repository.BayuRepository;
import id.bayu.example.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BayuRepository<User, Long> {

    List<User> findByName(String name);

    Optional<User> findByEmail(String email);

    List<User> findByAgeGreaterThan(Integer age);

    long countByName(String name);
}
