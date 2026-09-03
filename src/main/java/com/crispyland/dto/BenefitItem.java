package com.crispyland.dto;

public class BenefitItem {
    private String title;
    private String detail;

    public BenefitItem() {}

    public BenefitItem(String title, String detail) {
        this.title  = title;
        this.detail = detail;
    }

    public String getTitle()  { return title; }
    public void   setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void   setDetail(String detail) { this.detail = detail; }
}
