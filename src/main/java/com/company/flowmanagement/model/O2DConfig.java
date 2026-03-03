package com.company.flowmanagement.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "o2d_config")
public class O2DConfig {

    @Id
    private String id;

    private String name;
    private boolean configured;

    private String orderId;
    private String customerName;
    private String companyName;
    private String rawMaterial;
    private Integer quantity;
    private String cdd;
    private String mpd;
    private String startDate;

    private ArrayList<String> orderDetails = new ArrayList<>();
    private ArrayList<ProcessStep> processDetails = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getRawMaterial() {
        return rawMaterial;
    }

    public void setRawMaterial(String rawMaterial) {
        this.rawMaterial = rawMaterial;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getCDD() {
        return cdd;
    }

    public void setCDD(String cdd) {
        this.cdd = cdd;
    }

    public String getMPD() {
        return mpd;
    }

    public void setMPD(String mpd) {
        this.mpd = mpd;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public ArrayList<String> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(ArrayList<String> orderDetails) {
        this.orderDetails = orderDetails;
    }

    public ArrayList<ProcessStep> getProcessDetails() {
        return processDetails;
    }

    public void setProcessDetails(ArrayList<ProcessStep> processDetails) {
        this.processDetails = processDetails;
    }
}
