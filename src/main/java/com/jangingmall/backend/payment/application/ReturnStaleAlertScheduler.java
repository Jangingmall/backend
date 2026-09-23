package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.OrderReturnRepository;
import com.jangingmall.backend.payment.domain.ReturnStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReturnStaleAlertScheduler {

    private static final long STALE_THRESHOLD_HOURS = 24;

    private final OrderReturnRepository returns;
    private final OrderNotificationPublisher notifications;

    @Scheduled(fixedDelayString = "${return.stale-scan-millis:3600000}")
    @Transactional(readOnly = true)
    public void alertStaleReturns() {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_HOURS, ChronoUnit.HOURS);
        List<OrderReturn> staleReturns = returns.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ReturnStatus.REQUESTED, cutoff);
        staleReturns.forEach(notifications::returnStaleAlert);
    }
}
