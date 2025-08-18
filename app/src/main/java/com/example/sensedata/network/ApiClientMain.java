package com.example.sensedata.network;

import android.content.Context;

import com.example.sensedata.AuthInterceptor.AuthInterceptor;
import com.example.sensedata.AuthInterceptor.TokenAuthenticator;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;  // ← додай
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClientMain {
    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(context))
                    .authenticator(new TokenAuthenticator(context))
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl("http://192.168.92.32:5210/api/")
                    .client(client)
                    .addConverterFactory(ScalarsConverterFactory.create()) // ← СПОЧАТКУ
                    .addConverterFactory(GsonConverterFactory.create())    // ← ПОТІМ
                    .build();
        }
        return retrofit;
    }

    // (опційно) щоб можна було перебудувати клієнт після змін
    public static void reset() { retrofit = null; }
}
