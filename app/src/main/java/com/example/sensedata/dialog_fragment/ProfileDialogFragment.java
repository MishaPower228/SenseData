package com.example.sensedata.dialog_fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import android.widget.TextView;

import com.example.sensedata.R;
import com.example.sensedata.model.user.UserProfileDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.UserApiService;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileDialogFragment extends DialogFragment {

    public static final String TAG = "ProfileDialog";

    private TextView tvUsername, tvUserId, tvEmail;
    private Call<UserProfileDto> inFlight;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_profile, null, false);

        tvUsername = view.findViewById(R.id.tvUsername);
        tvUserId   = view.findViewById(R.id.tvUserId);
        tvEmail    = view.findViewById(R.id.tvServer); // використаємо для Email

        MaterialButton btnClose  = view.findViewById(R.id.btnClose);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);

        // Початковий текст із ресурсів
        tvUsername.setText(getString(R.string.guesttext));
        tvUserId.setText(getString(R.string.profile_id, "—"));
        tvEmail.setText(getString(R.string.profile_email, "—"));

        // Читаємо userId із prefs
        SharedPreferences sp = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        int userId = sp.getInt("userId", -1);
        Log.d(TAG, "Loaded userId from prefs = " + userId);

        if (userId != -1) {
            UserApiService api = ApiClientMain.getClient(requireContext()).create(UserApiService.class);
            inFlight = api.getUserProfile(userId);

            Log.d(TAG, "Запитуємо профіль для userId=" + userId);

            inFlight.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<UserProfileDto> call,
                                       @NonNull Response<UserProfileDto> response) {
                    if (!isAdded()) return;
                    Log.d(TAG, "Response code = " + response.code());

                    if (response.isSuccessful() && response.body() != null) {
                        UserProfileDto u = response.body();
                        Log.d(TAG, "Body = " + new Gson().toJson(u));

                        tvUsername.setText(getString(R.string.profile_hello, safe(u.getUsername())));
                        tvUserId.setText(getString(R.string.profile_id, String.valueOf(u.getId())));
                        tvEmail.setText(getString(R.string.profile_email, safe(u.getEmail())));
                    } else {
                        tvUsername.setText(getString(R.string.profile_hello, getString(R.string.usertext)));
                        tvUserId.setText(getString(R.string.profile_id, "—"));
                        tvEmail.setText(getString(R.string.profile_email, "—"));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<UserProfileDto> call, @NonNull Throwable t) {
                    if (!isAdded()) return;
                    Log.e(TAG, "onFailure: " + t.getMessage(), t);
                    tvUsername.setText(getString(R.string.profile_hello, getString(R.string.usertext)));
                    tvUserId.setText(getString(R.string.profile_id, "—"));
                    tvEmail.setText(getString(R.string.profile_email, "—"));
                }
            });
        } else {
            Log.e(TAG, "userId = -1 (не збережений після логіну)");
        }

        btnClose.setOnClickListener(v -> dismiss());
        btnLogout.setOnClickListener(v -> {
            dismiss();
            new LogoutDialogFragment().show(getParentFragmentManager(), LogoutDialogFragment.TAG);
        });

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();

        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dlg;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (inFlight != null) inFlight.cancel();
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
    }
}
