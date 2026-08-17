package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.user.entity.Gender;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 2025 Dietary Reference Intakes for Koreans (Ministry of Health and Welfare,
 * The Korean Nutrition Society). RNI is used when available; AI is used for
 * dietary fiber and potassium.
 */
@Component
public class KdrReferenceProvider {

    private static final List<AgeBand> MALE = List.of(
            band(1, 2, 20, 10, 450, 6, 1500, 250, 40),
            band(3, 5, 25, 15, 550, 6, 2300, 300, 45),
            band(6, 8, 35, 20, 700, 7, 2600, 450, 50),
            band(9, 11, 50, 20, 800, 8, 3100, 600, 70),
            band(12, 14, 60, 25, 950, 11, 3500, 750, 90),
            band(15, 18, 65, 25, 800, 11, 3500, 850, 100),
            band(19, 29, 65, 30, 800, 8, 3500, 800, 100),
            band(30, 49, 65, 30, 800, 8, 3500, 800, 100),
            band(50, 64, 60, 30, 800, 8, 3500, 750, 100),
            band(65, 74, 60, 30, 800, 8, 3500, 700, 100),
            band(75, Integer.MAX_VALUE, 60, 30, 800, 7, 3500, 700, 100)
    );

    private static final List<AgeBand> FEMALE = List.of(
            band(1, 2, 20, 10, 450, 6, 1500, 250, 40),
            band(3, 5, 25, 15, 550, 6, 2300, 300, 45),
            band(6, 8, 35, 15, 700, 7, 2600, 400, 50),
            band(9, 11, 45, 20, 800, 8, 3100, 550, 70),
            band(12, 14, 55, 20, 850, 13, 3500, 650, 90),
            band(15, 18, 55, 20, 700, 12, 3500, 650, 100),
            band(19, 29, 55, 20, 650, 12, 3500, 650, 100),
            band(30, 49, 50, 20, 650, 12, 3500, 650, 100),
            band(50, 64, 50, 25, 750, 7, 3500, 600, 100),
            band(65, 74, 50, 25, 750, 6, 3500, 600, 100),
            band(75, Integer.MAX_VALUE, 50, 25, 750, 6, 3500, 600, 100)
    );

    public KdrReferenceValues referenceFor(User user, LocalDate referenceDate) {
        if (user == null || user.getBirthYear() == null || user.getGender() == null) {
            throw new BaseException(ErrorResponseCode.KDRI_PROFILE_REQUIRED);
        }
        int age = referenceDate.getYear() - user.getBirthYear();
        if (age < 1) {
            throw new BaseException(ErrorResponseCode.KDRI_PROFILE_REQUIRED);
        }
        return switch (user.getGender()) {
            case MALE -> find(MALE, age);
            case FEMALE -> find(FEMALE, age);
            case PREFER_NOT_TO_SAY -> KdrReferenceValues.minimum(
                    find(MALE, age),
                    find(FEMALE, age)
            );
        };
    }

    private KdrReferenceValues find(List<AgeBand> bands, int age) {
        return bands.stream()
                .filter(band -> band.includes(age))
                .findFirst()
                .map(AgeBand::values)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.KDRI_PROFILE_REQUIRED));
    }

    private static AgeBand band(int minAge, int maxAge, int proteinG, int fiberG,
                                int calciumMg, int ironMg, int potassiumMg,
                                int vitaminAMcgRae, int vitaminCMg) {
        return new AgeBand(
                minAge,
                maxAge,
                new KdrReferenceValues(
                        decimal(proteinG),
                        decimal(fiberG),
                        decimal(calciumMg),
                        decimal(ironMg),
                        decimal(potassiumMg),
                        decimal(vitaminAMcgRae),
                        decimal(vitaminCMg)
                )
        );
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value);
    }

    private record AgeBand(int minAge, int maxAge, KdrReferenceValues values) {
        private boolean includes(int age) {
            return age >= minAge && age <= maxAge;
        }
    }
}
