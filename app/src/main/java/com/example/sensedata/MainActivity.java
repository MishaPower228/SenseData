package com.example.sensedata;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomInfo;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Налаштування системних відступів
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.room_recycler_view),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                }
        );

        // Пошук RecyclerView
        RecyclerView recyclerView = findViewById(R.id.room_recycler_view);
        recyclerView.setHasFixedSize(true);

        // Горизонтальний LayoutManager
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
        );
        recyclerView.setLayoutManager(layoutManager);

        // Створення списку кімнат
        List<RoomInfo> roomList = Arrays.asList(
                new RoomInfo("Living Room", R.drawable.livingroom, "22°C", "45%"),
                new RoomInfo("Bedroom", R.drawable.livingroom, "21°C", "50%"),
                new RoomInfo("Kitchen", R.drawable.livingroom, "25°C", "55%")
        );

        // Створення адаптера та встановлення
        RoomAdapter adapter = new RoomAdapter(roomList, room ->
                Toast.makeText(this, "Selected: " + room.name, Toast.LENGTH_SHORT).show()
        );
        recyclerView.setAdapter(adapter);
    }
}
