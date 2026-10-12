package com.shareverance.repository;

import com.shareverance.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    boolean existsById(Long id);
    boolean existsByEmail(String email);
}

