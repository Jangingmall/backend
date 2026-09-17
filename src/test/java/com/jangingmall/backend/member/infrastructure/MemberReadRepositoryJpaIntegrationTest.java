package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangingmall.backend.member.application.MemberReadRepository;
import com.jangingmall.backend.member.application.PageRequest;
import org.springframework.data.domain.Pageable;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberActivityRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.SellerApplication;
import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MemberReadRepositoryJpaIntegrationTest {

    @Autowired
    private MemberReadRepository reads;

    @Autowired
    private MemberActivityRepository activities;

    @Autowired
    private EntityManager entityManager;

    @Test
    void jpqlReadProjectionsExecuteWithoutNativeSql() {
        PageRequest cursorPage = PageRequest.from(null, 20);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        assertThat(reads.applications(cursorPage, "ALL").items()).isEmpty();
        assertThat(reads.wishes(999L, pageable).getContent()).isEmpty();
        assertThat(reads.orders(999L, pageable, "ALL").getContent()).isEmpty();
        assertThat(reads.order(999L, 999L)).isEmpty();
        assertThat(reads.reviews(999L, pageable, false).getContent()).isEmpty();
        assertThat(reads.reviews(999L, pageable, true).getContent()).isEmpty();
        assertThat(reads.recentViews(999L, null, 20).items()).isEmpty();
        assertThat(reads.artisans(null, 20, null, null, null, "POPULAR").items()).isEmpty();
        assertThat(reads.artisans(null, 20, null, null, null, "MOST_PRODUCTS").items()).isEmpty();
        assertThat(reads.artisans(null, 20, null, null, null, "RECENTLY_JOINED").items()).isEmpty();
        assertThat(reads.artisan(999L)).isEmpty();
        assertThat(reads.subscriptions(999L, cursorPage).items()).isEmpty();
        assertThat(reads.productVisible(999L)).isFalse();
        reads.categoryExists("없는 카테고리");
    }

    @Test
    void activityWritesUseJpaRepositories() {
        LocalDateTime first = LocalDateTime.now().minusMinutes(1);
        LocalDateTime latest = LocalDateTime.now();

        activities.recordView(999L, 777L, first);
        activities.recordView(999L, 777L, latest);
        activities.wish(999L, 777L);
        activities.wish(999L, 777L);
        activities.subscribe(999L, 555L);
        activities.subscribe(999L, 555L);
        activities.notifications(999L, false);

        assertThat(activities.isWished(999L, 777L)).isTrue();

        activities.unwish(999L, 777L);
        activities.unsubscribe(999L, 555L);
        activities.clearViews(999L);
        assertThat(activities.isWished(999L, 777L)).isFalse();
    }

    @Test
    void memberReadProjectionMapsJpaEntities() {
        Member artisan = activeMember("artisan-jpa@example.com");
        artisan.approveArtisan();
        entityManager.flush();
        SellerApplication application = new SellerApplication(
            artisan.getId(), "JPA 공방", "소개", "https://example.com/license.jpg");
        entityManager.persist(application);
        entityManager.persist(new ArtisanProfile(application));

        Member customer = activeMember("customer-jpa@example.com");
        Category category = Category.of("테스트 카테고리");
        entityManager.persist(category);
        Product product = Product.create(
            artisan.getId(), category, null, "테스트 상품", "설명", 10000, 3, "thumbnail.webp");
        entityManager.persist(product);
        entityManager.flush();
        product.changeStatus(ProductStatus.ON_SALE, artisan.getId());

        activities.wish(customer.getId(), product.getId());
        activities.recordView(customer.getId(), product.getId(), LocalDateTime.now());
        activities.subscribe(customer.getId(), artisan.getId());
        entityManager.flush();
        entityManager.clear();

        Pageable wishPage = org.springframework.data.domain.PageRequest.of(0, 20);
        PageRequest subscriptionPage = PageRequest.from(null, 20);
        assertThat(reads.wishes(customer.getId(), wishPage).getContent()).hasSize(1);
        assertThat(reads.recentViews(customer.getId(), null, 20).items()).hasSize(1);
        assertThat(reads.subscriptions(customer.getId(), subscriptionPage).items()).hasSize(1);
        assertThat(reads.artisans(null, 20, null, null, null, "POPULAR").items()).hasSize(1);
        assertThat(reads.artisan(artisan.getId())).isPresent();
        assertThat(reads.productVisible(product.getId())).isTrue();
    }

    private Member activeMember(String email) {
        Member member = Member.register(email, "hash", "테스트", "01012345678", MemberRole.USER,
            true, true, true, false);
        entityManager.persist(member);
        member.activate();
        entityManager.flush();
        return member;
    }
}
