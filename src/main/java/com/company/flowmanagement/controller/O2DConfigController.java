package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.model.ProcessStep;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/admin")
public class O2DConfigController {

    private final O2DConfigRepository repository;
    private final UserRepository userRepository;
    private final com.company.flowmanagement.repository.EmployeeRepository employeeRepository;

    public O2DConfigController(O2DConfigRepository repository, UserRepository userRepository,
            com.company.flowmanagement.repository.EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/fms-list")
    public String listFolders(Model model, Authentication authentication) {
        model.addAttribute("folders", getAccessibleFolders(authentication));
        return "fms-list";
    }

    @PostMapping("/fms-list")
    public String createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "rawMaterial", required = false) String rawMaterial,
            @RequestParam(value = "quantity", required = false) Integer quantity,
            @RequestParam(value = "cdd", required = false) String cdd,
            @RequestParam(value = "mpd", required = false) String mpd,
            @RequestParam(value = "startDate", required = false) String startDate,
            Authentication authentication) {
        User currentUser = authentication == null ? null : userRepository.findByUsername(authentication.getName());
        if (currentUser == null || !"SUPERADMIN".equals(currentUser.getRole())) {
            return "redirect:/admin/fms-list";
        }

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "redirect:/admin/fms-list";
        }

        O2DConfig config = new O2DConfig();
        config.setName(trimmed);
        config.setConfigured(false);
        config.setOrderId(safeTrim(orderId));
        config.setCustomerName(safeTrim(customerName));
        config.setCompanyName(safeTrim(companyName));
        config.setRawMaterial(safeTrim(rawMaterial));
        config.setQuantity(quantity);
        config.setCDD(safeTrim(cdd));
        config.setMPD(safeTrim(mpd));
        config.setStartDate(safeTrim(startDate));
        repository.save(config);

        return "redirect:/admin/fms-process?folderId=" + config.getId();
    }

    @GetMapping("/fms-process")
    public String viewO2D(@RequestParam("folderId") String folderId,
            @RequestParam(name = "edit", required = false, defaultValue = "false") boolean edit,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            Model model,
            Authentication authentication) {
        if (!canAccessFolder(authentication, folderId)) {
            return "redirect:/admin/fms-list";
        }

        O2DConfig config = repository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/admin/fms-list";
        }

        boolean hasData = config.isConfigured()
                || (config.getOrderDetails() != null && !config.getOrderDetails().isEmpty())
                || (config.getProcessDetails() != null && !config.getProcessDetails().isEmpty());

        model.addAttribute("folderId", folderId);
        model.addAttribute("folderName", config.getName());
        model.addAttribute("hasData", hasData);
        model.addAttribute("editMode", edit);
        model.addAttribute("config", config);
        model.addAttribute("returnUrl", returnUrl != null ? returnUrl : "");
        return "fms-process";
    }

    @PostMapping("/fms-process-steps")
    public String saveOrderDetails(
            @RequestParam("folderId") String folderId,
            @RequestParam(name = "orderDetails", required = false) List<String> orderDetails,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            Model model,
            HttpSession session,
            Authentication authentication) {
        if (!canAccessFolder(authentication, folderId)) {
            return "redirect:/admin/fms-list";
        }

        ArrayList<String> cleanedOrderDetails = cleanOrderDetails(orderDetails);
        session.setAttribute("orderDetailsDraft", cleanedOrderDetails);
        session.setAttribute("folderIdDraft", folderId);
        if (returnUrl != null && !returnUrl.isEmpty()) {
            session.setAttribute("returnUrlDraft", returnUrl);
        }

        String redirect = "redirect:/admin/fms-process-steps?folderId=" + folderId + "&edit=true";
        if (returnUrl != null && !returnUrl.isEmpty()) {
            redirect += "&returnUrl=" + returnUrl;
        }
        return redirect;
    }

    @GetMapping("/fms-process-steps")
    public String viewProcessSteps(@RequestParam("folderId") String folderId,
            @RequestParam(name = "edit", required = false, defaultValue = "false") boolean edit,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            HttpSession session,
            Model model,
            Authentication authentication) {
        if (!canAccessFolder(authentication, folderId)) {
            return "redirect:/admin/fms-list";
        }

        O2DConfig config = repository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/admin/fms-list";
        }
        if (config.isConfigured() && !edit) {
            return "redirect:/admin/fms-process?folderId=" + folderId;
        }

        if (session.getAttribute("orderDetailsDraft") == null) {
            if (edit && config.getOrderDetails() != null) {
                session.setAttribute("orderDetailsDraft", config.getOrderDetails());
                session.setAttribute("folderIdDraft", folderId);
            }
        }

        if (session.getAttribute("orderDetailsDraft") == null) {
            return "redirect:/admin/fms-process?folderId=" + folderId;
        }

        User currentUser = userRepository.findByUsername(authentication.getName());
        model.addAttribute("folderId", folderId);
        model.addAttribute("folderName", config.getName());
        model.addAttribute("editMode", edit);
        model.addAttribute("config", config);
        // Filter employees by the logged-in Admin's ID
        if (currentUser != null) {
            model.addAttribute("employees", employeeRepository.findByAdminId(currentUser.getId()));
        } else {
            model.addAttribute("employees", new ArrayList<>());
        }
        model.addAttribute("returnUrl", returnUrl != null ? returnUrl : "");
        return "fms-process-steps";
    }

    @PostMapping("/fms-process")
    public String saveO2D(
            @RequestParam("folderId") String folderId,
            @RequestParam(name = "stepProcess", required = false) List<String> stepProcess,
            @RequestParam(name = "responsiblePerson", required = false) List<String> responsiblePerson,
            @RequestParam(name = "targetType", required = false) List<String> targetType,
            @RequestParam(name = "days", required = false) List<String> days,
            HttpSession session,
            Authentication authentication) {
        if (!canAccessFolder(authentication, folderId)) {
            return "redirect:/admin/fms-list";
        }

        ArrayList<String> cleanedOrderDetails = new ArrayList<>();
        Object draftId = session.getAttribute("folderIdDraft");
        if (folderId.equals(draftId)) {
            Object draft = session.getAttribute("orderDetailsDraft");
            if (draft instanceof List) {
                cleanedOrderDetails = new ArrayList<>((List<String>) draft);
            }
        }

        ArrayList<ProcessStep> cleanedSteps = new ArrayList<>();
        if (stepProcess != null) {
            int size = stepProcess.size();

            IntStream.range(0, size).forEach(i -> {
                String step = valueAt(stepProcess, i);
                String person = valueAt(responsiblePerson, i);
                String type = valueAt(targetType, i);
                String dayValue = valueAt(days, i);

                if (step.isEmpty() && person.isEmpty() && type.isEmpty() && dayValue.isEmpty()) {
                    return;
                }

                ProcessStep processStep = new ProcessStep();
                processStep.setStepProcess(step);
                processStep.setResponsiblePerson(person);
                processStep.setTargetType(type);
                processStep.setDays(parseDays(dayValue));
                cleanedSteps.add(processStep);
            });
        }

        O2DConfig config = repository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/admin/fms-list";
        }
        config.setOrderDetails(cleanedOrderDetails);
        config.setProcessDetails(cleanedSteps);
        config.setConfigured(true);
        repository.save(config);

        session.removeAttribute("orderDetailsDraft");
        session.removeAttribute("folderIdDraft");

        String returnUrlVal = (String) session.getAttribute("returnUrlDraft");
        session.removeAttribute("returnUrlDraft");
        if (returnUrlVal != null && !returnUrlVal.isEmpty()) {
            return "redirect:" + returnUrlVal;
        }
        return "redirect:/admin/fms-list";
    }

    @PostMapping("/fms-list/delete")
    public String deleteFolder(@RequestParam("id") String id, Authentication authentication) {
        User currentUser = authentication == null ? null : userRepository.findByUsername(authentication.getName());
        if (currentUser == null || !"SUPERADMIN".equals(currentUser.getRole())) {
            return "redirect:/admin/fms-list";
        }

        if (id != null && !id.isBlank()) {
            repository.deleteById(id);
        }
        return "redirect:/admin/fms-list";
    }

    private ArrayList<String> cleanOrderDetails(List<String> orderDetails) {
        ArrayList<String> cleaned = new ArrayList<>();
        if (orderDetails == null) {
            return cleaned;
        }
        for (String value : orderDetails) {
            if (value != null && !value.trim().isEmpty()) {
                cleaned.add(value.trim());
            }
        }
        return cleaned;
    }

    private Integer parseDays(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index >= values.size()) {
            return "";
        }
        String value = values.get(index);
        return value == null ? "" : value.trim();
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    // --- MANAGE ACCESS APIS ---

    @GetMapping("/api/folder-access")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.List<java.util.Map<String, Object>> getFolderAccess(@RequestParam("folderId") String folderId) {
        List<User> employees = userRepository.findByRole("EMPLOYEE");
        List<java.util.Map<String, Object>> result = new ArrayList<>();

        for (User emp : employees) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("username", emp.getUsername());
            map.put("role", emp.getRole());

            boolean hasAccess = false;
            if (emp.getPermissions() != null) {
                // Check for explicit FMS folder permission
                hasAccess = emp.getPermissions().contains("FMS:" + folderId);
            }
            map.put("hasAccess", hasAccess);
            result.add(map);
        }
        return result;
    }

    @PostMapping("/api/folder-access")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> saveFolderAccess(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        String folderId = (String) body.get("folderId");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Boolean> accessMap = (java.util.Map<String, Boolean>) body.get("access");

        if (folderId == null || accessMap == null) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }

        accessMap.forEach((username, shouldHaveAccess) -> {
            User emp = userRepository.findByUsername(username);
            if (emp != null) {
                ArrayList<String> perms = emp.getPermissions();
                if (perms == null)
                    perms = new ArrayList<>();

                String permString = "FMS:" + folderId;

                if (shouldHaveAccess) {
                    if (!perms.contains(permString)) {
                        perms.add(permString);
                    }
                } else {
                    perms.remove(permString);
                }

                emp.setPermissions(perms);
                userRepository.save(emp);
            }
        });

        return org.springframework.http.ResponseEntity.ok().build();
    }

    private List<O2DConfig> getAccessibleFolders(Authentication authentication) {
        if (authentication == null) {
            return new ArrayList<>();
        }

        User currentUser = userRepository.findByUsername(authentication.getName());
        if (currentUser == null) {
            return new ArrayList<>();
        }

        if ("SUPERADMIN".equals(currentUser.getRole())) {
            return repository.findAll();
        }

        if (!"ADMIN".equals(currentUser.getRole())) {
            return new ArrayList<>();
        }

        List<O2DConfig> all = repository.findAll();
        List<O2DConfig> filtered = new ArrayList<>();
        for (O2DConfig folder : all) {
            if (canAccessFolder(authentication, folder.getId())) {
                filtered.add(folder);
            }
        }
        return filtered;
    }

    private boolean canAccessFolder(Authentication authentication, String folderId) {
        if (authentication == null || folderId == null || folderId.isBlank()) {
            return false;
        }

        User currentUser = userRepository.findByUsername(authentication.getName());
        if (currentUser == null) {
            return false;
        }

        if ("SUPERADMIN".equals(currentUser.getRole())) {
            return true;
        }

        if (!"ADMIN".equals(currentUser.getRole())) {
            return false;
        }

        List<String> perms = currentUser.getPermissions();
        return perms != null && perms.contains("ADMIN_FMS:" + folderId);
    }
}
