package com.example.sensedata.adapter;

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
        setHasStableIds(true); // 👈 стабільні ID для кращих анімацій/перемальовування
    }

    // ==== DiffUtil ====
    private static final DiffUtil.ItemCallback<RoomWithSensorDto> DIFF =
            new DiffUtil.ItemCallback<RoomWithSensorDto>() {
                @Override
                public boolean areItemsTheSame(@NonNull RoomWithSensorDto oldItem,
                                               @NonNull RoomWithSensorDto newItem) {
                    return safeEq(oldItem.getChipId(), newItem.getChipId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull RoomWithSensorDto oldItem,
                                                  @NonNull RoomWithSensorDto newItem) {
                    // Порівнюємо тільки те, що реально показуємо
                    return safeEq(oldItem.getRoomName(), newItem.getRoomName())
                            && safeEq(oldItem.getImageName(), newItem.getImageName())
                            && sameTempUi(oldItem.getTemperature(), newItem.getTemperature())
                            && sameHumUi(oldItem.getHumidity(), newItem.getHumidity());
                }
            };

    private static boolean safeEq(Object a, Object b) { return Objects.equals(a, b); }

    // Температуру показуємо як (int)Math.round(...) -> порівнюємо так само
    private static boolean sameTempUi(Double a, Double b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.isNaN() && b.isNaN()) return true;
        if (a.isNaN() || b.isNaN()) return false;
        return Math.round(a) == Math.round(b);
    }

    // Вологість показуємо як "%.0f %%" -> порівнюємо округлені значення
    private static boolean sameHumUi(Double a, Double b) {
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
        if (chip == null) return position; // fallback
        // перетворюємо hashCode у беззнаковий long для стабільності
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

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        private final TextView textRoomName;
        private final TextView textTemp;
        private final TextView textHumidity;
        private final ImageView imageRoom;

        RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            textRoomName = itemView.findViewById(R.id.textRoom);
            textTemp     = itemView.findViewById(R.id.textTemperature);
            textHumidity = itemView.findViewById(R.id.textHumidity);
            imageRoom    = itemView.findViewById(R.id.roomImage);
        }

        void bind(RoomWithSensorDto room,
                  OnRoomClickListener clickListener,
                  OnRoomLongClickListener longClickListener) {

            // ---- Текст ----
            String roomName = room.getRoomName() == null ? "Room" : room.getRoomName();
            textRoomName.setText(roomName);

            // Температура (округлення до цілого, як і показ у UI)
            Double t = room.getTemperature();
            String tStr = (t == null || t.isNaN())
                    ? "--"
                    : ((int) Math.round(t)) + " °C";

            // Вологість (без знаків після коми у відсотках)
            Double h = room.getHumidity();
            String hStr = (h == null || h.isNaN())
                    ? "--"
                    : String.format(Locale.getDefault(), "%.0f %%", h);

            textTemp.setText("\uD83C\uDF21" + tStr);  // 🌡
            textHumidity.setText("\uD83D\uDCA7" + hStr); // 💧

            // ---- Зображення ----
            imageRoom.setImageResource(getImageResId(room.getImageName()));
            imageRoom.setContentDescription(roomName);

            // ---- Кліки ----
            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onRoomClick(room);
            });
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onRoomLongClick(v, room);
                    return true; // споживаємо подію довгого натискання
                }
                return false;
            });
        }

        private int getImageResId(String imageName) {
            if (imageName == null) return R.drawable.livingroom;
            switch (imageName) {
                case "kitchen":        return R.drawable.kitchen;
                case "livingroom":    return R.drawable.livingroom;
                case "bathroom":  return R.drawable.bathroom;
                case "balkon":     return R.drawable.balkon;
                case "bedroom":        return R.drawable.bedroom;
                case "homeoffice":       return R.drawable.homeoffice;
                //case "kids_room":      return R.drawable.kids_room;     // опційно
                default:               return R.drawable.livingroom;
            }
        }
    }
}
