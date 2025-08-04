package com.example.sensedata.network;

import com.example.sensedata.model.RoomRequest;
import com.example.sensedata.model.RoomWithSensorDto;
//import com.example.sensedata.model.SensorDataDto;
//import com.example.sensedata.model.ComfortRecommendation;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RoomApiService {

    // Створити кімнату
    @POST("SensorData/room")
    Call<RoomWithSensorDto> createRoom(@Body RoomRequest request);

    // Отримати всі кімнати користувача за username
    @GET("DisplayData/{username}/rooms")
    Call<List<String>> getAllRooms(@Path("username") String username);

    // Отримати останні дані сенсорів для кімнати користувача
    //@GET("http://10.50.6.157:5210/api/DisplayData/{username}/latest/DTO")
    //Call<SensorDataDto> getLatestSensorData(@Path("username") String username);

    // Отримати рекомендації для кімнати користувача
    //@GET("http://10.50.6.157:5210/api/DisplayData/{username}/recommendations")
    //Call<List<ComfortRecommendation>> getRecommendations(@Path("username") String username);
}
