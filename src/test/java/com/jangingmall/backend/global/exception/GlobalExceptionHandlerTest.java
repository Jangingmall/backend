package com.jangingmall.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("비즈니스 예외 로그에 메서드·URI·상태코드가 포함된다")
    void businessExceptionLogIncludesRequest() {
        var request = new MockHttpServletRequest("POST", "/api/member/login");
        request.setQueryString("email=secret@example.com");

        var response = handler.handleBusiness(new DomainException(ErrorCode.TOO_MANY_REQUESTS, "잠김"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("TOO_MANY_REQUESTS", "POST", "/api/member/login", "429");
        assertThat(message).doesNotContain("secret@example.com");
    }

    @Test
    @DisplayName("접근 거부 로그에 메서드·URI·상태코드가 포함된다")
    void accessDeniedLogIncludesRequest() {
        var request = new MockHttpServletRequest("GET", "/api/admin/x");

        handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(appender.list.get(0).getFormattedMessage()).contains("GET", "/api/admin/x", "403");
    }
}
