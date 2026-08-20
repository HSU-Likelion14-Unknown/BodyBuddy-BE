package com.centerton.bodybuddy.domain.meal.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MealItemInputReqTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsServingUnitAndRejectsWeightUnit() {
        MealItemInputReq servingRequest = new MealItemInputReq(
                null, "두부", BigDecimal.ONE, "인분"
        );
        MealItemInputReq weightRequest = new MealItemInputReq(
                null, "두부", new BigDecimal("100"), "g"
        );

        assertThat(validator.validate(servingRequest)).isEmpty();
        assertThat(validator.validate(weightRequest))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("unit"));
    }
}
