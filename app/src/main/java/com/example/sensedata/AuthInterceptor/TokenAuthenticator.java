package com.example.sensedata.AuthInterceptor;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.sensedata.model.user.RefreshRequest;
import com.example.sensedata.model.user.UserResponse;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.UserApiService;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class TokenAuthenticator implements Authenticator {
    private final Context context;

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public Request authenticate(Route route, @NonNull Response response) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        String refreshToken = prefs.getString("refreshToken", null);
        String username = prefs.getString("username", null);

        if (refreshToken == null || username == null) {
            return null; // не можна оновити
        }

        // Викликаємо refresh API
        UserApiService api = ApiClientMain.getClient(context).create(UserApiService.class);
        RefreshRequest request = new RefreshRequest(refreshToken);
        retrofit2.Response<UserResponse> tokenResponse = api.refreshToken(request).execute();

        if (tokenResponse.isSuccessful() && tokenResponse.body() != null) {
            UserResponse newTokens = tokenResponse.body();

            // Зберігаємо нові токени
            prefs.edit()
                    .putString("accessToken", newTokens.getAccessToken())
                    .putString("refreshToken", newTokens.getRefreshToken())
                    .apply();

            // Повторити запит з новим токеном
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + newTokens.getAccessToken())
                    .build();
        }

        return null; // refresh не вдався — перехід до LoginActivity (можна реалізувати)
    }
}

