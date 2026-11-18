package com.doodok.domain.auth;

import com.doodok.domain.user.User;
import com.doodok.domain.user.UserErrorCode;
import com.doodok.domain.user.UserRepository;
import com.doodok.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String EMAIL_UNIQUE_INDEX = "uk_users_email";
    private static final String NICKNAME_UNIQUE_INDEX = "uk_users_nickname";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 요청 본문의 이메일이 아니라, 인증을 마친 이메일을 쓴다.
        String email = emailVerificationService.getVerifiedEmail(request.signupToken());

        // 1차: 대부분의 중복은 여기서 친절한 에러로 걸러진다.
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.create(email, passwordEncoder.encode(request.password()), request.nickname());

        // 2차: 위 검사와 저장 사이에 같은 값으로 동시에 가입하면 DB 유니크 인덱스가 막는다.
        // flush로 INSERT를 바로 실행해야 이 메서드 안에서 예외를 잡아 409로 바꿀 수 있다.
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateException(e);
        }

        // 가입에 성공했을 때만 토큰을 지운다. (닉네임 중복 등으로 실패하면 같은 토큰으로 다시 시도할 수 있다)
        emailVerificationService.consumeSignupToken(request.signupToken());
        return SignupResponse.from(user);
    }

    private RuntimeException toDuplicateException(DataIntegrityViolationException e) {
        String constraintName = findConstraintName(e);
        if (EMAIL_UNIQUE_INDEX.equalsIgnoreCase(constraintName)) {
            return new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (NICKNAME_UNIQUE_INDEX.equalsIgnoreCase(constraintName)) {
            return new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        return e; // 예상하지 못한 제약 위반은 그대로 올려 500으로 처리하고 로그를 남긴다.
    }

    private String findConstraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
