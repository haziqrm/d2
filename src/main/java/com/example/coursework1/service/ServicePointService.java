package com.example.coursework1.service;

import com.example.coursework1.dto.ServicePoint;
import com.example.coursework1.repository.ServicePointRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicePointService {
    private final ServicePointRepository repository;

    public ServicePointService(ServicePointRepository repository) {
        this.repository = repository;
    }

    public List<ServicePoint> fetchAllServicePoints() {
        return repository.fetchAllServicePoints();
    }
}