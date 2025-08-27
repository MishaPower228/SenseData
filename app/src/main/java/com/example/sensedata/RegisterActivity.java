package com.example.sensedata;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.sensedata.model.LoginRequest;
import com.example.sensedata.model.RegisterRequest;
import com.example.sensedata.model.UserResponse;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.UserApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends ImmersiveActivity {

    private EditText usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private Button registerButton;
    private TextView loginLink;
    private LinearLayout registerCard;
    private CheckBox showPasswordCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Якщо вже залогінений → одразу в MainActivity
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String token = prefs.getString("accessToken", null);
        String username = prefs.getString("username", null);
        if (token != null && username != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_register);

        // Клавіатура підштовхує контент
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        );

        // Ініціалізація в’юх
        usernameEditText = findViewById(R.id.editTextUsername);
        emailEditText = findViewById(R.id.editTextEmail);
        passwordEditText = findViewById(R.id.editTextPassword);
        confirmPasswordEditText = findViewById(R.id.editTextConfirmPassword);
        showPasswordCheckBox = findViewById(R.id.checkboxShowPassword);
        registerButton = findViewById(R.id.buttonRegister);
        loginLink = findViewById(R.id.textLoginLink);
        registerCard = findViewById(R.id.registerCard);

        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        showPasswordCheckBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                passwordEditText.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                confirmPasswordEditText.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
            } else {
                passwordEditText.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                confirmPasswordEditText.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
            }
            passwordEditText.setSelection(passwordEditText.getText().length());
            confirmPasswordEditText.setSelection(confirmPasswordEditText.getText().length());
        });

        registerButton.setOnClickListener(v -> {
            String usernameInput = usernameEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(usernameInput) || TextUtils.isEmpty(email) ||
                    TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
                animateError();
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.setError("Невірний email");
                animateError();
                return;
            }
            if (!password.equals(confirmPassword)) {
                confirmPasswordEditText.setError("Паролі не збігаються");
                animateError();
                return;
            }

            registerUser(usernameInput, email, password);
        });
    }

    private void animateError() {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        registerCard.startAnimation(shake);
    }

    private void registerUser(String username, String email, String password) {
        RegisterRequest req = new RegisterRequest(username, email, password);
        UserApiService api = ApiClientMain.getClient(this).create(UserApiService.class);

        api.register(req).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    UserResponse body = resp.body();

                    // прапорець для MainActivity → показати ThresholdDialog
                    getSharedPreferences("user_prefs", MODE_PRIVATE)
                            .edit().putBoolean("pending_threshold_dialog", true).apply();

                    boolean hasTokens = body.getAccessToken() != null && body.getRefreshToken() != null;
                    if (hasTokens) {
                        saveTokens(body);
                        Toast.makeText(RegisterActivity.this, "Успішна реєстрація і вхід", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // немає токенів → робимо автоматичний логін
                        loginUser(username, password);
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "Помилка реєстрації: " + resp.code(), Toast.LENGTH_SHORT).show();
                    animateError();
                }
            }
            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e("REGISTER_ERROR", "❌ " + t.getMessage(), t);
                Toast.makeText(RegisterActivity.this, "Сервер недоступний", Toast.LENGTH_SHORT).show();
                animateError();
            }
        });
    }

    private void loginUser(String username, String password) {
        LoginRequest req = new LoginRequest(username, password);
        UserApiService api = ApiClientMain.getClient(this).create(UserApiService.class);

        api.login(req).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    saveTokens(resp.body());
                    Toast.makeText(RegisterActivity.this, "Автоматичний вхід виконано", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Помилка логіну: " + resp.code(), Toast.LENGTH_SHORT).show();
                    animateError();
                }
            }
            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Помилка логіну: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                animateError();
            }
        });
    }

    private void saveTokens(UserResponse resp) {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putInt("userId", resp.getId())
                .putString("username", resp.getUsername())
                .putString("accessToken", resp.getAccessToken())
                .putString("refreshToken", resp.getRefreshToken())
                .apply();
    }
}
