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

import java.util.Objects;

public class RoomAdapter extends ListAdapter<RoomWithSensorDto, RoomAdapter.RoomViewHolder> {

    public interface OnRoomClickListener {
        void onRoomClick(RoomWithSensorDto room);
    }

    private final OnRoomClickListener listener;

    public RoomAdapter(OnRoomClickListener listener) {
        super(new DiffUtil.ItemCallback<RoomWithSensorDto>() {
            @Override
            public boolean areItemsTheSame(@NonNull RoomWithSensorDto oldItem, @NonNull RoomWithSensorDto newItem) {
                return Objects.equals(oldItem.getChipId(), newItem.getChipId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull RoomWithSensorDto oldItem, @NonNull RoomWithSensorDto newItem) {
                return Objects.equals(oldItem.getRoomName(), newItem.getRoomName()) &&
                        Objects.equals(oldItem.getTemperature(), newItem.getTemperature()) &&
                        Objects.equals(oldItem.getHumidity(), newItem.getHumidity()) &&
                        Objects.equals(oldItem.getImageName(), newItem.getImageName());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room_chip, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        RoomWithSensorDto room = getItem(position);
        holder.bind(room, listener);
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        private final TextView textRoomName, textTemp, textHumidity;
        private final ImageView imageRoom;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            textRoomName = itemView.findViewById(R.id.textRoom);
            textTemp = itemView.findViewById(R.id.textTemperature);
            textHumidity = itemView.findViewById(R.id.textHumidity);
            imageRoom = itemView.findViewById(R.id.roomImage);
        }

        public void bind(RoomWithSensorDto room, OnRoomClickListener listener) {
            textRoomName.setText(room.getRoomName());
            textTemp.setText("\uD83C\uDF21 Темп: " + (room.getTemperature() != null ? room.getTemperature() + " °C" : "--"));
            textHumidity.setText("\uD83D\uDCA7 Волога: " + (room.getHumidity() != null ? room.getHumidity() + " %" : "--"));

            int imageResId = getImageResId(room.getImageName());
            imageRoom.setImageResource(imageResId);

            itemView.setOnClickListener(v -> listener.onRoomClick(room));
        }

        private int getImageResId(String imageName) {
            switch (imageName) {
                case "kitchen":
                    return R.drawable.kitchen;
                case "living_room":
                    return R.drawable.living_room;
                case "living_room_2":
                    return R.drawable.living_room_2;
                case "livingroom":
                    return R.drawable.livingroom;
                default:
                    return R.drawable.living_room;
            }
        }
    }
}
