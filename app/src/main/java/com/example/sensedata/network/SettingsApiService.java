package com.example.sensedata.network;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import com.example.sensedata.model.RecommendationsDto;
import com.example.sensedata.model.RecommendationHistoryDto;
import com.example.sensedata.model.SaveLatestRecommendationDto;
import com.example.sensedata.model.AdjustmentAbsoluteRequestDto;
import com.example.sensedata.model.AdjustmentAbsoluteResponseDto;

public interface SettingsApiService {

    // GET /api/settings/advice/{chipId}/latest  -> { data, advice }
    @GET("settings/advice/{chipId}/latest")
    Call<RecommendationsDto> getLatestAdvice(@Path("chipId") String chipId);

    // POST /api/settings/advice/{chipId}/save-latest -> {saved,count} або 204
    @POST("settings/advice/{chipId}/save-latest")
    Call<SaveLatestRecommendationDto> saveLatestAdvice(@Path("chipId") String chipId);

    // GET /api/settings/advice/{chipId}/history?take=20
    @GET("Settings/advice/{chipId}/history")
    Call<List<RecommendationHistoryDto>> getAdviceHistory(
            @Path("chipId") String chipId,
            @Query("take") int take
    );

    // POST /api/settings/adjustments
    @POST("settings/adjustments")
    Call<AdjustmentAbsoluteResponseDto> postAdjustments(@Body AdjustmentAbsoluteRequestDto body);
}

