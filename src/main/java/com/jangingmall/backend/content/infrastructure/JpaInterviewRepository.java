package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface JpaInterviewRepositoryJpa extends JpaRepository<Interview, Long> {
    Optional<Interview> findByProductId(Long productId);
    boolean existsByProductId(Long productId);
}

@Repository
class JpaInterviewRepository implements InterviewRepository {

    private final JpaInterviewRepositoryJpa jpa;

    JpaInterviewRepository(JpaInterviewRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Interview save(Interview interview) {
        return jpa.save(interview);
    }

    @Override
    public Optional<Interview> findByProductId(Long productId) {
        return jpa.findByProductId(productId);
    }

    @Override
    public boolean existsByProductId(Long productId) {
        return jpa.existsByProductId(productId);
    }
}
