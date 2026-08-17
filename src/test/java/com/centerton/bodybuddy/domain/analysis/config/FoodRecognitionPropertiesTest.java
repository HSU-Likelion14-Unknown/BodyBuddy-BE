package com.centerton.bodybuddy.domain.analysis.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FoodRecognitionPropertiesTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void defaultsMinimumConfidenceToPointSix() {
        FoodRecognitionProperties properties = new FoodRecognitionProperties();

        assertThat(properties.getMinimumConfidence()).isEqualByComparingTo("0.60");
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsMinimumConfidenceOutsideZeroToOne() {
        FoodRecognitionProperties properties = new FoodRecognitionProperties();
        properties.setMinimumConfidence(new BigDecimal("1.01"));

        assertThat(validator.validate(properties)).isNotEmpty();
    }
}
