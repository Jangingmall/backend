package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberAccountService {
    private final MemberAccess access;
    private final PasswordEncoder passwords;
    private final RefreshTokenStore refreshTokens;

    @Transactional
    public MemberProfile update(Long memberId, Optional<String> name, Optional<String> nickname, Optional<String> phone) {
        var member = access.lock(memberId);
        member.updateProfile(name, nickname, phone);
        return MemberProfile.from(member);
    }

    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        var member = access.lock(memberId);
        if (!passwords.matches(currentPassword, member.getPasswordHash())) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        member.changePassword(passwords.encode(newPassword));
        refreshTokens.delete(memberId);
    }

    @Transactional
    public void withdraw(Long memberId, String reason) {
        access.lock(memberId).withdraw(reason);
        refreshTokens.delete(memberId);
    }
}
