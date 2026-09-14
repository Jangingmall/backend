package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaContentEditHistoryRepository extends JpaRepository<ContentEditHistory, Long>, ContentEditHistoryRepository {

    List<ContentEditHistory> findAllByContentIdOrderByVersionAsc(Long contentId);
}
