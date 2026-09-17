package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ContentBlockJpaRepository extends JpaRepository<ContentBlock, Long> {
    List<ContentBlock> findByContentIdOrderByDisplayOrderAsc(Long contentId);

    Optional<ContentBlock> findByContentIdAndDisplayOrder(Long contentId, int displayOrder);

    void deleteByContentId(Long contentId);
}

@Repository
class JpaContentBlockRepository implements ContentBlockRepository {

    private final ContentBlockJpaRepository jpa;

    JpaContentBlockRepository(ContentBlockJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ContentBlock> findByContentIdOrderByDisplayOrderAsc(Long contentId) {
        return jpa.findByContentIdOrderByDisplayOrderAsc(contentId);
    }

    @Override
    public Optional<ContentBlock> findByContentIdAndDisplayOrder(Long contentId, int displayOrder) {
        return jpa.findByContentIdAndDisplayOrder(contentId, displayOrder);
    }

    @Override
    public void deleteByContentId(Long contentId) {
        jpa.deleteByContentId(contentId);
    }

    @Override
    public List<ContentBlock> saveAll(Iterable<ContentBlock> blocks) {
        return jpa.saveAll(blocks);
    }
}
