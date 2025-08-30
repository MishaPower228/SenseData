package com.example.sensedata.dialog_fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.R;
import com.example.sensedata.model.sensorownership.SensorOwnershipUpdateDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditRoomDialogFragment extends DialogFragment {

    public static final String TAG = "EditRoomDialog";
    private static final String ARG_CHIP = "chipId";
    private static final String ARG_NAME = "name";
    private static final String ARG_IMAGE = "image";

    public interface OnRoomsChangedListener {
        void onRoomsChanged();
    }

    public static EditRoomDialogFragment newInstance(String chipId, String currentName, String currentImage) {
        Bundle b = new Bundle();
        b.putString(ARG_CHIP, chipId);
        b.putString(ARG_NAME, currentName);
        b.putString(ARG_IMAGE, currentImage);
        EditRoomDialogFragment f = new EditRoomDialogFragment();
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_room, null, false);

        String chipId = getArguments() != null ? getArguments().getString(ARG_CHIP) : null;
        String current = getArguments() != null ? getArguments().getString(ARG_NAME) : null;
        String currentImg = getArguments() != null ? getArguments().getString(ARG_IMAGE) : null;

        EditText etName = view.findViewById(R.id.etRoomName);
        etName.setText(current != null ? current : "");

        // Контейнери та картинки
        List<FrameLayout> containers = Arrays.asList(
                view.findViewById(R.id.container1),
                view.findViewById(R.id.container2),
                view.findViewById(R.id.container3),
                view.findViewById(R.id.container4),
                view.findViewById(R.id.container5),
                view.findViewById(R.id.container6)
        );
        List<ImageView> images = Arrays.asList(
                view.findViewById(R.id.img1),
                view.findViewById(R.id.img2),
                view.findViewById(R.id.img3),
                view.findViewById(R.id.img4),
                view.findViewById(R.id.img5),
                view.findViewById(R.id.img6)
        );

        final String[] selectedImage = {currentImg};

        // Вибір зображення
        for (int i = 0; i < images.size(); i++) {
            final int idx = i;
            images.get(i).setOnClickListener(v -> {
                for (FrameLayout c : containers) c.setSelected(false);
                containers.get(idx).setSelected(true);
                for (ImageView iv : images) {
                    iv.setScaleX(1f);
                    iv.setScaleY(1f);
                }
                v.setScaleX(0.95f);
                v.setScaleY(0.95f);
                Object tag = v.getTag();
                selectedImage[0] = tag == null ? null : String.valueOf(tag);
            });
        }

        // Підсвітити поточне
        if (currentImg != null) {
            for (ImageView image : images) {
                Object tag = image.getTag();
                if (tag != null && tag.toString().equals(currentImg)) {
                    image.performClick();
                    break;
                }
            }
        }

        // Кастомні кнопки
        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnSave = view.findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(v -> dismiss());
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
            if (newName.isEmpty()) {
                etName.setError(getString(R.string.error_enter_name));
                return;
            }
            if (selectedImage[0] == null) {
                Toast.makeText(requireContext(), R.string.error_select_image, Toast.LENGTH_SHORT).show();
                return;
            }
            if (chipId == null || chipId.trim().isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_no_chipid, Toast.LENGTH_SHORT).show();
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

        api.updateOwnership(ifMatch, body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> resp) {
                if (!isAdded()) return;

                if (resp.isSuccessful()) {
                    String etag = resp.headers().get("ETag");
                    if (etag != null) saveEtagForChip(requireContext(), chipId, etag);

                    Toast.makeText(requireContext(), R.string.room_updated, Toast.LENGTH_SHORT).show();

                    if (getActivity() instanceof OnRoomsChangedListener l) {
                        l.onRoomsChanged();
                    }
                    dismiss();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.error_put_failed, resp.code()), Toast.LENGTH_LONG).show();

                    if (getActivity() instanceof OnRoomsChangedListener l) {
                        l.onRoomsChanged();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        getString(R.string.error_put_crash, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

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
