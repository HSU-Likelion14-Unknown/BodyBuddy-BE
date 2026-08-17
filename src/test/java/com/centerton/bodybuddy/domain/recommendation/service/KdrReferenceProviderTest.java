package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.user.entity.Gender;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KdrReferenceProviderTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 16);

    private final KdrReferenceProvider provider = new KdrReferenceProvider();

    @ParameterizedTest
    @CsvSource({
            "MALE,1,20,10,450,6,1500,250,40",
            "MALE,3,25,15,550,6,2300,300,45",
            "MALE,6,35,20,700,7,2600,450,50",
            "MALE,9,50,20,800,8,3100,600,70",
            "MALE,12,60,25,950,11,3500,750,90",
            "MALE,15,65,25,800,11,3500,850,100",
            "MALE,19,65,30,800,8,3500,800,100",
            "MALE,30,65,30,800,8,3500,800,100",
            "MALE,50,60,30,800,8,3500,750,100",
            "MALE,65,60,30,800,8,3500,700,100",
            "MALE,75,60,30,800,7,3500,700,100",
            "FEMALE,1,20,10,450,6,1500,250,40",
            "FEMALE,3,25,15,550,6,2300,300,45",
            "FEMALE,6,35,15,700,7,2600,400,50",
            "FEMALE,9,45,20,800,8,3100,550,70",
            "FEMALE,12,55,20,850,13,3500,650,90",
            "FEMALE,15,55,20,700,12,3500,650,100",
            "FEMALE,19,55,20,650,12,3500,650,100",
            "FEMALE,30,50,20,650,12,3500,650,100",
            "FEMALE,50,50,25,750,7,3500,600,100",
            "FEMALE,65,50,25,750,6,3500,600,100",
            "FEMALE,75,50,25,750,6,3500,600,100"
    })
    void returns2025KdrReferenceAtEachAgeBoundary(
            Gender gender,
            int age,
            String protein,
            String fiber,
            String calcium,
            String iron,
            String potassium,
            String vitaminA,
            String vitaminC
    ) {
        KdrReferenceValues result = provider.referenceFor(user(age, gender), REFERENCE_DATE);

        assertThat(result.proteinG()).isEqualByComparingTo(protein);
        assertThat(result.fiberG()).isEqualByComparingTo(fiber);
        assertThat(result.calciumMg()).isEqualByComparingTo(calcium);
        assertThat(result.ironMg()).isEqualByComparingTo(iron);
        assertThat(result.potassiumMg()).isEqualByComparingTo(potassium);
        assertThat(result.vitaminAMcgRae()).isEqualByComparingTo(vitaminA);
        assertThat(result.vitaminCMg()).isEqualByComparingTo(vitaminC);
    }

    @Test
    void usesLowerSexSpecificValuesWhenGenderIsNotDisclosed() {
        KdrReferenceValues result = provider.referenceFor(
                user(19, Gender.PREFER_NOT_TO_SAY),
                REFERENCE_DATE
        );

        assertThat(result.proteinG()).isEqualByComparingTo("55");
        assertThat(result.fiberG()).isEqualByComparingTo("20");
        assertThat(result.calciumMg()).isEqualByComparingTo("650");
        assertThat(result.ironMg()).isEqualByComparingTo("8");
        assertThat(result.vitaminAMcgRae()).isEqualByComparingTo("650");
    }

    @Test
    void rejectsProfileWithoutBirthYear() {
        User user = User.builder().userId("user-id").gender(Gender.MALE).build();

        assertThatThrownBy(() -> provider.referenceFor(user, REFERENCE_DATE))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.KDRI_PROFILE_REQUIRED));
    }

    private User user(int age, Gender gender) {
        return User.builder()
                .userId("user-id")
                .birthYear(REFERENCE_DATE.getYear() - age)
                .gender(gender)
                .build();
    }
}
