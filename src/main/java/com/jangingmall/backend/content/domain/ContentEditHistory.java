package com.jangingmall.backend.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_edit_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(nullable = false)
    private int version;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "edited_by_type", nullable = false, length = 10)
    private EditedByType editedByType;

    @Column(name = "edited_by_member_id")
    private Long editedByMemberId;

    public static ContentEditHistory record(Long contentId, int version, EditedByType editedByType, Long editedByMemberId) {
        ContentEditHistory history = new ContentEditHistory();
        history.contentId = contentId;
        history.version = version;
        history.editedAt = LocalDateTime.now();
        history.editedByType = editedByType;
        history.editedByMemberId = editedByMemberId;
        return history;
    }
}
