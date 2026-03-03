package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProjectRepository extends MongoRepository<Project, String> {

    // Find projects by client name
    List<Project> findByClientName(String clientName);

    // Find projects by client org ID
    List<Project> findByClientOrg(String clientOrg);

    // Find all projects (for building client-project map)
    List<Project> findAll();
}
