package com.example.sensedata;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sensedata.model.LoginRequest;
import com.example.sensedata.model.UserResponse;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.UserApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextLogin, editTextPassword;
    private Button buttonLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editTextLogin = findViewById(R.id.editTextLogin);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);

        buttonLogin.setOnClickListener(v -> {
            String login = editTextLogin.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (TextUtils.isEmpty(login) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Заповніть логін і пароль", Toast.LENGTH_SHORT).show();
                return;
            }

            doLogin(login, password);
        });
    }

    private void doLogin(String login, String password) {
        LoginRequest request = new LoginRequest(login, password);
        UserApiService api = ApiClientMain.getClient(LoginActivity.this).create(UserApiService.class);

        api.login(request).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResponse token = response.body();

                    SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putInt("userId", token.getId()) // ✅ Додай це
                            .putString("username", login)
                            .putString("accessToken", token.getAccessToken())
                            .putString("refreshToken", token.getRefreshToken())
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
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Помилка з'єднання з сервером", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
