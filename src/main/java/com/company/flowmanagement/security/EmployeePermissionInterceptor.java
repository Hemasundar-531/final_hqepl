package com.company.flowmanagement.security;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts employee routes and checks permissions before allowing access.
 */
@Component
public class EmployeePermissionInterceptor implements HandlerInterceptor {

    private final EmployeeRepository employeeRepository;
    private final com.company.flowmanagement.repository.UserRepository userRepository;

    public EmployeePermissionInterceptor(EmployeeRepository employeeRepository,
            com.company.flowmanagement.repository.UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String requestURI = request.getRequestURI();

        // Allow dashboard, login, and error page without permission check
        if (requestURI.equals("/employee/dashboard") || 
            requestURI.equals("/login") || 
            requestURI.equals("/error")) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {

            response.sendRedirect("/login");
            return false;
        }

        // ✅ FIX: allow SUPERADMIN and ADMIN without employee profile
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isSuperAdmin || isAdmin) {

            System.out.println("✅ ADMIN/SUPERADMIN ACCESS GRANTED to " + requestURI);

            return true;
        }

        // Only EMPLOYEE needs employee profile
        String username = authentication.getName();

        Optional<Employee> employeeOpt = employeeRepository.findByName(username);

        if (employeeOpt.isEmpty()) {

            System.out.println("🚫 Employee profile NOT FOUND for " + username);

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: Employee profile not found.");

            return false;
        }

        Employee employee = employeeOpt.get();

        List<String> permissions = employee.getPermissions();

        if (permissions == null) {
            permissions = new ArrayList<>();
        }

        // Check permission
        if (!checkPermission(request, permissions, employee)) {

            System.out.println("🚫 ACCESS DENIED for " + username +
                    " to " + requestURI);

            System.out.println("Permissions: " + permissions);

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: You do not have permission to access this page.");

            return false;
        }

        System.out.println("✅ ACCESS GRANTED for " + username +
                " to " + requestURI);

        return true;
    }

    /**
     * Check if employee has permission to access URL
     */
    private boolean checkPermission(HttpServletRequest request,
            List<String> permissions,
            Employee employee) {

        String requestURI = request.getRequestURI();

        // Order Entry
        if (requestURI.startsWith("/employee/order-entry")) {

            // Check if specific folder is requested
            String folderId = request.getParameter("folderId");
            if (folderId != null && !folderId.isBlank()) {
                // Granular check: ORDER_ENTRY:{folderId} OR legacy global ORDER_ENTRY
                boolean hasSpecific = permissions.contains("ORDER_ENTRY:" + folderId.trim());
                boolean hasGlobal = permissions.contains("ORDER_ENTRY");
                return hasSpecific || hasGlobal;
            }

            // If no folder specified (e.g. main list), allow if they have ANY order entry
            // permission
            return permissions.contains("ORDER_ENTRY") ||
                    permissions.stream().anyMatch(p -> p.startsWith("ORDER_ENTRY:"));
        }

        // Planning Result
        if (requestURI.equals("/employee/planning-result")) {
            String folderId = request.getParameter("folderId");
            if (folderId != null && !folderId.isBlank()) {
                boolean hasSpecific = permissions.contains("ORDER_ENTRY:" + folderId.trim());
                boolean hasGlobal = permissions.contains("ORDER_ENTRY");
                return hasSpecific || hasGlobal;
            }
            return permissions.contains("ORDER_ENTRY") ||
                    permissions.stream().anyMatch(p -> p.startsWith("ORDER_ENTRY:"));
        }

        // Planning Edit
        if (requestURI.equals("/employee/planning-edit")) {
            String folderId = request.getParameter("folderId");
            if (folderId != null && !folderId.isBlank()) {
                boolean hasSpecific = permissions.contains("ORDER_ENTRY:" + folderId.trim());
                boolean hasGlobal = permissions.contains("ORDER_ENTRY");
                return hasSpecific || hasGlobal;
            }
            return permissions.contains("ORDER_ENTRY") ||
                    permissions.stream().anyMatch(p -> p.startsWith("ORDER_ENTRY:"));
        }

        // Task Manager
        if (requestURI.startsWith("/employee/task-manager")) {

            // Check company-wide permission
            if (employee.getAdminId() != null) {
                com.company.flowmanagement.model.User admin = userRepository.findById(employee.getAdminId())
                        .orElse(null);
                if (admin != null && admin.getPermissions() != null
                        && admin.getPermissions().contains("COMPANY_TASK_MANAGEMENT")) {
                    return true;
                }
            }

            return false; // Strict check: Only company-wide permission allows access
        }

        // FMS
        if (requestURI.startsWith("/employee/fms")) {

            // main FMS page
            if (requestURI.equals("/employee/fms")) {

                return permissions.stream()
                        .anyMatch(p -> p.startsWith("FMS:"));
            }

            // legacy support
            if (requestURI.startsWith("/employee/fms/folder1")) {

                return permissions.contains("FMS_FOLDER1") ||
                        permissions.stream()
                                .anyMatch(p -> p.startsWith("FMS:"));
            }

            if (requestURI.startsWith("/employee/fms/folder2")) {

                return permissions.contains("FMS_FOLDER2") ||
                        permissions.stream()
                                .anyMatch(p -> p.startsWith("FMS:"));
            }

            // generic FMS access
            return permissions.stream()
                    .anyMatch(p -> p.startsWith("FMS:"));
        }

        // default deny
        return false;
    }
}
