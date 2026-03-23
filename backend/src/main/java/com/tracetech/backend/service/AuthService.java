package com.tracetech.backend.service;

import com.tracetech.backend.config.JwtUtil;
import com.tracetech.backend.dto.AuthResponse;
import com.tracetech.backend.dto.LoginRequest;
import com.tracetech.backend.dto.RegisterRequest;
import com.tracetech.backend.entity.Vendor;
import com.tracetech.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final VendorRepository vendorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {

        // Check if email already exists
        if (vendorRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create new vendor
        Vendor vendor = Vendor.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .canteenName(request.getCanteenName())
                .onboardingDone(false)
                .build();

        vendorRepository.save(vendor);

        // Generate JWT token
        String token = jwtUtil.generateToken(vendor.getEmail());

        return AuthResponse.builder()
                .token(token)
                .email(vendor.getEmail())
                .name(vendor.getName())
                .canteenName(vendor.getCanteenName())
                .onboardingDone(vendor.getOnboardingDone())
                .build();
    }

    public AuthResponse login(LoginRequest request) {

        // Find vendor by email
        Vendor vendor = vendorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Check password
        if (!passwordEncoder.matches(request.getPassword(), vendor.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(vendor.getEmail());

        return AuthResponse.builder()
                .token(token)
                .email(vendor.getEmail())
                .name(vendor.getName())
                .canteenName(vendor.getCanteenName())
                .onboardingDone(vendor.getOnboardingDone())
                .build();
    }
}
