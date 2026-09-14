package com.jangingmall.backend.content.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BlockTag tag;

    @Column(name = "image_id", length = 30)
    private String imageId;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(length = 2000)
    private String text;

    public static ContentBlock create(Content content, short displayOrder, BlockTag tag, String imageId, String videoUrl, String text) {
        ContentBlock block = new ContentBlock();
        block.content = content;
        block.displayOrder = displayOrder;
        block.tag = tag;
        block.imageId = imageId;
        block.videoUrl = videoUrl;
        block.text = text;
        return block;
    }

    public void updateText(String text) {
        validateTextTag();
        this.text = text;
    }

    public void updateImage(String imageId) {
        validateImageTag();
        this.imageId = imageId;
    }

    public void updateVideoUrl(String videoUrl) {
        if (tag != BlockTag.video) {
            throw new BusinessRuleViolationException(ContentErrorMessage.INVALID_BLOCK_TAG.message());
        }
        this.videoUrl = videoUrl;
    }

    public void replaceWith(BlockTag tag, String text, String imageId, String videoUrl) {
        this.tag = tag;
        this.text = text;
        this.imageId = imageId;
        this.videoUrl = videoUrl;
    }

    private void validateTextTag() {
        if (tag == BlockTag.img || tag == BlockTag.video) {
            throw new BusinessRuleViolationException(ContentErrorMessage.INVALID_BLOCK_TAG.message());
        }
    }

    private void validateImageTag() {
        if (tag != BlockTag.img) {
            throw new BusinessRuleViolationException(ContentErrorMessage.INVALID_BLOCK_TAG.message());
        }
    }
}
