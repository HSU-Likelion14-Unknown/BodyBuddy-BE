package com.centerton.bodybuddy.domain.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MealRecognitionEventHandler {

    private final MealRecognitionProcessor recognitionProcessor;

    @Async("recognitionTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MealRecognitionRequestedEvent event) {
        recognitionProcessor.process(event.mealId(), event.analysisRunId());
    }
}
