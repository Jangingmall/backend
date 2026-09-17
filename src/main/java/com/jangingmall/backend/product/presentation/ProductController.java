package com.jangingmall.backend.product.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.MemberActivityService;
import com.jangingmall.backend.product.application.ProductCommand;
import com.jangingmall.backend.product.application.ProductQnaCommand;
import com.jangingmall.backend.product.application.ProductQnaResponse;
import com.jangingmall.backend.product.application.ProductQnaService;
import com.jangingmall.backend.product.application.ProductResponse;
import com.jangingmall.backend.product.application.ProductReviewCommand;
import com.jangingmall.backend.product.application.ProductReviewResponse;
import com.jangingmall.backend.product.application.ProductReviewService;
import com.jangingmall.backend.product.application.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductQnaService productQnaService;
    private final ProductReviewService productReviewService;
    private final MemberActivityService memberActivityService;

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ProductRequest.Create request
    ) {
        ProductCommand.Create command = new ProductCommand.Create(
            memberId,
            request.categoryId(),
            request.subcategoryId(),
            request.title(),
            request.description(),
            request.price(),
            request.stock(),
            request.thumbnailUrl(),
            request.giftThemes() != null ? request.giftThemes() : List.of(),
            request.purposeTags() != null ? request.purposeTags() : List.of(),
            request.productionPeriodDays(),
            request.colors() != null ? request.colors() : List.of(),
            request.images()
        );
        ProductResponse response = productService.create(command);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<Page<ProductResponse>> myProducts(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productService.findByArtisan(memberId, pageable));
    }

    @GetMapping
    public ApiResponse<Page<ProductResponse>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long subcategoryId,
        @RequestParam(required = false) String giftTheme,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer minPrice,
        @RequestParam(required = false) Integer maxPrice,
        @RequestParam(required = false) Boolean excludeSoldOut,
        @RequestParam(required = false) Long artisanId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductCommand.Search search = new ProductCommand.Search(
            keyword, categoryId, subcategoryId, giftTheme, sort,
            minPrice, maxPrice, excludeSoldOut, artisanId
        );
        return ApiResponse.ok(productService.findOnSale(search, pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> detail(@PathVariable Long productId) {
        return ApiResponse.ok(productService.findById(productId));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ProductResponse> update(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductRequest.Update request
    ) {
        ProductCommand.Update command = new ProductCommand.Update(
            productId,
            memberId,
            request.categoryId(),
            request.subcategoryId(),
            request.title(),
            request.description(),
            request.price(),
            request.stock(),
            request.thumbnailUrl(),
            request.giftThemes() != null ? request.giftThemes() : List.of(),
            request.purposeTags() != null ? request.purposeTags() : List.of(),
            request.productionPeriodDays(),
            request.colors() != null ? request.colors() : List.of(),
            request.images()
        );
        return ApiResponse.ok(productService.update(command));
    }

    @PatchMapping("/{productId}/status")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<Void> changeStatus(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductRequest.ChangeStatus request
    ) {
        productService.changeStatus(new ProductCommand.ChangeStatus(productId, memberId, request.status()));
        return ApiResponse.noContent();
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId
    ) {
        productService.delete(productId, memberId);
        return ApiResponse.noContent();
    }

    @PostMapping("/{productId}/wish")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> wish(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId
    ) {
        memberActivityService.wish(memberId, productId);
        return ApiResponse.noContent();
    }

    @DeleteMapping("/{productId}/wish")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> unwish(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId
    ) {
        memberActivityService.unwish(memberId, productId);
        return ApiResponse.noContent();
    }

    @GetMapping("/{productId}/questions")
    public ApiResponse<Page<ProductQnaResponse.QuestionView>> questions(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productQnaService.findQuestions(productId, memberId, pageable));
    }

    @PostMapping("/{productId}/questions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductQnaResponse.QuestionView>> ask(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductQnaRequest.Ask request
    ) {
        ProductQnaResponse.QuestionView response = productQnaService.ask(
            new ProductQnaCommand.Ask(productId, memberId, request.content(), request.secret())
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PostMapping("/{productId}/questions/{questionId}/answer")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<ProductQnaResponse.AnswerView>> answer(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @PathVariable Long questionId,
        @Valid @RequestBody ProductQnaRequest.Answer request
    ) {
        ProductQnaResponse.AnswerView response = productQnaService.answer(
            new ProductQnaCommand.Answer(questionId, memberId, request.content())
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/{productId}/reviews")
    public ApiResponse<Page<ProductReviewResponse.ReviewView>> reviews(
        @PathVariable Long productId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productReviewService.findReviews(productId, pageable));
    }

    @PostMapping("/{productId}/reviews")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductReviewResponse.ReviewView>> writeReview(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductReviewRequest.Write request
    ) {
        ProductReviewResponse.ReviewView response = productReviewService.write(
            new ProductReviewCommand.Write(productId, memberId, request.orderItemId(), request.rating(), request.content(),
                request.images())
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }
}
