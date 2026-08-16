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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiFoodRecognitionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiProperties properties = new OpenAiProperties();

    private MockRestServiceServer server;
    private OpenAiFoodRecognitionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.test")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiFoodRecognitionClient(
                builder.build(),
                objectMapper,
                properties
        );
    }

    @Test
    void recognizesTextWithStructuredOutputAndMetadata() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> requestBody.set(bodyOf(request)))
                .andRespond(withSuccess(
                        openAiResponse("""
                                {"candidates":[
                                  {"foodName":"두부","confidence":0.96},
                                  {"foodName":"김치","confidence":0.82}
                                ]}
                                """),
                        MediaType.APPLICATION_JSON
                ));

        FoodRecognitionResponse result = client.recognize(
                FoodRecognitionInput.text("두부와 김치")
        );

        assertThat(result.provider()).isEqualTo("OPENAI");
        assertThat(result.model()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(result.promptVersion()).isEqualTo("food-recognition-v1");
        assertThat(result.providerResponseId()).isEqualTo("resp_123");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(24);
        assertThat(result.candidates()).containsExactly(
                new FoodRecognitionCandidate("두부", new BigDecimal("0.96")),
                new FoodRecognitionCandidate("김치", new BigDecimal("0.82"))
        );

        JsonNode body = requestBody.get();
        assertThat(body.path("model").stringValue())
                .isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("text").path("format").path("type").stringValue())
                .isEqualTo("json_schema");
        assertThat(body.path("text").path("format").path("strict").asBoolean())
                .isTrue();
        assertThat(body.path("input").get(0).path("content").get(0).path("text").stringValue())
                .contains("두부와 김치");
        server.verify();
    }

    @Test
    void sendsImageAsBase64DataUrl() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(request -> requestBody.set(bodyOf(request)))
                .andRespond(withSuccess(
                        openAiResponse("""
                                {"candidates":[{"foodName":"김치찌개","confidence":0.91}]}
                                """),
                        MediaType.APPLICATION_JSON
                ));

        client.recognize(FoodRecognitionInput.image(
                new byte[]{1, 2, 3},
                "image/png"
        ));

        JsonNode image = requestBody.get()
                .path("input").get(0)
                .path("content").get(1);
        assertThat(image.path("type").stringValue()).isEqualTo("input_image");
        assertThat(image.path("image_url").stringValue())
                .isEqualTo("data:image/png;base64,AQID");
        assertThat(image.path("detail").stringValue()).isEqualTo("auto");
        server.verify();
    }

    @Test
    void rejectsInvalidStructuredCandidate() throws Exception {
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(
                        openAiResponse("""
                                {"candidates":[{"foodName":"김치찌개","confidence":1.2}]}
                                """),
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.recognize(
                FoodRecognitionInput.text("김치찌개")
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.AI_BAD_RESPONSE)
        );
        server.verify();
    }

    @Test
    void mapsProviderHttpFailureToUnavailable() {
        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("x-request-id", "req_rate_limited"));

        assertThatThrownBy(() -> client.recognize(
                FoodRecognitionInput.text("김치찌개")
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.AI_SERVICE_UNAVAILABLE)
        );
        server.verify();
    }

    @Test
    void rejectsUnsupportedImageBeforeCallingProvider() {
        assertThatThrownBy(() -> client.recognize(
                FoodRecognitionInput.image(new byte[]{1}, "image/gif")
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.AI_BAD_RESPONSE)
        );
        server.verify();
    }

    private JsonNode bodyOf(org.springframework.http.HttpRequest request) {
        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        return objectMapper.readTree(mockRequest.getBodyAsBytes());
    }

    private String openAiResponse(String outputText) {
        return objectMapper.writeValueAsString(Map.of(
                "id", "resp_123",
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
                        "input_tokens", 120,
                        "output_tokens", 24
                )
        ));
    }
}
