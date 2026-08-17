package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationQueryService {

    private final RecommendationRepository recommendationRepository;
    private final RecommendationResponseAssembler responseAssembler;

    @Transactional(readOnly = true)
    public RecommendationRes findByMealId(String mealId) {
        return recommendationRepository.findByMealMealId(mealId)
                .map(responseAssembler::assemble)
                .orElse(null);
    }
}
