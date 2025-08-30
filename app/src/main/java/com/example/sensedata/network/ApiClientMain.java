package com.example.sensedata.network;

import android.content.Context;
import android.util.Log;

import com.example.sensedata.AuthInterceptor.AuthInterceptor;
import com.example.sensedata.AuthInterceptor.TokenAuthenticator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClientMain {

    // ⚠️ Замiни на свою адресу. Обов’язково закінчується на /api/
    private final static String BASE_URL = "http://192.168.0.200:5210/api/";

    // можна вмикати/вимикати логування без BuildConfig
    private final static boolean HTTP_LOGS_ENABLED = true;

    private static volatile Retrofit retrofit;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            synchronized (ApiClientMain.class) {
                if (retrofit == null) {
                    OkHttpClient ok = buildOkHttp(context);
                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(ok)
                            // Спочатку Scalars (plain text), потім Gson (JSON)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .addConverterFactory(GsonConverterFactory.create(gson()))
                            .build();
                }
            }
        }
        return retrofit;
    }

    // ----------------- helpers -----------------

    private static OkHttpClient buildOkHttp(Context context) {
        HttpLoggingInterceptor logger =
                new HttpLoggingInterceptor(msg -> Log.d("OkHttp", msg));
        logger.setLevel(HTTP_LOGS_ENABLED
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        return new OkHttpClient.Builder()
                // токен у заголовок
                .addInterceptor(new AuthInterceptor(context))
                // авто-рефреш токена при 401
                .authenticator(new TokenAuthenticator(context))
                // логер — після auth, щоб бачити фінальні заголовки/тіло
                .addInterceptor(logger)
                .retryOnConnectionFailure(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static Gson gson() {
        return new GsonBuilder()
                .create();
    }
}
