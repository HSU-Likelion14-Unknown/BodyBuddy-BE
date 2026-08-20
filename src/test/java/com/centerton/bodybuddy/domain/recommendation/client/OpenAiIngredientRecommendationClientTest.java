package com.centerton.bodybuddy.domain.recommendation.client;

import com.centerton.bodybuddy.domain.analysis.config.OpenAiProperties;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiIngredientRecommendationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiIngredientRecommendationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.test")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiIngredientRecommendationClient(
                builder.build(),
                objectMapper,
                new OpenAiProperties()
        );
    }

    @Test
    void requestsNutritionPerServingWithStructuredOutput() {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> requestBody.set(bodyOf(request)))
                .andRespond(withSuccess(openAiResponse(), MediaType.APPLICATION_JSON));

        assertThat(client.recommend(input())).isEmpty();

        JsonNode body = requestBody.get();
        assertThat(body.path("instructions").stringValue())
                .contains("1인분")
                .doesNotContain("100g");
        JsonNode candidateProperties = body.path("text").path("format").path("schema")
                .path("properties").path("candidates").path("items").path("properties");
        assertThat(candidateProperties.has("nutritionPerServing")).isTrue();
        assertThat(candidateProperties.has("nutritionPer100g")).isFalse();
        server.verify();
    }

    @Test
    void requestsDishCompletionForKnownIngredientWithoutReplacingItsNutrition() {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> requestBody.set(bodyOf(request)))
                .andRespond(withSuccess(dishOpenAiResponse(), MediaType.APPLICATION_JSON));

        List<AiDishCandidate> result = client.recommendDishes(
                new AiDishRecommendationInput(
                        "시금치",
                        List.of(),
                        List.of("가지")
                )
        );

        assertThat(result)
                .extracting(AiDishCandidate::dishName)
                .containsExactly("시금치나물", "시금치국");
        JsonNode body = requestBody.get();
        assertThat(body.path("instructions").stringValue())
                .contains("입력된 원재료", "2~3개");
        assertThat(body.path("input").toString()).contains("시금치", "가지");
        JsonNode properties = body.path("text").path("format").path("schema")
                .path("properties");
        assertThat(properties.has("dishes")).isTrue();
        assertThat(properties.has("nutritionPerServing")).isFalse();
        server.verify();
    }

    private AiIngredientRecommendationInput input() {
        KdrReferenceValues reference = new KdrReferenceValues(
                value("65"), value("30"), value("800"), value("8"),
                value("3500"), value("800"), value("100")
        );
        NutritionGapResult gap = new NutritionGapResult(
                reference,
                NutritionValues.builder().build(),
                Map.of(TargetNutrient.IRON, new NutrientGap(
                        value("8"), BigDecimal.ZERO, value("8"), BigDecimal.ONE
                )),
                TargetNutrient.IRON
        );
        return new AiIngredientRecommendationInput(
                TargetNutrient.IRON,
                reference,
                gap,
                1,
                value("20"),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private JsonNode bodyOf(org.springframework.http.HttpRequest request) {
        return objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsBytes());
    }

    private String openAiResponse() {
        return objectMapper.writeValueAsString(Map.of(
                "id", "resp_recommendation",
                "status", "completed",
                "model", "gpt-5-mini-2025-08-07",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", "{\"candidates\":[]}"
                        ))
                ))
        ));
    }

    private String dishOpenAiResponse() {
        return objectMapper.writeValueAsString(Map.of(
                "id", "resp_dishes",
                "status", "completed",
                "model", "gpt-5-mini-2025-08-07",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", "{\"dishes\":["
                                        + "{\"dishName\":\"시금치나물\","
                                        + "\"ingredientNames\":[\"시금치\"],"
                                        + "\"allergenCodes\":[]},"
                                        + "{\"dishName\":\"시금치국\","
                                        + "\"ingredientNames\":[\"시금치\",\"된장\"],"
                                        + "\"allergenCodes\":[\"SOY\"]}]}"
                        ))
                ))
        ));
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
