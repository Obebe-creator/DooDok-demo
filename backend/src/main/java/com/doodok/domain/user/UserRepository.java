package com.doodok.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 탈퇴하지 않은 회원 중 같은 이메일이 있는지 */
    boolean existsByEmailAndDeletedAtIsNull(String email);

    /** 탈퇴하지 않은 회원 중 같은 닉네임이 있는지 */
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
