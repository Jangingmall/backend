package com.jangingmall.backend.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 10)
    private String tag;

    @Column(name = "image_id", length = 30)
    private String imageId;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(length = 2000)
    private String text;

    public ContentBlock(Long contentId, int displayOrder, String tag, String imageId, String videoUrl, String text) {
        this.contentId = contentId;
        this.displayOrder = displayOrder;
        this.tag = tag;
        this.imageId = imageId;
        this.videoUrl = videoUrl;
        this.text = text;
    }

    public void update(String tag, String imageId, String videoUrl, String text) {
        this.tag = tag;
        this.imageId = imageId;
        this.videoUrl = videoUrl;
        this.text = text;
    }
}
