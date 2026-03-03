package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.FMSStep;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FMSStepRepository extends MongoRepository<FMSStep, String> {
    // Add custom queries here if needed
}
