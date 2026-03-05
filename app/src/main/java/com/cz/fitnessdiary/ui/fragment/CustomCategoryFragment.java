package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.ui.adapter.CustomTrackerManageAdapter;
import com.cz.fitnessdiary.viewmodel.CustomCategoryDetailViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomCategoryFragment extends Fragment {

    private CustomCategoryDetailViewModel viewModel;
    private CustomTrackerManageAdapter adapter;
    private TextView tvSummary;
    private TextView tvEmpty;
    private ProgressBar progressLoading;

    private List<CustomTracker> trackers = new ArrayList<>();
    private long lastNavigateTs = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvSummary = view.findViewById(R.id.tv_summary);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);

        adapter = new CustomTrackerManageAdapter(new CustomTrackerManageAdapter.Listener() {
            @Override
            public void onOpen(CustomTracker tracker) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastNavigateTs < 280) return;
                lastNavigateTs = now;

                Bundle args = new Bundle();
                args.putLong("targetId", tracker.getId());
                args.putString("title", tracker.getName());
                args.putLong("selectedDate", requireArguments().getLong("selectedDate", System.currentTimeMillis()));

                NavOptions navOptions = new NavOptions.Builder()
                        .setEnterAnim(R.anim.slide_in_right)
                        .setExitAnim(R.anim.fade_out_fast)
                        .setPopEnterAnim(R.anim.fade_in_fast)
                        .setPopExitAnim(R.anim.slide_out_right)
                        .build();
                NavHostFragment.findNavController(CustomCategoryFragment.this)
                        .navigate(R.id.customCategoryDetailFragment, args, navOptions);
            }

            @Override
            public void onToggle(CustomTracker tracker, boolean enabled) {
                tracker.setEnabled(enabled);
                viewModel.updateTracker(tracker);
            }

            @Override
            public void onMoveUp(int position) {
                if (position <= 0 || position >= trackers.size()) return;
                swapSortOrder(position, position - 1);
            }

            @Override
            public void onMoveDown(int position) {
                if (position < 0 || position >= trackers.size() - 1) return;
                swapSortOrder(position, position + 1);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CustomCategoryDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        viewModel.getAllTrackers().observe(getViewLifecycleOwner(), items -> {
            trackers = items == null ? new ArrayList<>() : new ArrayList<>(items);
            bindList(viewModel.getTrackerMeta().getValue());
        });

        viewModel.getTrackerMeta().observe(getViewLifecycleOwner(), this::bindList);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());
    }

    private void bindList(Map<Long, CustomCategoryDetailViewModel.TrackerMeta> metaMap) {
        int enabledCount = 0;
        for (CustomTracker tracker : trackers) if (tracker.isEnabled()) enabledCount++;
        tvSummary.setText("已启用分类 " + enabledCount + " 个");

        adapter.submitList(trackers, convertMeta(metaMap));
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(trackers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private java.util.Map<Long, CustomTrackerManageAdapter.TrackerMeta> convertMeta(
            Map<Long, CustomCategoryDetailViewModel.TrackerMeta> src) {
        java.util.Map<Long, CustomTrackerManageAdapter.TrackerMeta> out = new java.util.HashMap<>();
        if (src == null) return out;
        for (Map.Entry<Long, CustomCategoryDetailViewModel.TrackerMeta> e : src.entrySet()) {
            CustomCategoryDetailViewModel.TrackerMeta m = e.getValue();
            out.put(e.getKey(), new CustomTrackerManageAdapter.TrackerMeta(m.todayCount, m.latestText));
        }
        return out;
    }

    private void swapSortOrder(int i, int j) {
        CustomTracker a = trackers.get(i);
        CustomTracker b = trackers.get(j);
        int so = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(so);
        viewModel.updateTracker(a);
        viewModel.updateTracker(b);
    }

    private void showAddDialog() {
        EditText et = new EditText(requireContext());
        et.setHint("分类名称（如 阅读/拉伸）");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("新增自定义分类").setView(et)
                .setPositiveButton("创建", (d, w) -> {
                    String text = et.getText() == null ? "" : et.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(getContext(), "请输入分类名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.addTracker(text, "次");
                }).setNegativeButton("取消", null).show();
    }
}
