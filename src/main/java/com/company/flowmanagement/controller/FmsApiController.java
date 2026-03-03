package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.repository.O2DConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/fms")
public class FmsApiController {

    private final O2DConfigRepository repository;

    public FmsApiController(O2DConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new FMS folder. Name must be unique (case-insensitive).
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, Authentication authentication) {
        boolean isSuperAdmin = authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
        if (!isSuperAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorBody("Only superadmin can create flows."));
        }

        String name = body != null ? body.get("name") : null;
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("FMS name is required."));
        }

        if (!repository.findByNameIgnoreCase(trimmed).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(errorBody("A folder with this name already exists."));
        }

        O2DConfig config = new O2DConfig();
        config.setName(trimmed);
        config.setConfigured(false);
        config = repository.save(config);

        Map<String, Object> result = new HashMap<>();
        result.put("id", config.getId());
        result.put("name", config.getName());
        result.put("configured", config.isConfigured());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private static Map<String, String> errorBody(String message) {
        Map<String, String> map = new HashMap<>();
        map.put("error", message);
        return map;
    }
}
