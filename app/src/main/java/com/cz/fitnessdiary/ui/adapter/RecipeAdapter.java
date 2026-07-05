package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.Recipe;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.ViewHolder> {

    private List<Recipe> recipes = new ArrayList<>();
    private OnRecipeActionListener listener;

    public interface OnRecipeActionListener {
        void onRecord(Recipe recipe);
        void onDelete(Recipe recipe);
        void onEdit(Recipe recipe);
    }

    public RecipeAdapter(OnRecipeActionListener listener) {
        this.listener = listener;
    }

    public void setRecipes(List<Recipe> recipes) {
        this.recipes = recipes != null ? recipes : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recipe, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recipe recipe = recipes.get(position);
        holder.tvName.setText(recipe.getName());
        holder.tvCalories.setText((int) recipe.getTotalCalories() + " kcal");

        int foodCount = 0;
        try {
            foodCount = new com.google.gson.Gson()
                    .fromJson(recipe.getFoodsJson(), com.google.gson.JsonArray.class).size();
        } catch (Exception ignored) {}
        holder.tvFoodCount.setText(foodCount + " 道食物");

        holder.btnRecord.setOnClickListener(v -> {
            if (listener != null) listener.onRecord(recipe);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(recipe);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(recipe);
        });
    }

    @Override
    public int getItemCount() { return recipes.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCalories, tvFoodCount;
        MaterialButton btnRecord, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_recipe_name);
            tvCalories = itemView.findViewById(R.id.tv_recipe_calories);
            tvFoodCount = itemView.findViewById(R.id.tv_recipe_food_count);
            btnRecord = itemView.findViewById(R.id.btn_record_recipe);
            btnDelete = itemView.findViewById(R.id.btn_delete_recipe);
        }
    }
}
