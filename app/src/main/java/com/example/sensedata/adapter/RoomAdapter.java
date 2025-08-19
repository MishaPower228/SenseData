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
import com.example.sensedata.model.RoomWithSensorDto;

import java.util.Locale;
import java.util.Objects;

public class RoomAdapter extends ListAdapter<RoomWithSensorDto, RoomAdapter.RoomViewHolder> {

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
        super(new DiffUtil.ItemCallback<RoomWithSensorDto>() {
            @Override
            public boolean areItemsTheSame(@NonNull RoomWithSensorDto oldItem, @NonNull RoomWithSensorDto newItem) {
                return Objects.equals(oldItem.getChipId(), newItem.getChipId());
            }
            @Override
            public boolean areContentsTheSame(@NonNull RoomWithSensorDto oldItem, @NonNull RoomWithSensorDto newItem) {
                return Objects.equals(oldItem.getRoomName(), newItem.getRoomName()) &&
                        Objects.equals(oldItem.getImageName(), newItem.getImageName()) &&
                        Objects.equals(oldItem.getTemperature(), newItem.getTemperature()) &&
                        Objects.equals(oldItem.getHumidity(), newItem.getHumidity());
            }
        });
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        setHasStableIds(false);
    }

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
        private final TextView textRoomName, textTemp, textHumidity;
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
            // текст
            textRoomName.setText(room.getRoomName());

            Double t = room.getTemperature();
            String tStr = (t == null || t.isNaN()) ? "--" : ((int)Math.round(t)) + " °C";
            Double h = room.getHumidity();
            String hStr = (h == null || h.isNaN()) ? "--" :
                    String.format(Locale.getDefault(), "%.0f %%", h);

            textTemp.setText("\uD83C\uDF21" + tStr);
            textHumidity.setText("\uD83D\uDCA7" + hStr);

            // картинка
            imageRoom.setImageResource(getImageResId(room.getImageName()));

            // кліки
            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onRoomClick(room);
            });
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onRoomLongClick(v, room); // ВАЖЛИВО: передаємо anchor
                    return true; // ВАЖЛИВО: true, щоб подію не з’їло
                }
                return false;
            });
        }

        private int getImageResId(String imageName) {
            if (imageName == null) return R.drawable.living_room;
            switch (imageName) {
                case "kitchen": return R.drawable.kitchen;
                case "living_room": return R.drawable.living_room;
                case "living_room_2": return R.drawable.living_room_2;
                case "livingroom": return R.drawable.livingroom;
                default: return R.drawable.living_room;
            }
        }
    }
}
