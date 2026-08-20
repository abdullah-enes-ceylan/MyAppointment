package com.randevu.backend.service;

import com.randevu.backend.dto.request.RegisterRequest;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.EmailAlreadyExistsException;
import com.randevu.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Yeni musteri kaydi olusturur. Rol daima USER'dir ve id daima veritabanindan
    // uretilir — ikisi de RegisterRequest DTO'sunda hic bulunmadigi icin istemci
    // ne rol ne de mevcut bir kaydin ID'sini gonderebilir (mass assignment kapali).
    public User registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .surName(request.getSurName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.USER)
                .build();

        return userRepository.save(user);
    }
}
