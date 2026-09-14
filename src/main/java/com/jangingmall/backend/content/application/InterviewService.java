package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewErrorMessage;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.ConflictException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ProductRepository productRepository;

    @Transactional
    public InterviewResponse create(InterviewCommand.Create command) {
        Product product = getProduct(command.productId());
        verifyOwner(product, command.requesterId());
        if (interviewRepository.existsByProductId(command.productId())) {
            throw new ConflictException(InterviewErrorMessage.ALREADY_EXISTS.message());
        }
        Interview interview = Interview.create(
            command.productId(),
            command.process(),
            command.materials(),
            command.technique(),
            command.story()
        );
        return InterviewResponse.from(interviewRepository.save(interview));
    }

    @Transactional(readOnly = true)
    public InterviewResponse find(Long productId, Long requesterId) {
        Product product = getProduct(productId);
        verifyOwner(product, requesterId);
        Interview interview = interviewRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException(InterviewErrorMessage.NOT_FOUND.message()));
        return InterviewResponse.from(interview);
    }

    @Transactional
    public InterviewResponse update(InterviewCommand.Update command) {
        Product product = getProduct(command.productId());
        verifyOwner(product, command.requesterId());
        Interview interview = interviewRepository.findByProductId(command.productId())
            .orElseThrow(() -> new NotFoundException(InterviewErrorMessage.NOT_FOUND.message()));
        interview.update(command.process(), command.materials(), command.technique(), command.story());
        return InterviewResponse.from(interview);
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
    }

    private void verifyOwner(Product product, Long requesterId) {
        if (!product.getArtisanId().equals(requesterId)) {
            throw new ForbiddenException(InterviewErrorMessage.FORBIDDEN.message());
        }
    }
}
