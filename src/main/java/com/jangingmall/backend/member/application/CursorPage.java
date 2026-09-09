package com.jangingmall.backend.member.application;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext, long totalCount) {
    public CursorPage {
        items = List.copyOf(items);
    }
}
