package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.CategoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface JpaCategoryRepositoryJpa extends JpaRepository<Category, Long> {}

@Repository
class JpaCategoryRepository implements CategoryRepository {

    private final JpaCategoryRepositoryJpa jpa;

    JpaCategoryRepository(JpaCategoryRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAll();
    }

    @Override
    public Optional<Category> findById(Long categoryId) {
        return jpa.findById(categoryId);
    }
}
