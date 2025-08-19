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

import androidx.appcompat.app.AppCompatActivity;

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

        // Якщо користувач уже залогінений — в головний екран
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String accessToken = prefs.getString("accessToken", null);
        String username    = prefs.getString("username", null);
        if (accessToken != null && username != null) {
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_register);

        // Клавіатура "підштовхує" контент
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        );

        // Підкладаємо IMEInsets у корінь (NestedScrollView або інший контейнер)
        View root = findViewById(R.id.register_root);
        if (root == null) root = findViewById(android.R.id.content);
        applyImePadding(root);
        if (!(root instanceof androidx.core.widget.NestedScrollView)) {
            // Якщо корінь не скролиться – піднімаємо кнопку над клавіатурою
            View btn = findViewById(R.id.buttonRegister);
            if (btn != null) applyImeMargin(btn);
        }

        // Ініціалізація вьюх
        usernameEditText        = findViewById(R.id.editTextUsername);
        emailEditText           = findViewById(R.id.editTextEmail);
        passwordEditText        = findViewById(R.id.editTextPassword);
        confirmPasswordEditText = findViewById(R.id.editTextConfirmPassword);
        showPasswordCheckBox    = findViewById(R.id.checkboxShowPassword);
        registerButton          = findViewById(R.id.buttonRegister);
        loginLink               = findViewById(R.id.textLoginLink);
        registerCard            = findViewById(R.id.registerCard);

        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // Показ/приховування пароля (без зміни inputType)
        showPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
            String usernameInput   = usernameEditText.getText().toString().trim();
            String email           = emailEditText.getText().toString().trim();
            String password        = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(usernameInput) || TextUtils.isEmpty(email)
                    || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
                animateError();
                Toast.makeText(RegisterActivity.this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.setError("Невірний email формат");
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
        RegisterRequest request = new RegisterRequest(username, email, password);
        UserApiService api = ApiClientMain.getClient(RegisterActivity.this).create(UserApiService.class);

        api.register(request).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.code() == 201) {
                    SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("username", username);

                    // 💡 Збереження userId, отриманого з відповіді
                    int userId = response.body().getId(); // ← це твій userId з UserResponse
                    editor.putInt("userId", userId);

                    editor.apply();

                    Toast.makeText(RegisterActivity.this, "Реєстрація успішна", Toast.LENGTH_SHORT).show();

                    loginUser(username, password);
                } else {
                    Toast.makeText(RegisterActivity.this, "Помилка реєстрації", Toast.LENGTH_SHORT).show();
                    animateError();
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e("REGISTER_ERROR", "❌ Retrofit error: " + t.getMessage(), t);
                Toast.makeText(RegisterActivity.this, "Сервер недоступний", Toast.LENGTH_SHORT).show();
                animateError();
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

                    // ⬇️ Тут зберігаємо все в SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putInt("userId", response.body().getId())
                            .putString("username", response.body().getUsername())
                            .putString("accessToken", response.body().getAccessToken())
                            .putString("refreshToken", response.body().getRefreshToken())
                            .apply();

                    Toast.makeText(RegisterActivity.this, "Успішна реєстрація і вхід", Toast.LENGTH_SHORT).show();

                    // Переходимо на головну активність
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();

                } else {
                    Toast.makeText(RegisterActivity.this, "Автоматичний вхід не вдався", Toast.LENGTH_SHORT).show();
                    animateError();
                }
            }


            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Помилка при вході: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                animateError();
            }
        });
    }
}
