package com.company.flowmanagement.model;

public class ProcessStep {

    private String stepProcess;
    private String responsiblePerson;
    private String targetType;
    private Integer days;

    public String getStepProcess() {
        return stepProcess;
    }

    public void setStepProcess(String stepProcess) {
        this.stepProcess = stepProcess;
    }

    public String getResponsiblePerson() {
        return responsiblePerson;
    }

    public void setResponsiblePerson(String responsiblePerson) {
        this.responsiblePerson = responsiblePerson;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    private String status = "PENDING";
    private String remarks;
    private String completionDate;
    private String completionFile;

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

    public String getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(String completionDate) {
        this.completionDate = completionDate;
    }

    public String getCompletionFile() {
        return completionFile;
    }

    public void setCompletionFile(String completionFile) {
        this.completionFile = completionFile;
    }
}
