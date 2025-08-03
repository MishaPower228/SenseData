package com.example.sensedata;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sensedata.model.LoginRequest;
import com.example.sensedata.model.RegisterRequest;
import com.example.sensedata.model.UserResponse;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.UserApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText usernameEditText, emailEditText, passwordEditText;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔐 Перевірка, чи вже збережено токен і username
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String accessToken = prefs.getString("accessToken", null);
        String username = prefs.getString("username", null);

        if (accessToken != null && username != null) {
            // 🔄 Уже авторизований → переходимо в MainActivity
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // 🔽 Якщо токена немає — показуємо екран реєстрації
        setContentView(R.layout.activity_register);

        usernameEditText = findViewById(R.id.editTextUsername);
        emailEditText = findViewById(R.id.editTextEmail);
        passwordEditText = findViewById(R.id.editTextPassword);
        registerButton = findViewById(R.id.buttonRegister);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString().trim();
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                    Toast.makeText(RegisterActivity.this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
                    return;
                }

                registerUser(username, email, password);
            }
        });
    }


    private void registerUser(String username, String email, String password) {
        RegisterRequest request = new RegisterRequest(username, email, password);
        UserApiService api = ApiClientMain.getClient(RegisterActivity.this).create(UserApiService.class);

        api.register(request).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.code() == 201) {
                    // Зберегти username у SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    prefs.edit().putString("username", username).apply();

                    Toast.makeText(RegisterActivity.this, "Реєстрація успішна", Toast.LENGTH_SHORT).show();

                    // Автоматичний логін
                    loginUser(username, password);
                } else {
                    Toast.makeText(RegisterActivity.this, "Помилка реєстрації", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e("REGISTER_ERROR", "❌ Retrofit error: " + t.getMessage(), t);
                Toast.makeText(RegisterActivity.this, "Сервер недоступний", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void loginUser(String username, String password) {
        LoginRequest loginRequest = new LoginRequest(username, password);
        UserApiService api = ApiClientMain.getClient(RegisterActivity.this).create(UserApiService.class);

        api.login(loginRequest).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String accessToken = response.body().getAccessToken();
                    String refreshToken = response.body().getRefreshToken();

                    SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putString("username", username)
                            .putString("accessToken", accessToken)
                            .putString("refreshToken", refreshToken)
                            .apply();

                    Toast.makeText(RegisterActivity.this, "Успішна реєстрація і вхід", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Автоматичний вхід не вдався", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Помилка при вході: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

}
