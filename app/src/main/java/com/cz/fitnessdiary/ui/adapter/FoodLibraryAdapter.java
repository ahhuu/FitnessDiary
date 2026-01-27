package com.cz.fitnessdiary.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;

import java.util.ArrayList;
import java.util.List;

public class FoodLibraryAdapter extends RecyclerView.Adapter<FoodLibraryAdapter.ViewHolder> {

    private Context context;
    private List<FoodLibrary> foodList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FoodLibrary food);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public FoodLibraryAdapter(Context context) {
        this.context = context;
        this.foodList = new ArrayList<>();
    }

    public void setFoodList(List<FoodLibrary> foodList) {
        this.foodList = foodList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_food_library, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FoodLibrary food = foodList.get(position);
        holder.tvName.setText(food.getName());
        holder.tvDetails.setText(food.getCaloriesPer100g() + " 千卡/100g");
        
        String macros = String.format("蛋白质: %.1fg · 碳水: %.1fg", 
                food.getProteinPer100g(), food.getCarbsPer100g());
        holder.tvMacros.setText(macros);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(food);
            }
        });
    }

    @Override
    public int getItemCount() {
        return foodList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvDetails;
        TextView tvMacros;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDetails = itemView.findViewById(R.id.tv_details);
            tvMacros = itemView.findViewById(R.id.tv_macros);
        }
    }
}
