package com.centerton.bodybuddy.domain.analysis.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "bodybuddy.openai")
public class OpenAiProperties {

    @NotBlank
    private String apiKey;

    @NotNull
    private URI baseUrl = URI.create("https://api.openai.com");

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(45);

    @Valid
    @NotNull
    private FoodRecognition foodRecognition = new FoodRecognition();

    @Valid
    @NotNull
    private FoodNutritionEstimation foodNutritionEstimation = new FoodNutritionEstimation();

    @Getter
    @Setter
    public static class FoodRecognition {

        @NotBlank
        private String model = "gpt-5-mini-2025-08-07";

        @NotBlank
        private String promptVersion = "food-recognition-v1";

        @Min(1)
        @Max(4096)
        private int maxOutputTokens = 600;

        @Pattern(regexp = "auto|low|high")
        private String imageDetail = "auto";
    }

    @Getter
    @Setter
    public static class FoodNutritionEstimation {

        @NotBlank
        private String model = "gpt-5-mini-2025-08-07";

        @NotBlank
        private String promptVersion = "food-nutrition-estimation-v1";

        @Min(1)
        @Max(4096)
        private int maxOutputTokens = 500;
    }
}
