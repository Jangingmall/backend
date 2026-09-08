package com.jangingmall.backend.member.domain;

import java.util.Optional;

public interface MemberRepository {

    boolean existsByEmail(String email);

    Member save(Member member);

    Optional<Member> findByEmail(String email);

    Optional<Member> findById(Long memberId);

    Optional<Member> findByIdForUpdate(Long memberId);
}
