package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaContentBlockRepository extends JpaRepository<ContentBlock, Long>, ContentBlockRepository {

    Optional<ContentBlock> findByContentIdAndDisplayOrder(Long contentId, short displayOrder);

    void deleteAllByContentId(Long contentId);
}
