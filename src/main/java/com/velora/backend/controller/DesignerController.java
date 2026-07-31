package com.velora.backend.controller;

import com.velora.backend.dto.designer.DesignerPublicProfileResponse;
import com.velora.backend.service.DesignerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/designers")
@RequiredArgsConstructor
@Tag(name = "Designers", description = "Public designer profiles (public)")
public class DesignerController {

    private final DesignerService designerService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a designer's public profile")
    public ResponseEntity<DesignerPublicProfileResponse> getPublicProfile(@PathVariable Long id) {
        return ResponseEntity.ok(designerService.getPublicProfile(id));
    }
}
