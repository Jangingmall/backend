package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.Exhibition;
import com.jangingmall.backend.product.domain.ExhibitionRepository;
import com.jangingmall.backend.product.domain.ExhibitionSort;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaExhibitionRepositoryJpa extends JpaRepository<Exhibition, Long> {
    List<Exhibition> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<Exhibition> findByActiveTrueOrderByCreatedAtDescIdDesc();

    Optional<Exhibition> findByIdAndActiveTrue(Long id);
}

@Repository
class JpaExhibitionRepository implements ExhibitionRepository {

    private final JpaExhibitionRepositoryJpa jpa;

    JpaExhibitionRepository(JpaExhibitionRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Exhibition> findAllActive(ExhibitionSort sort) {
        return sort == ExhibitionSort.NEWEST
            ? jpa.findByActiveTrueOrderByCreatedAtDescIdDesc()
            : jpa.findByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @Override
    public Optional<Exhibition> findActiveById(Long exhibitionId) {
        return jpa.findByIdAndActiveTrue(exhibitionId);
    }
}
