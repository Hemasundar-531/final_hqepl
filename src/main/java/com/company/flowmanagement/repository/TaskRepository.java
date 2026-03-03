package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface TaskRepository extends MongoRepository<Task, String> {

    // Find tasks assigned to a specific user
    List<Task> findByAssignedToIdOrderByCreatedAtDesc(String assignedToId);

    // Find tasks assigned by a specific user
    List<Task> findByAssignedByIdOrderByCreatedAtDesc(String assignedById);

    // Find tasks by status
    List<Task> findByStatusOrderByCreatedAtDesc(String status);

    // Find tasks by project
    List<Task> findByProjectIdOrderByCreatedAtDesc(String projectId);

    // Find tasks by client
    List<Task> findByClientOrgIdOrderByCreatedAtDesc(String clientOrgId);

    // Find tasks by task ID
    Task findByTaskId(String taskId);

    List<Task> findByTaskIdStartingWith(String prefix);

    // Custom query to find active tasks (not completed) assigned to user
    @Query("{ 'assignedToId': ?0, 'status': { $ne: 'Completed' } }")
    List<Task> findActiveTasksByAssignedToId(String assignedToId);

    // Custom query to find completed tasks assigned to user
    @Query("{ 'assignedToId': ?0, 'status': 'Completed' }")
    List<Task> findCompletedTasksByAssignedToId(String assignedToId);

    // Custom query to find tasks assigned by user (excluding self-assigned)
    @Query("{ 'assignedById': ?0, 'assignedToId': { $ne: ?0 } }")
    List<Task> findDelegatedTasksByAssignedById(String assignedById);

    // Count tasks by status for dashboard stats
    long countByStatus(String status);

    // Find overdue tasks
    @Query("{ 'targetDate': { $lt: ?0 }, 'status': { $ne: 'Completed' } }")
    List<Task> findOverdueTasks(String currentDate);

    // Find tasks due today or before
    @Query("{ 'targetDate': { $lte: ?0 }, 'status': { $ne: 'Completed' } }")
    List<Task> findTasksDueByDate(String date);
}
