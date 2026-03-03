package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.Task;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.UserRepository;
import com.company.flowmanagement.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Map;

@Controller
@RequestMapping("/employee/task-manager")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final com.company.flowmanagement.service.EmployeeService employeeService;

    public TaskController(TaskService taskService, UserRepository userRepository,
            com.company.flowmanagement.repository.EmployeeRepository employeeRepository,
            com.company.flowmanagement.service.EmployeeService employeeService) {
        this.taskService = taskService;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
    }

    @GetMapping
    public String taskManager(Model model, Authentication authentication) {
        String username = authentication.getName();

        // Load partial employee context first (for name, permissions, sidebar)
        java.util.Map<String, Object> context = employeeService.getEmployeeContext(username);
        model.addAllAttributes(context);

        // Filter employees by adminId so they only see their own company members
        Object employeeObj = context.get("employee");
        if (employeeObj instanceof com.company.flowmanagement.model.Employee) {
            String adminId = ((com.company.flowmanagement.model.Employee) employeeObj).getAdminId();
            model.addAttribute("allEmployees", employeeService.getEmployeesByAdminId(adminId));
        } else {
            model.addAttribute("allEmployees", new java.util.ArrayList<>());
        }

        User user = userRepository.findByUsername(username);

        if (user != null) {
            // Get dashboard stats
            Map<String, Object> stats = taskService.getDashboardStats(username);
            model.addAttribute("dashboardStats", stats);

            // Get client-project map
            Map<String, List<com.company.flowmanagement.model.Project>> clientProjectMap = taskService
                    .getClientProjectMap(username);
            model.addAttribute("clientProjectMap", clientProjectMap);

            // Get user tasks
            Map<String, List<Task>> tasks = taskService.getUserTasks(username);
            model.addAttribute("myTasks", tasks.get("myTasks"));
            model.addAttribute("completedTasks", tasks.get("completedTasks"));
            model.addAttribute("delegatedTasks", tasks.get("delegatedTasks"));
        }

        return "employee-task-manager";
    }

    // API endpoints for AJAX operations

    @GetMapping("/api/dashboard-stats")
    @ResponseBody
    public ResponseEntity<?> getDashboardStats(Authentication authentication) {
        String username = authentication.getName();
        Map<String, Object> stats = taskService.getDashboardStats(username);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<?> getTasks(Authentication authentication) {
        String username = authentication.getName();
        Map<String, List<Task>> tasks = taskService.getUserTasks(username);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<?> createTask(@ModelAttribute Task task,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username);

            if (user != null) {
                task.setAssignedById(user.getId());
                task.setAssignedByName(username);

                // Resolve assignedToId from assignedToName (Case-Insensitive)
                if (task.getAssignedToName() != null && !task.getAssignedToName().isBlank()) {
                    String targetName = task.getAssignedToName().trim();
                    User assignedUser = userRepository.findFirstByUsernameIgnoreCase(targetName);

                    if (assignedUser == null) {
                        // Fallback to searching by Employee name if User username is different
                        com.company.flowmanagement.model.Employee emp = employeeRepository.findByName(targetName)
                                .orElse(null);
                        if (emp != null) {
                            assignedUser = userRepository.findByUsername(emp.getName());
                            if (assignedUser == null && emp.getAdminId() != null) {
                                // Last resort: check if there's a user with this email/name
                                assignedUser = userRepository.findAll().stream()
                                        .filter(u -> targetName.equalsIgnoreCase(u.getUsername()))
                                        .findFirst().orElse(null);
                            }
                        }
                    }

                    if (assignedUser != null) {
                        task.setAssignedToId(assignedUser.getId());
                        task.setAssignedToName(assignedUser.getUsername());
                    }
                }

                // Handle file upload
                if (file != null && !file.isEmpty()) {
                    String fileName = saveFile(file);
                    task.setAssignedFile(fileName);
                }

                Task savedTask = taskService.createTask(task);
                return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/tasks/bulk")
    @ResponseBody
    public ResponseEntity<?> createBulkTasks(@RequestBody List<Task> tasks,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username);

            if (user != null) {
                tasks.forEach(task -> {
                    task.setAssignedById(user.getId());
                    task.setAssignedByName(username);

                    // Resolve assignedToId from assignedToName
                    if (task.getAssignedToName() != null) {
                        User assignedUser = userRepository.findByUsername(task.getAssignedToName());
                        if (assignedUser != null) {
                            task.setAssignedToId(assignedUser.getId());
                        }
                    }
                });

                List<Task> savedTasks = taskService.createBulkTasks(tasks);
                return ResponseEntity.status(HttpStatus.CREATED).body(savedTasks);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/tasks/{taskId}/complete")
    @ResponseBody
    public ResponseEntity<?> completeTask(@PathVariable("taskId") String taskId,
            @RequestParam("remarks") String remarks,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication authentication) {
        try {
            String completionDate = LocalDate.now().toString();

            // Handle file upload
            String fileName = null;
            if (file != null && !file.isEmpty()) {
                fileName = saveFile(file);
            }

            Task updatedTask = taskService.updateTaskStatus(taskId, "Completed", remarks, completionDate, fileName);
            if (updatedTask != null) {
                return ResponseEntity.ok(updatedTask);
            }

            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/tasks/bulk-complete")
    @ResponseBody
    public ResponseEntity<?> bulkCompleteTasks(@RequestBody List<String> taskIds,
            Authentication authentication) {
        try {
            String completionDate = LocalDate.now().toString();

            List<Task> updatedTasks = taskIds.stream()
                    .map(taskId -> taskService.updateTaskStatus(taskId, "Completed",
                            "Completed via bulk action", completionDate, null))
                    .filter(task -> task != null)
                    .toList();

            return ResponseEntity.ok(Map.of("updated", updatedTasks.size()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/projects")
    @ResponseBody
    public ResponseEntity<?> getProjects(Authentication authentication) {
        String username = authentication.getName();
        Map<String, List<com.company.flowmanagement.model.Project>> map = taskService.getClientProjectMap(username);
        List<com.company.flowmanagement.model.Project> projects = map.values().stream().flatMap(List::stream)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<?> getMe(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(Map.of("username", username));
    }

    private String saveFile(MultipartFile file) throws IOException {
        String uploadDir = "uploads/tasks/";
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        return fileName;
    }
}
