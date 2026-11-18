package com.example.nail_salon_appt_scheduler;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@Conditional(GeminiConfiguration.ApiKeyPresent.class)
class GeminiConfiguration {
    private static final String API_KEY = "spring.ai.google.genai.api-key";

    @Bean
    Client geminiClient(@Value("${" + API_KEY + "}") String apiKey,
                        @Value("${salon.ai.timeout-ms}") int timeoutMs) {
        return Client.builder().apiKey(apiKey).httpOptions(requestOptions(timeoutMs).build()).build();
    }

    HttpOptions.Builder requestOptions(int timeoutMs) {
        return HttpOptions.builder().timeout(timeoutMs)
                .retryOptions(HttpRetryOptions.builder().attempts(2)
                        .initialDelay(0.3).maxDelay(1.0)
                        .httpStatusCodes(408, 429, 500, 502, 503, 504));
    }

    @Bean
    ChatClient salonChatClient(Client client, @Value("${spring.ai.google.genai.chat.model}") String model) {
        var toolManager = ToolCallingManager.builder()
                .resolutionFallbackEnabled(false).maxTotalToolCalls(6).maxCallsPerTool(3).build();
        var chatModel = GoogleGenAiChatModel.builder().genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().model(model)
                        .thinkingLevel(GoogleGenAiThinkingLevel.LOW).maxOutputTokens(1024).build())
                .toolCallingManager(toolManager).retryTemplate(sdkOnlyRetries()).build();
        return ChatClient.builder(chatModel, ObservationRegistry.NOOP, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(toolManager)).build();
    }

    @Bean
    EmbeddingModel salonEmbeddingModel(Client client,
                                      @Value("${spring.ai.google.genai.embedding.text.model}") String model) {
        var connection = GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(client).build();
        var options = GoogleGenAiTextEmbeddingOptions.builder().model(model)
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.SEMANTIC_SIMILARITY).build();
        return new GoogleGenAiTextEmbeddingModel(connection, options, sdkOnlyRetries());
    }

    private RetryTemplate sdkOnlyRetries() {
        return new RetryTemplate(RetryPolicy.builder().maxRetries(0).build());
    }

    static class ApiKeyPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(API_KEY));
        }
    }
}
