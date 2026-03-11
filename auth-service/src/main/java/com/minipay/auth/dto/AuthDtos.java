package com.minipay.auth.dto;

import com.minipay.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
            String phoneNumber,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            User.Role role
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user
    ) {}

    public record UserResponse(
            String id,
            String email,
            String phoneNumber,
            User.Role role,
            User.Status status
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId().toString(),
                    user.getEmail(),
                    user.getPhoneNumber(),
                    user.getRole(),
                    user.getStatus()
            );
        }
    }

    public record UserRegisteredEvent(String userId, String email, String role) {}
}