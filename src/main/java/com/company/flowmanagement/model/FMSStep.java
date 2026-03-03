package com.company.flowmanagement.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "FMSsteps")
public class FMSStep {

    @Id
    private String id;

    private String stepName; // steps
    private String responsibilityPerson; // responsiblity person
    private String targetDate; // target date
    private String completedDate; // complted date
    private String remarks; // remarks
    private String pdfLink; // PDF
    private String atsScore; // ats score
    private String status; // status
    private String extraInfo; // one blank at the end

    public FMSStep() {
    }

    public FMSStep(String stepName, String responsibilityPerson, String targetDate, String status) {
        this.stepName = stepName;
        this.responsibilityPerson = responsibilityPerson;
        this.targetDate = targetDate;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getResponsibilityPerson() {
        return responsibilityPerson;
    }

    public void setResponsibilityPerson(String responsibilityPerson) {
        this.responsibilityPerson = responsibilityPerson;
    }

    public String getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(String targetDate) {
        this.targetDate = targetDate;
    }

    public String getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(String completedDate) {
        this.completedDate = completedDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getPdfLink() {
        return pdfLink;
    }

    public void setPdfLink(String pdfLink) {
        this.pdfLink = pdfLink;
    }

    public String getAtsScore() {
        return atsScore;
    }

    public void setAtsScore(String atsScore) {
        this.atsScore = atsScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }
}
