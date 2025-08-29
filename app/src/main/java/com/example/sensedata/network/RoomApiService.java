package com.example.sensedata.network;

import com.example.sensedata.model.room.RoomWithSensorDto;
import com.example.sensedata.model.sensorownership.SensorOwnershipRequestDto;
import com.example.sensedata.model.sensorownership.SensorOwnershipUpdateDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface RoomApiService {

    // Створити ownership (username + chipId + roomName + imageName)
    @POST("DisplayData/ownership")
    Call<RoomWithSensorDto> createOwnership(@Body SensorOwnershipRequestDto body);

    // Отримати кімнату за chipId + userId (залишаємо як є, якщо бек так очікує)
    @GET("DisplayData/ownership/{chipId}/user/{userId}/latest")
    Call<RoomWithSensorDto> getRoomByChipId(
            @Path("chipId") String chipId,
            @Path("userId") int userId
    );

    // Усі кімнати користувача
    @GET("DisplayData/byUser/{userId}")
    Call<List<RoomWithSensorDto>> getAllRooms(@Path("userId") int userId);

    // Оновлення ownership з ETag (If-Match)
    @PUT("sensordata/ownership")
    Call<Void> updateOwnership(
            @Header("If-Match") String ifMatch,
            @Body SensorOwnershipUpdateDto body
    );

    // Видалення ownership
    @DELETE("sensordata/ownership/{chipId}/user/{userId}")
    Call<Void> deleteOwnership(
            @Path("chipId") String chipId,
            @Path("userId") int userId
    );
}
