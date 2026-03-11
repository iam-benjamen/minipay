package com.minipay.auth.service;

import com.minipay.auth.dto.AuthDtos;
import com.minipay.auth.entity.User;
import com.minipay.auth.repository.UserRepository;
import com.minipay.common.exception.ConflictException;
import com.minipay.common.exception.ResourceNotFoundException;
import com.minipay.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new ConflictException("Phone number already registered");
        }

        User user = User.builder()
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() != null ? request.role() : User.Role.CUSTOMER)
                .status(User.Status.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: {}", user.getId());

        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new UnauthorizedException("Account is disabled, contact the Administrator");
        }

        log.info("User logged in: {}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse refresh(String refreshToken) {
        try {
            var claims = jwtService.validateToken(refreshToken);
            String userId = claims.getSubject();

            User user = userRepository.findById(java.util.UUID.fromString(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));

            if (!jwtService.isRefreshTokenValid(user.getId(), refreshToken)) {
                throw new UnauthorizedException("Invalid credentials");
            }

            jwtService.revokeRefreshToken(user.getId());
            return buildAuthResponse(user);

        } catch (io.jsonwebtoken.JwtException e) {
            throw new UnauthorizedException("Invalid credentials");
        }
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = authorizationHeader.substring(7);

        try {
            var claims = jwtService.validateToken(token);
            UUID userId = UUID.fromString(claims.getSubject());
            jwtService.revokeRefreshToken(userId);
            log.info("User logged out: {}", userId);
        } catch (io.jsonwebtoken.JwtException e) {
            throw new UnauthorizedException("Invalid credentials");
        }
    }

    private AuthDtos.AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthDtos.AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                900,
                AuthDtos.UserResponse.from(user)
        );
    }
}