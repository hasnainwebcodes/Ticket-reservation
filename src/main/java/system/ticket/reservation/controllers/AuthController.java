package system.ticket.reservation.controllers;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import system.ticket.reservation.dto.ApiResponse;
import system.ticket.reservation.dto.AuthResponse;
import system.ticket.reservation.dto.LoginRequest;
import system.ticket.reservation.dto.RegisterRequest;
import system.ticket.reservation.entity.UserPrincipal;
import system.ticket.reservation.services.UserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        return ResponseEntity.ok(ApiResponse.success(
                userService.getCurrentUserProfile(principal.getId()), "Authenticated"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(userService.register(request),
                        "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse) {
        AuthResponse auth = userService.login(request, authenticationManager);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                buildJwtCookie(auth.getToken()).toString());
        return ResponseEntity.ok(ApiResponse.success(auth, "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletResponse httpResponse) {
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("jwt", "")
                        .httpOnly(true).path("/").maxAge(0).build().toString());
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private ResponseCookie buildJwtCookie(String token) {
        return ResponseCookie.from("jwt", token)
                .httpOnly(true).secure(false).path("/")
                .maxAge(86400).sameSite("Lax").build();
    }
}