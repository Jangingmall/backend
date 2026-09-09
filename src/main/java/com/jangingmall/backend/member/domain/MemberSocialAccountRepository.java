package com.jangingmall.backend.member.domain;

import java.util.Optional;

public interface MemberSocialAccountRepository {
    Optional<MemberSocialAccount> findByRegistrationIdAndProviderUserId(String registrationId,String providerUserId);
    MemberSocialAccount save(MemberSocialAccount account);
}
