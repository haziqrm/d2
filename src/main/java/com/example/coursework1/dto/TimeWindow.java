package com.example.coursework1.dto;

public class TimeWindow {

    private String dayOfWeek;
    private String from;
    private String until;

    public TimeWindow() {}

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getUntil() { return until; }
    public void setUntil(String until) { this.until = until; }
}