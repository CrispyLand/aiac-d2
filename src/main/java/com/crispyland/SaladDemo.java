package com.crispyland;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class SaladDemo {

    private static final String API_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL     = "openai/gpt-oss-20b";
    private static final int    MAX_TURNS = 6;

    // One schema for every turn: message + done + nullable recipe.
    // Strict-mode rules: additionalProperties:false, every property in required,
    // recipe nullable via "type":["object","null"] but still listed in required.
    private static final String SCHEMA = """
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

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("GROQ_API_KEY is not set.");
            return;
        }

        ObjectMapper mapper  = new ObjectMapper();
        HttpClient   client  = HttpClient.newHttpClient();
        Scanner      scanner = new Scanner(System.in);

        ArrayNode messages = mapper.createArrayNode();

        messages.addObject()
            .put("role", "system")
            .put("content",
                "You are a salad recipe assistant. Ask ONE short question at a time to gather: "
                + "kind of salad, dietary restrictions, and ingredients on hand. "
                + "Ask at most 3 questions total. "
                + "While still gathering: set done=false, put your question in message, set recipe=null. "
                + "Once you have enough info: set done=true, put a one-line intro in message, "
                + "and fill recipe with name, an ingredients array, and a steps array.");

        String initialMsg = "I want to make a salad.";
        messages.addObject().put("role", "user").put("content", initialMsg);
        System.out.println("You: " + initialMsg);

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.set("messages", messages);

            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", "salad_turn");
            jsonSchema.put("strict", true);
            jsonSchema.set("schema", mapper.readTree(SCHEMA));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println("Request failed with status " + response.statusCode());
                System.out.println(response.body());
                return;
            }

            JsonNode apiResp = mapper.readTree(response.body());
            String   rawJson = apiResp.at("/choices/0/message/content").asText();

            // Keep raw JSON in history so the model remembers context
            messages.addObject().put("role", "assistant").put("content", rawJson);

            JsonNode turnData = mapper.readTree(rawJson);
            String  message   = turnData.get("message").asText();
            boolean done      = turnData.get("done").asBoolean();

            if (done) {
                System.out.println("\nAssistant: " + message);

                JsonNode recipe = turnData.get("recipe");
                System.out.println("\n=== RECIPE: " + recipe.get("name").asText() + " ===");

                System.out.println("\nIngredients:");
                int i = 1;
                for (JsonNode ingredient : recipe.get("ingredients")) {
                    System.out.println("  " + i++ + ". " + ingredient.asText());
                }

                System.out.println("\nSteps:");
                int step = 1;
                for (JsonNode s : recipe.get("steps")) {
                    System.out.println("  " + step++ + ". " + s.asText());
                }
                return;
            }

            System.out.println("\nAssistant: " + message);
            System.out.print("You: ");
            String userInput = scanner.nextLine();
            messages.addObject().put("role", "user").put("content", userInput);
        }

        System.out.println("\n(Reached the turn limit without a done signal.)");
    }
}
