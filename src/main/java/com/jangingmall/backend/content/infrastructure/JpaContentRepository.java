package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaContentRepository extends JpaRepository<Content, Long>, ContentRepository {

    Optional<Content> findByProductId(Long productId);

    Optional<Content> findByIdAndProductId(Long contentId, Long productId);
}
