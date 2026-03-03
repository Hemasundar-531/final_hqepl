package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.O2DConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface O2DConfigRepository extends MongoRepository<O2DConfig, String> {

    List<O2DConfig> findByNameIgnoreCase(String name);

    List<O2DConfig> findByCompanyName(String companyName);
}
