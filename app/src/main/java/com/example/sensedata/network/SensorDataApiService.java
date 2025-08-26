package com.example.sensedata.network;

import com.example.sensedata.model.SensorDataDTO;
import com.example.sensedata.model.SensorPointDto;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SensorDataApiService {
    @GET("sensordata/{chipId}/history")
    Call<List<SensorDataDTO>> getHistory(
            @Path("chipId") String chipId,
            @Query("take") Integer take,
            @Query("from") String fromIso,
            @Query("to")   String toIso
    );

    @GET("sensordata/{chipId}/latest")
    Call<SensorDataDTO> getLatest(@Path("chipId") String chipId);

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
