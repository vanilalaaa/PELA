package com.pela.identity.adapter.in.web;

import com.pela.identity.application.port.in.AuthTokens;
import com.pela.identity.application.port.in.LoginUseCase;
import com.pela.identity.application.port.in.LogoutUseCase;
import com.pela.identity.application.port.in.PasswordRecoveryUseCase;
import com.pela.identity.application.port.in.RefreshSessionUseCase;
import com.pela.identity.application.port.in.RegisterUseCase;
import com.pela.identity.application.port.in.RegisterUseCase.RegisterCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-01 / Auth.*: đăng ký, đăng nhập, phiên, quên mật khẩu. Chỉ map request ↔ use case. */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final RegisterUseCase register;
    private final LoginUseCase login;
    private final RefreshSessionUseCase refresh;
    private final LogoutUseCase logout;
    private final PasswordRecoveryUseCase recovery;

    public AuthController(RegisterUseCase register, LoginUseCase login, RefreshSessionUseCase refresh,
            LogoutUseCase logout, PasswordRecoveryUseCase recovery) {
        this.register = register;
        this.login = login;
        this.refresh = refresh;
        this.logout = logout;
        this.recovery = recovery;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        var account = register.register(new RegisterCommand(
                request.fullName(), request.email(), request.password(), request.confirmPassword()));
        return new RegisterResponse(account.userId(), account.email(), account.status());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.of(login.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.of(refresh.refresh(request.refreshToken()));
    }

    /** Auth.Logout: mặc định chỉ thiết bị này; {@code allDevices = true} để thoát mọi thiết bị. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) LogoutRequest request) {
        logout.logout(jwt.getTokenValue(), request != null && Boolean.TRUE.equals(request.allDevices()));
    }

    /** Luôn 202 dù email có tồn tại hay không. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        recovery.requestReset(request.email());
    }

    @PostMapping("/password/verify-otp")
    public ResetTokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        var token = recovery.verifyCode(request.email(), request.code());
        return new ResetTokenResponse(token.token(), token.expiresInSeconds());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.resetPassword(request.resetToken(), request.newPassword(), request.confirmPassword());
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 72) String password,
            @NotBlank String confirmPassword) {

        @Override
        public String toString() {
            return "RegisterRequest[***]";
        }
    }

    public record RegisterResponse(UUID userId, String email, String status) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

        @Override
        public String toString() {
            return "LoginRequest[***]";
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[***]";
        }
    }

    public record LogoutRequest(Boolean allDevices) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record VerifyOtpRequest(@NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {

        @Override
        public String toString() {
            return "VerifyOtpRequest[***]";
        }
    }

    public record ResetPasswordRequest(@NotBlank String resetToken, @NotBlank @Size(max = 72) String newPassword,
            @NotBlank String confirmPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {

        static TokenResponse of(AuthTokens tokens) {
            return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(),
                    tokens.tokenType());
        }

        @Override
        public String toString() {
            return "TokenResponse[expiresIn=" + expiresIn + "]";
        }
    }

    public record ResetTokenResponse(String resetToken, long expiresIn) {

        @Override
        public String toString() {
            return "ResetTokenResponse[expiresIn=" + expiresIn + "]";
        }
    }
}
