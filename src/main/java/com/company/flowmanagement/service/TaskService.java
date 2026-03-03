package com.company.flowmanagement.service;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.model.Project;
import com.company.flowmanagement.model.Task;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.ProjectRepository;
import com.company.flowmanagement.repository.TaskRepository;
import com.company.flowmanagement.repository.UserRepository;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.repository.PlanningEntryRepository;
import com.company.flowmanagement.repository.OrderEntryRepository;
import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.model.ProcessStep;
import com.company.flowmanagement.model.PlanningEntry;
import com.company.flowmanagement.model.OrderEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final O2DConfigRepository o2dConfigRepository;
    private final PlanningEntryRepository planningEntryRepository;
    private final OrderEntryRepository orderEntryRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
            EmployeeRepository employeeRepository, UserRepository userRepository,
            O2DConfigRepository o2dConfigRepository,
            PlanningEntryRepository planningEntryRepository,
            OrderEntryRepository orderEntryRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.o2dConfigRepository = o2dConfigRepository;
        this.planningEntryRepository = planningEntryRepository;
        this.orderEntryRepository = orderEntryRepository;
    }

    // Generate unique task ID
    public String generateTaskId() {
        long count = taskRepository.count();
        return String.format("TSK-%d", count + 1);
    }

    // Helper to get all tasks (DB + FMS)
    // Helper to get all tasks (DB + FMS)
    private List<Task> getAllTasksForUser(String username) {
        User user = userRepository.findByUsername(username);
        List<Task> allTasks = new ArrayList<>();

        // 1. Resolve Employee Info
        Employee employee = employeeRepository.findByName(username).orElse(null);
        String fullName = (employee != null) ? employee.getName() : null;
        String employeeCompany = (employee != null && employee.getDepartment() != null) ? employee.getDepartment() : "";

        String assignerName = "System";
        if (employee != null && employee.getAdminId() != null) {
            User admin = userRepository.findById(employee.getAdminId()).orElse(null);
            if (admin != null) {
                if (admin.getCompanyName() != null && !admin.getCompanyName().isBlank()) {
                    employeeCompany = admin.getCompanyName();
                    assignerName = admin.getCompanyName();
                } else if (admin.getUsername() != null && !admin.getUsername().isBlank()) {
                    employeeCompany = admin.getUsername();
                    assignerName = admin.getUsername();
                } else {
                    assignerName = "Admin";
                }
            }
        }

        // 2. DB Tasks (Assigned TO Me)
        if (user != null) {
            List<Task> assignedToMe = taskRepository.findByAssignedToIdOrderByCreatedAtDesc(user.getId());
            allTasks.addAll(assignedToMe);
        }

        // Final Sort by CreatedAt Desc
        allTasks.sort((a, b) -> {
            Instant t1 = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.MIN;
            Instant t2 = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.MIN;
            return t2.compareTo(t1);
        });

        // Auto-detect overdue: any "In Progress" task past its target date becomes
        // "Overdue"
        // Auto-correct: any "Completed" task finished after its target date becomes
        // "Delayed"
        LocalDate today = LocalDate.now();
        List<Task> toSave = new ArrayList<>();
        for (Task task : allTasks) {
            if (task.getTargetDate() == null)
                continue;
            try {
                LocalDate targetDate = LocalDate.parse(task.getTargetDate());

                // In Progress + past target → Overdue
                if ("In Progress".equalsIgnoreCase(task.getStatus()) && today.isAfter(targetDate)) {
                    task.setStatus("Overdue");
                    toSave.add(task);
                }

                // Completed + finished after target → Delayed
                if ("Completed".equalsIgnoreCase(task.getStatus()) && task.getCompletionDate() != null) {
                    LocalDate completionDate = LocalDate.parse(task.getCompletionDate());
                    if (completionDate.isAfter(targetDate)) {
                        task.setStatus("Delayed");
                        toSave.add(task);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (!toSave.isEmpty()) {
            taskRepository.saveAll(toSave);
        }

        // Calculate ATS for each task
        for (Task task : allTasks) {
            task.setAtsScore(calculateTaskATS(task));
        }

        return allTasks;
    }

    // Helper for fuzzy field matching
    private String findFieldValue(Map<String, String> fields, String label, String defaultKey) {
        if (fields == null)
            return "-";
        if (fields.containsKey(defaultKey))
            return fields.get(defaultKey);

        // Normalize
        String search = label.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String key = e.getKey().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (key.contains(search) || search.contains(key)) {
                return e.getValue();
            }
        }
        return "-";
    }

    // Get dashboard stats for a user
    public Map<String, Object> getDashboardStats(String username) {
        List<Task> allTasks = getAllTasksForUser(username);

        long totalTasks = allTasks.size();
        long completedCount = allTasks.stream()
                .filter(t -> "Completed".equalsIgnoreCase(t.getStatus())).count();
        long inProgressCount = allTasks.stream()
                .filter(t -> "In Progress".equalsIgnoreCase(t.getStatus())).count();
        long onTimeCount = allTasks.stream()
                .filter(t -> "On Time".equalsIgnoreCase(t.getStatus()) || "Completed".equalsIgnoreCase(t.getStatus()))
                .count();

        // Calculate OTC score (On Time Completion)
        long denominator = totalTasks - inProgressCount;
        double otcScore = denominator > 0 ? (double) onTimeCount / denominator * 100 : 0;
        String otcScoreStr = String.format("%.0f%%", otcScore);

        // Calculate ATS score (Average Task Score)
        List<Double> taskScores = allTasks.stream()
                .map(Task::getAtsScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        double atsAverage = !taskScores.isEmpty() ? taskScores.stream().mapToDouble(d -> d).average().orElse(0.0) : 0.0;
        String atsScoreStr = String.format("%.0f%%", atsAverage * 100);

        // Chart data
        Map<String, Long> statusCounts = allTasks.stream()
                .collect(Collectors.groupingBy(task -> task.getStatus() != null ? task.getStatus() : "PENDING",
                        Collectors.counting()));

        long onTimeTotal = statusCounts.getOrDefault("On Time", 0L) + completedCount;
        long overdueCount = statusCounts.getOrDefault("Overdue", 0L);
        long delayedCount = statusCounts.getOrDefault("Delayed", 0L);
        long inProgressCountMapped = statusCounts.getOrDefault("In Progress", 0L);
        long pendingCount = statusCounts.getOrDefault("PENDING", 0L);

        List<Map<String, Object>> chartData = Arrays.asList(
                Map.of("name", "On Time", "value", onTimeTotal, "color", "#22c55e"),
                Map.of("name", "Delayed", "value", delayedCount, "color", "#facc15"),
                Map.of("name", "Overdue", "value", overdueCount, "color", "#ef4444"),
                Map.of("name", "Pending", "value", pendingCount, "color", "#94a3b8"));

        Map<String, Object> response = new HashMap<>();
        response.put("total_tasks", totalTasks);
        response.put("on_time_count", onTimeCount);
        response.put("otc_score", otcScoreStr);
        response.put("ats_score", atsScoreStr);
        response.put("chart_data", chartData);
        response.put("totalTasks", totalTasks);
        response.put("onTime", onTimeTotal);
        response.put("overdue", overdueCount);
        response.put("delayed", delayedCount);
        response.put("inProgress", inProgressCountMapped);
        response.put("completed", completedCount);

        return response;
    }

    /**
     * ATS (Adherence to Schedule) Calculation — applied step by step:
     *
     * Rule 6: Status = Not in Progress / Pending → NULL
     * Rule 5: Status = Overdue → 0%
     * Rule 1: SD = CD = TD → 100%
     * Rule 4: TD >= CD (on time or early) → 100%
     * Rule 2: SD = TD, but CD > TD (late) → 1 / (CD − SD) days
     * Rule 3: SD ≠ TD, CD > TD (late) → (TD − SD) / (CD − SD)
     *
     * Edge case: If task was entered after its deadline (SD > TD),
     * we use TD as the effective start so the formula doesn't divide by zero.
     */
    private Double calculateTaskATS(Task task) {

        // Rule 6: Not started or pending → skip
        if (task.getStatus() == null
                || "Not in Progress".equalsIgnoreCase(task.getStatus())
                || "Pending".equalsIgnoreCase(task.getStatus())) {
            return null;
        }

        // Rule 5: Overdue → 0%
        if ("Overdue".equalsIgnoreCase(task.getStatus())) {
            return 0.0;
        }

        // Must have a completion date to score
        if (task.getCompletionDate() == null) {
            return null;
        }

        try {
            // SD = task creation date (when the task was assigned)
            LocalDate sd = task.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            // TD = target/deadline date
            LocalDate td = task.getTargetDate() != null ? LocalDate.parse(task.getTargetDate()) : sd;
            // CD = completion date
            LocalDate cd = LocalDate.parse(task.getCompletionDate());

            // If SD is after TD (task entered into system after deadline),
            // use TD as the effective start to keep the formula meaningful.
            if (sd.isAfter(td)) {
                sd = td;
            }

            // ── Step 1: Rule 1 — all three dates are the same ──────────────
            // Example: assigned today, deadline today, completed today → 100%
            if (sd.equals(cd) && cd.equals(td)) {
                return 1.0;
            }

            // ── Step 2: Rule 4 — completed on or before target ─────────────
            // Completed early or exactly on deadline → 100%
            if (!cd.isAfter(td)) {
                return 1.0;
            }

            // ── We are now in the LATE zone (CD > TD) ───────────────────────

            // ── Step 3: Rule 2 — SD = TD (zero-day window) ─────────────────
            // Same-day task that was completed late → ATS = 1 / days_late
            if (sd.equals(td)) {
                long daysLate = java.time.temporal.ChronoUnit.DAYS.between(sd, cd);
                return daysLate > 0 ? Math.min(1.0, 1.0 / daysLate) : 0.0;
            }

            // ── Step 4: Rule 3 — normal late case ─────────────────────────
            // ATS = (TD − SD) / (CD − SD) ∈ [0, 1]
            long plannedDays = java.time.temporal.ChronoUnit.DAYS.between(sd, td);
            long actualDays = java.time.temporal.ChronoUnit.DAYS.between(sd, cd);

            if (actualDays > 0) {
                double score = (double) plannedDays / actualDays;
                return Math.max(0.0, Math.min(1.0, score));
            }

            return 0.0;

        } catch (Exception e) {
            return null;
        }
    }

    // Get client-project map for a user
    public Map<String, List<Project>> getClientProjectMap(String username) {
        // User user = userRepository.findByUsername(username);
        // Look up db tasks plus FMS tasks to build this map dynamically if needed?
        // For now, keep existing logic (returns all projects)
        // OR better: return projects relevant to the user's tasks

        List<Project> allProjects = projectRepository.findAll();

        return allProjects.stream()
                .collect(Collectors.groupingBy(Project::getClientName));
    }

    // Get tasks for user
    public Map<String, List<Task>> getUserTasks(String username) {
        List<Task> allTasks = getAllTasksForUser(username);

        List<Task> myActive = allTasks.stream()
                .filter(t -> !"Completed".equalsIgnoreCase(t.getStatus())
                        && !"Delayed".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        List<Task> myCompleted = allTasks.stream()
                .filter(t -> "Completed".equalsIgnoreCase(t.getStatus())
                        || "Delayed".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        User user = userRepository.findByUsername(username);
        List<Task> delegated = user != null ? taskRepository.findDelegatedTasksByAssignedById(user.getId())
                : new ArrayList<>();

        return Map.of(
                "myTasks", myActive,
                "completedTasks", myCompleted,
                "delegatedTasks", delegated);
    }

    // Create task
    public Task createTask(Task task) {
        task.setTaskId(generateTaskId());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return taskRepository.save(task);
    }

    // Update task status
    public Task updateTaskStatus(String taskId, String status, String remarks, String completionDate,
            String completionFile) {
        // For now, all tasks use the same database repository
        // FMS tasks are now treated as regular tasks with sequential IDs

        Task task = taskRepository.findByTaskId(taskId);
        if (task == null && taskId.startsWith("TSK-FMS-")) {
            // Reconstruct the virtual task from FMS configs before saving to DB
            String[] parts = taskId.split("-");
            if (parts.length >= 4) {
                // TSK-FMS-[ORDERID]-[INDEX]
                // Index is the last part
                int stepIndex;
                try {
                    stepIndex = Integer.parseInt(parts[parts.length - 1]) - 1;
                } catch (Exception e) {
                    return null;
                }

                // Re-build orderId by joining parts between "FMS" and the index
                StringBuilder orderIdBuilder = new StringBuilder();
                for (int i = 2; i < parts.length - 1; i++) {
                    if (i > 2)
                        orderIdBuilder.append("-");
                    orderIdBuilder.append(parts[i]);
                }
                String orderId = orderIdBuilder.toString();

                // Scan for the configuration that owning this order
                for (O2DConfig config : o2dConfigRepository.findAll()) {
                    PlanningEntry plan = planningEntryRepository
                            .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(config.getId(), orderId);
                    if (plan != null && config.getProcessDetails().size() > stepIndex) {
                        ProcessStep step = config.getProcessDetails().get(stepIndex);
                        String responsiblePerson = step.getResponsiblePerson();
                        if (plan.getStepResponsiblePersons() != null && step.getStepProcess() != null
                                && !step.getStepProcess().isBlank()) {
                            String savedResponsible = plan.getStepResponsiblePersons().get(step.getStepProcess());
                            if (savedResponsible != null && !savedResponsible.isBlank()) {
                                responsiblePerson = savedResponsible;
                            }
                        }
                        task = new Task();
                        task.setTaskId(taskId);
                        task.setTitle(step.getStepProcess() + " (" + orderId + ")");
                        task.setProjectName(config.getName());
                        task.setClientName(config.getCompanyName() != null ? config.getCompanyName() : "FMS Client");
                        task.setAssignedToName(responsiblePerson);
                        task.setAssignedByName("Company Admin");
                        task.setTargetDate(plan.getStartDate()); // Simplified target date for reconstruction
                        break;
                    }
                }
            }
        }

        if (task != null) {
            // If being marked as "Completed", check if it's late → "Delayed"
            String finalStatus = status;
            if ("Completed".equalsIgnoreCase(status) && task.getTargetDate() != null && completionDate != null) {
                try {
                    LocalDate td = LocalDate.parse(task.getTargetDate());
                    LocalDate cd = LocalDate.parse(completionDate);
                    if (cd.isAfter(td)) {
                        finalStatus = "Delayed";
                    }
                } catch (Exception ignored) {
                }
            }

            task.setStatus(finalStatus);
            task.setRemarks(remarks);
            task.setCompletionDate(completionDate);
            if (completionFile != null) {
                task.setCompletionFile(completionFile);
            }
            task.setUpdatedAt(Instant.now());
            return taskRepository.save(task);
        }
        return null;
    }

    // Bulk create tasks
    public List<Task> createBulkTasks(List<Task> tasks) {
        tasks.forEach(task -> {
            task.setTaskId(generateTaskId());
            task.setCreatedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
        });
        return taskRepository.saveAll(tasks);
    }
}
