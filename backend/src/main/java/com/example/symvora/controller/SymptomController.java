package com.example.symvora.controller;

import com.example.symvora.model.AIResponse;
import com.example.symvora.model.SymptomRequest;
import com.example.symvora.service.SymptomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SymptomController {

    @Autowired
    private SymptomService symptomService;

    @PostMapping("/analyze")
    public AIResponse analyzeSymptoms(@RequestBody SymptomRequest request) {
        return symptomService.analyzeSymptoms(request);
    }
}
