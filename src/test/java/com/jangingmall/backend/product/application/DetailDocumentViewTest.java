package com.jangingmall.backend.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DetailDocumentViewTest {

    private static final String DOCUMENT = """
        {"schemaVersion":"2.0","canvasWidth":774,"root":[{"id":"s1","type":"element","tag":"section","children":[
          {"id":"c1","type":"element","tag":"article","children":[
            {"id":"c1t","type":"element","tag":"h3","children":[{"id":"c1tt","type":"text","value":"만든 방식"}]},
            {"id":"c1b","type":"element","tag":"p","children":[{"id":"c1bt","type":"text","value":"손으로 빚었습니다."}]}]},
          {"id":"c2","type":"element","tag":"article","children":[
            {"id":"c2t","type":"element","tag":"h3","children":[{"id":"c2tt","type":"text","value":"쓰임새"}]},
            {"id":"c2b","type":"element","tag":"p","children":[{"id":"c2bt","type":"text","value":"차를 우리기 좋습니다."}]}]}]}]}
        """;

    @Test
    @DisplayName("상세 JSON 을 읽고 article 카드에서 특징 제목·설명을 뽑는다")
    void parsesDocumentAndExtractsFeatures() {
        var document = DetailDocumentView.parse(DOCUMENT);

        assertThat(document).isNotNull();
        assertThat(DetailDocumentView.features(document)).containsExactly(
            new ProductResponse.FeatureView("만든 방식", "손으로 빚었습니다."),
            new ProductResponse.FeatureView("쓰임새", "차를 우리기 좋습니다."));
    }

    @Test
    @DisplayName("비어 있거나 깨졌거나 root 가 없는 JSON 은 없는 것으로 본다")
    void ignoresMissingOrBrokenDocuments() {
        assertThat(DetailDocumentView.parse(null)).isNull();
        assertThat(DetailDocumentView.parse(" ")).isNull();
        assertThat(DetailDocumentView.parse("{broken")).isNull();
        assertThat(DetailDocumentView.parse("{\"a\":1}")).isNull();
        assertThat(DetailDocumentView.features(null)).isEmpty();
    }
}
