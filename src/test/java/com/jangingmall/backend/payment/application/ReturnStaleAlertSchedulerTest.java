package com.jangingmall.backend.payment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.OrderReturnRepository;
import com.jangingmall.backend.payment.domain.ReturnReason;
import com.jangingmall.backend.payment.domain.ReturnStatus;
import com.jangingmall.backend.payment.domain.ReturnType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReturnStaleAlertSchedulerTest {

    @Mock private OrderReturnRepository returns;
    @Mock private OrderNotificationPublisher notifications;
    private ReturnStaleAlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReturnStaleAlertScheduler(returns, notifications);
    }

    @Test
    @DisplayName("PAY-P1-099 24시간 초과 REQUESTED 건에 대해 알림을 발송한다")
    void alertsForStaleReturns() {
        OrderReturn stale = staleReturn(1L);
        when(returns.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(eq(ReturnStatus.REQUESTED), any(Instant.class)))
            .thenReturn(List.of(stale));

        scheduler.alertStaleReturns();

        verify(notifications).returnStaleAlert(stale);
    }

    @Test
    @DisplayName("PAY-P1-100 미처리 건이 없으면 알림을 발송하지 않는다")
    void noAlertWhenNoStaleReturns() {
        when(returns.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(eq(ReturnStatus.REQUESTED), any(Instant.class)))
            .thenReturn(List.of());

        scheduler.alertStaleReturns();

        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("PAY-P1-101 여러 미처리 건에 대해 각각 알림을 발송한다")
    void alertsForEachStaleReturn() {
        when(returns.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(eq(ReturnStatus.REQUESTED), any(Instant.class)))
            .thenReturn(List.of(staleReturn(1L), staleReturn(2L), staleReturn(3L)));

        scheduler.alertStaleReturns();

        verify(notifications, times(3)).returnStaleAlert(any(OrderReturn.class));
    }

    private OrderReturn staleReturn(Long id) {
        OrderReturn orderReturn = new OrderReturn(id * 10, ReturnType.RETURN, ReturnReason.CHANGE_OF_MIND, null, 9L, "[]");
        ReflectionTestUtils.setField(orderReturn, "id", id);
        return orderReturn;
    }
}
