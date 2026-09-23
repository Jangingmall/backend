package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangingmall.backend.member.application.MemberReadRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberActivityRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.SellerApplication;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.image.domain.ImageUpload;
import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductImage;
import com.jangingmall.backend.product.domain.ProductStatus;
import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.ReturnReason;
import com.jangingmall.backend.payment.domain.ReturnType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

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
        Pageable pageable = PageRequest.of(0, 20);

        assertThat(reads.applications(pageable, "ALL").getContent()).isEmpty();
        assertThat(reads.wishes(999L, pageable).getContent()).isEmpty();
        assertThat(reads.orders(999L, pageable, "ALL", null, null, null).getContent()).isEmpty();
        assertThat(reads.order(999L, 999L)).isEmpty();
        assertThat(reads.reviews(999L, pageable, false).getContent()).isEmpty();
        assertThat(reads.reviews(999L, pageable, true).getContent()).isEmpty();
        assertThat(reads.recentViews(999L, pageable).getContent()).isEmpty();
        assertThat(reads.artisans(pageable, null, null, null, "POPULAR").getContent()).isEmpty();
        assertThat(reads.artisans(pageable, null, null, null, "MOST_PRODUCTS").getContent()).isEmpty();
        assertThat(reads.artisans(pageable, null, null, null, "RECENTLY_JOINED").getContent()).isEmpty();
        assertThat(reads.artisan(999L)).isEmpty();
        assertThat(reads.subscriptions(999L, pageable).getContent()).isEmpty();
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

        Pageable page20 = PageRequest.of(0, 20);
        assertThat(reads.wishes(customer.getId(), page20).getContent()).hasSize(1);
        assertThat(reads.recentViews(customer.getId(), page20).getContent()).hasSize(1);
        assertThat(reads.subscriptions(customer.getId(), page20).getContent()).hasSize(1);
        assertThat(reads.artisans(page20, null, null, null, "POPULAR").getContent()).hasSize(1);
        assertThat(reads.artisan(artisan.getId())).isPresent();
        assertThat(reads.productVisible(product.getId())).isTrue();
    }

    @Test
    void orderListFiltersInvisibleStatesBeforePagingAndSupportsMultipleStatuses() {
        Member artisan = activeMember("order-artisan@example.com");
        Member customer = activeMember("order-customer@example.com");
        Category category = Category.of("주문 카테고리");
        entityManager.persist(category);
        Product product = Product.create(artisan.getId(), category, null, "주문 상품", "설명", 10_000, 10, "legacy.webp");
        entityManager.persist(product);
        entityManager.flush();
        ImageUpload image = new ImageUpload("01JORDERIMAGE000000000000001", artisan.getId(), ImagePurpose.PRODUCT,
            1280, 1280, """
                {"320w":{"objectKey":"images/product/320w.webp"},"640w":{"objectKey":"images/product/640w.webp"},"1280w":{"objectKey":"images/product/1280w.webp"}}
                """, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(image, "consumed", true);
        entityManager.persist(image);
        entityManager.persist(new ProductImage(product.getId(), image.getId(), 0, "주문 상품"));

        PurchaseOrder created = order("ORD-LIST-001", customer.getId(), product.getId());
        PurchaseOrder paymentFailed = order("ORD-LIST-002", customer.getId(), product.getId());
        paymentFailed.markPaymentFailed();
        PurchaseOrder orphanReturn = order("ORD-LIST-003", customer.getId(), product.getId());
        orphanReturn.markPaid();
        orphanReturn.requestReturn();
        PurchaseOrder returned = order("ORD-LIST-004", customer.getId(), product.getId());
        returned.markPaid();
        returned.requestReturn();
        PurchaseOrder canceled = order("ORD-LIST-005", customer.getId(), product.getId());
        canceled.cancelBeforePayment();
        entityManager.persist(created);
        entityManager.persist(paymentFailed);
        entityManager.persist(orphanReturn);
        entityManager.persist(returned);
        entityManager.persist(canceled);
        entityManager.flush();
        entityManager.persist(new OrderReturn(returned.getId(), ReturnType.RETURN, ReturnReason.CHANGE_OF_MIND,
            null, 1L, "[]", "[]"));
        entityManager.flush();
        entityManager.clear();

        Pageable page = PageRequest.of(0, 20);
        var all = reads.orders(customer.getId(), page, "ALL", null, null, null);
        var filtered = reads.orders(customer.getId(), page, "CANCELED,RETURN_REQUESTED", null, null, null);

        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getContent()).extracting(value -> value.get("status"))
            .containsExactlyInAnyOrder("CREATED", "CANCELED", "RETURN_REQUESTED");
        assertThat(filtered.getTotalElements()).isEqualTo(2);
        assertThat(filtered.getContent()).extracting(value -> value.get("status"))
            .containsExactlyInAnyOrder("CANCELED", "RETURN_REQUESTED");
        Map<String, Object> detail = reads.order(customer.getId(), created.getId()).orElseThrow();
        assertThat(detail).containsEntry("paymentMethod", "CARD").containsEntry("shippingAmount", 0L);
        Map<String, Object> item = (Map<String, Object>) ((java.util.List<?>) detail.get("items")).getFirst();
        assertThat(item).containsKeys("artisan", "options", "reviewId", "thumbnail");
        Map<String, Object> thumbnail = (Map<String, Object>) item.get("thumbnail");
        assertThat(thumbnail).containsEntry("imageId", image.getId());
        assertThat((java.util.List<?>) thumbnail.get("variants")).hasSize(3);
    }

    private PurchaseOrder order(String orderNumber, Long memberId, Long productId) {
        return new PurchaseOrder(orderNumber, memberId,
            new PurchaseOrder.ShippingAddress(1L, "홍길동", "01012345678", "03187", "서울", "101호"),
            null, null, java.util.List.of(new PurchaseOrder.OrderLine(productId, "주문 상품", 10_000L, 1, null,
                "{\"selectedOptions\":[],\"textInputs\":[]}")));
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
