package com.randevu.backend.controller;

import com.randevu.backend.entity.User;
import com.randevu.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Tüm kullanıcıları getiren API
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // Yeni kullanıcı kaydı oluşturan API
    @PostMapping("/register")
    public User registerUser(@RequestBody User user) {
        return userService.registerUser(user);
    }
}