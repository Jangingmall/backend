package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.Subcategory;
import com.jangingmall.backend.product.domain.SubcategoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface JpaSubcategoryRepositoryJpa extends JpaRepository<Subcategory, Long> {
    List<Subcategory> findByCategoryId(Long categoryId);
}

@Repository
class JpaSubcategoryRepository implements SubcategoryRepository {

    private final JpaSubcategoryRepositoryJpa jpa;

    JpaSubcategoryRepository(JpaSubcategoryRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Subcategory> findAll() {
        return jpa.findAll();
    }

    @Override
    public List<Subcategory> findByCategoryId(Long categoryId) {
        return jpa.findByCategoryId(categoryId);
    }

    @Override
    public Optional<Subcategory> findById(Long subcategoryId) {
        return jpa.findById(subcategoryId);
    }
}
