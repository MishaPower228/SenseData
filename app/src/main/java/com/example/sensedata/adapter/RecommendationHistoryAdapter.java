package com.example.sensedata.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.R;
import com.example.sensedata.model.recommendations.RecommendationHistoryDto;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class RecommendationHistoryAdapter extends ListAdapter<RecommendationHistoryDto, RecommendationHistoryAdapter.VH> {

    private final SimpleDateFormat inFmt  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat outFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    // ==== DiffUtil ====
    private static final DiffUtil.ItemCallback<RecommendationHistoryDto> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull RecommendationHistoryDto oldItem,
                                               @NonNull RecommendationHistoryDto newItem) {
                    // Унікальність за первинним ключем Id
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull RecommendationHistoryDto oldItem,
                                                  @NonNull RecommendationHistoryDto newItem) {
                    return Objects.equals(oldItem.recommendation, newItem.recommendation)
                            && Objects.equals(oldItem.createdAt, newItem.createdAt)
                            && Objects.equals(oldItem.roomName, newItem.roomName);
                }
            };

    public RecommendationHistoryAdapter() {
        super(DIFF);
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
        RecommendationHistoryDto it = getItem(pos);

        // --- формат дати ---
        String pretty = it.createdAt;
        try {
            if (pretty != null) {
                String cut = pretty.replace("Z", "").replaceAll("\\.\\d+", "");
                java.util.Date d = inFmt.parse(cut);
                if (d != null) pretty = outFmt.format(d);
            }
        } catch (Exception ignore) {}
        h.tvDate.setText(pretty == null ? "" : pretty);

        // --- текст рекомендацій ---
        String txt = it.recommendation == null ? "" : it.recommendation;
        txt = txt.replace("\r", "");
        StringBuilder sb = new StringBuilder();
        String[] lines = txt.split("\n");
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

    // ==== ViewHolder ====
    public static class VH extends RecyclerView.ViewHolder {
        final TextView tvDate, tvText;
        public VH(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDate);
            tvText = v.findViewById(R.id.tvText);
        }
    }
}
