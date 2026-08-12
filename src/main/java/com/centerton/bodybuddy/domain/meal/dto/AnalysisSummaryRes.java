package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisSummaryRes {
    private String analysisRunId;
    private AnalysisStatus status;
}
