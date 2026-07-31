package com.velora.backend.controller;

import com.velora.backend.dto.professional.ProfessionalPublicProfileResponse;
import com.velora.backend.service.ProfessionalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professionals")
@RequiredArgsConstructor
@Tag(name = "Professionals", description = "Public professional profiles (public)")
public class ProfessionalController {

    private final ProfessionalService professionalService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a professional's public profile")
    public ResponseEntity<ProfessionalPublicProfileResponse> getPublicProfile(@PathVariable Long id) {
        return ResponseEntity.ok(professionalService.getPublicProfile(id));
    }
}
