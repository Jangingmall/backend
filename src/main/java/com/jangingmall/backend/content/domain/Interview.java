package com.jangingmall.backend.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "interview")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interview {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String process;

    @Column(nullable = false, length = 255)
    private String materials;

    @Column(nullable = false, length = 100)
    private String technique;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String story;

    public static Interview create(Long productId, String process, String materials, String technique, String story) {
        Interview interview = new Interview();
        interview.productId = productId;
        interview.process = process;
        interview.materials = materials;
        interview.technique = technique;
        interview.story = story;
        return interview;
    }

    public void update(String process, String materials, String technique, String story) {
        if (process != null) {
            this.process = process;
        }
        if (materials != null) {
            this.materials = materials;
        }
        if (technique != null) {
            this.technique = technique;
        }
        if (story != null) {
            this.story = story;
        }
    }
}
