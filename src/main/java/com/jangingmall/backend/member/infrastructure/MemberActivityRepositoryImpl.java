package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MemberActivityRepositoryImpl implements MemberActivityRepository {
    @PersistenceContext private EntityManager entityManager;

    @Override
    public Optional<MemberSettings> settings(Long memberId) {
        return Optional.ofNullable(entityManager.find(MemberSettings.class, memberId));
    }

    @Override
    public MemberSettings saveSettings(MemberSettings settings) {
        return entityManager.merge(settings);
    }

    @Override
    public void recordView(Long memberId, Long productId, LocalDateTime viewedAt) {
        entityManager.createNativeQuery("""
            INSERT INTO recent_view(member_id, product_id, viewed_at) VALUES (:memberId, :productId, :viewedAt)
            ON CONFLICT (member_id, product_id) DO UPDATE
            SET viewed_at = GREATEST(recent_view.viewed_at, EXCLUDED.viewed_at)
            """).setParameter("memberId", memberId).setParameter("productId", productId)
            .setParameter("viewedAt", viewedAt).executeUpdate();
    }

    @Override
    public void clearViews(Long memberId) {
        entityManager.createNativeQuery("DELETE FROM recent_view WHERE member_id = :memberId")
            .setParameter("memberId", memberId).executeUpdate();
    }

    @Override
    public void subscribe(Long memberId, Long artisanId) {
        entityManager.createNativeQuery("""
            INSERT INTO artisan_subscription(member_id, artisan_id, notifications_enabled, created_at)
            VALUES (:memberId, :artisanId, true, CURRENT_TIMESTAMP)
            ON CONFLICT (member_id, artisan_id) DO NOTHING
            """).setParameter("memberId", memberId).setParameter("artisanId", artisanId).executeUpdate();
    }

    @Override
    public void unsubscribe(Long memberId, Long artisanId) {
        entityManager.createNativeQuery("DELETE FROM artisan_subscription WHERE member_id=:memberId AND artisan_id=:artisanId")
            .setParameter("memberId", memberId).setParameter("artisanId", artisanId).executeUpdate();
    }

    @Override
    public void notifications(Long memberId, boolean enabled) {
        entityManager.createNativeQuery("UPDATE artisan_subscription SET notifications_enabled=:enabled WHERE member_id=:memberId")
            .setParameter("memberId", memberId).setParameter("enabled", enabled).executeUpdate();
    }
}
