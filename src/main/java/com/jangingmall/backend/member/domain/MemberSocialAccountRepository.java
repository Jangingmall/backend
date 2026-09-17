package com.jangingmall.backend.member.domain;

import java.util.Optional;

public interface MemberSocialAccountRepository {
    Optional<MemberSocialAccount> findByRegistrationIdAndProviderUserId(String registrationId,String providerUserId);
    Optional<MemberSocialAccount> findFirstByMemberIdOrderByIdAsc(Long memberId);
    MemberSocialAccount save(MemberSocialAccount account);
}
