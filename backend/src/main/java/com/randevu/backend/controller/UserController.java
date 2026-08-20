package com.randevu.backend.controller;

import com.randevu.backend.dto.request.RegisterRequest;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.EmailAlreadyExistsException;
import com.randevu.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Tum kullanicilari getiren API
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // Yeni kullanici kaydi olusturan API. RegisterRequest DTO sayesinde istemci
    // sadece ad/soyad/email/sifre/telefon gonderebilir — id ve role gonderemez.
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
        try {
            User createdUser = userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (EmailAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
