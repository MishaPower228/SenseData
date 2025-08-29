package com.example.sensedata.network;

import com.example.sensedata.model.adjustment.AdjustmentAbsoluteRequestDto;
import com.example.sensedata.model.adjustment.AdjustmentAbsoluteResponseDto;
import com.example.sensedata.model.adjustment.EffectiveSettingDto;
import com.example.sensedata.model.recommendations.RecommendationHistoryDto;
import com.example.sensedata.model.recommendations.RecommendationsDto;
import com.example.sensedata.model.recommendations.SaveLatestRecommendationDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SettingsApiService {

    // ---- Advice ----
    @GET("settings/advice/{chipId}/latest")
    Call<RecommendationsDto> getLatestAdvice(@Path("chipId") String chipId);

    @POST("settings/advice/{chipId}/save-latest")
    Call<SaveLatestRecommendationDto> saveLatestAdvice(@Path("chipId") String chipId);

    @GET("settings/advice/{chipId}/history")
    Call<List<RecommendationHistoryDto>> getAdviceHistory(
            @Path("chipId") String chipId,
            @Query("take") int take
    );

    // ---- Абсолютні пороги (встановлення)
    @POST("settings/adjustments/{chipId}")
    Call<AdjustmentAbsoluteResponseDto> postAdjustmentsForChip(
            @Path("chipId") String chipId,
            @Body AdjustmentAbsoluteRequestDto body
    );

    // ---- Ефективні пороги (читаємо те, що реально діє)
    @GET("settings/effective/{chipId}")
    Call<List<EffectiveSettingDto>> getEffectiveByChip(@Path("chipId") String chipId);
}
