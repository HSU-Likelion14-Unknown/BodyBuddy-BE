package com.centerton.bodybuddy.domain.analysis.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MealRecognitionEventHandlerTest {

    @Mock
    private MealRecognitionProcessor recognitionProcessor;

    @Test
    void delegatesCommittedRecognitionRequestToProcessor() {
        MealRecognitionEventHandler handler = new MealRecognitionEventHandler(recognitionProcessor);

        handler.handle(new MealRecognitionRequestedEvent("meal-id", "run-id"));

        verify(recognitionProcessor).process("meal-id", "run-id");
    }
}
