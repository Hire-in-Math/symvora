package com.example.symvora.service;

import com.example.symvora.model.AIResponse;
import com.example.symvora.model.SymptomRequest;
import org.springframework.stereotype.Service;

@Service
public class SymptomService {
    
    public AIResponse analyzeSymptoms(SymptomRequest request) {
        // TODO: Integrate with AI API later
        AIResponse response = new AIResponse();
        response.setResult(
            "Based on your symptoms, here are some general possibilities:\n\n" +
            "Possible Conditions:\n" +
            "• Common cold or flu\n" +
            "• Seasonal allergies\n" +
            "• Stress-related symptoms\n\n" +
            "General Advice:\n" +
            "• Rest and stay hydrated\n" +
            "• Monitor your symptoms\n" +
            "• Avoid self-diagnosis\n\n" +
            "⚠️ IMPORTANT: This is for informational purposes only. " +
            "Always consult a healthcare professional for proper diagnosis and treatment."
        );
        return response;
    }
}
