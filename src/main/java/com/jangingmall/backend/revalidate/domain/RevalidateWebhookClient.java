package com.jangingmall.backend.revalidate.domain;

public interface RevalidateWebhookClient {
    void send(RevalidateEvent event);
}
