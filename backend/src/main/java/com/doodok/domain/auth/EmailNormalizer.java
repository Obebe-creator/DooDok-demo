package com.doodok.domain.auth;

import java.util.Locale;

/**
 * 이메일 정규화: 앞뒤 공백 제거 + 소문자 변환.
 * 요청 DTO 생성자에서 호출해, 검증(@Email, @Size)이 정규화된 값(= 실제로 저장·비교할 값)에 적용되게 한다.
 */
final class EmailNormalizer {

    private EmailNormalizer() {
    }

    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
