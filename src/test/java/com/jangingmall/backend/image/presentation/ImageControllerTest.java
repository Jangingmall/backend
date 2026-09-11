package com.jangingmall.backend.image.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.image.application.ImageService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ImageController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ImageControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ImageService images;

    @Test
    @DisplayName("IMG-P0-009 Presigned URL 발급은 인증이 필요하고 정상 요청은 200이다")
    void createsPresignedUrlsForAuthenticatedMember() throws Exception {
        when(images.createPresignedUpload(eq(1L), any())).thenReturn(new ImageService.PresignedUpload(
            "01JIMAGE000000000000000000", List.of(new ImageService.VariantUpload(
                "320w", "images/product/1/id/320w.webp", "https://s3.example/320w")), 300));

        mockMvc.perform(post("/api/images/presigned-url").contentType(APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/images/presigned-url").with(user()).contentType(APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imageId").value("01JIMAGE000000000000000000"))
            .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    }

    @Test
    @DisplayName("IMG-P0-010 Presigned URL 요청 필수값 누락은 400 INVALID_INPUT이다")
    void validatesPresignedUrlRequest() throws Exception {
        mockMvc.perform(post("/api/images/presigned-url").with(user()).contentType(APPLICATION_JSON)
                .content("{\"fileName\":\"\",\"contentType\":\"\",\"purpose\":\"PRODUCT\","
                    + "\"sourceWidth\":0,\"sourceHeight\":0,\"variants\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("IMG-P2-011 미사용 이미지 삭제는 정상 200이고 타인 소유는 403이다")
    void deletesOnlyOwnedUnusedImage() throws Exception {
        mockMvc.perform(delete("/api/images/01JIMAGE000000000000000000").with(user()))
            .andExpect(status().isOk());

        doThrow(new DomainException(ErrorCode.FORBIDDEN)).when(images)
            .deleteUnused(1L, "01JOTHER000000000000000000");
        mockMvc.perform(delete("/api/images/01JOTHER000000000000000000").with(user()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("IMG-P0-012 내부 검증 API는 관리자 토큰만 접근할 수 있다")
    void protectsInternalVerificationEndpoint() throws Exception {
        String request = "{\"imageId\":\"01JIMAGE000000000000000000\",\"requesterId\":1}";
        when(images.verifyAndConsume(1L, "01JIMAGE000000000000000000"))
            .thenReturn(new ImageService.Verification(true, true, List.of("images/product/1/id/320w.webp")));

        mockMvc.perform(post("/api/internal/images/verify").with(user()).contentType(APPLICATION_JSON)
                .content(request))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/internal/images/verify").with(admin()).contentType(APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exists").value(true))
            .andExpect(jsonPath("$.data.ownerMatched").value(true));
    }

    private RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
            99L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private String validRequest() {
        return "{\"fileName\":\"bowl.webp\",\"contentType\":\"image/webp\","
            + "\"purpose\":\"PRODUCT\",\"sourceWidth\":1200,\"sourceHeight\":800,"
            + "\"variants\":[\"320w\",\"640w\",\"1280w\"]}";
    }
}
