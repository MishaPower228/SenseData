package com.example.sensedata.network;

import com.example.sensedata.model.RoomRequest;
import com.example.sensedata.model.RoomWithSensorDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface RoomApiService {
    @POST("http://10.50.6.157:5210/api/SensorData/room")
    Call<RoomWithSensorDto> createRoom(@Body RoomRequest request);
    @GET("http://10.50.6.157:5210/api/SensorData/room-info")
    Call<List<RoomWithSensorDto>> getAllRooms();
}
