package com.example.coursework1.dto;

public class QueryAttribute {
    private String attribute;
    private String operator;
    private String value;

    public QueryAttribute() {}

    public String getAttribute() { return attribute; }
    public String getOperator() { return operator; }
    public String getValue() { return value; }

    public void setAttribute(String attribute) { this.attribute = attribute; }
    public void setOperator(String operator) { this.operator = operator; }
    public void setValue(String value) { this.value = value; }
}
