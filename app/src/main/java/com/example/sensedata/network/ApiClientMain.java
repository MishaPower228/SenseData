package com.example.sensedata.network;

import android.content.Context;

import com.example.sensedata.AuthInterceptor.AuthInterceptor;
import com.example.sensedata.AuthInterceptor.TokenAuthenticator;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClientMain {
    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(context)) // додає токен до кожного запиту
                    .authenticator(new TokenAuthenticator(context)) // обробляє 401
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl("http://192.168.0.200:5210/api/") // твоя локальна адреса
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit;
    }
}

