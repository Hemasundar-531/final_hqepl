package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    private final UserRepository userRepository;
    private final O2DConfigRepository o2dConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.company.flowmanagement.repository.EmployeeRepository employeeRepository;
    private final EmployeeController employeeController;

    public SuperAdminController(UserRepository userRepository,
            O2DConfigRepository o2dConfigRepository,
            PasswordEncoder passwordEncoder,
            com.company.flowmanagement.repository.EmployeeRepository employeeRepository,
            EmployeeController employeeController) {
        this.userRepository = userRepository;
        this.o2dConfigRepository = o2dConfigRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
        this.employeeController = employeeController;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<User> admins = userRepository.findByRole("ADMIN");
        List<com.company.flowmanagement.model.O2DConfig> folders = o2dConfigRepository.findAll();

        // Create a map of FolderID -> FolderName for quick lookup
        Map<String, String> folderMap = new HashMap<>();
        for (com.company.flowmanagement.model.O2DConfig folder : folders) {
            folderMap.put(folder.getId(), folder.getName());
        }

        // Map AdminID -> List of Flow Maps {id, name}
        Map<String, List<Map<String, String>>> adminFlows = new HashMap<>();

        for (User admin : admins) {
            List<Map<String, String>> flows = new ArrayList<>();
            if (admin.getPermissions() != null) {
                for (String perm : admin.getPermissions()) {
                    if (perm.startsWith("ADMIN_FMS:")) {
                        String folderId = perm.substring("ADMIN_FMS:".length());
                        if (folderMap.containsKey(folderId)) {
                            Map<String, String> flow = new HashMap<>();
                            flow.put("id", folderId);
                            flow.put("name", folderMap.get(folderId));
                            flows.add(flow);
                        }
                    }
                }
            }
            adminFlows.put(admin.getId(), flows);
        }

        model.addAttribute("admins", admins);
        model.addAttribute("folders", folders);
        model.addAttribute("adminFlows", adminFlows);
        return "superadmin-dashboard";
    }

    @GetMapping("/company-manage")
    public String companyManage(Model model) {
        List<User> admins = userRepository.findByRole("ADMIN");
        model.addAttribute("admins", admins);
        return "superadmin-company-manage";
    }

    @GetMapping("/company-detail/{id}")
    public String companyDetail(@PathVariable("id") String id, Model model) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin == null) {
            return "redirect:/superadmin/company-manage";
        }

        // Get all FMS folders
        List<com.company.flowmanagement.model.O2DConfig> allFolders = o2dConfigRepository.findAll();

        // Filter folders this admin has access to
        List<com.company.flowmanagement.model.O2DConfig> adminFolders = new ArrayList<>();
        if (admin.getPermissions() != null) {
            for (com.company.flowmanagement.model.O2DConfig folder : allFolders) {
                if (admin.getPermissions().contains("ADMIN_FMS:" + folder.getId())) {
                    adminFolders.add(folder);
                }
            }
        }

        model.addAttribute("admin", admin);
        model.addAttribute("folders", adminFolders);
        model.addAttribute("allFolders", allFolders);
        model.addAttribute("companyEmployees", employeeRepository.findByAdminId(id));

        long configuredCount = adminFolders.stream().filter(f -> f.isConfigured()).count();
        model.addAttribute("configuredCount", configuredCount);
        model.addAttribute("pendingCount", adminFolders.size() - configuredCount);

        return "superadmin-company-detail";
    }

    @PostMapping("/company-detail/{id}/create-fms")
    public String createCompanyFms(@PathVariable("id") String id,
            @RequestParam("name") String name,
            RedirectAttributes redirectAttributes) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin == null) {
            return "redirect:/superadmin/company-manage";
        }

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            redirectAttributes.addFlashAttribute("fmsError", "FMS name is required.");
            return "redirect:/superadmin/company-detail/" + id;
        }

        if (!o2dConfigRepository.findByNameIgnoreCase(trimmed).isEmpty()) {
            redirectAttributes.addFlashAttribute("fmsError", "An FMS flow with this name already exists.");
            return "redirect:/superadmin/company-detail/" + id;
        }

        com.company.flowmanagement.model.O2DConfig config = new com.company.flowmanagement.model.O2DConfig();
        config.setName(trimmed);
        config.setConfigured(false);
        config = o2dConfigRepository.save(config);

        ArrayList<String> perms = admin.getPermissions();
        if (perms == null) {
            perms = new ArrayList<>();
        }
        String permission = "ADMIN_FMS:" + config.getId();
        if (!perms.contains(permission)) {
            perms.add(permission);
            admin.setPermissions(perms);
            userRepository.save(admin);
        }

        redirectAttributes.addFlashAttribute("fmsSuccess", "FMS flow created successfully.");
        return "redirect:/superadmin/company-detail/" + id;
    }

    @GetMapping("/company-detail/{adminId}/folder/{folderId}")
    public String companyFolderDetail(@PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @RequestParam(name = "edit", required = false, defaultValue = "false") boolean edit,
            Model model) {
        User admin = userRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return "redirect:/superadmin/company-manage";
        }

        com.company.flowmanagement.model.O2DConfig folder = o2dConfigRepository.findById(folderId).orElse(null);
        if (folder == null) {
            return "redirect:/superadmin/company-detail/" + adminId;
        }

        model.addAttribute("admin", admin);
        model.addAttribute("folder", folder);
        model.addAttribute("adminId", adminId);
        model.addAttribute("folderId", folderId);
        model.addAttribute("editMode", edit);
        if (edit) {
            model.addAttribute("employees", employeeRepository.findByAdminId(adminId));
        }
        return "superadmin-folder-detail";
    }

    @GetMapping("/company-detail/{adminId}/folder/{folderId}/employee/{employeeId}/order-entry")
    public String employeeOrderEntryFromSuperAdmin(@PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam(name = "entryId", required = false) String entryId,
            @RequestParam(name = "planOrderId", required = false) String planOrderId,
            @RequestParam(name = "planStart", required = false) String planStart,
            @RequestParam(name = "planning", required = false) Boolean planning,
            @RequestParam(name = "saved", required = false) Boolean saved,
            Model model) {
        User admin = userRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return "redirect:/superadmin/company-manage";
        }

        com.company.flowmanagement.model.Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getAdminId() == null || !adminId.equals(employee.getAdminId())) {
            return "redirect:/superadmin/company-detail/" + adminId;
        }

        UsernamePasswordAuthenticationToken employeeAuth = new UsernamePasswordAuthenticationToken(
                employee.getName(), null, java.util.List.of());

        String result = employeeController.orderEntry(folderId, entryId, planOrderId, planStart, planning, saved, model,
                employeeAuth);
        if (result != null && result.startsWith("redirect:")) {
            return "redirect:/superadmin/company-detail/" + adminId + "/folder/" + folderId;
        }

        com.company.flowmanagement.model.O2DConfig folder = o2dConfigRepository.findById(folderId).orElse(null);
        model.addAttribute("admin", admin);
        model.addAttribute("folder", folder);
        model.addAttribute("adminId", adminId);
        model.addAttribute("folderId", folderId);
        model.addAttribute("selectedEmployee", employee);
        String basePath = "/superadmin/company-detail/" + adminId + "/folder/" + folderId + "/employee/" + employeeId
                + "/order-entry";
        model.addAttribute("superadminView", true);
        model.addAttribute("orderEntryBasePath", basePath);
        model.addAttribute("orderEntryEntryPath", basePath + "/entry");
        model.addAttribute("orderEntryPlanningPath", basePath + "/planning");
        model.addAttribute("orderEntryPlanningStatusPath", basePath + "/planning-status");
        model.addAttribute("orderEntryFetchEntryPath", basePath + "/entry");
        return "employee-order-entry";
    }

    @GetMapping("/company-detail/{adminId}/folder/{folderId}/employee/{employeeId}/order-entry/entry")
    @ResponseBody
    public com.company.flowmanagement.model.OrderEntry fetchEmployeeOrderEntryFromSuperAdmin(
            @PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam("orderId") String orderId) {
        com.company.flowmanagement.model.Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getAdminId() == null || !adminId.equals(employee.getAdminId())) {
            return null;
        }
        return employeeController.fetchEntry(folderId, orderId);
    }

    @PostMapping("/company-detail/{adminId}/folder/{folderId}/employee/{employeeId}/order-entry/entry")
    public String createEmployeeOrderEntryFromSuperAdmin(@PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam("folderId") String postedFolderId,
            @RequestParam("orderId") String orderId,
            @RequestParam Map<String, String> params) {
        com.company.flowmanagement.model.Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getAdminId() == null || !adminId.equals(employee.getAdminId())) {
            return "redirect:/superadmin/company-detail/" + adminId + "/folder/" + folderId;
        }
        String redirect = employeeController.createOrderEntry(postedFolderId, orderId, params);
        return rewriteOrderEntryRedirect(adminId, folderId, employeeId, redirect);
    }

    @PostMapping("/company-detail/{adminId}/folder/{folderId}/employee/{employeeId}/order-entry/planning")
    public String submitPlanningFromSuperAdmin(@PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam("folderId") String postedFolderId,
            @RequestParam("orderId") String orderId,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "responsiblePerson", required = false) List<String> responsiblePersons,
            @RequestParam(name = "targetDate", required = false) List<String> targetDates) {
        com.company.flowmanagement.model.Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getAdminId() == null || !adminId.equals(employee.getAdminId())) {
            return "redirect:/superadmin/company-detail/" + adminId + "/folder/" + folderId;
        }
        String redirect = employeeController.submitPlanning(postedFolderId, orderId, startDate, responsiblePersons,
            targetDates);
        return rewriteOrderEntryRedirect(adminId, folderId, employeeId, redirect);
    }

    @PostMapping("/company-detail/{adminId}/folder/{folderId}/employee/{employeeId}/order-entry/planning-status")
    public String updatePlanningStatusFromSuperAdmin(@PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam("entryId") String entryId,
            @RequestParam("planningStatus") String planningStatus,
            @RequestParam("folderId") String postedFolderId) {
        com.company.flowmanagement.model.Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getAdminId() == null || !adminId.equals(employee.getAdminId())) {
            return "redirect:/superadmin/company-detail/" + adminId + "/folder/" + folderId;
        }
        String redirect = employeeController.updatePlanningStatus(entryId, planningStatus, postedFolderId);
        return rewriteOrderEntryRedirect(adminId, folderId, employeeId, redirect);
    }

    private String rewriteOrderEntryRedirect(String adminId, String fallbackFolderId, String employeeId,
            String redirect) {
        String base = "/superadmin/company-detail/" + adminId + "/folder/" + fallbackFolderId + "/employee/"
                + employeeId
                + "/order-entry";
        if (redirect == null || !redirect.startsWith("redirect:/employee/order-entry")) {
            return "redirect:" + base;
        }
        int queryIdx = redirect.indexOf('?');
        if (queryIdx < 0) {
            return "redirect:" + base;
        }
        String query = redirect.substring(queryIdx + 1);
        String folderFromQuery = null;
        for (String pair : query.split("&")) {
            if (pair.startsWith("folderId=")) {
                folderFromQuery = pair.substring("folderId=".length());
                break;
            }
        }
        String resolvedFolder = (folderFromQuery != null && !folderFromQuery.isBlank()) ? folderFromQuery
                : fallbackFolderId;
        return "redirect:/superadmin/company-detail/" + adminId + "/folder/" + resolvedFolder + "/employee/"
                + employeeId
                + "/order-entry?" + query;
    }

    @PostMapping("/company-detail/{adminId}/folder/{folderId}/save")
    public String saveFolderDetails(@PathVariable("adminId") String adminId,
            @PathVariable("folderId") String folderId,
            @RequestParam(name = "orderDetails", required = false) java.util.List<String> orderDetails,
            @RequestParam(name = "stepProcess", required = false) java.util.List<String> stepProcess,
            @RequestParam(name = "responsiblePerson", required = false) java.util.List<String> responsiblePerson,
            @RequestParam(name = "targetType", required = false) java.util.List<String> targetType,
            @RequestParam(name = "days", required = false) java.util.List<String> days) {

        com.company.flowmanagement.model.O2DConfig config = o2dConfigRepository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/superadmin/company-detail/" + adminId;
        }

        // Clean order details
        java.util.ArrayList<String> cleanedOrder = new java.util.ArrayList<>();
        if (orderDetails != null) {
            for (String val : orderDetails) {
                if (val != null && !val.trim().isEmpty()) {
                    cleanedOrder.add(val.trim());
                }
            }
        }
        config.setOrderDetails(cleanedOrder);

        // Clean process steps
        java.util.ArrayList<com.company.flowmanagement.model.ProcessStep> cleanedSteps = new java.util.ArrayList<>();
        if (stepProcess != null) {
            for (int i = 0; i < stepProcess.size(); i++) {
                String step = stepProcess.get(i) != null ? stepProcess.get(i).trim() : "";
                String person = (responsiblePerson != null && i < responsiblePerson.size()
                        && responsiblePerson.get(i) != null) ? responsiblePerson.get(i).trim() : "";
                String type = (targetType != null && i < targetType.size() && targetType.get(i) != null)
                        ? targetType.get(i).trim()
                        : "";
                String dayVal = (days != null && i < days.size() && days.get(i) != null) ? days.get(i).trim() : "";

                if (step.isEmpty() && person.isEmpty() && type.isEmpty() && dayVal.isEmpty()) {
                    continue;
                }

                com.company.flowmanagement.model.ProcessStep ps = new com.company.flowmanagement.model.ProcessStep();
                ps.setStepProcess(step);
                ps.setResponsiblePerson(person);
                ps.setTargetType(type);
                try {
                    ps.setDays(Integer.parseInt(dayVal));
                } catch (NumberFormatException e) {
                    ps.setDays(null);
                }
                cleanedSteps.add(ps);
            }
        }
        config.setProcessDetails(cleanedSteps);
        config.setConfigured(!cleanedOrder.isEmpty() || !cleanedSteps.isEmpty());
        o2dConfigRepository.save(config);

        return "redirect:/superadmin/company-detail/" + adminId + "/folder/" + folderId;
    }

    @PostMapping("/verify-password")
    @ResponseBody
    public Map<String, Object> verifyPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        String password = body.get("password");
        User superadmin = userRepository.findByUsername("superadmin");
        if (superadmin != null && passwordEncoder.matches(password, superadmin.getPassword())) {
            result.put("valid", true);
        } else {
            result.put("valid", false);
            result.put("error", "Invalid password");
        }
        return result;
    }

    @PostMapping("/create-admin")
    public String createAdmin(@RequestParam("companyName") String companyName,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "companyLogo", required = false) MultipartFile companyLogo,
            @RequestParam(value = "fmsFolders", required = false) List<String> fmsFolders,
            Model model) {

        try {
            // Check if username/email already exists
            if (userRepository.findByUsername(email) != null) {
                model.addAttribute("error", "Admin with this email already exists.");
                return dashboard(model);
            }

            User newAdmin = new User();
            newAdmin.setUsername(email); // Using email as username
            newAdmin.setEmail(email);
            newAdmin.setPassword(passwordEncoder.encode(password));
            newAdmin.setRawPassword(password);
            newAdmin.setRole("ADMIN");
            newAdmin.setCompanyName(companyName);
            newAdmin.setCustomerName(customerName);

            // Handle FMS Permissions
            if (fmsFolders != null && !fmsFolders.isEmpty()) {
                ArrayList<String> perms = new ArrayList<>();
                for (String folderId : fmsFolders) {
                    perms.add("ADMIN_FMS:" + folderId);
                }
                newAdmin.setPermissions(perms);
            }

            // Handle File Upload
            if (companyLogo != null && !companyLogo.isEmpty()) {
                String uploadDir = "uploads/logos/";
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String fileName = System.currentTimeMillis() + "_" + companyLogo.getOriginalFilename();
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(companyLogo.getInputStream(), filePath);

                // Save relative path for web access
                newAdmin.setCompanyLogo("/uploads/logos/" + fileName);
            }

            userRepository.save(newAdmin);
            model.addAttribute("success", "Admin created successfully!");

        } catch (IOException e) {
            model.addAttribute("error", "Failed to upload logo: " + e.getMessage());
        }

        return dashboard(model);
    }

    @PostMapping("/update-admin")
    public String updateAdmin(@RequestParam("id") String id,
            @RequestParam("companyName") String companyName,
            @RequestParam("customerName") String customerName,
            @RequestParam("email") String email,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "companyLogo", required = false) MultipartFile companyLogo,
            @RequestParam(value = "fmsFolders", required = false) List<String> fmsFolders,
            Model model) {

        try {
            User admin = userRepository.findById(id).orElse(null);
            if (admin == null) {
                model.addAttribute("error", "Admin not found.");
                return dashboard(model);
            }

            // Update fields
            admin.setCompanyName(companyName);
            admin.setCustomerName(customerName);
            admin.setEmail(email);
            admin.setUsername(email); // Keep username synced with email

            // Update password if provided
            if (password != null && !password.isEmpty()) {
                admin.setPassword(passwordEncoder.encode(password));
                admin.setRawPassword(password);
            }

            // Update FMS Permissions
            // First, remove all existing ADMIN_FMS permissions
            List<String> currentPerms = admin.getPermissions();
            if (currentPerms == null) {
                currentPerms = new ArrayList<>();
            } else {
                currentPerms.removeIf(p -> p.startsWith("ADMIN_FMS:"));
            }

            // Add new permissions
            if (fmsFolders != null && !fmsFolders.isEmpty()) {
                for (String folderId : fmsFolders) {
                    currentPerms.add("ADMIN_FMS:" + folderId);
                }
            }
            admin.setPermissions((ArrayList<String>) currentPerms);

            // Handle File Upload
            if (companyLogo != null && !companyLogo.isEmpty()) {
                String uploadDir = "uploads/logos/";
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String fileName = System.currentTimeMillis() + "_" + companyLogo.getOriginalFilename();
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(companyLogo.getInputStream(), filePath);

                // Save relative path for web access
                admin.setCompanyLogo("/uploads/logos/" + fileName);
            }

            userRepository.save(admin);
            model.addAttribute("success", "Admin updated successfully!");

        } catch (IOException e) {
            model.addAttribute("error", "Failed to upload logo: " + e.getMessage());
        }

        return dashboard(model);
    }

    @GetMapping("/api/folder-access")
    @ResponseBody
    public List<Map<String, Object>> getFolderAccess(@RequestParam("folderId") String folderId) {
        List<User> admins = userRepository.findByRole("ADMIN");
        List<Map<String, Object>> result = new ArrayList<>();

        for (User admin : admins) {
            Map<String, Object> map = new HashMap<>();
            map.put("username", admin.getUsername());
            map.put("email", admin.getEmail());

            boolean hasAccess = admin.getPermissions() != null
                    && admin.getPermissions().contains("ADMIN_FMS:" + folderId);
            map.put("hasAccess", hasAccess);
            result.add(map);
        }
        return result;
    }

    @PostMapping("/api/folder-access")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> saveFolderAccess(@RequestBody Map<String, Object> body) {
        String folderId = (String) body.get("folderId");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> accessMap = (Map<String, Boolean>) body.get("access");

        if (folderId == null || accessMap == null) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }

        accessMap.forEach((username, shouldHaveAccess) -> {
            User admin = userRepository.findByUsername(username);
            if (admin == null || !"ADMIN".equals(admin.getRole())) {
                return;
            }

            ArrayList<String> perms = admin.getPermissions();
            if (perms == null) {
                perms = new ArrayList<>();
            }

            String permString = "ADMIN_FMS:" + folderId;
            if (Boolean.TRUE.equals(shouldHaveAccess)) {
                if (!perms.contains(permString)) {
                    perms.add(permString);
                }
            } else {
                perms.remove(permString);
            }

            admin.setPermissions(perms);
            userRepository.save(admin);
        });

        return org.springframework.http.ResponseEntity.ok().build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/delete-admin/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteAdmin(
            @org.springframework.web.bind.annotation.PathVariable("id") String id) {

        System.out.println("Processing Request: DELETE /delete-admin/" + id);

        if (id == null || id.trim().isEmpty() || "null".equals(id)) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Collections.singletonMap("error", "Invalid ID provided"));
        }

        try {
            User admin = userRepository.findById(id).orElse(null);
            if (admin == null) {
                System.out.println("Error: Admin not found with ID: " + id);
                return org.springframework.http.ResponseEntity.status(404)
                        .body(java.util.Collections.singletonMap("error", "Admin not found with ID: " + id));
            }

            if (!"ADMIN".equals(admin.getRole())) {
                System.out.println("Error: User is not an admin: " + admin.getUsername());
                return org.springframework.http.ResponseEntity.badRequest()
                        .body(java.util.Collections.singletonMap("error", "User is not an admin"));
            }

            // CASCADE DELETE: Delete all employees associated with this admin
            List<com.company.flowmanagement.model.Employee> employees = employeeRepository.findByAdminId(id);
            int empCount = 0;
            if (employees != null) {
                for (com.company.flowmanagement.model.Employee emp : employees) {
                    try {
                        // Delete the User account for the employee
                        if (emp.getName() != null) {
                            userRepository.deleteByUsername(emp.getName());
                        }
                        // Delete the Employee record
                        employeeRepository.delete(emp);
                        empCount++;
                    } catch (Exception e) {
                        System.err.println("Failed to delete employee " + emp.getName() + ": " + e.getMessage());
                        // Continue deleting other employees
                    }
                }
            }

            System.out.println("Cascade Delete: Removed " + empCount + " employees for admin " + admin.getUsername());

            // Delete the Admin User
            userRepository.deleteById(id);
            System.out.println("Success: Deleted admin " + id);

            return org.springframework.http.ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Delete Failed: " + e.getMessage());
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Collections.singletonMap("error", "Delete Failed: " + e.getMessage()));
        }
    }

    @PostMapping("/api/admin-fms-access")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> updateAdminFlows(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        @SuppressWarnings("unchecked")
        List<String> folderIds = (List<String>) body.get("folderIds");

        if (userId == null || folderIds == null) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }

        User admin = userRepository.findById(userId).orElse(null);
        if (admin == null || !"ADMIN".equals(admin.getRole())) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Collections.singletonMap("error", "Invalid admin user"));
        }

        ArrayList<String> perms = admin.getPermissions();
        if (perms == null) {
            perms = new ArrayList<>();
        }

        // Remove all existing FMS permissions
        perms.removeIf(p -> p.startsWith("ADMIN_FMS:"));

        // Add new FMS permissions
        for (String fid : folderIds) {
            perms.add("ADMIN_FMS:" + fid);
        }

        admin.setPermissions(perms);
        userRepository.save(admin);

        return org.springframework.http.ResponseEntity.ok().build();
    }

    @PostMapping("/company-detail/{id}/update-permissions")
    public String updatePermissions(@PathVariable("id") String id,
            @RequestParam(name = "taskManagement", required = false) String taskManagement,
            RedirectAttributes redirectAttributes) {

        User admin = userRepository.findById(id).orElse(null);
        if (admin == null) {
            return "redirect:/superadmin/company-manage";
        }

        ArrayList<String> perms = admin.getPermissions();
        if (perms == null) {
            perms = new ArrayList<>();
        }

        String permission = "COMPANY_TASK_MANAGEMENT";
        if ("on".equals(taskManagement)) {
            if (!perms.contains(permission)) {
                perms.add(permission);
            }
        } else {
            perms.remove(permission);
        }

        admin.setPermissions(perms);
        userRepository.save(admin);

        redirectAttributes.addFlashAttribute("success", "Permissions updated successfully.");
        return "redirect:/superadmin/company-detail/" + id;
    }
}
