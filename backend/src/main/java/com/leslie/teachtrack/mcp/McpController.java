package com.leslie.teachtrack.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.leslie.teachtrack.auth.AuthPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpController {
    private final McpService service;

    public McpController(McpService service) {
        this.service = service;
    }

    @PostMapping("/mcp")
    public ResponseEntity<JsonNode> rpc(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody JsonNode request
    ) {
        JsonNode response = service.handle(request, principal);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }
}
