package com.example.sensedata.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.R;
import com.example.sensedata.model.room.RoomWithSensorDto;

import java.util.Locale;
import java.util.Objects;

public class RoomAdapter extends ListAdapter<RoomWithSensorDto, RoomAdapter.RoomViewHolder> {

    // ==== Публічні лістенери кліків ====
    public interface OnRoomClickListener {
        void onRoomClick(RoomWithSensorDto room);
    }
    public interface OnRoomLongClickListener {
        void onRoomLongClick(View anchor, RoomWithSensorDto room);
    }

    private final OnRoomClickListener clickListener;
    private final OnRoomLongClickListener longClickListener;

    public RoomAdapter(OnRoomClickListener clickListener,
                       OnRoomLongClickListener longClickListener) {
        super(DIFF);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        setHasStableIds(true);
    }

    // ==== DiffUtil ====
    private static final DiffUtil.ItemCallback<RoomWithSensorDto> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull RoomWithSensorDto oldItem,
                                       @NonNull RoomWithSensorDto newItem) {
            return Objects.equals(oldItem.getChipId(), newItem.getChipId());
        }
        @Override
        public boolean areContentsTheSame(@NonNull RoomWithSensorDto oldItem,
                                          @NonNull RoomWithSensorDto newItem) {
            return Objects.equals(oldItem.getRoomName(), newItem.getRoomName())
                    && Objects.equals(oldItem.getImageName(), newItem.getImageName())
                    && sameRounded(oldItem.getTemperature(), newItem.getTemperature())
                    && sameRounded(oldItem.getHumidity(), newItem.getHumidity());
        }
    };

    private static boolean sameRounded(Double a, Double b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.isNaN() && b.isNaN()) return true;
        if (a.isNaN() || b.isNaN()) return false;
        return Math.round(a) == Math.round(b);
    }

    // ==== Stable IDs ====
    @Override
    public long getItemId(int position) {
        RoomWithSensorDto item = getItem(position);
        String chip = item == null ? null : item.getChipId();
        if (chip == null) return position;
        return (long) chip.hashCode() & 0x00000000ffffffffL;
    }

    // ==== ViewHolder ====
    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room_chip, parent, false);
        return new RoomViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener, longClickListener);
    }

    /** Має бути public, інакше студія лаялась: "exposed outside visibility scope". */
    public static class RoomViewHolder extends RecyclerView.ViewHolder {
        private final TextView textRoomName;
        private final TextView textTemp;
        private final TextView textHumidity;
        private final ImageView imageRoom;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            textRoomName = itemView.findViewById(R.id.textRoom);
            textTemp     = itemView.findViewById(R.id.textTemperature);
            textHumidity = itemView.findViewById(R.id.textHumidity);
            imageRoom    = itemView.findViewById(R.id.roomImage);
        }

        void bind(@NonNull RoomWithSensorDto room,
                  @NonNull OnRoomClickListener clickListener,
                  @NonNull OnRoomLongClickListener longClickListener) {

            final var ctx = itemView.getContext();

            // Назва кімнати
            String roomName = room.getRoomName();
            if (roomName == null || roomName.trim().isEmpty()) {
                roomName = ctx.getString(R.string.room_default_name);
            }
            textRoomName.setText(roomName);

            // Температура (цілі градуси)
            String tText = ctx.getString(
                    R.string.temp_value,
                    (room.getTemperature() == null || room.getTemperature().isNaN())
                            ? ctx.getString(R.string.value_dash)
                            : String.format(Locale.getDefault(), "%d °C", Math.round(room.getTemperature()))
            );
            textTemp.setText(tText);

            // Вологість (цілі відсотки)
            String hText = ctx.getString(
                    R.string.humidity_value,
                    (room.getHumidity() == null || room.getHumidity().isNaN())
                            ? ctx.getString(R.string.value_dash)
                            : String.format(Locale.getDefault(), "%.0f %%", room.getHumidity())
            );
            textHumidity.setText(hText);

            // Зображення
            imageRoom.setImageResource(getImageResId(room.getImageName()));
            imageRoom.setContentDescription(roomName);

            // Кліки
            itemView.setOnClickListener(v -> clickListener.onRoomClick(room));
            itemView.setOnLongClickListener(v -> {
                longClickListener.onRoomLongClick(v, room);
                return true;
            });
        }

        private int getImageResId(String imageName) {

            Log.d("RoomAdapter", "Binding imageName=" + imageName);

            if (imageName == null) return R.drawable.livingroom;
            return switch (imageName) {
                case "kitchen"    -> R.drawable.kitchen;
                case "livingroom" -> R.drawable.livingroom;
                case "bathroom"   -> R.drawable.bathroom;
                case "balkon"       -> R.drawable.balkon;
                case "bedroom"    -> R.drawable.bedroom;
                case "homeoffice" -> R.drawable.homeoffice;
                default           -> R.drawable.livingroom;
            };
        }

    }
}
