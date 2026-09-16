package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.*;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberActivityRepositoryImpl implements MemberActivityRepository {
    private final MemberSettingsJpaRepository settings;
    private final RecentViewJpaRepository recentViews;
    private final WishlistJpaRepository wishlists;
    private final ArtisanSubscriptionJpaRepository subscriptions;

    @Override
    public Optional<MemberSettings> settings(Long memberId) {
        return settings.findById(memberId);
    }

    @Override
    public MemberSettings saveSettings(MemberSettings settings) {
        return this.settings.save(settings);
    }

    @Override
    public void recordView(Long memberId, Long productId, LocalDateTime viewedAt) {
        RecentView recentView = recentViews.findByMemberIdAndProductId(memberId, productId)
            .orElseGet(() -> RecentView.create(memberId, productId, viewedAt));
        recentView.record(viewedAt);
        recentViews.save(recentView);
    }

    @Override
    public void clearViews(Long memberId) {
        recentViews.deleteByMemberId(memberId);
    }

    @Override
    public void wish(Long memberId, Long productId) {
        if (!wishlists.existsByMemberIdAndProductId(memberId, productId)) {
            wishlists.save(Wishlist.create(memberId, productId));
        }
    }

    @Override
    public void unwish(Long memberId, Long productId) {
        wishlists.deleteByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public boolean isWished(Long memberId, Long productId) {
        return wishlists.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public void subscribe(Long memberId, Long artisanId) {
        if (!subscriptions.existsByMemberIdAndArtisanId(memberId, artisanId)) {
            subscriptions.save(ArtisanSubscription.create(memberId, artisanId));
        }
    }

    @Override
    public void unsubscribe(Long memberId, Long artisanId) {
        subscriptions.deleteByMemberIdAndArtisanId(memberId, artisanId);
    }

    @Override
    public void notifications(Long memberId, boolean enabled) {
        subscriptions.findAllByMemberId(memberId).forEach(subscription -> subscription.changeNotifications(enabled));
    }
}
