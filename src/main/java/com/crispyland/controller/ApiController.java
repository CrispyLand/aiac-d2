package com.crispyland.controller;

import com.crispyland.dto.CompareRequest;
import com.crispyland.dto.CompareResponse;
import com.crispyland.dto.SaladRequest;
import com.crispyland.dto.SaladResponse;
import com.crispyland.service.GroqService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GroqService groqService;

    @PostMapping("/compare")
    public ResponseEntity<CompareResponse> compare(@RequestBody CompareRequest request) {
        try {
            return ResponseEntity.ok(groqService.compare(
                request.getPrompt(),
                request.getTemperature(),
                request.getLength(),
                request.getMaxTokens()
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return ResponseEntity.ok(new CompareResponse("Error: " + msg, java.util.List.of()));
        }
    }

    @PostMapping("/salad")
    public ResponseEntity<SaladResponse> saladTurn(@RequestBody SaladRequest request) {
        try {
            return ResponseEntity.ok(groqService.saladTurn(request.getMessages(), request.getMaxTokens(), request.getMaxTurns()));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return ResponseEntity.ok(new SaladResponse("API Error: " + msg, true, null));
        }
    }
}
