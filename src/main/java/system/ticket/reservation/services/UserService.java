package system.ticket.reservation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.ticket.reservation.dto.AuthResponse;
import system.ticket.reservation.dto.LoginRequest;
import system.ticket.reservation.dto.RegisterRequest;
import system.ticket.reservation.entity.User;
import system.ticket.reservation.entity.UserPrincipal;
import system.ticket.reservation.repos.UserRepository;
import system.ticket.reservation.services.JwtService;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ─── UserDetailsService contract ──────────────────────────────
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));
        return new UserPrincipal(user);
    }

    // ─── Register ─────────────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(User.Role.USER)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return buildAuthResponse(user, token);
    }

    // ─── Login ────────────────────────────────────────────────────
    public AuthResponse login(LoginRequest request,
                              AuthenticationManager authManager) {
        // This throws BadCredentialsException if wrong — handled by GlobalExceptionHandler
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, token);
    }

    // ─── Get Profile ──────────────────────────────────────────────
    public AuthResponse getCurrentUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return buildAuthResponse(user, null); // no token needed for profile fetch
    }

    // ─── Private Helper ───────────────────────────────────────────
    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}