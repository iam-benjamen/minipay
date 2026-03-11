package com.minipay.auth.service;


import com.minipay.auth.dto.AuthDtos;
import com.minipay.auth.entity.User;
import com.minipay.auth.repository.UserRepository;
import com.minipay.common.exception.ConflictException;
import com.minipay.common.exception.ResourceNotFoundException;
import com.minipay.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("customer@minipay.com")
                .phoneNumber("+2348012345678")
                .passwordHash("hashed_password")
                .role(User.Role.CUSTOMER)
                .status(User.Status.ACTIVE)
                .build();
    }

    @Nested
    class Register {

        private AuthDtos.RegisterRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new AuthDtos.RegisterRequest(
                    "customer@minipay.com",
                    "+2348012345678",
                    "password123",
                    User.Role.CUSTOMER
            );
        }

        @Test
        void happyPath_savesUserAndReturnsTokens() {
            when(userRepository.existsByEmail(validRequest.email())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(validRequest.phoneNumber())).thenReturn(false);
            when(passwordEncoder.encode(validRequest.password())).thenReturn("hashed_password");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(any())).thenReturn("access_token");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh_token");

            AuthDtos.AuthResponse response = authService.register(validRequest);

            assertThat(response.accessToken()).isEqualTo("access_token");
            assertThat(response.refreshToken()).isEqualTo("refresh_token");
            assertThat(response.tokenType()).isEqualTo("Bearer");

            verify(userRepository).save(any(User.class));
            verify(jwtService).generateAccessToken(any());
            verify(jwtService).generateRefreshToken(any());
        }

        @Test
        void nullRole_defaultsToCustomer() {
            var requestWithNullRole = new AuthDtos.RegisterRequest(
                    "customer@minipay.com",
                    "+2348012345678",
                    "password123",
                    null
            );

            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(any())).thenReturn("access_token");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh_token");

            authService.register(requestWithNullRole);

            verify(userRepository).save(argThat(user -> user.getRole() == User.Role.CUSTOMER));
        }

        @Test
        void duplicateEmail_throwsConflictException() {
            when(userRepository.existsByEmail(validRequest.email())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        void duplicatePhone_throwsConflictException() {
            when(userRepository.existsByEmail(validRequest.email())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(validRequest.phoneNumber())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Phone number already registered");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class Login {

        private AuthDtos.LoginRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new AuthDtos.LoginRequest("customer@minipay.com", "password123");
        }

        @Test
        void happyPath_returnsTokens() {
            when(userRepository.findByEmail(validRequest.email())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(validRequest.password(), testUser.getPasswordHash())).thenReturn(true);
            when(jwtService.generateAccessToken(testUser)).thenReturn("access_token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh_token");

            AuthDtos.AuthResponse response = authService.login(validRequest);

            assertThat(response.accessToken()).isEqualTo("access_token");
            assertThat(response.refreshToken()).isEqualTo("refresh_token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
        }

        @Test
        void userNotFound_throwsResourceNotFoundException() {
            when(userRepository.findByEmail(validRequest.email())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(validRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void wrongPassword_throwsUnauthorizedException() {
            when(userRepository.findByEmail(validRequest.email())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(validRequest.password(), testUser.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(validRequest))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void inactiveUser_throwsUnauthorizedException() {
            User inactiveUser = User.builder()
                    .id(testUserId)
                    .email("customer@minipay.com")
                    .passwordHash("hashed_password")
                    .role(User.Role.CUSTOMER)
                    .status(User.Status.INACTIVE)
                    .build();

            when(userRepository.findByEmail(validRequest.email())).thenReturn(Optional.of(inactiveUser));
            when(passwordEncoder.matches(validRequest.password(), inactiveUser.getPasswordHash())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(validRequest))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Account is disabled");
        }
    }

    @Nested
    class Refresh {

        private Claims mockClaims;

        @BeforeEach
        void setUp() {
            mockClaims = mock(Claims.class);
        }

        @Test
        void happyPath_revokesOldTokenAndReturnsNewTokens() {
            when(mockClaims.getSubject()).thenReturn(testUserId.toString());
            when(jwtService.validateToken("valid_refresh_token")).thenReturn(mockClaims);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(jwtService.isRefreshTokenValid(testUserId, "valid_refresh_token")).thenReturn(true);
            when(jwtService.generateAccessToken(testUser)).thenReturn("new_access_token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("new_refresh_token");

            AuthDtos.AuthResponse response = authService.refresh("valid_refresh_token");

            assertThat(response.accessToken()).isEqualTo("new_access_token");
            assertThat(response.refreshToken()).isEqualTo("new_refresh_token");
            verify(jwtService).revokeRefreshToken(testUserId);
        }

        @Test
        void invalidJwt_throwsUnauthorizedException() {
            when(jwtService.validateToken("bad_token")).thenThrow(new JwtException("invalid"));

            assertThatThrownBy(() -> authService.refresh("bad_token"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void tokenNotInRedis_throwsUnauthorizedException() {
            when(mockClaims.getSubject()).thenReturn(testUserId.toString());
            when(jwtService.validateToken("valid_refresh_token")).thenReturn(mockClaims);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(jwtService.isRefreshTokenValid(testUserId, "valid_refresh_token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh("valid_refresh_token"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void userNotFound_throwsResourceNotFoundException() {
            when(mockClaims.getSubject()).thenReturn(testUserId.toString());
            when(jwtService.validateToken("valid_refresh_token")).thenReturn(mockClaims);
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("valid_refresh_token"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Logout {

        @Test
        void happyPath_revokesRefreshToken() {
            Claims mockClaims = mock(Claims.class);
            when(mockClaims.getSubject()).thenReturn(testUserId.toString());
            when(jwtService.validateToken("valid_token")).thenReturn(mockClaims);

            authService.logout("Bearer valid_token");

            verify(jwtService).revokeRefreshToken(testUserId);
        }

        @Test
        void nullHeader_throwsUnauthorizedException() {
            assertThatThrownBy(() -> authService.logout(null))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void missingBearerPrefix_throwsUnauthorizedException() {
            assertThatThrownBy(() -> authService.logout("valid_token"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void invalidJwt_throwsUnauthorizedException() {
            when(jwtService.validateToken("bad_token")).thenThrow(new JwtException("invalid"));

            assertThatThrownBy(() -> authService.logout("Bearer bad_token"))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
