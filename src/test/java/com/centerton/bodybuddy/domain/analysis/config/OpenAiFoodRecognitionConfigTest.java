package com.centerton.bodybuddy.domain.analysis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiFoodRecognitionConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenAiFoodRecognitionConfig.class)
            .withPropertyValues(
                    "bodybuddy.food-recognition.provider=openai",
                    "bodybuddy.openai.api-key=test-key"
            );

    @Test
    void createsOpenAiRestClientWithoutAutoConfiguredBuilder() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OpenAiProperties.class);
            assertThat(context).hasSingleBean(RestClient.class);
        });
    }
}
