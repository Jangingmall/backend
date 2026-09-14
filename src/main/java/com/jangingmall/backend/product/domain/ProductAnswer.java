package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_answer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long id;

    @Column(name = "question_id", nullable = false, unique = true)
    private Long questionId;

    @Column(name = "artisan_id", nullable = false)
    private Long artisanId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    public static ProductAnswer reply(Long questionId, Long artisanId, String content) {
        ProductAnswer a = new ProductAnswer();
        a.questionId = questionId;
        a.artisanId = artisanId;
        a.content = content;
        return a;
    }

    @PrePersist
    void onCreate() {
        answeredAt = LocalDateTime.now();
    }
}
