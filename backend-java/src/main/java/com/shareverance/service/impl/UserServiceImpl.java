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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
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
        String email = request.email() == null ? "" : request.email().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessRuleException("El correo electrónico no es válido");
        }
        if (request.password() == null || request.password().length() < 8
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleException("La contraseña debe tener entre 8 y 72 bytes UTF-8");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Ya existe un usuario con el correo: " + email);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                null,
                request.fullName(),
                email.toLowerCase(java.util.Locale.ROOT),
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
