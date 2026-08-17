package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.RecognizedFood;
import lombok.Builder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Getter
@Builder
public class RecognitionCandidatesRes {
    private String mealId;
    private List<RecognitionCandidateRes> candidates;

    public static RecognitionCandidatesRes from(AiAnalysisRun run) {
        List<RecognizedFood> foods = run.getNormalizedResponse().getFoods();
        List<RecognitionCandidateRes> candidates = IntStream.range(0, foods.size())
                .mapToObj(index -> RecognitionCandidateRes.from(
                        foods.get(index),
                        candidateId(run.getAnalysisRunId(), index),
                        index
                ))
                .toList();
        return RecognitionCandidatesRes.builder()
                .mealId(run.getMeal().getMealId())
                .candidates(candidates)
                .build();
    }

    private static String candidateId(String runId, int index) {
        return UUID.nameUUIDFromBytes(
                (runId + ":" + index).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
