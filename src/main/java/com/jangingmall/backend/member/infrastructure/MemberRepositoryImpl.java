package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepositoryImpl implements MemberRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean existsByEmail(String email) {
        Long count = entityManager.createQuery(
                "select count(member) from Member member where member.email = :email",
                Long.class
            )
            .setParameter("email", email)
            .getSingleResult();
        return count > 0;
    }

    @Override
    public Member save(Member member) {
        if (member.getId() == null) {
            entityManager.persist(member);
            return member;
        }
        return entityManager.merge(member);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return entityManager.createQuery(
                "select member from Member member where member.email = :email and member.deletedAt is null",
                Member.class
            )
            .setParameter("email", email)
            .getResultStream()
            .findFirst();
    }

    @Override
    public Optional<Member> findById(Long memberId) {
        return Optional.ofNullable(entityManager.find(Member.class, memberId))
            .filter(member -> member.getDeletedAt() == null);
    }
}
