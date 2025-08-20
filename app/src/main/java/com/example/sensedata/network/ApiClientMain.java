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
    private static String BASE_URL = "http://192.168.92.32:5210/api/";

    // можна вмикати/вимикати логування без BuildConfig
    private static volatile boolean HTTP_LOGS_ENABLED = true;

    private static volatile Retrofit retrofit;

    private ApiClientMain() {}

    /** Увімкнути/вимкнути HTTP-логи в рантаймі (за потреби) */
    public static void setHttpLoggingEnabled(boolean enabled) {
        HTTP_LOGS_ENABLED = enabled;
        reset();
    }

    /** За потреби поміняти базовий URL і перебудувати клієнт */
    public static void setBaseUrl(String baseUrl) {
        if (baseUrl == null || !baseUrl.endsWith("/")) {
            throw new IllegalArgumentException("BASE_URL must end with '/'");
        }
        BASE_URL = baseUrl;
        reset();
    }

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

    public static void reset() {
        retrofit = null;
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
                .setLenient()
                .create();
    }
}
