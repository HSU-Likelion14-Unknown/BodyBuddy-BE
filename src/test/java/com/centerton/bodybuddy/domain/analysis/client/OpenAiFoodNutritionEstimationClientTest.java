package com.centerton.bodybuddy.domain.analysis.client;

import com.centerton.bodybuddy.domain.analysis.config.OpenAiProperties;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiFoodNutritionEstimationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiProperties properties = new OpenAiProperties();

    private MockRestServiceServer server;
    private OpenAiFoodNutritionEstimationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.test")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiFoodNutritionEstimationClient(
                builder.build(),
                objectMapper,
                properties
        );
    }

    @Test
    void estimatesConfirmedAmountWithStructuredOutputAndMetadata() {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> requestBody.set(bodyOf(request)))
                .andRespond(withSuccess(
                        openAiResponse(nutritionJson()),
                        MediaType.APPLICATION_JSON
                ));

        FoodNutritionEstimationResponse result = client.estimate(
                new FoodNutritionEstimationInput(
                        "짜장면",
                        new BigDecimal("1.5"),
                        "인분"
                )
        ).orElseThrow();

        assertThat(result.nutrition().getCaloriesKcal()).isEqualByComparingTo("975");
        assertThat(result.nutrition().getCarbohydrateG()).isEqualByComparingTo("165");
        assertThat(result.confidence()).isEqualByComparingTo("0.74");
        assertThat(result.provider()).isEqualTo("OPENAI");
        assertThat(result.model()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(result.promptVersion()).isEqualTo("food-nutrition-estimation-v2");
        assertThat(result.providerResponseId()).isEqualTo("resp_nutrition");
        assertThat(result.inputTokens()).isEqualTo(88);
        assertThat(result.outputTokens()).isEqualTo(42);

        JsonNode body = requestBody.get();
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("text").path("format").path("type").stringValue())
                .isEqualTo("json_schema");
        assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
        String inputText = body.path("input").get(0)
                .path("content").get(0)
                .path("text").stringValue();
        assertThat(inputText).contains("짜장면", "1.5", "인분");
        assertThat(body.path("instructions").stringValue())
                .contains("인분")
                .doesNotContain("100g");
        server.verify();
    }

    @Test
    void rejectsNegativeNutritionValue() {
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(
                        openAiResponse(nutritionJson().replace("975", "-1")),
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.estimate(new FoodNutritionEstimationInput(
                "짜장면",
                BigDecimal.ONE,
                "인분"
        ))).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.AI_BAD_RESPONSE));
        server.verify();
    }

    @Test
    void mapsProviderFailureToUnavailable() {
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.estimate(new FoodNutritionEstimationInput(
                "짜장면",
                BigDecimal.ONE,
                "인분"
        ))).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void rejectsWeightUnitBeforeCallingProvider() {
        assertThatThrownBy(() -> client.estimate(new FoodNutritionEstimationInput(
                "짜장면",
                new BigDecimal("100"),
                "g"
        ))).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.AI_BAD_RESPONSE));
        server.verify();
    }

    private JsonNode bodyOf(org.springframework.http.HttpRequest request) {
        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        return objectMapper.readTree(mockRequest.getBodyAsBytes());
    }

    private String openAiResponse(String outputText) {
        return objectMapper.writeValueAsString(Map.of(
                "id", "resp_nutrition",
                "status", "completed",
                "model", "gpt-5-mini-2025-08-07",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", outputText
                        ))
                )),
                "usage", Map.of(
                        "input_tokens", 88,
                        "output_tokens", 42
                )
        ));
    }

    private String nutritionJson() {
        return """
                {
                  "caloriesKcal":975,
                  "carbohydrateG":165,
                  "proteinG":30,
                  "fatG":22.5,
                  "fiberG":9,
                  "sodiumMg":2700,
                  "calciumMg":120,
                  "ironMg":4.5,
                  "potassiumMg":825,
                  "vitaminAMcgRae":180,
                  "vitaminCMg":18,
                  "confidence":0.74
                }
                """;
    }
}
