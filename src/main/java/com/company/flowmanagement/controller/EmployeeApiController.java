package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/employees")
public class EmployeeApiController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final com.company.flowmanagement.repository.UserRepository userRepository;
    private final O2DConfigRepository o2dConfigRepository;

    public EmployeeApiController(EmployeeRepository employeeRepository,
            EmployeeService employeeService,
            com.company.flowmanagement.repository.UserRepository userRepository,
            O2DConfigRepository o2dConfigRepository) {
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.o2dConfigRepository = o2dConfigRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        com.company.flowmanagement.model.User admin = userRepository.findByUsername(principal.getName());
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("User not found");
        }

        // Filter employees by adminId
        List<Employee> employees = employeeRepository.findByAdminId(admin.getId());

        // Attach display password for UI toggle (uses stored raw password when
        // available)
        for (Employee emp : employees) {
            String displayPwd = EmployeeService.DEFAULT_EMPLOYEE_PASSWORD;
            if (emp.getName() != null) {
                com.company.flowmanagement.model.User user = userRepository.findByUsername(emp.getName());
                if (user != null && user.getRawPassword() != null && !user.getRawPassword().isBlank()) {
                    displayPwd = user.getRawPassword();
                }
            }
            emp.setPassword(displayPwd);
        }

        // Filter FMS folders by permissions (ADMIN_FMS:{id})
        List<com.company.flowmanagement.model.O2DConfig> folders = new ArrayList<>();
        List<String> perms = admin.getPermissions();
        if (perms != null) {
            List<String> folderIds = new ArrayList<>();
            for (String perm : perms) {
                if (perm.startsWith("ADMIN_FMS:")) {
                    folderIds.add(perm.substring("ADMIN_FMS:".length()));
                }
            }
            if (!folderIds.isEmpty()) {
                folders = (List<com.company.flowmanagement.model.O2DConfig>) o2dConfigRepository.findAllById(folderIds);
            }
        }

        return ResponseEntity.ok(Map.of(
                "employees", employees,
                "fmsFolders", folders));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Employee employee, java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        com.company.flowmanagement.model.User admin = userRepository.findByUsername(principal.getName());
        if (admin != null) {
            employee.setAdminId(admin.getId());
        }
        try {
            Employee saved = employeeService.createEmployee(employee);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add employee. Please try again."));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> update(@PathVariable("id") String id, @RequestBody Employee employee) {
        return employeeRepository.findById(id)
                .map(existing -> {
                    existing.setName(employee.getName());
                    existing.setDepartment(employee.getDepartment());
                    existing.setStatus(employee.getStatus());
                    if (employee.getPermissions() != null) {
                        existing.setPermissions(employee.getPermissions());
                    }
                    return ResponseEntity.ok(employeeRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/permissions")
    public ResponseEntity<?> updatePermissions(@PathVariable("id") String id,
            @RequestBody List<String> permissions) {
        try {
            java.util.Optional<Employee> employeeOpt = employeeRepository.findById(id);
            if (employeeOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Employee existing = employeeOpt.get();

            // Set the new permissions
            existing.setPermissions(permissions != null ? permissions : new ArrayList<>());
            Employee saved = employeeRepository.save(existing);

            // Sync permissions to User
            com.company.flowmanagement.model.User user = userRepository.findByUsername(existing.getName());
            if (user != null) {
                user.setPermissions(new ArrayList<>(permissions));
                userRepository.save(user);
            }

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating permissions: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (employeeRepository.existsById(id)) {
            employeeRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Employee> getCurrentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        return employeeRepository.findByName(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
