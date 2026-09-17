package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.AddressData;
import com.jangingmall.backend.member.application.AddressService;
import com.jangingmall.backend.member.presentation.dto.MemberAccountRequests;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AddressControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private AddressService addresses;

    private static final AddressData SAMPLE_ADDRESS = new AddressData(
        1L, "김도공", "01012345678", "04524", "서울시 중구 세종대로 110", "101호", true);

    @Test
    @DisplayName("내 배송지 목록을 조회한다")
    void listAddresses() throws Exception {
        when(addresses.list(1L)).thenReturn(List.of(SAMPLE_ADDRESS));

        mockMvc.perform(get("/api/member/me/addresses").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].addressId").value(1))
            .andExpect(jsonPath("$.data[0].recipientName").value("김도공"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "address-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("배송지")
                    .summary("배송지 목록 조회")
                    .description("회원의 배송지 목록을 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].addressId").type(JsonFieldType.NUMBER).description("배송지 ID"),
                        fieldWithPath("data[].recipientName").type(JsonFieldType.STRING).description("수령인"),
                        fieldWithPath("data[].phone").type(JsonFieldType.STRING).description("전화번호"),
                        fieldWithPath("data[].zipCode").type(JsonFieldType.STRING).description("우편번호"),
                        fieldWithPath("data[].address1").type(JsonFieldType.STRING).description("기본 주소"),
                        fieldWithPath("data[].address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                        fieldWithPath("data[].isDefault").type(JsonFieldType.BOOLEAN).description("기본 배송지 여부")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("배송지 추가는 201과 생성된 배송지를 반환한다")
    void createAddress() throws Exception {
        when(addresses.create(eq(1L), any())).thenReturn(SAMPLE_ADDRESS);

        mockMvc.perform(post("/api/member/me/addresses").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new MemberAccountRequests.CreateAddress(
                    "김도공", "01012345678", "04524", "서울시 중구 세종대로 110", "101호", true))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.addressId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "address-create",
                resource(ResourceSnippetParameters.builder()
                    .tag("배송지")
                    .summary("배송지 추가")
                    .description("새 배송지를 등록합니다.")
                    .requestFields(
                        fieldWithPath("recipientName").type(JsonFieldType.STRING).description("수령인 (최대 50자)"),
                        fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호 (숫자만)"),
                        fieldWithPath("zipCode").type(JsonFieldType.STRING).description("우편번호 (최대 10자)"),
                        fieldWithPath("address1").type(JsonFieldType.STRING).description("기본 주소 (최대 255자)"),
                        fieldWithPath("address2").type(JsonFieldType.STRING).optional().description("상세 주소 (최대 255자)"),
                        fieldWithPath("isDefault").type(JsonFieldType.BOOLEAN).description("기본 배송지 여부")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.addressId").type(JsonFieldType.NUMBER).description("배송지 ID"),
                        fieldWithPath("data.recipientName").type(JsonFieldType.STRING).description("수령인"),
                        fieldWithPath("data.phone").type(JsonFieldType.STRING).description("전화번호"),
                        fieldWithPath("data.zipCode").type(JsonFieldType.STRING).description("우편번호"),
                        fieldWithPath("data.address1").type(JsonFieldType.STRING).description("기본 주소"),
                        fieldWithPath("data.address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                        fieldWithPath("data.isDefault").type(JsonFieldType.BOOLEAN).description("기본 배송지 여부")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("배송지 수정은 변경된 배송지를 반환한다")
    void updateAddress() throws Exception {
        AddressData updated = new AddressData(1L, "김도공", "01098765432", "04524", "서울시 중구 세종대로 110", "201호", false);
        when(addresses.update(eq(1L), eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/member/me/addresses/{addressId}", 1L).with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new MemberAccountRequests.UpdateAddress(null, "01098765432", null, null, "201호", null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.addressId").value(1))
            .andExpect(jsonPath("$.data.address2").value("201호"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "address-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("배송지")
                    .summary("배송지 수정")
                    .description("배송지 정보를 부분 수정합니다. null 필드는 변경하지 않습니다.")
                    .requestFields(
                        fieldWithPath("recipientName").type(JsonFieldType.STRING).optional().description("수령인"),
                        fieldWithPath("phone").type(JsonFieldType.STRING).optional().description("전화번호"),
                        fieldWithPath("zipCode").type(JsonFieldType.STRING).optional().description("우편번호"),
                        fieldWithPath("address1").type(JsonFieldType.STRING).optional().description("기본 주소"),
                        fieldWithPath("address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                        fieldWithPath("isDefault").type(JsonFieldType.BOOLEAN).optional().description("기본 배송지 여부")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.addressId").type(JsonFieldType.NUMBER).description("배송지 ID"),
                        fieldWithPath("data.recipientName").type(JsonFieldType.STRING).description("수령인"),
                        fieldWithPath("data.phone").type(JsonFieldType.STRING).description("전화번호"),
                        fieldWithPath("data.zipCode").type(JsonFieldType.STRING).description("우편번호"),
                        fieldWithPath("data.address1").type(JsonFieldType.STRING).description("기본 주소"),
                        fieldWithPath("data.address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                        fieldWithPath("data.isDefault").type(JsonFieldType.BOOLEAN).description("기본 배송지 여부")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("배송지 삭제는 성공 시 200을 반환한다")
    void deleteAddress() throws Exception {
        mockMvc.perform(delete("/api/member/me/addresses/{addressId}", 1L).with(user()))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "address-delete",
                resource(ResourceSnippetParameters.builder()
                    .tag("배송지")
                    .summary("배송지 삭제")
                    .description("배송지를 삭제합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("응답 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("인증 없이 배송지 목록 조회하면 401을 반환한다")
    @WithAnonymousUser
    void listAddressesWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/member/me/addresses"))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("address-list-unauthorized", "배송지", "배송지 목록 — 인증 없음", "인증 없이 접근하면 401을 반환합니다."));
    }

    private RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
