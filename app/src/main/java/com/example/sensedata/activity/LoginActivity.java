package com.example.sensedata.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;


import com.example.sensedata.R;
import com.example.sensedata.model.user.LoginRequest;
import com.example.sensedata.model.user.UserResponse;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.UserApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends ImmersiveActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Клавіатура "підштовхує" контент
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        );

        // Знаходимо корінь для insets
        View root = findViewById(R.id.login_root);
        if (root == null) root = findViewById(android.R.id.content);
        applyImePadding(root);

        // Якщо root НЕ є NestedScrollView → підсунемо кнопку логіну
        Button buttonLogin = findViewById(R.id.buttonLogin);
        if (!(root instanceof androidx.core.widget.NestedScrollView) && buttonLogin != null) {
            applyImeMargin(buttonLogin);
        }

        // Поля вводу
        EditText editTextLogin    = findViewById(R.id.editTextLogin);
        EditText editTextPassword = findViewById(R.id.editTextPassword);
        TextView textRegister     = findViewById(R.id.textRegister);



        // Авторизація
        assert buttonLogin != null;
        buttonLogin.setOnClickListener(v -> {
            String login    = editTextLogin.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (TextUtils.isEmpty(login) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Заповніть логін і пароль", Toast.LENGTH_SHORT).show();
                return;
            }

            doLogin(login, password);
        });

        // Перехід на реєстрацію
        textRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void doLogin(String login, String password) {
        LoginRequest request = new LoginRequest(login, password);
        UserApiService api = ApiClientMain.getClient(LoginActivity.this).create(UserApiService.class);

        api.login(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<UserResponse> call,
                                   @NonNull Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResponse body = response.body();

                    boolean hasTokens = body.getAccessToken() != null && body.getRefreshToken() != null;
                    if (!hasTokens) {
                        Toast.makeText(LoginActivity.this, "Помилка: сервер не повернув токени", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putInt("userId", body.getId())
                            .putString("username", body.getUsername())
                            .putString("accessToken", body.getAccessToken())
                            .putString("refreshToken", body.getRefreshToken())
                            .putBoolean("firstRun", false)
                            .apply();

                    Toast.makeText(LoginActivity.this, "Успішний вхід", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, "Невірний логін або пароль", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                Toast.makeText(LoginActivity.this, "Помилка з'єднання: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
