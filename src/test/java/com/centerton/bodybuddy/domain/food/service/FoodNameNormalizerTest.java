package com.centerton.bodybuddy.domain.food.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FoodNameNormalizerTest {

    @Test
    void normalizesCatalogNameLikeTheImportPipeline() {
        assertThat(FoodNameNormalizer.normalizeCatalogName("  두부 ,  부침용  "))
                .isEqualTo("두부,부침용");
    }

    @Test
    void normalizesAliasLookupAcrossSpacingAndPunctuation() {
        assertThat(FoodNameNormalizer.normalizeLookupName("망둑어 (풀망둑)"))
                .isEqualTo("망둑어풀망둑");
    }

    @Test
    void appliesNfkcUnicodeNormalization() {
        assertThat(FoodNameNormalizer.normalizeLookupName("ＡＢＣ 두부"))
                .isEqualTo("abc두부");
    }
}
