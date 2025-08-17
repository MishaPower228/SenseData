package com.example.sensedata.network;

import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipRequestDTO;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface RoomApiService {

    // POST: створити прив'язку плати до юзера (кімната)
    @POST("api/DisplayData/ownership")
    Call<RoomWithSensorDto> createRoom(@Body SensorOwnershipRequestDTO request);

    // GET: отримати кімнату по chipId
    @GET("api/DisplayData/ownership/{chipId}/user/{userId}/latest")
    Call<RoomWithSensorDto> getRoomByChipId(
            @Path("chipId") String chipId,
            @Path("userId") int userId
    );

    // GET: отримати всі кімнати користувача
    @GET("api/DisplayData/user/{userId}")
    Call<List<RoomWithSensorDto>> getAllRooms(@Path("userId") int userId);
}
