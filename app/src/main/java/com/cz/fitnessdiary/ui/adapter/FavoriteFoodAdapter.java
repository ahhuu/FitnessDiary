package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FavoriteFood;

import java.util.ArrayList;
import java.util.List;

public class FavoriteFoodAdapter extends RecyclerView.Adapter<FavoriteFoodAdapter.ViewHolder> {

    private List<FavoriteFood> foods = new ArrayList<>();
    private OnFavoriteFoodActionListener listener;

    public interface OnFavoriteFoodActionListener {
        void onRecord(FavoriteFood food);
        void onRemove(FavoriteFood food);
    }

    public FavoriteFoodAdapter(OnFavoriteFoodActionListener listener) {
        this.listener = listener;
    }

    public void setFoods(List<FavoriteFood> foods) {
        this.foods = foods != null ? foods : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_food, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteFood food = foods.get(position);
        holder.tvName.setText(food.getFoodName());
        holder.tvCalories.setText((int) food.getCalories() + " kcal");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRecord(food);
        });
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(food);
        });
    }

    @Override
    public int getItemCount() { return foods.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCalories;
        ImageButton btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_fav_food_name);
            tvCalories = itemView.findViewById(R.id.tv_fav_food_calories);
            btnRemove = itemView.findViewById(R.id.btn_remove_fav);
        }
    }
}
