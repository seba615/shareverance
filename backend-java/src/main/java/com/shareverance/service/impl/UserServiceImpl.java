package com.shareverance.service.impl;

import com.shareverance.dto.UserRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.User;
import com.shareverance.repository.UserRepository;
import com.shareverance.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User createUser(UserRequest request) {
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new BusinessRuleException("El nombre del usuario no puede estar vacío");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessRuleException("El correo electrónico es obligatorio");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("La contraseña es obligatoria");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Ya existe un usuario con el correo: " + request.email());
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                null,
                request.fullName(),
                request.email(),
                passwordHash,
                LocalDateTime.now(),
                true
        );

        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User updateUser(Long id, UserRequest request) {
        User user = getUserById(id);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName());
        }
        if (request.email() != null && !request.email().isBlank() && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new BusinessRuleException("Ya existe un usuario registrado con el correo: " + request.email());
            }
            user.setEmail(request.email());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return userRepository.save(user);
    }

    @Override
    public User deactivateUser(Long id) {
        User user = getUserById(id);
        user.setIsActive(false);
        return userRepository.save(user);
    }
}
