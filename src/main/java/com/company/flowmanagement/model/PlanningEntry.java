package com.company.flowmanagement.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "planning_entries")
public class PlanningEntry {

    @Id
    private String id;

    private String folderId;
    private String orderId;
    private String startDate;
    private java.util.Map<String, String> stepTargetDates = new java.util.HashMap<>();
    private java.util.Map<String, String> stepResponsiblePersons = new java.util.HashMap<>();
    private java.util.Map<String, String> stepStatuses = new java.util.HashMap<>();
    private java.util.Map<String, String> stepCompletionDates = new java.util.HashMap<>();
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public java.util.Map<String, String> getStepTargetDates() {
        return stepTargetDates;
    }

    public void setStepTargetDates(java.util.Map<String, String> stepTargetDates) {
        this.stepTargetDates = stepTargetDates;
    }

    public java.util.Map<String, String> getStepResponsiblePersons() {
        return stepResponsiblePersons;
    }

    public void setStepResponsiblePersons(java.util.Map<String, String> stepResponsiblePersons) {
        this.stepResponsiblePersons = stepResponsiblePersons;
    }

    public java.util.Map<String, String> getStepStatuses() {
        return stepStatuses;
    }

    public void setStepStatuses(java.util.Map<String, String> stepStatuses) {
        this.stepStatuses = stepStatuses;
    }

    public java.util.Map<String, String> getStepCompletionDates() {
        return stepCompletionDates;
    }

    public void setStepCompletionDates(java.util.Map<String, String> stepCompletionDates) {
        this.stepCompletionDates = stepCompletionDates;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
