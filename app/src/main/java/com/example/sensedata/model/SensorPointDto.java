package com.example.sensedata.model;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

// DTO
public class SensorPointDto {
    public String timestampUtc;  // ISO-8601 (наприклад "2025-08-26T10:00:00Z")
    public Integer temperature;
    public Integer humidity;
}


