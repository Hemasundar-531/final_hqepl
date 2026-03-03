package com.company.flowmanagement.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tasks")
public class Task {

    @Id
    private String id;

    private String taskId; // Unique task identifier like "TASK-001"
    private String title;
    private String description;

    // Project and Client references
    private String projectId;
    private String projectName;
    private String clientOrgId;
    private String clientName;

    // Assignment details
    private String assignedToId;
    private String assignedToName;
    private String assignedById;
    private String assignedByName;

    // Dates
    private String targetDate;
    private String completionDate;

    // Status and tracking
    private String status; // In Progress, Completed, Delayed, Overdue, On Time
    private String remarks;

    // File attachments
    private String assignedFile; // File path or URL
    private String completionFile; // File path or URL

    // Repeatable task settings
    private Boolean isRepeatable = false;
    private String repeatFrequency; // Weekly, Monthly
    private String repeatEndDate;
    private String repeatDay; // Monday, Tuesday, etc.
    private String repeatWeek; // First, Second, etc. (for monthly)

    // Internal task flag
    private Boolean isInternal = false;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    // Transient fields for dashboard/UI
    private Double atsScore;

    // Constructors
    public Task() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "In Progress";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getClientOrgId() {
        return clientOrgId;
    }

    public void setClientOrgId(String clientOrgId) {
        this.clientOrgId = clientOrgId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(String assignedToId) {
        this.assignedToId = assignedToId;
    }

    public String getAssignedToName() {
        return assignedToName;
    }

    public void setAssignedToName(String assignedToName) {
        this.assignedToName = assignedToName;
    }

    public String getAssignedById() {
        return assignedById;
    }

    public void setAssignedById(String assignedById) {
        this.assignedById = assignedById;
    }

    public String getAssignedByName() {
        return assignedByName;
    }

    public void setAssignedByName(String assignedByName) {
        this.assignedByName = assignedByName;
    }

    public String getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(String targetDate) {
        this.targetDate = targetDate;
    }

    public String getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(String completionDate) {
        this.completionDate = completionDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getAssignedFile() {
        return assignedFile;
    }

    public void setAssignedFile(String assignedFile) {
        this.assignedFile = assignedFile;
    }

    public String getCompletionFile() {
        return completionFile;
    }

    public void setCompletionFile(String completionFile) {
        this.completionFile = completionFile;
    }

    public Boolean getIsRepeatable() {
        return isRepeatable;
    }

    public void setIsRepeatable(Boolean isRepeatable) {
        this.isRepeatable = isRepeatable;
    }
    public String getRepeatFrequency() {
        return repeatFrequency;
    }

    public void setRepeatFrequency(String repeatFrequency) {
        this.repeatFrequency = repeatFrequency;
    }

    public String getRepeatEndDate() {
        return repeatEndDate;
    }

    public void setRepeatEndDate(String repeatEndDate) {
        this.repeatEndDate = repeatEndDate;
    }

    public String getRepeatDay() {
        return repeatDay;
    }

    public void setRepeatDay(String repeatDay) {
        this.repeatDay = repeatDay;
    }

    public String getRepeatWeek() {
        return repeatWeek;
    }

    public void setRepeatWeek(String repeatWeek) {
        this.repeatWeek = repeatWeek;
    }

    public Boolean getIsInternal() {
        return isInternal;
    }

    public void setIsInternal(Boolean isInternal) {
        this.isInternal = isInternal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Double getAtsScore() {
        return atsScore;
    }

    public void setAtsScore(Double atsScore) {
        this.atsScore = atsScore;
    }
}
