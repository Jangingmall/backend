package com.jangingmall.backend.product.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.product.application.ExhibitionQueryService;
import com.jangingmall.backend.product.application.ExhibitionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exhibitions")
@RequiredArgsConstructor
public class ExhibitionController {

    private final ExhibitionQueryService exhibitionQueryService;

    @GetMapping
    public ApiResponse<List<ExhibitionResponse.Summary>> list(@RequestParam(required = false) String sort) {
        return ApiResponse.ok(exhibitionQueryService.findAll(sort));
    }

    @GetMapping("/{exhibitionId}")
    public ApiResponse<ExhibitionResponse.Detail> detail(@PathVariable Long exhibitionId,
                                                         @RequestParam(required = false) String sort) {
        return ApiResponse.ok(exhibitionQueryService.findById(exhibitionId, sort));
    }
}
