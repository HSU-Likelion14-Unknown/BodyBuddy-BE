package com.centerton.bodybuddy.domain.analysis.repository;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, String> {
    Optional<AiAnalysisRun> findFirstByMealMealIdOrderByStartedAtDesc(String mealId);

    Optional<AiAnalysisRun> findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
            String mealId,
            AnalysisStatus status
    );

    void deleteAllByMealMealId(String mealId);
}
