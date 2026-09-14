package com.jangingmall.backend.content.domain;

import java.util.List;

public interface ContentEditHistoryRepository {

    ContentEditHistory save(ContentEditHistory history);

    List<ContentEditHistory> findAllByContentIdOrderByVersionAsc(Long contentId);
}
