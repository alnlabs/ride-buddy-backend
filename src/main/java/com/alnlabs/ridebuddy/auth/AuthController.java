package com.alnlabs.ridebuddy.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    public Map<String, Object> requestOtp(@RequestBody OtpRequest body) {
        return authService.requestOtp(body.phone());
    }

    @PostMapping("/otp/verify")
    public AuthService.TokenResponse verify(@RequestBody OtpVerifyRequest body) {
        return authService.verifyOtp(body.phone(), body.code(), body.displayName());
    }

    @PostMapping("/refresh")
    public AuthService.TokenResponse refresh(@RequestBody RefreshRequest body) {
        return authService.refresh(body.refreshToken());
    }

    public record OtpRequest(@NotBlank String phone) {}

    public record OtpVerifyRequest(
            @NotBlank String phone,
            @NotBlank String code,
            String displayName
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}
}
