package com.shareverance.controller;

import com.shareverance.service.PredictionService;
import com.shareverance.service.PredictionService.PredictionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/monthly")
    public PredictionResponse getMonthlyPrediction(
            @PathVariable Long organizationId,
            @RequestParam(required = false) String targetMonth,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        return predictionService.getMonthlyPrediction(organizationId, userId, targetMonth);
    }
}

