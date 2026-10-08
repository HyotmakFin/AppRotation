package com.rotate.xposed;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    public interface OnItemClickListener {
        void onItemClick(AppEntry entry);
    }

    private final List<AppEntry> items;
    private final String[] options;
    private final OnItemClickListener listener;

    public AppAdapter(Context context, List<AppEntry> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
        this.options = context.getResources().getStringArray(R.array.rotation_options);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final AppEntry entry = items.get(position);

        holder.icon.setImageDrawable(entry.icon);
        holder.name.setText(entry.label);
        holder.pkg.setText(entry.packageName);
        holder.value.setText(options[Config.indexOfDegrees(entry.orientation)]);

        int colorAttr = entry.orientation == Config.NONE
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorPrimary;
        holder.value.setTextColor(MaterialColors.getColor(holder.value, colorAttr));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(entry));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final TextView value;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            name = itemView.findViewById(R.id.item_name);
            pkg = itemView.findViewById(R.id.item_package);
            value = itemView.findViewById(R.id.item_value);
        }
    }
}
