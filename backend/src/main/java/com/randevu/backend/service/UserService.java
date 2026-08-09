package com.randevu.backend.service;

import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // 1. Encoder'ı tanımla

    // 2. Constructor içine passwordEncoder'ı ekle
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User registerUser(User user) {
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }

        // 3. Kullanıcının girdiği saf şifreyi al, BCrypt ile hashle ve tekrar set et!
        String hashedPwd = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPwd);

        return userRepository.save(user);
    }
}