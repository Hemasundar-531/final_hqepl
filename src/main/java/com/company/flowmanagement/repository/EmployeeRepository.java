package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends MongoRepository<Employee, String> {

    List<Employee> findAllByOrderById();

    List<Employee> findByAdminId(String adminId);

    Optional<Employee> findByName(String name);
}
