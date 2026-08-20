package com.randevu.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

// Kayit formundan gelen veriyi tasir. Bilerek "id" ve "role" alani yoktur —
// istemci kendine ID veya rol atayamasin diye (bkz. UserService.registerUser).
@Getter
@Setter
public class RegisterRequest {
    private String name;
    private String surName;
    private String email;
    private String password;
    private String phone;
}
