package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.doodok.domain.user.User;
import com.doodok.domain.user.UserErrorCode;
import com.doodok.domain.user.UserRepository;
import com.doodok.global.exception.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * DB·Redis 없이 회원가입 로직만 검증한다. 의존 객체는 모두 가짜 객체(Mock)로 바꾼다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TOKEN = "signup-token";
    private static final String EMAIL = "reader@doodok.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    private final SignupRequest request = new SignupRequest(TOKEN, "Passw0rd!", "책읽는곰");

    @BeforeEach
    void setUp() {
        given(emailVerificationService.getVerifiedEmail(TOKEN)).willReturn(EMAIL);
    }

    @Test
    @DisplayName("signupToken의 인증된 이메일로 가입하고, 성공하면 토큰을 소모한다")
    void signupSuccess() {
        given(passwordEncoder.encode("Passw0rd!")).willReturn("{bcrypt}encoded");

        SignupResponse response = authService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getPasswordHash()).isEqualTo("{bcrypt}encoded");
        assertThat(saved.getNickname()).isEqualTo("책읽는곰");
        assertThat(response.email()).isEqualTo(EMAIL);
        verify(emailVerificationService).consumeSignupToken(TOKEN);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 DUPLICATE_EMAIL")
    void duplicateEmail() {
        given(userRepository.existsByEmailAndDeletedAtIsNull(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("닉네임이 중복이면 DUPLICATE_NICKNAME이고, 토큰은 남겨 두어 다시 시도할 수 있다")
    void duplicateNicknameKeepsToken() {
        given(userRepository.existsByNicknameAndDeletedAtIsNull("책읽는곰")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
        verify(userRepository, never()).saveAndFlush(any());
        verify(emailVerificationService, never()).consumeSignupToken(any());
    }

    @Test
    @DisplayName("사전 검사를 통과했어도 저장할 때 유니크 인덱스에 걸리면 어떤 값이 중복인지 구분해 409로 바꾼다")
    void uniqueIndexViolationMappedToErrorCode() {
        given(passwordEncoder.encode(any())).willReturn("{bcrypt}encoded");
        given(userRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate key", null, "uk_users_nickname")));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("다른 제약 위반은 숨기지 않고 그대로 던진다")
    void unknownConstraintViolationRethrown() {
        given(passwordEncoder.encode(any())).willReturn("{bcrypt}encoded");
        DataIntegrityViolationException unknown = new DataIntegrityViolationException(
                "other", new ConstraintViolationException("check", null, "ck_users_role"));
        given(userRepository.saveAndFlush(any())).willThrow(unknown);

        assertThatThrownBy(() -> authService.signup(request)).isSameAs(unknown);
    }
}
