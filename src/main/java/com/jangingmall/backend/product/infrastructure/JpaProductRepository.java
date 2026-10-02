package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.application.ProductCommand;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductSort;
import com.jangingmall.backend.product.domain.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaProductRepositoryJpa extends JpaRepository<Product, Long> {
    Page<Product> findByArtisanId(Long artisanId, Pageable pageable);
}

@Repository
class JpaProductRepository implements ProductRepository {
    private static final String SALES_COUNT_EXPR = "(SELECT coalesce(sum(oi.quantity), 0L) FROM OrderItem oi WHERE oi.productId = p.id"
        + " AND oi.order.status IN (com.jangingmall.backend.payment.domain.OrderStatus.PAID,"
        + " com.jangingmall.backend.payment.domain.OrderStatus.IN_DELIVERY,"
        + " com.jangingmall.backend.payment.domain.OrderStatus.DELIVERED,"
        + " com.jangingmall.backend.payment.domain.OrderStatus.PURCHASE_CONFIRMED))";
    private static final String WISHLIST_COUNT_EXPR = "(SELECT count(w) FROM Wishlist w WHERE w.productId = p.id)";
    private static final String REVIEW_COUNT_EXPR = "(SELECT count(r) FROM ProductReview r WHERE r.productId = p.id)";


    private static final List<ProductStatus> ON_SALE_STATUSES = List.of(ProductStatus.ON_SALE);
    private static final List<ProductStatus> INCLUDE_SOLD_OUT = List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);

    @PersistenceContext
    private EntityManager entityManager;

    private final JpaProductRepositoryJpa jpa;

    JpaProductRepository(JpaProductRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Product save(Product product) {
        return jpa.save(product);
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return jpa.findById(productId);
    }

    @Override
    public Page<Product> findByArtisanId(Long artisanId, Pageable pageable) {
        return jpa.findByArtisanId(artisanId, pageable);
    }

    @Override
    public Page<Product> findOnSale(ProductCommand.Search search, Pageable pageable) {
        List<ProductStatus> statuses = Boolean.TRUE.equals(search.excludeSoldOut()) ? ON_SALE_STATUSES : INCLUDE_SOLD_OUT;
        StringBuilder where = buildWhere(search, statuses);
        String orderClause = resolveOrder(search.sort(), pageable);

        TypedQuery<Product> query = entityManager.createQuery(
            "SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.subcategory"
                + where + orderClause, Product.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT count(p) FROM Product p" + where, Long.class);

        applyParameters(query, search, statuses);
        applyParameters(countQuery, search, statuses);

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
    }

    @Override
    public List<Long> findAllOnSaleIds() {
        return entityManager.createQuery(
            "SELECT p.id FROM Product p WHERE p.status IN :statuses", Long.class)
            .setParameter("statuses", ON_SALE_STATUSES)
            .getResultList();
    }

    @Override
    public void delete(Product product) {
        jpa.delete(product);
    }

    private StringBuilder buildWhere(ProductCommand.Search search, List<ProductStatus> statuses) {
        StringBuilder where = new StringBuilder(" WHERE p.status IN :statuses");
        if (search.keyword() != null && !search.keyword().isBlank()) {
            where.append(" AND p.title LIKE :keyword");
        }
        if (search.categoryId() != null) {
            where.append(" AND p.category.id = :categoryId");
        }
        if (search.subcategoryId() != null) {
            where.append(" AND p.subcategory.id = :subcategoryId");
        }
        if (search.giftTheme() != null && !search.giftTheme().isBlank()) {
            where.append(" AND :giftTheme MEMBER OF p.giftThemes");
        }
        if (search.minPrice() != null) {
            where.append(" AND p.price >= :minPrice");
        }
        if (search.maxPrice() != null) {
            where.append(" AND p.price <= :maxPrice");
        }
        if (search.artisanId() != null) {
            where.append(" AND p.artisanId = :artisanId");
        }
        return where;
    }

    private <T> void applyParameters(TypedQuery<T> query, ProductCommand.Search search, List<ProductStatus> statuses) {
        query.setParameter("statuses", statuses);
        if (search.keyword() != null && !search.keyword().isBlank()) {
            query.setParameter("keyword", "%" + search.keyword() + "%");
        }
        if (search.categoryId() != null) {
            query.setParameter("categoryId", search.categoryId());
        }
        if (search.subcategoryId() != null) {
            query.setParameter("subcategoryId", search.subcategoryId());
        }
        if (search.giftTheme() != null && !search.giftTheme().isBlank()) {
            query.setParameter("giftTheme", search.giftTheme());
        }
        if (search.minPrice() != null) {
            query.setParameter("minPrice", search.minPrice());
        }
        if (search.maxPrice() != null) {
            query.setParameter("maxPrice", search.maxPrice());
        }
        if (search.artisanId() != null) {
            query.setParameter("artisanId", search.artisanId());
        }
    }

    private String resolveOrder(String sort, Pageable pageable) {
        if (sort != null) {
            return switch (ProductSort.from(sort)) {
                case SALES_COUNT -> " ORDER BY " + SALES_COUNT_EXPR + " DESC, p.id DESC";
                case WISHLIST_COUNT -> " ORDER BY " + WISHLIST_COUNT_EXPR + " DESC, p.id DESC";
                case POPULAR -> " ORDER BY (p.popularityBoost + 3 * " + SALES_COUNT_EXPR + " + 2 * " + WISHLIST_COUNT_EXPR
                    + " + " + REVIEW_COUNT_EXPR + ") DESC, p.id DESC";
                case PRICE_ASC -> " ORDER BY p.price ASC, p.id DESC";
                case PRICE_DESC -> " ORDER BY p.price DESC, p.id DESC";
                case NEWEST -> " ORDER BY p.createdAt DESC, p.id DESC";
            };
        }
        if (pageable.getSort().isSorted()) {
            List<String> orders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                String direction = order.isAscending() ? "ASC" : "DESC";
                orders.add("p." + order.getProperty() + " " + direction);
            }
            return " ORDER BY " + String.join(", ", orders);
        }
        return " ORDER BY p.createdAt DESC, p.id DESC";
    }
}
