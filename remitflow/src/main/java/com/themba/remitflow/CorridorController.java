package com.themba.remitflow;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** See remitflow-v1-spec.md section 7. No GET /{id}: not named in that section. */
@RestController
@RequestMapping("/api/v1/corridors")
public class CorridorController {

    private final CorridorService service;

    public CorridorController(CorridorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CorridorResponse create(@Valid @RequestBody CorridorRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<CorridorResponse> findAll() {
        return service.findAll();
    }
}
