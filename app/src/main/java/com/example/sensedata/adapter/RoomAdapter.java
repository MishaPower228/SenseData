package com.example.sensedata.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.R;
import com.example.sensedata.model.RoomWithSensorDto;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    public interface OnRoomClickListener {
        void onRoomClick(RoomWithSensorDto room);
    }

    private final List<RoomWithSensorDto> roomList;
    private final OnRoomClickListener listener;

    public RoomAdapter(List<RoomWithSensorDto> roomList, OnRoomClickListener listener) {
        this.roomList = roomList;
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
        RoomWithSensorDto room = roomList.get(position);
        holder.bind(room, listener);
    }

    @Override
    public int getItemCount() {
        return roomList.size();
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
            textRoomName.setText(room.name);
            textTemp.setText("🌡 Темп: " + (room.temperature != null ? room.temperature + " °C" : "--"));
            textHumidity.setText("💧 Волога: " + (room.humidity != null ? room.humidity + " %" : "--"));

            int imageResId = getImageResId(room.imageName);
            imageRoom.setImageResource(imageResId);

            itemView.setOnClickListener(v -> listener.onRoomClick(room));
        }

        private int getImageResId(String imageName) {
            switch (imageName) {
                case "kitchen": return R.drawable.kitchen;
                case "living_room": return R.drawable.living_room;
                case "livingroom": return R.drawable.livingroom;
                case "living_room_2": return R.drawable.living_room_2;
                default: return R.drawable.living_room;
            }
        }
    }
}
