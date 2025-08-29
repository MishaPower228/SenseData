package com.example.sensedata.network;

import com.example.sensedata.model.sensordata.SensorDataDto;
import com.example.sensedata.model.historychart.SensorPointDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SensorDataApiService {

    // Сирі записи телеметрії (за період)
    @GET("sensordata/{chipId}/history")
    Call<List<SensorDataDto>> getHistory(
            @Path("chipId") String chipId,
            @Query("take") Integer take,
            @Query("from") String fromIso,
            @Query("to")   String toIso
    );

    // Останній запис телеметрії
    @GET("sensordata/{chipId}/latest")
    Call<SensorDataDto> getLatest(@Path("chipId") String chipId);

    // Агреговані серії для графіків
    @GET("sensordata/ownership/{chipId}/series")
    Call<List<SensorPointDto>> getSeries(
            @Path("chipId") String chipId,
            @Query("from") String fromIso,
            @Query("to") String toIso,
            @Query("bucket") String bucket // "hour" для дня, "day" для тижня
    );

    @GET("sensordata/ownership/{chipId}/day")
    Call<List<SensorPointDto>> getDay(@Path("chipId") String chipId);

    @GET("sensordata/ownership/{chipId}/week")
    Call<List<SensorPointDto>> getWeek(@Path("chipId") String chipId);
}
