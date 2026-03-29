package com.example.notex;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {

    private ArrayList<String> originalList;
    private ArrayList<String> filteredList;
    private OnItemLongClickListener longClickListener;
    private OnSelectionChangeListener selectionChangeListener;
    
    private boolean isSelectionMode = false;
    private HashSet<Integer> selectedPositions = new HashSet<>();

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public NoteAdapter(ArrayList<String> list) {
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
    }

    public void updateData(ArrayList<String> newList) {
        this.originalList = newList;
        this.filteredList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (String item : originalList) {
                if (item.toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }

    public ArrayList<Integer> getSelectedPositions() {
        return new ArrayList<>(selectedPositions);
    }

    public String getItem(int position) {
        if (position >= 0 && position < filteredList.size()) {
            return filteredList.get(position);
        }
        return null;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvContent;
        MaterialCardView cardView;

        public ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.title);
            tvContent = itemView.findViewById(R.id.content);
            cardView = (MaterialCardView) itemView;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = filteredList.get(position);
        String[] parts = LocalDatabaseHelper.splitNote(item);

        String fullText = parts[0];
        long time;
        try {
            time = Long.parseLong(parts[1]);
        } catch (Exception e) {
            time = 0;
        }
        String noteId = parts[2];

        // Format Date
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
        String formattedTime = (time > 0) ? sdf.format(new Date(time)) : "Recent";

        holder.tvTitle.setText(formattedTime);
        holder.tvContent.setText(fullText);

        // Selection UI
        if (selectedPositions.contains(position)) {
            holder.cardView.setStrokeWidth(4);
            holder.cardView.setStrokeColor(Color.parseColor("#2196F3"));
            holder.cardView.setCardBackgroundColor(Color.parseColor("#2A2A2A"));
        } else {
            holder.cardView.setStrokeWidth(0);
            holder.cardView.setCardBackgroundColor(Color.parseColor("#1A1A1A"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(holder.getAdapterPosition());
            } else {
                Intent intent = new Intent(v.getContext(), NoteDetailActivity.class);
                intent.putExtra("text", fullText);
                intent.putExtra("noteId", noteId);
                v.getContext().startActivity(intent);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(holder.getAdapterPosition());
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }
}
