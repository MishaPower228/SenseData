package com.example.sensedata.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.sensedata.R;
import com.example.sensedata.model.RecommendationHistoryDto;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class RecommendationHistoryAdapter extends RecyclerView.Adapter<RecommendationHistoryAdapter.VH> {

    private final List<RecommendationHistoryDto> items = new ArrayList<>();
    private final SimpleDateFormat inFmt  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat outFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public void submit(List<RecommendationHistoryDto> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation_history, parent, false);
        return new VH(v);
    }


    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        RecommendationHistoryDto it = items.get(pos);

        // формат дати
        String pretty = it.createdAt;
        try {
            if (pretty != null) {
                String cut = pretty.replace("Z", "").replaceAll("\\.\\d+", "");
                java.util.Date d = inFmt.parse(cut);
                if (d != null) pretty = outFmt.format(d);
            }
        } catch (Exception ignore) {}
        h.tvDate.setText(pretty == null ? "" : pretty);

        // текст без крапок/булітів
        String txt = (it.recommendation == null) ? "" : it.recommendation;

        // косметика: прибрати можливі "• " всередині, зробити кожен рядок з emoji
        txt = txt.replace("\r", "");
        String[] lines = txt.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().replaceFirst("^•\\s*", "");
            sb.append(iconFor(line)).append(" ").append(line);
            if (i < lines.length - 1) sb.append("\n");
        }
        h.tvText.setText(sb.toString());
    }

    private String iconFor(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        if (l.contains("газ")) return "🔥";
        if (l.contains("волог")) return "💧";
        if (l.contains("темп") || l.contains("жарк") || l.contains("холод")) return "🌡";
        return "🧠";
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvDate, tvText;
        VH(View v) {
            super(v);
            tvDate   = v.findViewById(R.id.tvDate);
            tvText   = v.findViewById(R.id.tvText);
        }
    }

}
