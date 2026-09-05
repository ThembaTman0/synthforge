package com.themba.remitflow;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** See remitflow-v1-spec.md section 7. */
@RestController
@RequestMapping("/api/v1/counterparties")
public class CounterpartyController {

    private final CounterpartyService service;

    public CounterpartyController(CounterpartyService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CounterpartyResponse create(@Valid @RequestBody CounterpartyRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<CounterpartyResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CounterpartyResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
