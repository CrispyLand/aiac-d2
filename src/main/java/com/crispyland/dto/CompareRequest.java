package com.crispyland.dto;

public class CompareRequest {
    private String prompt;
    private double temperature;
    private String length;
    private int    maxTokens = 2048;

    public String getPrompt()      { return prompt; }
    public void   setPrompt(String prompt) { this.prompt = prompt; }
    public double getTemperature() { return temperature; }
    public void   setTemperature(double temperature) { this.temperature = temperature; }
    public String getLength()      { return length; }
    public void   setLength(String length) { this.length = length; }
    public int    getMaxTokens()   { return maxTokens; }
    public void   setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
