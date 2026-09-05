package com.themba.remitflow;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CorridorService {

    private final CorridorRepository repository;

    public CorridorService(CorridorRepository repository) {
        this.repository = repository;
    }

    public CorridorResponse create(CorridorRequest request) {
        Corridor corridor = new Corridor();
        corridor.setSourceCurrency(request.sourceCurrency());
        corridor.setTargetCurrency(request.targetCurrency());
        corridor.setExchangeRate(request.exchangeRate());
        corridor.setFeePercent(request.feePercent());
        return CorridorResponse.from(repository.save(corridor));
    }

    public List<CorridorResponse> findAll() {
        return repository.findAll().stream().map(CorridorResponse::from).toList();
    }
}
