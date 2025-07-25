package com.example.sensedata.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.R;
import com.example.sensedata.model.RoomInfo;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    private final List<RoomInfo> roomList;
    private final OnRoomClickListener listener;

    public interface OnRoomClickListener {
        void onRoomClick(RoomInfo room);
    }

    public RoomAdapter(List<RoomInfo> roomList, OnRoomClickListener listener) {
        this.roomList = roomList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_chip, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        RoomInfo room = roomList.get(position);
        holder.textRoom.setText(room.name);
        holder.textTemperature.setText("🌡 " + room.temperature);
        holder.textHumidity.setText("💧 " + room.humidity);
        holder.roomImage.setImageResource(room.imageResId);

        holder.itemView.setOnClickListener(v -> listener.onRoomClick(room));
    }

    @Override
    public int getItemCount() {
        return roomList.size();
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView textRoom, textTemperature, textHumidity;
        ImageView roomImage;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            textRoom = itemView.findViewById(R.id.textRoom);
            textTemperature = itemView.findViewById(R.id.textTemperature);
            textHumidity = itemView.findViewById(R.id.textHumidity);
            roomImage = itemView.findViewById(R.id.roomImage);
        }
    }
}
