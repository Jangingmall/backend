package com.jangingmall.backend.image.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ImageController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal-api.backend-auth-token=test-agent-token")
class ImageControllerTest extends RestDocsControllerTest {

    private static final String AGENT_TOKEN = "test-agent-token";

    @MockitoBean private ImageService images;

    @Test
    @DisplayName("Presigned URL 발급은 인증된 회원에게 업로드 URL을 반환한다")
    void createsPresignedUrl() throws Exception {
        when(images.createPresignedUpload(eq(1L), any())).thenReturn(new ImageService.PresignedUpload(
            "01JIMAGE000000000000000000",
            List.of(new ImageService.VariantUpload("320w", "images/product/1/id/320w.webp", "https://s3.example/320w")),
            300));

        mockMvc.perform(post("/api/images/presigned-url").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.PresignedUrlRequest(
                    "bowl.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                    List.of(new ImageController.VariantRequest("320w", 1024)), null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imageId").value("01JIMAGE000000000000000000"))
            .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "image-presigned-url",
                resource(ResourceSnippetParameters.builder()
                    .tag("이미지")
                    .summary("Presigned URL 발급")
                    .description("S3 업로드를 위한 Presigned URL을 발급합니다. 클라이언트는 반환된 URL로 직접 S3에 업로드합니다.")
                    .requestFields(
                        fieldWithPath("fileName").type(JsonFieldType.STRING).description("원본 파일명"),
                        fieldWithPath("contentType").type(JsonFieldType.STRING).description("MIME 타입 (예: image/webp)"),
                        fieldWithPath("purpose").type(JsonFieldType.STRING).description("용도 (PRODUCT, PROFILE 등)"),
                        fieldWithPath("sourceWidth").type(JsonFieldType.NUMBER).description("원본 이미지 너비 (px)"),
                        fieldWithPath("sourceHeight").type(JsonFieldType.NUMBER).description("원본 이미지 높이 (px)"),
                        fieldWithPath("variants").type(JsonFieldType.ARRAY).description("업로드할 사이즈 변형 이름 목록 (예: 320w, 640w, 1280w)"),
                        fieldWithPath("memberId").type(JsonFieldType.NUMBER).optional().description("AGENT 전용 — 업로드 소유자 회원 ID (일반 회원은 생략)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.imageId").type(JsonFieldType.STRING).description("이미지 ID (ULID)"),
                        fieldWithPath("data.uploads").type(JsonFieldType.ARRAY).description("각 변형별 업로드 URL"),
                        fieldWithPath("data.uploads[].variant").type(JsonFieldType.STRING).description("변형 이름"),
                        fieldWithPath("data.uploads[].objectKey").type(JsonFieldType.STRING).description("S3 오브젝트 키"),
                        fieldWithPath("data.uploads[].presignedUrl").type(JsonFieldType.STRING).description("S3 Presigned URL"),
                        fieldWithPath("data.expiresInSeconds").type(JsonFieldType.NUMBER).description("URL 유효 시간 (초)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("협업 계약의 문자열 variants 요청도 Presigned URL을 발급한다")
    void createsPresignedUrlFromContractShape() throws Exception {
        when(images.createPresignedUpload(eq(1L), any())).thenReturn(new ImageService.PresignedUpload(
            "01JIMAGE000000000000000000",
            List.of(new ImageService.VariantUpload("320w", "images/product/1/id/320w.webp", "https://s3.example/320w")),
            300));

        mockMvc.perform(post("/api/images/presigned-url").with(user())
                .contentType(APPLICATION_JSON)
                .content("{\"fileName\":\"photo.jpg\",\"contentType\":\"image/webp\","
                    + "\"purpose\":\"PRODUCT\",\"sourceWidth\":1200,\"sourceHeight\":800,"
                    + "\"variants\":[\"320w\",\"640w\",\"1280w\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imageId").value("01JIMAGE000000000000000000"));
    }

    @Test
    @DisplayName("AGENT 토큰으로 memberId를 지정하면 해당 회원의 Presigned URL을 발급한다")
    void agentCreatesPresignedUrlWithMemberId() throws Exception {
        when(images.createPresignedUpload(eq(42L), any())).thenReturn(new ImageService.PresignedUpload(
            "01JAGENT00000000000000000",
            List.of(new ImageService.VariantUpload("320w", "images/product/42/id/320w.webp", "https://s3.example/320w")),
            300));

        mockMvc.perform(post("/api/images/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AGENT_TOKEN)
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.PresignedUrlRequest(
                    "product.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                    List.of(new ImageController.VariantRequest("320w", 1024)), 42L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imageId").value("01JAGENT00000000000000000"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "image-presigned-url-agent",
                resource(ResourceSnippetParameters.builder()
                    .tag("이미지")
                    .summary("Presigned URL 발급 (AGENT)")
                    .description("AI AGENT가 특정 회원 대신 업로드 URL을 발급합니다. memberId 필수.")
                    .requestFields(
                        fieldWithPath("fileName").type(JsonFieldType.STRING).description("원본 파일명"),
                        fieldWithPath("contentType").type(JsonFieldType.STRING).description("MIME 타입 (예: image/webp)"),
                        fieldWithPath("purpose").type(JsonFieldType.STRING).description("용도 (PRODUCT, PROFILE 등)"),
                        fieldWithPath("sourceWidth").type(JsonFieldType.NUMBER).description("원본 이미지 너비 (px)"),
                        fieldWithPath("sourceHeight").type(JsonFieldType.NUMBER).description("원본 이미지 높이 (px)"),
                        fieldWithPath("variants").type(JsonFieldType.ARRAY).description("업로드할 사이즈 변형 이름 목록"),
                        fieldWithPath("memberId").type(JsonFieldType.NUMBER).description("업로드 소유자 회원 ID (AGENT 필수)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.imageId").type(JsonFieldType.STRING).description("이미지 ID (ULID)"),
                        fieldWithPath("data.uploads").type(JsonFieldType.ARRAY).description("각 변형별 업로드 URL"),
                        fieldWithPath("data.uploads[].variant").type(JsonFieldType.STRING).description("변형 이름"),
                        fieldWithPath("data.uploads[].objectKey").type(JsonFieldType.STRING).description("S3 오브젝트 키"),
                        fieldWithPath("data.uploads[].presignedUrl").type(JsonFieldType.STRING).description("S3 Presigned URL"),
                        fieldWithPath("data.expiresInSeconds").type(JsonFieldType.NUMBER).description("URL 유효 시간 (초)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AGENT 토큰으로 memberId 없이 요청하면 400을 반환한다")
    void agentWithoutMemberIdReturns400() throws Exception {
        mockMvc.perform(post("/api/images/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AGENT_TOKEN)
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.PresignedUrlRequest(
                    "product.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                    List.of(new ImageController.VariantRequest("320w", 1024)), null))))
            .andExpect(status().isBadRequest())
            .andDo(documentError("image-presigned-url-agent-no-member-id", "이미지", "Presigned URL — AGENT memberId 누락", "AGENT가 memberId 없이 요청하면 400을 반환합니다."));
    }

    @Test
    @DisplayName("AGENT 토큰 불일치 시 일반 인증으로 통과하지 못하면 401을 반환한다")
    @WithAnonymousUser
    void agentWithWrongTokenFallsThrough401() throws Exception {
        mockMvc.perform(post("/api/images/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.PresignedUrlRequest(
                    "product.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                    List.of(new ImageController.VariantRequest("320w", 1024)), null))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AGENT 토큰으로 비허용 경로 접근은 401을 반환한다")
    @WithAnonymousUser
    void agentTokenOnDisallowedPathReturns401() throws Exception {
        mockMvc.perform(delete("/api/images/01JIMAGE000000000000000000")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AGENT_TOKEN))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Presigned URL 발급은 인증 없이 접근하면 401을 반환한다")
    @WithAnonymousUser
    void presignedUrlRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/images/presigned-url")
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.PresignedUrlRequest(
                    "bowl.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                    List.of(new ImageController.VariantRequest("320w", 1024)), null))))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("image-presigned-url-unauthorized", "이미지", "Presigned URL — 인증 없음", "인증 없이 접근하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("미사용 이미지 삭제는 본인 소유 이미지를 삭제한다")
    void deletesOwnImage() throws Exception {
        mockMvc.perform(delete("/api/images/{imageId}", "01JIMAGE000000000000000000").with(user()))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "image-delete",
                resource(ResourceSnippetParameters.builder()
                    .tag("이미지")
                    .summary("미사용 이미지 삭제")
                    .description("아직 상품에 연결되지 않은 임시 이미지를 삭제합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("응답 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("타인 소유 이미지 삭제는 403 FORBIDDEN을 반환한다")
    void rejectsDeletingOtherMembersImage() throws Exception {
        doThrow(new DomainException(ErrorCode.FORBIDDEN)).when(images)
            .deleteUnused(1L, "01JOTHER000000000000000000");

        mockMvc.perform(delete("/api/images/{imageId}", "01JOTHER000000000000000000").with(user()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
            .andDo(documentError("image-delete-forbidden", "이미지", "이미지 삭제 — 권한 없음", "타인 소유 이미지 삭제 시 403을 반환합니다."));
    }

    @Test
    @DisplayName("사용 중인 이미지 삭제는 409 CONFLICT를 반환한다")
    void rejectsDeletingConsumedImage() throws Exception {
        doThrow(new DomainException(ErrorCode.CONFLICT)).when(images)
            .deleteUnused(1L, "01JCONSUMED000000000000000");

        mockMvc.perform(delete("/api/images/{imageId}", "01JCONSUMED000000000000000").with(user()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andDo(documentError("image-delete-conflict", "이미지", "이미지 삭제 — 사용 중", "이미 상품에 연결된 이미지 삭제 시 409를 반환합니다."));
    }

    @Test
    @DisplayName("내부 이미지 검증 API는 관리자만 접근할 수 있다")
    void verifiesImageAsAdmin() throws Exception {
        when(images.verifyAndConsume(1L, "01JIMAGE000000000000000000"))
            .thenReturn(new ImageService.Verification(true, true, List.of("images/product/1/id/320w.webp")));

        mockMvc.perform(post("/api/internal/images/verify").with(admin())
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.VerifyImageRequest("01JIMAGE000000000000000000", 1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exists").value(true))
            .andExpect(jsonPath("$.data.ownerMatched").value(true))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "image-internal-verify",
                resource(ResourceSnippetParameters.builder()
                    .tag("이미지")
                    .summary("이미지 검증 (내부)")
                    .description("내부 서비스에서 이미지 존재 여부와 소유권을 검증합니다. 관리자 토큰 필요.")
                    .requestFields(
                        fieldWithPath("imageId").type(JsonFieldType.STRING).description("이미지 ID (ULID)"),
                        fieldWithPath("requesterId").type(JsonFieldType.NUMBER).description("소유자 확인용 회원 ID")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.exists").type(JsonFieldType.BOOLEAN).description("이미지 존재 여부"),
                        fieldWithPath("data.ownerMatched").type(JsonFieldType.BOOLEAN).description("소유권 일치 여부"),
                        fieldWithPath("data.variants").type(JsonFieldType.ARRAY).description("S3 오브젝트 키 목록")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("내부 이미지 검증 API는 일반 사용자가 접근하면 403을 반환한다")
    void rejectsInternalVerificationForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/internal/images/verify").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.VerifyImageRequest("01JIMAGE000000000000000000", 1L))))
            .andExpect(status().isForbidden())
            .andDo(documentError("image-internal-verify-forbidden", "이미지", "이미지 검증 — 권한 없음", "관리자가 아니면 403을 반환합니다."));
    }

    @Test
    @DisplayName("존재하지 않는 이미지 검증은 404 NOT_FOUND를 반환한다")
    void reportsMissingImage() throws Exception {
        when(images.verifyAndConsume(1L, "01JMISSING0000000000000000"))
            .thenThrow(new DomainException(ErrorCode.NOT_FOUND));

        mockMvc.perform(post("/api/internal/images/verify").with(admin())
                .contentType(APPLICATION_JSON)
                .content(json(new ImageController.VerifyImageRequest("01JMISSING0000000000000000", 1L))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
            .andDo(documentError("image-internal-verify-not-found", "이미지", "이미지 검증 — 미존재", "이미지가 없으면 404를 반환합니다."));
    }

    private RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
            99L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
