package com.example.sensedata.network;

import com.example.sensedata.model.sensordata.SensorDataDto;
import com.example.sensedata.model.historychart.SensorPointDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface SensorDataApiService {

    // Останній запис телеметрії
    @GET("sensordata/{chipId}/latest")
    Call<SensorDataDto> getLatest(@Path("chipId") String chipId);

    @GET("sensordata/ownership/{chipId}/day")
    Call<List<SensorPointDto>> getDay(@Path("chipId") String chipId);

    @GET("sensordata/ownership/{chipId}/week")
    Call<List<SensorPointDto>> getWeek(@Path("chipId") String chipId);
}
