package com.example.sensedata.dialog_fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.R;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Діалог підтвердження видалення кімнати з кастомними кнопками. */
public class DeleteRoomDialogFragment extends DialogFragment {

    public static final String TAG = "DeleteRoomDialog";
    private static final String ARG_CHIP = "chipId";
    private static final String ARG_NAME = "roomName";

    public interface OnRoomsChangedListener {
        void onRoomsChanged();
    }

    public static DeleteRoomDialogFragment newInstance(String chipId, String roomName) {
        Bundle b = new Bundle();
        b.putString(ARG_CHIP, chipId);
        b.putString(ARG_NAME, roomName);
        DeleteRoomDialogFragment f = new DeleteRoomDialogFragment();
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_delete_room, null, false);

        final String chipId = requireArguments().getString(ARG_CHIP, null);
        final String roomName = requireArguments().getString(ARG_NAME, null);

        TextView tvMsg = view.findViewById(R.id.tvDeleteMsg);
        if (roomName != null && !roomName.trim().isEmpty()) {
            tvMsg.setText(getString(R.string.delete_room_message, roomName));
        }

        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnDelete = view.findViewById(R.id.btnDelete);

        btnCancel.setOnClickListener(v -> dismiss());
        btnDelete.setOnClickListener(v -> {
            int userId = getSavedUserId(requireContext());
            if (userId == -1 || chipId == null || chipId.trim().isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_no_ids, Toast.LENGTH_SHORT).show();
                return;
            }

            setEnabled(btnCancel, false);
            setEnabled(btnDelete, false);

            RoomApiService api = ApiClientMain.getClient(requireContext()).create(RoomApiService.class);
            api.deleteOwnership(chipId, userId).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> resp) {
                    if (!isAdded()) return;

                    int code = resp.code();
                    if (resp.isSuccessful()) {
                        removeEtagForChip(requireContext(), chipId);
                        Toast.makeText(requireContext(), R.string.room_deleted, Toast.LENGTH_SHORT).show();
                        notifyRoomsChanged();
                        dismiss();
                    } else {
                        switch (code) {
                            case 404:
                                removeEtagForChip(requireContext(), chipId);
                                Toast.makeText(requireContext(), R.string.room_already_deleted, Toast.LENGTH_SHORT).show();
                                notifyRoomsChanged();
                                dismiss();
                                break;
                            case 412:
                                Toast.makeText(requireContext(), R.string.error_outdated_data, Toast.LENGTH_LONG).show();
                                setEnabled(btnCancel, true);
                                setEnabled(btnDelete, true);
                                notifyRoomsChanged();
                                break;
                            case 409:
                                Toast.makeText(requireContext(), R.string.error_conflict, Toast.LENGTH_LONG).show();
                                setEnabled(btnCancel, true);
                                setEnabled(btnDelete, true);
                                notifyRoomsChanged();
                                break;
                            default:
                                Toast.makeText(requireContext(),
                                        getString(R.string.error_delete_code, code), Toast.LENGTH_SHORT).show();
                                setEnabled(btnCancel, true);
                                setEnabled(btnDelete, true);
                                break;
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            getString(R.string.error_delete_crash, t.getMessage()), Toast.LENGTH_SHORT).show();
                    setEnabled(btnCancel, true);
                    setEnabled(btnDelete, true);
                }
            });
        });

        return new MaterialAlertDialogBuilder(requireContext(), R.style.Overlay_SenseData_AlertDialog)
                .setView(view)
                .setCancelable(true)
                .create();
    }

    private void notifyRoomsChanged() {
        if (getActivity() instanceof OnRoomsChangedListener l) {
            l.onRoomsChanged();
        }
    }

    private static int getSavedUserId(Context ctx) {
        return ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                .getInt("userId", -1);
    }

    private static void removeEtagForChip(Context ctx, String chipId) {
        ctx.getSharedPreferences("etag_store", Context.MODE_PRIVATE)
                .edit().remove("etag_" + chipId).apply();
    }

    private static void setEnabled(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.6f);
    }
}
