package com.jangingmall.backend.product.domain;

import java.util.List;
import java.util.Optional;

public interface ExhibitionRepository {

    List<Exhibition> findAllActive(ExhibitionSort sort);

    Optional<Exhibition> findActiveById(Long exhibitionId);
}
