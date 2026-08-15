package com.centerton.bodybuddy.domain.food.service;

import java.text.Normalizer;
import java.util.Locale;

public final class FoodNameNormalizer {

    private FoodNameNormalizer() {
    }

    public static String normalizeCatalogName(String value) {
        String normalized = normalizeUnicode(value).trim();
        normalized = normalized.replaceAll("\\s*,\\s*", ",");
        return normalized.replaceAll("\\s+", " ");
    }

    public static String normalizeLookupName(String value) {
        return normalizeUnicode(value)
                .replaceAll("[\\s,./·ㆍ_()\\[\\]{}\\-]+", "");
    }

    private static String normalizeUnicode(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
