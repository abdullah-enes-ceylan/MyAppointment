package com.randevu.backend.service;

import com.randevu.backend.dto.request.ChangePasswordRequest;
import com.randevu.backend.dto.request.RegisterRequest;
import com.randevu.backend.dto.request.UpdateProfileRequest;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.BusinessRuleException;
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

    // Profil bilgilerini gunceller. Alanlar TEK TEK, acikca kopyalaniyor
    // (BusinessMapper.applyToEntity'deki ayni desen) -- email, password,
    // role ve id'ye hic dokunulmuyor. UpdateProfileRequest'te bu alanlar
    // zaten bulunmadigi icin istemci gondermeye calissa bile etkisiz.
    public User updateProfile(User user, UpdateProfileRequest request) {
        user.setName(request.getName());
        user.setSurName(request.getSurName());
        user.setPhone(request.getPhone());
        return userRepository.save(user);
    }

    // Sifre degistirir. Once MEVCUT sifre dogrulanir (bkz.
    // ChangePasswordRequest'teki "yeniden kimlik dogrulama" gerekcesi).
    //
    // Hata tipi neden BusinessRuleException (409), 401 degil: 401 donersek
    // frontend'deki axios interceptor kullaniciyi ZORLA CIKISA atar (bkz.
    // api/axios.js) -- oysa burada olan sey sadece bir form alanina yanlis
    // sifre yazilmasi. Oturum hala gecerli, kullanici sayfada kalmali ve
    // hatayi gorup tekrar denemeli.
    public User changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Mevcut şifreniz hatalı.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessRuleException("Yeni şifre, mevcut şifrenizle aynı olamaz.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        return userRepository.save(user);
    }
}
