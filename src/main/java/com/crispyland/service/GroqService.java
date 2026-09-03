package com.crispyland.service;

import com.crispyland.dto.BenefitItem;
import com.crispyland.dto.ChatMessage;
import com.crispyland.dto.CompareResponse;
import com.crispyland.dto.SaladResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class GroqService {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL   = "openai/gpt-oss-20b";

    // Section 2 schema: summary (always) + optional list of items
    private static final String BENEFITS_SCHEMA = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["summary", "items"],
          "properties": {
            "summary": { "type": "string" },
            "items": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["title", "detail"],
                "properties": {
                  "title":  { "type": "string" },
                  "detail": { "type": "string" }
                }
              }
            }
          }
        }
        """;

    // Section 3 schema: every turn — message + done + nullable recipe
    private static final String SALAD_SCHEMA = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["message", "done", "recipe"],
          "properties": {
            "message": { "type": "string" },
            "done":    { "type": "boolean" },
            "recipe": {
              "type": ["object", "null"],
              "additionalProperties": false,
              "required": ["name", "ingredients", "steps"],
              "properties": {
                "name": { "type": "string" },
                "ingredients": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "steps": {
                  "type": "array",
                  "items": { "type": "string" }
                }
              }
            }
          }
        }
        """;

    @Value("${GROQ_API_KEY:}")
    private String apiKeyProperty;

    private String apiKey() {
        if (apiKeyProperty != null && !apiKeyProperty.isBlank()) return apiKeyProperty;
        String env = System.getenv("GROQ_API_KEY");
        return env != null ? env : "";
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper   = new ObjectMapper();

    // ----------------------------------------------------------------
    // Section 1 + 2: same prompt, unconstrained vs constrained (concurrent)
    // ----------------------------------------------------------------
    public CompareResponse compare(String prompt, double temperature, String length, int maxTokens) throws Exception {
        String lengthHint = switch (length) {
            case "short" -> "Be very concise. Respond in 2-3 sentences maximum.";
            case "long"  -> "Provide a comprehensive, detailed response with examples.";
            default      -> "Provide a moderately detailed response.";
        };

        CompletableFuture<String>           unstructuredFuture = sendUnstructuredAsync(prompt, temperature, lengthHint, maxTokens);
        CompletableFuture<List<BenefitItem>> benefitsFuture    = sendStructuredAsync(prompt, temperature, lengthHint, maxTokens);

        return new CompareResponse(unstructuredFuture.get(), benefitsFuture.get());
    }

    private CompletableFuture<String> sendUnstructuredAsync(String prompt, double temperature, String lengthHint, int maxTokens) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", lengthHint);
            messages.addObject().put("role", "user").put("content", prompt);

            return sendAsync(body).thenApply(resp ->
                resp == null ? "Error: API call failed." : resp.at("/choices/0/message/content").asText()
            );
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<List<BenefitItem>> sendStructuredAsync(String prompt, double temperature, String lengthHint, int maxTokens) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system")
                    .put("content", lengthHint
                        + " Always set 'summary' to a concise direct answer to the prompt."
                        + " If the answer is a list, also populate 'items' (each with title and detail)."
                        + " If the answer is not a list, leave 'items' as an empty array.");
            messages.addObject().put("role", "user").put("content", prompt);

            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", "benefits_list");
            jsonSchema.put("strict", true);
            jsonSchema.set("schema", mapper.readTree(BENEFITS_SCHEMA));

            return sendAsync(body).thenApply(resp -> {
                if (resp == null) return List.of();
                try {
                    String rawJson = resp.at("/choices/0/message/content").asText();
                    JsonNode data = mapper.readTree(rawJson);
                    // summary is stored as first synthetic item so CompareResponse stays simple
                    List<BenefitItem> items = new ArrayList<>();
                    items.add(new BenefitItem("__summary__", data.get("summary").asText()));
                    for (JsonNode b : data.get("items")) {
                        items.add(new BenefitItem(b.get("title").asText(), b.get("detail").asText()));
                    }
                    return items;
                } catch (Exception e) {
                    return List.of();
                }
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ----------------------------------------------------------------
    // Section 3: multi-turn salad assistant, strict json_schema every turn
    // ----------------------------------------------------------------
    public SaladResponse saladTurn(List<ChatMessage> messages, int maxTokens) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", MODEL);
        body.put("max_tokens", maxTokens);

        ArrayNode msgs = body.putArray("messages");
        msgs.addObject()
            .put("role", "system")
            .put("content",
                "You are a salad recipe assistant. Ask ONE short question at a time to gather: "
                + "kind of salad, dietary restrictions, and ingredients on hand. "
                + "Ask at most 3 questions total. "
                + "While still gathering: set done=false, put your question in message, set recipe=null. "
                + "Once you have enough info: set done=true, put a one-line intro in message, "
                + "and fill recipe with name, an ingredients array, and a steps array.");

        for (ChatMessage msg : messages) {
            msgs.addObject().put("role", msg.getRole()).put("content", msg.getContent());
        }

        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "salad_turn");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", mapper.readTree(SALAD_SCHEMA));

        JsonNode resp = sendSync(body);
        String   rawJson   = resp.at("/choices/0/message/content").asText();
        JsonNode turnData  = mapper.readTree(rawJson);

        return new SaladResponse(
            turnData.get("message").asText(),
            turnData.get("done").asBoolean(),
            turnData.get("recipe")
        );
    }

    // ----------------------------------------------------------------
    // HTTP helpers
    // ----------------------------------------------------------------
    private CompletableFuture<JsonNode> sendAsync(ObjectNode body) {
        try {
            HttpRequest request = buildRequest(body);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("Groq API error " + response.statusCode() + ": " + response.body());
                        return null;
                    }
                    try { return mapper.readTree(response.body()); } catch (Exception e) { return null; }
                });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private JsonNode sendSync(ObjectNode body) throws Exception {
        HttpRequest request = buildRequest(body);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.err.println("Groq API error " + response.statusCode() + ": " + response.body());
            throw new RuntimeException("Groq API " + response.statusCode() + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private HttpRequest buildRequest(ObjectNode body) throws Exception {
        return HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();
    }
}
