package com.shareverance.service;

import com.shareverance.dto.UserRequest;
import com.shareverance.model.User;

import java.util.List;

public interface UserService {
    User createUser(UserRequest request);
    User getUserById(Long id);
    User getUserByEmail(String email);
    List<User> getAllUsers();
    User updateUser(Long id, UserRequest request);
    User deactivateUser(Long id);
}

