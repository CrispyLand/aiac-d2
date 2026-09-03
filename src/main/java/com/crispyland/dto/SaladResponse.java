package com.crispyland.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class SaladResponse {
    private String   message;
    private boolean  done;
    private JsonNode recipe;   // null while gathering, populated when done=true

    public SaladResponse() {}

    public SaladResponse(String message, boolean done, JsonNode recipe) {
        this.message = message;
        this.done    = done;
        this.recipe  = recipe;
    }

    public String   getMessage() { return message; }
    public void     setMessage(String message) { this.message = message; }
    public boolean  isDone()    { return done; }
    public void     setDone(boolean done) { this.done = done; }
    public JsonNode getRecipe() { return recipe; }
    public void     setRecipe(JsonNode recipe) { this.recipe = recipe; }
}
