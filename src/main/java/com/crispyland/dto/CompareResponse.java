package com.crispyland.dto;

import java.util.List;

public class CompareResponse {
    private String           unstructured;
    private List<BenefitItem> benefits;

    public CompareResponse() {}

    public CompareResponse(String unstructured, List<BenefitItem> benefits) {
        this.unstructured = unstructured;
        this.benefits     = benefits;
    }

    public String            getUnstructured() { return unstructured; }
    public void              setUnstructured(String unstructured) { this.unstructured = unstructured; }
    public List<BenefitItem> getBenefits()     { return benefits; }
    public void              setBenefits(List<BenefitItem> benefits) { this.benefits = benefits; }
}
