package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.SubcategoryMaterial;
import com.jangingmall.backend.product.domain.SubcategoryMaterialRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface JpaSubcategoryMaterialRepositoryJpa extends JpaRepository<SubcategoryMaterial, Long> {

    @Query("SELECT m.name FROM SubcategoryMaterial m WHERE m.subcategory.id = :subcategoryId ORDER BY m.name")
    List<String> findNamesBySubcategoryId(@Param("subcategoryId") Long subcategoryId);
}

@Repository
class JpaSubcategoryMaterialRepository implements SubcategoryMaterialRepository {

    private final JpaSubcategoryMaterialRepositoryJpa jpa;

    JpaSubcategoryMaterialRepository(JpaSubcategoryMaterialRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<String> findNamesBySubcategoryId(Long subcategoryId) {
        return jpa.findNamesBySubcategoryId(subcategoryId);
    }
}
