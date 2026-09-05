package com.themba.remitflow;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CounterpartyService {

    private final CounterpartyRepository repository;

    public CounterpartyService(CounterpartyRepository repository) {
        this.repository = repository;
    }

    public CounterpartyResponse create(CounterpartyRequest request) {
        Counterparty counterparty = new Counterparty();
        counterparty.setCompanyName(request.companyName());
        counterparty.setEmail(request.email());
        counterparty.setIban(request.iban());
        counterparty.setBic(request.bic());
        counterparty.setCountry(request.country());
        return CounterpartyResponse.from(repository.save(counterparty));
    }

    public List<CounterpartyResponse> findAll() {
        return repository.findAll().stream().map(CounterpartyResponse::from).toList();
    }

    public CounterpartyResponse findById(Long id) {
        return repository.findById(id)
                .map(CounterpartyResponse::from)
                .orElseThrow(() -> new NotFoundException("Counterparty " + id + " not found"));
    }
}
