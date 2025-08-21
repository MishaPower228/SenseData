package com.example.sensedata.network;

import com.example.sensedata.model.SensorDataDTO;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SensorDataApiService {
    @GET("api/sensordata/{chipId}/history")
    Call<List<SensorDataDTO>> getHistory(
            @Path("chipId") String chipId,
            @Query("take") Integer take,
            @Query("from") String fromIso,
            @Query("to")   String toIso
    );

    @GET("api/sensordata/{chipId}/latest")
    Call<SensorDataDTO> getLatest(@Path("chipId") String chipId);
}
