package com.example.sensedata;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.R;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipUpdateDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Діалог редагування кімнати з кастомними кнопками. */
public class EditRoomDialogFragment extends DialogFragment {

    public static final String TAG = "EditRoomDialog";
    private static final String ARG_CHIP  = "chipId";
    private static final String ARG_NAME  = "name";
    private static final String ARG_IMAGE = "image";

    public interface OnRoomsChangedListener { void onRoomsChanged(); }

    public static EditRoomDialogFragment newInstance(String chipId, String currentName, String currentImage) {
        Bundle b = new Bundle();
        b.putString(ARG_CHIP, chipId);
        b.putString(ARG_NAME, currentName);
        b.putString(ARG_IMAGE, currentImage);
        EditRoomDialogFragment f = new EditRoomDialogFragment();
        f.setArguments(b);
        return f;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_room, null, false);

        String chipId     = getArguments() != null ? getArguments().getString(ARG_CHIP) : null;
        String current    = getArguments() != null ? getArguments().getString(ARG_NAME) : null;
        String currentImg = getArguments() != null ? getArguments().getString(ARG_IMAGE) : null;

        EditText etName = view.findViewById(R.id.etRoomName);
        etName.setText(current != null ? current : "");

        // Контейнери та картинки
        FrameLayout[] containers = new FrameLayout[] {
                view.findViewById(R.id.container1),
                view.findViewById(R.id.container2),
                view.findViewById(R.id.container3),
                view.findViewById(R.id.container4),
                view.findViewById(R.id.container5),
                view.findViewById(R.id.container6)
        };
        ImageView[] images = new ImageView[] {
                view.findViewById(R.id.img1),
                view.findViewById(R.id.img2),
                view.findViewById(R.id.img3),
                view.findViewById(R.id.img4),
                view.findViewById(R.id.img5),
                view.findViewById(R.id.img6)
        };

        final String[] selectedImage = { currentImg };

        // Вибір зображення (підсвічування контейнера і зменшення обраного)
        for (int i = 0; i < images.length; i++) {
            final int idx = i;
            images[i].setOnClickListener(v -> {
                for (FrameLayout c : containers) c.setSelected(false);
                containers[idx].setSelected(true);
                for (ImageView iv : images) { iv.setScaleX(1f); iv.setScaleY(1f); }
                v.setScaleX(0.95f); v.setScaleY(0.95f);
                Object tag = v.getTag();
                selectedImage[0] = tag == null ? null : String.valueOf(tag);
            });
        }

        // Підсвітити поточне
        if (currentImg != null) {
            for (int i = 0; i < images.length; i++) {
                Object tag = images[i].getTag();
                if (tag != null && tag.toString().equals(currentImg)) {
                    images[i].performClick();
                    break;
                }
            }
        }

        // КАСТОМНІ КНОПКИ
        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnSave   = view.findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(v -> dismiss());
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
            if (newName.isEmpty()) { etName.setError("Введіть назву"); return; }
            if (selectedImage[0] == null) {
                Toast.makeText(requireContext(), "Оберіть зображення", Toast.LENGTH_SHORT).show();
                return;
            }
            if (chipId == null || chipId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Немає chipId", Toast.LENGTH_SHORT).show();
                return;
            }
            doPutUpdateOwnership(chipId, newName, selectedImage[0]);
        });

        return new MaterialAlertDialogBuilder(requireContext(), R.style.Overlay_SenseData_AlertDialog)
                .setView(view)
                .setCancelable(true)
                .create();
    }

    private void doPutUpdateOwnership(String chipId, String newName, String newImage) {
        RoomApiService api = ApiClientMain.getClient(requireContext()).create(RoomApiService.class);
        SensorOwnershipUpdateDto body = new SensorOwnershipUpdateDto(chipId, newName, newImage);

        String ifMatch = getEtagForChip(requireContext(), chipId);

        api.updateOwnership(ifMatch, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                if (!isAdded()) return;

                if (resp.isSuccessful()) {
                    // зберігаємо новий ETag, якщо сервер повернув
                    String etag = resp.headers().get("ETag");
                    if (etag != null) saveEtagForChip(requireContext(), chipId, etag);

                    Toast.makeText(requireContext(), "Кімнату оновлено", Toast.LENGTH_SHORT).show();

                    // сповістити активність перезавантажити список
                    if (getActivity() instanceof OnRoomsChangedListener l) {
                        l.onRoomsChanged();
                    }
                    dismiss();
                } else {
                    int code = resp.code();
                    String serverMsg = null;
                    try { if (resp.errorBody() != null) serverMsg = resp.errorBody().string(); } catch (Exception ignore) {}
                    Toast.makeText(requireContext(),
                            "Помилка PUT: " + code + (serverMsg != null ? (" • " + serverMsg) : ""),
                            Toast.LENGTH_LONG).show();

                    // 412, 404, 409 — теж підкажемо оновити список
                    if (getActivity() instanceof OnRoomsChangedListener l) {
                        l.onRoomsChanged();
                    }
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "PUT збій: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- ETag локальне сховище (щоб не тягнути з Activity) ---
    private static String getEtagForChip(Context ctx, String chipId) {
        return ctx.getSharedPreferences("etag_store", Context.MODE_PRIVATE)
                .getString("etag_" + chipId, null);
    }
    private static void saveEtagForChip(Context ctx, String chipId, String etag) {
        if (etag == null) return;
        ctx.getSharedPreferences("etag_store", Context.MODE_PRIVATE)
                .edit().putString("etag_" + chipId, etag).apply();
    }
}
