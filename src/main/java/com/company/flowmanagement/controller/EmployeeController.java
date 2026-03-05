package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.O2DConfigRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;
import com.company.flowmanagement.model.OrderEntry;
import com.company.flowmanagement.model.PlanningEntry;
import com.company.flowmanagement.repository.OrderEntryRepository;
import com.company.flowmanagement.repository.PlanningEntryRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.company.flowmanagement.service.TaskService;
import com.company.flowmanagement.repository.UserRepository;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.model.Task;
import com.company.flowmanagement.model.ProcessStep;
import java.time.LocalDate;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final O2DConfigRepository o2dConfigRepository;
    private final OrderEntryRepository orderEntryRepository;
    private final PlanningEntryRepository planningEntryRepository;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    private final com.company.flowmanagement.service.EmployeeService employeeService;

    public EmployeeController(EmployeeRepository employeeRepository, O2DConfigRepository o2dConfigRepository,
            OrderEntryRepository orderEntryRepository, PlanningEntryRepository planningEntryRepository,
            TaskService taskService, UserRepository userRepository,
            com.company.flowmanagement.service.EmployeeService employeeService) {
        this.employeeRepository = employeeRepository;
        this.o2dConfigRepository = o2dConfigRepository;
        this.orderEntryRepository = orderEntryRepository;
        this.planningEntryRepository = planningEntryRepository;
        this.taskService = taskService;
        this.userRepository = userRepository;
        this.employeeService = employeeService;
    }

    @GetMapping("/dashboard")
    public String employeeDashboard(Authentication authentication) {
        String username = authentication.getName();
        Map<String, Object> context = employeeService.getEmployeeContext(username);

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) context.get("permissions");

        if (permissions != null) {
            // 1. Task Manager (Priority)
            if (permissions.contains("TASK_MANAGER")) {
                return "redirect:/employee/task-manager";
            }

            // 2. First FMS Folder
            @SuppressWarnings("unchecked")
            List<com.company.flowmanagement.model.O2DConfig> folders = (List<com.company.flowmanagement.model.O2DConfig>) context
                    .get("fmsFolders");
            if (folders != null && !folders.isEmpty()) {
                return "redirect:/employee/fms/" + folders.get(0).getId();
            }

            // 3. Order Entry
            if (permissions.contains("ORDER_ENTRY")
                    || permissions.stream().anyMatch(p -> p.startsWith("ORDER_ENTRY:"))) {
                return "redirect:/employee/order-entry";
            }
        }

        // Fallback or No permissions - allow dashboard template to show status
        return "employee-dashboard";
    }

    @GetMapping("/order-entry")
    public String orderEntry(@RequestParam(name = "folderId", required = false) String folderId,
            @RequestParam(name = "entryId", required = false) String entryId,
            @RequestParam(name = "planOrderId", required = false) String planOrderId,
            @RequestParam(name = "planStart", required = false) String planStart,
            @RequestParam(name = "planning", required = false) Boolean planning,
            @RequestParam(name = "saved", required = false) Boolean saved,
            Model model, Authentication authentication) {

        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        model.addAttribute("superadminView", false);
        model.addAttribute("orderEntryBasePath", "/employee/order-entry");
        model.addAttribute("orderEntryEntryPath", "/employee/order-entry/entry");
        model.addAttribute("orderEntryPlanningPath", "/employee/order-entry/planning");
        model.addAttribute("orderEntryPlanningStatusPath", "/employee/order-entry/planning-status");
        model.addAttribute("orderEntryFetchEntryPath", "/employee/order-entry/entry");
        model.addAttribute("orderEntryPlanningResultPath", "/employee/planning-result");

        O2DConfig config = null;

        // Use the context we just loaded to get authorized folders
        @SuppressWarnings("unchecked")
        List<O2DConfig> authorizedFolders = (List<O2DConfig>) model.getAttribute("orderEntryFolders");

        if (folderId != null && !folderId.isBlank()) {
            config = o2dConfigRepository.findById(folderId).orElse(null);
            // Verify access to requested folder (though interceptor should have caught
            // this, good for safety)
            if (config != null && authorizedFolders != null) {
                boolean hasAccess = authorizedFolders.stream().anyMatch(f -> f.getId().equals(folderId));
                if (!hasAccess) {
                    config = null; // Deny access to unauthorized folder
                }
            }
        }

        // Default to first authorized folder
        if (config == null && authorizedFolders != null && !authorizedFolders.isEmpty()) {
            config = authorizedFolders.get(0);
        }

        if (config != null) {
            model.addAttribute("orderDetails", config.getOrderDetails());
            model.addAttribute("selectedFolderId", config.getId());
            model.addAttribute("configOrderId", config.getOrderId());
            model.addAttribute("configCustomerName", config.getCustomerName());
            model.addAttribute("configCompanyName", config.getCompanyName());
            model.addAttribute("configRawMaterial", config.getRawMaterial());
            model.addAttribute("configQuantity", config.getQuantity());
            model.addAttribute("configCDD", config.getCDD());
            model.addAttribute("configMPD", config.getMPD());
            model.addAttribute("configStartDate", config.getStartDate());
            model.addAttribute("processDetails", config.getProcessDetails());

            List<String> responsibleOptions = new ArrayList<>();
            List<com.company.flowmanagement.model.Employee> allEmployees = employeeRepository.findAll();
            for (var emp : allEmployees) {
                if (emp.getName() != null && !emp.getName().isBlank() &&
                        (emp.getStatus() == null || !emp.getStatus().equals("Deleted"))) {
                    responsibleOptions.add(emp.getName());
                }
            }
            if (responsibleOptions.isEmpty()) {
                responsibleOptions.add("Employee");
            }
            model.addAttribute("responsibleOptions", responsibleOptions);

            List<OrderEntry> entries = orderEntryRepository.findByFolderIdOrderByCreatedAtDesc(config.getId());
            model.addAttribute("orderEntries", entries);
            model.addAttribute("totalOrders", entries.size());
            model.addAttribute("totalOrders", entries.size());
            int completedCount = 0;
            int pendingCount = 0;

            int maxId = 0;
            for (OrderEntry e : entries) {
                if (e.getOrderId() != null && e.getOrderId().startsWith("O2D-")) {
                    String s = e.getOrderId().substring(4).replaceAll("[^0-9]", "");
                    if (!s.isEmpty()) {
                        try {
                            int val = Integer.parseInt(s);
                            if (val > maxId)
                                maxId = val;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            model.addAttribute("nextOrderId", "O2D-" + (maxId + 1));

            Map<String, OrderEntry> latestEntriesMap = new LinkedHashMap<>();
            for (OrderEntry entry : entries) {
                if (entry.getOrderId() != null && !latestEntriesMap.containsKey(entry.getOrderId())) {
                    latestEntriesMap.put(entry.getOrderId(), entry);
                }
            }

            List<Map<String, Object>> entryRows = new ArrayList<>();
            int sr = 1;
            List<String> pendingOrderIds = new ArrayList<>();

            // Build a map: orderId -> PlanningEntry
            Map<String, PlanningEntry> planMap = new java.util.HashMap<>();
            try {
                List<PlanningEntry> allPlans = planningEntryRepository
                        .findByFolderIdOrderByCreatedAtAsc(config.getId());
                for (PlanningEntry plan : allPlans) {
                    if (plan.getOrderId() != null) {
                        planMap.put(plan.getOrderId().trim(), plan);
                    }
                }
            } catch (Exception ignored) {
            }

            for (OrderEntry entry : latestEntriesMap.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String orderIdTrim = entry.getOrderId() != null ? entry.getOrderId().trim() : "";
                PlanningEntry orderPlan = planMap.get(orderIdTrim);

                row.put("sr", sr++);
                row.put("entryId", entry.getId());
                row.put("orderId", entry.getOrderId());
                List<String> values = new ArrayList<>();
                if (config.getOrderDetails() != null) {
                    for (String detail : config.getOrderDetails()) {
                        String normalizedDetail = normalizeKey(detail);
                        String value = "";
                        if (entry.getFields() != null) {
                            if (entry.getFields().containsKey(normalizedDetail)) {
                                value = entry.getFields().get(normalizedDetail);
                            } else {
                                for (Map.Entry<String, String> field : entry.getFields().entrySet()) {
                                    if (normalizeKey(field.getKey()).equals(normalizedDetail)) {
                                        value = field.getValue();
                                        break;
                                    }
                                }
                            }
                        }
                        values.add(value == null ? "" : value);
                    }
                }
                row.put("values", values);
                row.put("customerName", findFieldValue(entry.getFields(), "Customer Name", "customer_name"));
                row.put("companyName", findFieldValue(entry.getFields(), "Company Name", "company_name"));

                // Add planning start date
                row.put("plannedAt",
                        orderPlan != null && orderPlan.getStartDate() != null ? orderPlan.getStartDate() : "-");

                String planningStatusAttr = findFieldValue(entry.getFields(), "Planning Status", "planning_status");
                if (planningStatusAttr == null || planningStatusAttr.isBlank() || "-".equals(planningStatusAttr)) {
                    planningStatusAttr = "Pending";
                }

                // --- Calculate Overall Status from individual planning entry ---
                String displayStatus = planningStatusAttr;
                if (orderPlan != null) {
                    boolean allStepCompleted = true;

                    Map<String, String> statuses = orderPlan.getStepStatuses();
                    List<ProcessStep> configSteps = config.getProcessDetails();

                    if (configSteps != null && !configSteps.isEmpty()) {
                        for (ProcessStep step : configSteps) {
                            String s = (statuses != null) ? statuses.get(step.getStepProcess()) : null;
                            if (!"Completed".equalsIgnoreCase(s)) {
                                allStepCompleted = false;
                            }
                        }
                    } else {
                        allStepCompleted = false;
                    }

                    if (allStepCompleted) {
                        displayStatus = "Completed";
                    } else {
                        // If we have a PlanningEntry, it's "Planned"
                        displayStatus = "Planned";
                    }
                }

                if ("Completed".equalsIgnoreCase(displayStatus) || "Planning".equalsIgnoreCase(displayStatus)
                        || "Planned".equalsIgnoreCase(displayStatus)) {
                    completedCount++;
                } else {
                    pendingCount++;
                }
                row.put("planningStatus", displayStatus);
                if ("Pending".equalsIgnoreCase(displayStatus) && !orderIdTrim.isEmpty()) {
                    if (!pendingOrderIds.contains(orderIdTrim)) {
                        pendingOrderIds.add(orderIdTrim);
                    }
                }
                entryRows.add(row);
            }
            model.addAttribute("orderEntryRows", entryRows);
            model.addAttribute("completedOrders", completedCount);
            model.addAttribute("pendingOrders", pendingCount);
            model.addAttribute("pendingOrderIds", pendingOrderIds);
            if (entryId != null && !entryId.isBlank()) {
                OrderEntry selected = orderEntryRepository.findById(entryId).orElse(null);
                if (selected != null) {
                    model.addAttribute("selectedEntry", selected);
                    model.addAttribute("selectedEntryFields", selected.getFields());
                }
            } else if (planOrderId != null && !planOrderId.isBlank()) {
                OrderEntry selected = orderEntryRepository
                        .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(config.getId(), planOrderId.trim());
                if (selected != null) {
                    model.addAttribute("selectedEntry", selected);
                    model.addAttribute("selectedEntryFields", selected.getFields());
                }
            } else if (!entries.isEmpty()) {
                OrderEntry latest = entries.get(0);
                model.addAttribute("selectedEntry", latest);
                model.addAttribute("selectedEntryFields", latest.getFields());
            }

        }
        model.addAttribute("saved", saved != null && saved);
        return "employee-order-entry";
    }

    @GetMapping("/planning-edit")
    public String planningEdit(@RequestParam("folderId") String folderId,
            @RequestParam("planOrderId") String planOrderId,
            Model model, Authentication authentication) {

        O2DConfig config = o2dConfigRepository.findById(folderId.trim()).orElse(null);
        if (config == null)
            return "redirect:/employee/order-entry";

        PlanningEntry existing = planningEntryRepository
                .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId.trim(), planOrderId.trim());

        OrderEntry orderEntry = orderEntryRepository
                .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId.trim(), planOrderId.trim());

        List<String> responsibleOptions = new ArrayList<>();
        List<com.company.flowmanagement.model.Employee> allEmployees = employeeRepository.findAll();
        for (var emp : allEmployees) {
            if (emp.getName() != null && !emp.getName().isBlank() &&
                    (emp.getStatus() == null || !emp.getStatus().equals("Deleted"))) {
                responsibleOptions.add(emp.getName());
            }
        }
        if (responsibleOptions.isEmpty())
            responsibleOptions.add("Employee");

        model.addAttribute("folderId", folderId);
        model.addAttribute("planOrderId", planOrderId);
        model.addAttribute("currentStartDate", existing != null ? existing.getStartDate() : "");
        model.addAttribute("orderDetails", config.getOrderDetails());
        model.addAttribute("processDetails", config.getProcessDetails());
        model.addAttribute("responsibleOptions", responsibleOptions);
        model.addAttribute("selectedEntryFields", orderEntry != null ? orderEntry.getFields() : null);
        return "employee-planning-edit";
    }

    @GetMapping("/planning-result")
    public String planningResult(@RequestParam("folderId") String folderId,
            @RequestParam("planOrderId") String planOrderId,
            Model model, Authentication authentication) {

        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));

        O2DConfig config = o2dConfigRepository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/employee/dashboard";
        }

        PlanningEntry planningEntry = planningEntryRepository
                .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId, planOrderId.trim());

        if (planningEntry == null) {
            model.addAttribute("error", "No planning found for Order: " + planOrderId);
            return "employee-planning-result";
        }

        model.addAttribute("config", config);
        model.addAttribute("planOrderId", planOrderId);
        model.addAttribute("planningStart", planningEntry.getStartDate());

        // Submission timestamp: when the user submitted the planning form
        String submittedAt = "-";
        if (planningEntry.getCreatedAt() != null) {
            submittedAt = java.time.format.DateTimeFormatter
                    .ofPattern("dd-MMM-yyyy HH:mm")
                    .withZone(java.time.ZoneId.of("Asia/Kolkata"))
                    .format(planningEntry.getCreatedAt());
        }
        model.addAttribute("submittedAt", submittedAt);

        OrderEntry orderEntry = orderEntryRepository
                .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId, planOrderId.trim());
        Map<String, String> entryFields = orderEntry != null ? orderEntry.getFields() : null;
        model.addAttribute("customerName", findFieldValue(entryFields, "Customer Name", "customer_name"));
        model.addAttribute("companyName", findFieldValue(entryFields, "Company Name", "company_name"));

        List<Map<String, String>> rows = new ArrayList<>();
        String planningStart = planningEntry.getStartDate();
        if (planningStart != null && !planningStart.isBlank()) {
            try {
                var startDate = java.time.LocalDate.parse(planningStart.trim());
                int planningSr = 1;
                for (var step : config.getProcessDetails()) {
                    var row = new LinkedHashMap<String, String>();
                    row.put("sr", String.valueOf(planningSr++));
                    row.put("stepProcess", step.getStepProcess());
                    row.put("responsiblePerson", step.getResponsiblePerson());
                    row.put("targetType", step.getTargetType());
                    row.put("days", step.getDays() == null ? "" : String.valueOf(step.getDays()));
                    String savedTargetDate = planningEntry.getStepTargetDates() != null
                            ? planningEntry.getStepTargetDates().get(step.getStepProcess())
                            : null;
                    if (savedTargetDate != null && !savedTargetDate.isBlank()) {
                        row.put("targetDate", savedTargetDate);
                    } else if (step.getDays() != null) {
                        row.put("targetDate", startDate.plusDays(step.getDays()).toString());
                    } else {
                        row.put("targetDate", "-");
                    }
                    row.put("status", step.getStatus() != null ? step.getStatus() : "Pending");
                    rows.add(row);
                }
            } catch (Exception ignored) {
            }
        }
        model.addAttribute("rows", rows);

        return "employee-planning-result";
    }

    @GetMapping("/fms")
    public String fmsMain(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        return "employee-fms";
    }

    @GetMapping("/fms/folder1")
    public String fmsFolder1(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        return "employee-fms-folder1";
    }

    @GetMapping("/fms/folder2")
    public String fmsFolder2(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        return "employee-fms-folder2";
    }

    @GetMapping("/fms/{folderId}")
    public String fmsDynamicFolder(@PathVariable("folderId") String folderId, Model model,
            Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));

        // Check if employee has permission for this folder
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) model.getAttribute("permissions");
        if (permissions == null || !permissions.contains("FMS:" + folderId)) {
            return "redirect:/employee/dashboard";
        }

        // Load folder-specific template, fallback to generic template
        Optional<O2DConfig> folderOpt = o2dConfigRepository.findById(folderId);
        if (folderOpt.isEmpty()) {
            return "redirect:/employee/dashboard";
        }

        O2DConfig config = folderOpt.get();
        model.addAttribute("currentFolder", config);

        // --- TASK MANAGER INTEGRATION ---
        model.addAttribute("allEmployees", employeeService.getAllEmployees());
        User user = userRepository.findByUsername(username);

        if (user != null) {
            // Get dashboard stats
            Map<String, Object> stats = taskService.getDashboardStats(username);
            model.addAttribute("dashboardStats", stats);

            // Get client-project map
            Map<String, List<com.company.flowmanagement.model.Project>> clientProjectMap = taskService
                    .getClientProjectMap(username);
            model.addAttribute("clientProjectMap", clientProjectMap);

            // Get user tasks (standard tasks)
            Map<String, List<Task>> tasks = taskService.getUserTasks(username);
            model.addAttribute("myTasks", tasks.get("myTasks"));
            model.addAttribute("completedTasks", tasks.get("completedTasks"));
            model.addAttribute("delegatedTasks", tasks.get("delegatedTasks"));

            // --- FMS ORDER PROCESS TASKS ---
            // 1. Fetch ALL planning entries for this folder
            List<PlanningEntry> allPlannings = planningEntryRepository.findByFolderIdOrderByCreatedAtAsc(folderId);

            // 2. DEDUPLICATE: Only process the LATEST PlanningEntry for each unique OrderID
            Map<String, PlanningEntry> latestPlanningByOrder = new LinkedHashMap<>();
            if (allPlannings != null) {
                for (PlanningEntry plan : allPlannings) {
                    String ordId = plan.getOrderId();
                    if (ordId != null) {
                        ordId = ordId.trim();
                        if (!ordId.isEmpty()) {
                            latestPlanningByOrder.put(ordId, plan);
                        }
                    }
                }
            }

            // 3. Fetch all order entries to get details (Customer, Company etc.)
            List<OrderEntry> allEntries = orderEntryRepository.findByFolderIdOrderByCreatedAtDesc(folderId);

            // Map OrderID -> Latest OrderEntry
            Map<String, OrderEntry> latestOrders = new LinkedHashMap<>();
            if (allEntries != null) {
                for (OrderEntry entry : allEntries) {
                    if (entry.getOrderId() != null && !latestOrders.containsKey(entry.getOrderId())) {
                        latestOrders.put(entry.getOrderId(), entry);
                    }
                }
            }

            List<Map<String, String>> fmsOverdueTasks = new ArrayList<>();
            List<Map<String, String>> fmsCompletedTasks = new ArrayList<>();
            Map<String, List<Map<String, String>>> fmsPendingTasksByStep = new LinkedHashMap<>();

            try {
                if (config.getProcessDetails() != null) {
                    int taskSr = 1;

                    // Iterate over DEDUPLICATED latest planning instances
                    for (PlanningEntry plan : latestPlanningByOrder.values()) {
                        String orderId = plan.getOrderId();
                        String startDateStr = plan.getStartDate();

                        if (orderId == null || startDateStr == null)
                            continue;

                        LocalDate startDate = null;
                        try {
                            startDate = LocalDate.parse(startDateStr);
                        } catch (Exception e) {
                            continue;
                        }

                        OrderEntry orderDetails = latestOrders.get(orderId);
                        String customerName = findFieldValue(orderDetails != null ? orderDetails.getFields() : null,
                                "Customer Name", "customer_name");
                        String companyName = findFieldValue(orderDetails != null ? orderDetails.getFields() : null,
                                "Company Name", "company_name");

                        String employeeDisplayName = (String) model.getAttribute("employeeName");

                        // Iterate over GLOBAL process steps (READ ONLY)
                        for (ProcessStep step : config.getProcessDetails()) {
                            String stepName = step.getStepProcess();

                            // ARCHITECTURE FIX: Read responsible person from PlanningEntry (per order)
                            // Fallback to O2DConfig for migration safety.
                            String resp = (plan.getStepResponsiblePersons() != null)
                                    ? plan.getStepResponsiblePersons().get(stepName)
                                    : null;

                            if (resp == null || resp.isBlank()) {
                                resp = step.getResponsiblePerson();
                            }

                            // Match step's responsiblePerson against login user OR display name
                            boolean isAssignedToMe = resp != null && (resp.trim().equalsIgnoreCase(username.trim()) ||
                                    (employeeDisplayName != null
                                            && resp.trim().equalsIgnoreCase(employeeDisplayName.trim())));

                            if (isAssignedToMe) {
                                Map<String, String> taskMap = new LinkedHashMap<>();
                                taskMap.put("sr", String.valueOf(taskSr++));
                                taskMap.put("planningEntryId", plan.getId());
                                taskMap.put("startDate", startDateStr);
                                taskMap.put("orderId", orderId);
                                taskMap.put("customerName", customerName);
                                taskMap.put("companyName", companyName);
                                taskMap.put("responsiblePerson", (resp != null ? resp : "-"));

                                taskMap.put("taskName", stepName);

                                // Target Date priority: 1. saved planning value, 2. calculated days, 3. order
                                // entry date
                                String savedTargetDate = plan.getStepTargetDates() != null
                                        ? plan.getStepTargetDates().get(stepName)
                                        : null;
                                if (savedTargetDate != null && !savedTargetDate.isBlank()) {
                                    taskMap.put("targetDate", savedTargetDate);
                                } else if (step.getDays() != null) {
                                    taskMap.put("targetDate", startDate.plusDays(step.getDays()).toString());
                                } else {
                                    Map<String, String> oFields = orderDetails != null ? orderDetails.getFields()
                                            : null;
                                    String orderDate = findFieldValue(oFields, "Target Date", "target_date");
                                    if (orderDate == null || orderDate.equals("-"))
                                        orderDate = findFieldValue(oFields, "Delivery Date", "delivery_date");
                                    if (orderDate == null || orderDate.equals("-"))
                                        orderDate = findFieldValue(oFields, "Due Date", "due_date");
                                    taskMap.put("targetDate",
                                            (orderDate != null && !orderDate.equals("-")) ? orderDate : "-");
                                }

                                // ISOLATION FIX: Read status/completion ONLY from the specific PlanningEntry
                                Map<String, String> stepStatuses = plan.getStepStatuses();
                                Map<String, String> stepCompletionDates = plan.getStepCompletionDates();

                                String status = (stepStatuses != null) ? stepStatuses.get(stepName) : null;
                                if (status == null || status.isBlank()) {
                                    status = "In Progress";
                                }

                                String completionDate = (stepCompletionDates != null)
                                        ? stepCompletionDates.get(stepName)
                                        : "";

                                taskMap.put("completionDate", completionDate);
                                taskMap.put("status", status);
                                taskMap.put("pdf", "");

                                // Categorize Overdue
                                if (taskMap.get("targetDate") != null && !taskMap.get("targetDate").equals("-")) {
                                    try {
                                        LocalDate target = LocalDate.parse(taskMap.get("targetDate"));
                                        if (target.isBefore(LocalDate.now()) && !"Completed".equalsIgnoreCase(status)) {
                                            taskMap.put("status", "Overdue");
                                            status = "Overdue";
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }

                                if ("Completed".equalsIgnoreCase(status)) {
                                    fmsCompletedTasks.add(taskMap);
                                } else {
                                    fmsPendingTasksByStep.computeIfAbsent(stepName, k -> new ArrayList<>())
                                            .add(taskMap);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("CRITICAL ERROR in FMS Folder Task Logic: " + e.getMessage());
                e.printStackTrace();
            }

            model.addAttribute("fmsOverdueTasks", fmsOverdueTasks);
            model.addAttribute("fmsCompletedTasks", fmsCompletedTasks);
            model.addAttribute("fmsPendingTasksByStep", fmsPendingTasksByStep);

            // ── FMS DASHBOARD STATS ──────────────────────────────────────────────────
            int fmsInProgress = 0;
            int fmsOverdueCount = 0;
            for (List<Map<String, String>> stepTasks : fmsPendingTasksByStep.values()) {
                for (Map<String, String> task : stepTasks) {
                    if ("Overdue".equalsIgnoreCase(task.get("status")))
                        fmsOverdueCount++;
                    else
                        fmsInProgress++;
                }
            }

            int fmsOnTime = 0;
            int fmsDelayed = 0;
            double fmsAtsTotalScore = 0;
            int fmsAtsCount = 0;

            for (Map<String, String> task : fmsCompletedTasks) {
                String targetDateStr = task.get("targetDate");
                String completionDateStr = task.get("completionDate");
                String startDateStr2 = task.get("startDate");

                if (completionDateStr != null && !completionDateStr.isBlank() && targetDateStr != null
                        && !targetDateStr.equals("-")) {
                    try {
                        LocalDate target = LocalDate.parse(targetDateStr);
                        LocalDate completion = LocalDate.parse(completionDateStr);
                        if (!completion.isAfter(target)) {
                            fmsOnTime++;
                        } else {
                            fmsDelayed++;
                            task.put("status", "Delayed");
                        }

                        if (startDateStr2 != null && !startDateStr2.isBlank()) {
                            LocalDate sd = LocalDate.parse(startDateStr2);
                            double ats = 0;
                            if (!completion.isAfter(target)) {
                                ats = 100;
                            } else {
                                long compStartDiff = java.time.temporal.ChronoUnit.DAYS.between(sd, completion);
                                long targetStartDiff = java.time.temporal.ChronoUnit.DAYS.between(sd, target);
                                if (sd.isEqual(target)) {
                                    ats = compStartDiff > 0 ? (1.0 / compStartDiff) * 100 : 0;
                                } else {
                                    ats = compStartDiff > 0 ? ((double) targetStartDiff / compStartDiff) * 100 : 0;
                                }
                            }
                            fmsAtsTotalScore += Math.max(0, Math.min(100, ats));
                            fmsAtsCount++;
                        }
                    } catch (Exception e2) {
                        fmsOnTime++;
                    }
                } else if (targetDateStr != null && !targetDateStr.equals("-")) {
                    fmsOnTime++;
                }
            }

            if (fmsOverdueCount > 0) {
                fmsAtsCount += fmsOverdueCount;
            }

            int fmsTotal = fmsInProgress + fmsOverdueCount + fmsCompletedTasks.size();
            int otcPercent = fmsTotal > 0 ? (fmsOnTime * 100 / fmsTotal) : 0;
            String atsDisplay = fmsAtsCount > 0 ? String.format("%.0f%%", fmsAtsTotalScore / fmsAtsCount) : "-";

            List<Map<String, Object>> chartData = new ArrayList<>();
            if (fmsOnTime > 0)
                chartData.add(Map.of("name", "On Time", "value", fmsOnTime, "color", "#22c55e"));
            if (fmsDelayed > 0)
                chartData.add(Map.of("name", "Delayed", "value", fmsDelayed, "color", "#f59e0b"));
            if (fmsOverdueCount > 0)
                chartData.add(Map.of("name", "Overdue", "value", fmsOverdueCount, "color", "#ef4444"));

            Map<String, Object> fmsDashboardStats = new HashMap<>();
            fmsDashboardStats.put("totalTasks", fmsTotal);
            fmsDashboardStats.put("onTime", fmsOnTime);
            fmsDashboardStats.put("overdue", fmsOverdueCount);
            fmsDashboardStats.put("delayed", fmsDelayed);
            fmsDashboardStats.put("inProgress", fmsInProgress);
            fmsDashboardStats.put("otcScore", otcPercent + "%");
            fmsDashboardStats.put("atsScore", atsDisplay);
            fmsDashboardStats.put("chart_data", chartData);
            fmsDashboardStats.put("otc_score", otcPercent + "%");
            model.addAttribute("dashboardStats", fmsDashboardStats);
        }

        return "employee-fms-folder";
    }

    /**
     * Mark an FMS planning step as Completed.
     * Called by the tick button on the FMS folder page.
     */
    @org.springframework.web.bind.annotation.PatchMapping("/fms/api/complete-step")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> completeFmsStep(
            @RequestParam("planningEntryId") String planningEntryId,
            @RequestParam("stepName") String stepName) {
        try {
            Optional<com.company.flowmanagement.model.PlanningEntry> optPlan = planningEntryRepository
                    .findById(planningEntryId);
            if (optPlan.isEmpty()) {
                return org.springframework.http.ResponseEntity.status(404)
                        .body(Map.of("error", "Planning entry not found"));
            }
            com.company.flowmanagement.model.PlanningEntry plan = optPlan.get();

            // Find matching step in the config
            Optional<com.company.flowmanagement.model.O2DConfig> configOpt = o2dConfigRepository
                    .findById(plan.getFolderId());
            if (configOpt.isEmpty()) {
                return org.springframework.http.ResponseEntity.status(404)
                        .body(Map.of("error", "FMS folder config not found"));
            }

            // Store status in the specific planning entry, NOT the global config
            if (plan.getStepStatuses() == null) {
                plan.setStepStatuses(new java.util.HashMap<>());
            }
            if (plan.getStepCompletionDates() == null) {
                plan.setStepCompletionDates(new java.util.HashMap<>());
            }

            plan.getStepStatuses().put(stepName, "Completed");
            plan.getStepCompletionDates().put(stepName, LocalDate.now().toString());

            planningEntryRepository.save(plan);
            return org.springframework.http.ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/order-entry/entry")
    @ResponseBody
    public OrderEntry fetchEntry(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId) {
        if (folderId == null || folderId.isBlank() || orderId == null || orderId.isBlank()) {
            return null;
        }
        return orderEntryRepository.findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId.trim(), orderId.trim());
    }

    @PostMapping("/order-entry/entry")
    public String createOrderEntry(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId,
            @RequestParam Map<String, String> params) {
        if (folderId == null || folderId.isBlank()) {
            return "redirect:/employee/order-entry";
        }
        Map<String, String> fields = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("field_")) {
                fields.put(key.substring("field_".length()), value);
            }
        });

        OrderEntry entry = new OrderEntry();
        entry.setFolderId(folderId.trim());
        entry.setOrderId(orderId == null ? "" : orderId.trim());
        entry.setFields(fields);
        entry.setCreatedAt(Instant.now());
        orderEntryRepository.save(entry);

        return "redirect:/employee/order-entry?folderId=" + folderId + "&entryId=" + entry.getId() + "&saved=true";
    }

    @PostMapping("/order-entry/planning")
    public String submitPlanning(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "responsiblePerson", required = false) List<String> responsiblePersons,
            @RequestParam(name = "targetDate", required = false) List<String> targetDates) {

        if (folderId == null || folderId.isBlank()) {
            return "redirect:/employee/order-entry";
        }

        String safeFolder = folderId.trim();
        String safeOrder = orderId == null ? "" : orderId.trim();
        String safeStart = startDate == null ? "" : startDate.trim();

        // 1. Save Planning Entry
        if (!safeStart.isBlank()) {
            PlanningEntry planningEntry = new PlanningEntry();
            planningEntry.setFolderId(safeFolder);
            planningEntry.setOrderId(safeOrder);
            planningEntry.setStartDate(safeStart);

            // Persist per-step target dates (manual and automatic) submitted by form
            O2DConfig cfgForTargets = o2dConfigRepository.findById(safeFolder).orElse(null);
            if (cfgForTargets != null && cfgForTargets.getProcessDetails() != null
                    && targetDates != null && !targetDates.isEmpty()) {
                Map<String, String> stepTargetDates = new LinkedHashMap<>();
                int limit = Math.min(cfgForTargets.getProcessDetails().size(), targetDates.size());
                for (int i = 0; i < limit; i++) {
                    ProcessStep step = cfgForTargets.getProcessDetails().get(i);
                    if (step == null || step.getStepProcess() == null || step.getStepProcess().isBlank()) {
                        continue;
                    }
                    String postedDate = targetDates.get(i) == null ? "" : targetDates.get(i).trim();
                    if (!postedDate.isBlank()) {
                        stepTargetDates.put(step.getStepProcess(), postedDate);
                    }
                }
                planningEntry.setStepTargetDates(stepTargetDates);
            }

            // Correct handling of responsible persons: Save ONLY into PlanningEntry
            if (cfgForTargets != null && cfgForTargets.getProcessDetails() != null
                    && responsiblePersons != null && !responsiblePersons.isEmpty()) {
                Map<String, String> stepResps = new LinkedHashMap<>();
                int limit = Math.min(cfgForTargets.getProcessDetails().size(), responsiblePersons.size());
                for (int i = 0; i < limit; i++) {
                    ProcessStep step = cfgForTargets.getProcessDetails().get(i);
                    if (step == null || step.getStepProcess() == null)
                        continue;
                    String postedResp = responsiblePersons.get(i) == null ? "" : responsiblePersons.get(i).trim();
                    if (!postedResp.isBlank()) {
                        stepResps.put(step.getStepProcess(), postedResp);
                    }
                }
                planningEntry.setStepResponsiblePersons(stepResps);
            }

            planningEntry.setCreatedAt(Instant.now());
            planningEntryRepository.save(planningEntry);

            // 2. ONLY UPDATE ORDER STATUS -> PLANNED if date is provided
            if (!safeOrder.isBlank()) {
                OrderEntry entry = orderEntryRepository
                        .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(safeFolder, safeOrder);

                if (entry != null) {
                    Map<String, String> fields = entry.getFields();
                    if (fields == null) {
                        fields = new LinkedHashMap<>();
                    }

                    fields.put("planning_status", "Planned");
                    entry.setFields(fields);

                    orderEntryRepository.save(entry);
                }
            }
        }

        // Redirect back to planning result so the updated Time Stamp is visible
        if (!safeOrder.isBlank())

        {
            return "redirect:/employee/planning-result?folderId=" + safeFolder + "&planOrderId=" + safeOrder;
        }
        return "redirect:/employee/order-entry?folderId=" + safeFolder;
    }

    @PostMapping("/order-entry/planning-status")
    public String updatePlanningStatus(@RequestParam("entryId") String entryId,
            @RequestParam("planningStatus") String planningStatus,
            @RequestParam("folderId") String folderId) {
        if (entryId == null || entryId.isBlank()) {
            return "redirect:/employee/order-entry?folderId=" + (folderId == null ? "" : folderId.trim());
        }
        OrderEntry entry = orderEntryRepository.findById(entryId.trim()).orElse(null);
        if (entry != null) {
            Map<String, String> fields = entry.getFields();
            if (fields == null) {
                fields = new LinkedHashMap<>();
            }
            String statusValue = planningStatus == null ? "" : planningStatus.trim();
            fields.put("planning_status", statusValue);
            entry.setFields(fields);
            orderEntryRepository.save(entry);
        }
        String safeFolder = folderId == null ? "" : folderId.trim();
        return "redirect:/employee/order-entry?folderId=" + safeFolder;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private static String findFieldValue(Map<String, String> fields, String label, String defaultKey) {

        if (fields == null || fields.isEmpty()) {
            return "-";
        }

        if (defaultKey != null && fields.containsKey(defaultKey)) {

            String val = fields.get(defaultKey);

            return (val == null || val.isBlank()) ? "-" : val;
        }

        String normalizedLabel = normalizeKey(label);

        for (Map.Entry<String, String> entry : fields.entrySet()) {

            if (normalizeKey(entry.getKey()).equals(normalizedLabel)) {

                String val = entry.getValue();

                return (val == null || val.isBlank()) ? "-" : val;
            }
        }

        return "-";
    }

    @PostMapping("/fms/bulk-complete")
    @ResponseBody
    public Map<String, Object> bulkCompleteFmsTasks(@RequestBody List<Map<String, String>> tasks) {
        Map<String, Object> response = new LinkedHashMap<>();
        int updatedCount = 0;
        int errorCount = 0;

        try {
            if (tasks == null || tasks.isEmpty()) {
                response.put("success", false);
                response.put("message", "No tasks selected.");
                response.put("updatedCount", 0);
                response.put("errorCount", 0);
                return response;
            }

            for (Map<String, String> task : tasks) {
                String planningId = task.get("planningId");
                String stepName = task.get("stepName");

                if (planningId == null || planningId.isBlank() || stepName == null || stepName.isBlank()) {
                    errorCount++;
                    continue;
                }

                // Fetch the PlanningEntry from database
                Optional<PlanningEntry> planningOptional = planningEntryRepository.findById(planningId);
                if (!planningOptional.isPresent()) {
                    errorCount++;
                    continue;
                }

                PlanningEntry planning = planningOptional.get();

                // Update step status to "Completed"
                if (planning.getStepStatuses() == null) {
                    planning.setStepStatuses(new LinkedHashMap<>());
                }
                planning.getStepStatuses().put(stepName, "Completed");

                // Update step completion date to today
                if (planning.getStepCompletionDates() == null) {
                    planning.setStepCompletionDates(new LinkedHashMap<>());
                }
                LocalDate today = LocalDate.now();
                planning.getStepCompletionDates().put(stepName, today.toString());

                // Save the updated entry
                planningEntryRepository.save(planning);
                updatedCount++;
            }

            response.put("success", true);
            response.put("message", "Successfully updated " + updatedCount + " task(s)");
            response.put("updatedCount", updatedCount);
            response.put("errorCount", errorCount);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error processing bulk completion: " + e.toString());
            response.put("updatedCount", updatedCount);
            response.put("errorCount", errorCount);
        }

        return response;
    }

}
