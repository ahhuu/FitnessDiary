package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.LinkedHashMap;
import java.util.Map;

public class MoodPickerBottomSheet extends BottomSheetDialogFragment {

    private static final Map<String, String[]> MOOD_MAP = new LinkedHashMap<>();

    static {
        MOOD_MAP.put("HAPPY", new String[]{"😊", "开心"});
        MOOD_MAP.put("NEUTRAL", new String[]{"😐", "一般"});
        MOOD_MAP.put("SAD", new String[]{"😢", "低落"});
        MOOD_MAP.put("IRRITABLE", new String[]{"😡", "烦躁"});
        MOOD_MAP.put("ANXIOUS", new String[]{"😰", "焦虑"});
    }

    public interface OnMoodSelectedListener {
        void onMoodSelected(String moodCode);
    }

    private OnMoodSelectedListener listener;
    private String currentMoodCode;

    public static MoodPickerBottomSheet newInstance(String currentMoodCode) {
        MoodPickerBottomSheet sheet = new MoodPickerBottomSheet();
        Bundle args = new Bundle();
        args.putString("current_mood", currentMoodCode);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnMoodSelectedListener(OnMoodSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_mood_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            currentMoodCode = getArguments().getString("current_mood");
        }

        LinearLayout optionsLayout = view.findViewById(R.id.layout_mood_options);

        for (Map.Entry<String, String[]> entry : MOOD_MAP.entrySet()) {
            String code = entry.getKey();
            String emoji = entry.getValue()[0];
            String name = entry.getValue()[1];

            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER);
            item.setPadding(dp(10), dp(6), dp(10), dp(6));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            item.setLayoutParams(lp);

            TextView tvEmoji = new TextView(requireContext());
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(28);
            tvEmoji.setGravity(android.view.Gravity.CENTER);

            TextView tvName = new TextView(requireContext());
            tvName.setText(name);
            tvName.setTextSize(11);
            tvName.setTextColor(requireContext().getResources().getColor(R.color.text_secondary, null));
            tvName.setGravity(android.view.Gravity.CENTER);

            boolean isSelected = code.equals(currentMoodCode);
            tvEmoji.setAlpha(isSelected ? 1.0f : 0.4f);
            tvName.setAlpha(isSelected ? 1.0f : 0.4f);

            item.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoodSelected(code);
                }
                dismiss();
            });

            item.addView(tvEmoji);
            item.addView(tvName);
            optionsLayout.addView(item);
        }

        view.findViewById(R.id.btn_mood_done).setOnClickListener(v -> dismiss());
    }

    private int dp(int x) {
        return Math.round(x * requireContext().getResources().getDisplayMetrics().density);
    }

    public static String getMoodEmoji(String code) {
        String[] pair = MOOD_MAP.get(code);
        return pair != null ? pair[0] : "—";
    }

    public static String getMoodName(String code) {
        String[] pair = MOOD_MAP.get(code);
        return pair != null ? pair[1] : "点击记录";
    }

    public static String getMoodSummary(String code) {
        switch (code != null ? code : "") {
            case "HAPPY": return "今天感觉很不错！";
            case "NEUTRAL": return "平平淡淡才是真";
            case "SAD": return "抱抱，明天会更好";
            case "IRRITABLE": return "深呼吸，放轻松";
            case "ANXIOUS": return "别担心，一切都会好的";
            default: return "记录每日心情";
        }
    }
}
