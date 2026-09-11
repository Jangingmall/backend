package com.jangingmall.backend.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.payment.application.CheckoutCatalog;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JdbcCheckoutCatalogTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PAY-P0-009 TEXT 옵션 그룹을 하나라도 입력하지 않으면 422다")
    void requiresEveryTextOptionGroup() {
        assertThatThrownBy(() -> JdbcCheckoutCatalog.requireAllTextInputs(Set.of(10L), Set.of(10L, 11L)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("PAY-P0-038 조건부 재고 차감에서 한 행도 갱신되지 않으면 초과판매를 차단한다")
    void rejectsLostInventoryRace() {
        JdbcCheckoutCatalog catalog = new JdbcCheckoutCatalog(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> catalog.reserve(List.of(
            new CheckoutCatalog.InventoryLine(7L, 1, List.of()))))
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}
