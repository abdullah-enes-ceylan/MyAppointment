package com.randevu.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    // http://localhost:8080/api/test/ping adresine istek geldiğinde çalışır
    @GetMapping("/ping")
    public Map<String, String> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("mesaj", "Randevum Backend Sunucusu Başarılı Bir Şekilde Çalışıyor!");
        response.put("durum", "BASARILI");
        return response;
    }
}