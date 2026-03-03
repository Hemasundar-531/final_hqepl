package com.company.flowmanagement.service;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Service for employee operations. When adding an employee, also creates
 * login credentials: username = employee name, default password = 1234567
 * (BCrypt), role = EMPLOYEE.
 */
@Service
public class EmployeeService {

    public static final String DEFAULT_EMPLOYEE_PASSWORD = "1234567";
    public static final String DEFAULT_EMPLOYEE_ROLE = "EMPLOYEE";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.company.flowmanagement.repository.O2DConfigRepository o2dConfigRepository;

    public EmployeeService(EmployeeRepository employeeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            com.company.flowmanagement.repository.O2DConfigRepository o2dConfigRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.o2dConfigRepository = o2dConfigRepository;
    }

    /**
     * Save employee and create login credentials.
     * Username = employee name; password = 1234567 (BCrypt); role = EMPLOYEE.
     *
     * @throws IllegalArgumentException if name is blank or a user with this
     *                                  username already exists
     */
    public Employee createEmployee(Employee employee) {
        String name = employee.getName() != null ? employee.getName().trim() : "";
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Employee name is required.");
        }

        if (userRepository.findByUsername(name) != null) {
            throw new IllegalArgumentException(
                    "A login account with the name \"" + name + "\" already exists. Use a unique employee name.");
        }

        if (employee.getPermissions() == null) {
            employee.setPermissions(new ArrayList<>());
        }

        if (employee.getStatus() == null || employee.getStatus().isEmpty()) {
            employee.setStatus("Active"); // Default status
        }

        Employee saved = employeeRepository.save(employee);

        User user = new User();
        user.setUsername(name);
        user.setEmail(employee.getEmail());

        String rawPassword = (employee.getPassword() != null && !employee.getPassword().isEmpty())
                ? employee.getPassword()
                : DEFAULT_EMPLOYEE_PASSWORD;
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRawPassword(rawPassword);

        user.setRole(DEFAULT_EMPLOYEE_ROLE);
        user.setPermissions(new ArrayList<>(employee.getPermissions())); // Copy permissions to User
        user.setCompanyName(employee.getDepartment()); // Optional: map department or keep null
        userRepository.save(user);

        return saved;
    }

    public java.util.Map<String, Object> getEmployeeContext(String username) {
        java.util.Map<String, Object> context = new java.util.HashMap<>();

        com.company.flowmanagement.model.Employee employee = employeeRepository.findByName(username)
                .orElseGet(() -> {
                    com.company.flowmanagement.model.Employee e = new com.company.flowmanagement.model.Employee();
                    e.setName(username);
                    e.setPermissions(new ArrayList<>());
                    return e;
                });

        String employeeCompanyName = "";
        if (employee.getAdminId() != null && !employee.getAdminId().isBlank()) {
            User admin = userRepository.findById(employee.getAdminId()).orElse(null);
            if (admin != null) {
                if (admin.getCompanyName() != null && !admin.getCompanyName().isBlank()) {
                    employeeCompanyName = admin.getCompanyName().trim();
                } else if (admin.getUsername() != null && !admin.getUsername().isBlank()) {
                    employeeCompanyName = admin.getUsername().trim();
                }
            }
        }
        if ((employeeCompanyName == null || employeeCompanyName.isBlank())
                && employee.getDepartment() != null && !employee.getDepartment().isBlank()) {
            employeeCompanyName = employee.getDepartment().trim();
        }

        java.util.List<String> permissions = employee.getPermissions();
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
        // Enforce strict company-level control: Remove individual TASK_MANAGER
        // permission
        permissions.remove("TASK_MANAGER");

        // Check for Company-Wide Task Management Permission
        if (employee.getAdminId() != null && !employee.getAdminId().isBlank()) {
            User admin = userRepository.findById(employee.getAdminId()).orElse(null);
            if (admin != null && admin.getPermissions() != null
                    && admin.getPermissions().contains("COMPANY_TASK_MANAGEMENT")) {
                if (!permissions.contains("TASK_MANAGER")) {
                    permissions.add("TASK_MANAGER");
                }
            }
        }
        final java.util.List<String> finalPermissions = new ArrayList<>(permissions);

        // Fetch all FMS folders from database
        java.util.List<com.company.flowmanagement.model.O2DConfig> allFmsFolders = o2dConfigRepository.findAll();

        // 1. Filter FMS Folders (Flow Access)
        java.util.List<com.company.flowmanagement.model.O2DConfig> employeeFmsFolders = allFmsFolders.stream()
                .filter(folder -> finalPermissions.contains("FMS:" + folder.getId()))
                .collect(java.util.stream.Collectors.toList());

        // 2. Filter Order Entry Folders (Order Entry Access)
        // Legacy global 'ORDER_ENTRY' grants access to ALL, otherwise check specific
        // 'ORDER_ENTRY:{id}'
        boolean hasGlobalOrderEntry = finalPermissions.contains("ORDER_ENTRY");
        java.util.List<com.company.flowmanagement.model.O2DConfig> orderEntryFolders = allFmsFolders.stream()
                .filter(folder -> hasGlobalOrderEntry || finalPermissions.contains("ORDER_ENTRY:" + folder.getId()))
                .collect(java.util.stream.Collectors.toList());

        // Fix Dashboard "Not Assigned" issue:
        // If user has ANY order entry access, add the global 'ORDER_ENTRY' string to
        // the view-layer permissions
        // so the dashboard card lights up.
        if (!orderEntryFolders.isEmpty() && !finalPermissions.contains("ORDER_ENTRY")) {
            finalPermissions.add("ORDER_ENTRY");
        }

        context.put("employeeName", employee.getName());
        context.put("employeeCompanyName", employeeCompanyName);
        context.put("permissions", finalPermissions);
        context.put("employee", employee);
        context.put("fmsFolders", employeeFmsFolders);
        context.put("orderEntryFolders", orderEntryFolders);

        return context;
    }

    public java.util.List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public java.util.List<Employee> getEmployeesByAdminId(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            return new java.util.ArrayList<>();
        }
        return employeeRepository.findByAdminId(adminId);
    }
}
