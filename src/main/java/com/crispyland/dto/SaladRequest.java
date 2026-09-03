package com.crispyland.dto;

import java.util.List;

public class SaladRequest {
    private List<ChatMessage> messages;
    private int               maxTokens = 2048;

    public List<ChatMessage> getMessages()  { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
    public int  getMaxTokens()              { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
