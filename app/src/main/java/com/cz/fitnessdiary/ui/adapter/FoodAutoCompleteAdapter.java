package com.cz.fitnessdiary.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义自动完成适配器，支持以"包含"（Contains）的方式进行过滤
 * 替代系统默认的 "startsWith" 过滤逻辑
 */
public class FoodAutoCompleteAdapter extends ArrayAdapter<FoodLibrary> {

    private List<FoodLibrary> originalList;
    private List<FoodLibrary> filteredList;
    private Context context;

    public FoodAutoCompleteAdapter(@NonNull Context context, @NonNull List<FoodLibrary> foodList) {
        super(context, R.layout.item_food_dropdown, foodList);
        this.context = context;
        this.originalList = new ArrayList<>(foodList);
        this.filteredList = new ArrayList<>(foodList);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_food_dropdown, parent, false);
        }

        FoodLibrary food = getItem(position);
        TextView textView = convertView.findViewById(android.R.id.text1);

        if (food != null) {
            textView.setText(food.getName());
        }

        return convertView;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<FoodLibrary> suggestions = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    suggestions.addAll(originalList);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();

                    for (FoodLibrary item : originalList) {
                        // 核心修改：使用 contains 而不是 startsWith
                        if (item.getName().toLowerCase().contains(filterPattern)) {
                            suggestions.add(item);
                        }
                    }
                }

                results.values = suggestions;
                results.count = suggestions.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList.clear();
                if (results.values != null) {
                    // noinspection unchecked
                    filteredList.addAll((List<FoodLibrary>) results.values);
                }
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return ((FoodLibrary) resultValue).getName();
            }
        };
    }

    @Override
    public int getCount() {
        return filteredList.size();
    }

    @Nullable
    @Override
    public FoodLibrary getItem(int position) {
        // 防止索引越界
        if (position >= 0 && position < filteredList.size()) {
            return filteredList.get(position);
        }
        return null;
    }
}
