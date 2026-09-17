package com.jangingmall.backend.content.domain;

import java.util.List;
import java.util.Map;

public sealed interface ReactNode permits ReactNode.ElementNode, ReactNode.TextNode {

    String id();

    record ElementNode(
        String id,
        AllowedTag tag,
        Map<String, Object> props,
        List<ReactNode> children
    ) implements ReactNode {}

    record TextNode(
        String id,
        String value,
        List<Map<String, Object>> marks
    ) implements ReactNode {}
}
