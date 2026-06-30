package com.banking;

import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import com.banking.enums.UserRole;
import com.banking.enums.UserStatus;
import com.banking.exception.BankingException;
import com.banking.repository.RefreshTokenRepository;
import com.banking.repository.UserRepository;
import com.banking.service.AuditService;
import com.banking.service.AuthService;
import com.banking.service.EmailService;
import com.banking.service.NotificationService;
import com.banking.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("john.doe@example.com");
        registerRequest.setPhoneNumber("9876543210");
        registerRequest.setPassword("Test@1234");
        registerRequest.setDateOfBirth(LocalDate.of(1990, 1, 15));
        registerRequest.setNationalId("123456789012");
        registerRequest.setCity("Mumbai");
        registerRequest.setState("Maharashtra");
        registerRequest.setCountry("India");

        sampleUser = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phoneNumber("9876543210")
                .passwordHash("$2a$12$encodedPassword")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .nationalId("123456789012")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ---- Registration Tests ----

    @Test
    @DisplayName("Should register user successfully")
    void registerUser_Success() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(userRepository.existsByNationalId(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$encodedPassword");
        when(userRepository.save(any())).thenReturn(sampleUser);

        UserResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        verify(userRepository).save(any(User.class));
        verify(emailService).sendWelcomeEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void registerUser_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Email");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when phone already exists")
    void registerUser_DuplicatePhone_ThrowsException() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(registerRequest.getPhoneNumber())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Phone number");
    }

    @Test
    @DisplayName("Should throw exception when national ID already exists")
    void registerUser_DuplicateNationalId_ThrowsException() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(userRepository.existsByNationalId(registerRequest.getNationalId())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("National ID");
    }

    // ---- Login Tests ----

    @Test
    @DisplayName("Should login successfully and return JWT tokens")
    void login_Success() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("john.doe@example.com");
        loginRequest.setPassword("Test@1234");

        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("john.doe@example.com", "Test@1234"));
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(sampleUser));
        when(jwtUtil.generateToken(sampleUser)).thenReturn("mock.access.token");
        when(jwtUtil.generateRefreshToken(sampleUser)).thenReturn("mock.refresh.token");
        when(jwtUtil.getExpirationTime()).thenReturn(86400000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.access.token");
        assertThat(response.getRefreshToken()).isEqualTo("mock.refresh.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        verify(refreshTokenRepository).revokeAllUserTokens(sampleUser.getId());
    }

    @Test
    @DisplayName("Should throw exception for suspended user login")
    void login_SuspendedUser_ThrowsException() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("john.doe@example.com");
        loginRequest.setPassword("Test@1234");

        sampleUser.setStatus(UserStatus.SUSPENDED);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("john.doe@example.com", "Test@1234"));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("suspended");
    }

    // ---- Logout Tests ----

    @Test
    @DisplayName("Should logout and revoke tokens")
    void logout_RevokesTokens() {
        authService.logout(sampleUser);
        verify(refreshTokenRepository).revokeAllUserTokens(sampleUser.getId());
    }
}
