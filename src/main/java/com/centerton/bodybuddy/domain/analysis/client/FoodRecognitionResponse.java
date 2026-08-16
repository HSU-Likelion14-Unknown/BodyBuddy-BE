package com.centerton.bodybuddy.domain.analysis.client;

import java.util.List;

public record FoodRecognitionResponse(
        List<FoodRecognitionCandidate> candidates,
        String provider,
        String model,
        String promptVersion,
        String providerResponseId,
        Integer inputTokens,
        Integer outputTokens
) {
}
