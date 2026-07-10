package com.cz.fitnessdiary.ui.bottomSheet;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.gridlayout.widget.GridLayout;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentChallengeBottomSheetBinding;
import com.cz.fitnessdiary.databinding.ItemChallengeCardBinding;
import com.cz.fitnessdiary.utils.ChallengeManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ChallengeBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentChallengeBottomSheetBinding binding;
    private String selectedEmoji = "🔥";
    private int selectedFailsLimit = 3;
    private String selectedBinding = "NONE";
    private int currentCategoryFilter = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ChallengeBottomSheetFragment newInstance() {
        return new ChallengeBottomSheetFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChallengeBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupTabs();
        setupCreateForm();
        // 在子线程执行结算，然后回主线程更新 UI
        refreshViewStateAsync();
    }

    // ─────────────── 异步刷新（核心修复：所有 DB 操作在子线程）───────────────

    private void refreshViewStateAsync() {
        Context context = getContext();
        if (context == null) return;

        // 显示加载中状态
        binding.layoutActive.setVisibility(View.GONE);
        binding.layoutPicker.setVisibility(View.GONE);
        binding.layoutCreate.setVisibility(View.GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            // 子线程：执行天结算（涉及 Room 查询）
            try {
                ChallengeManager.checkToday(context);
            } catch (Exception ignored) {}

            String activeType = ChallengeManager.getActiveType(context);
            String status = ChallengeManager.getStatus(context);

            mainHandler.post(() -> {
                if (!isAdded() || binding == null) return;

                if (activeType != null && "ACTIVE".equals(status)) {
                    switchView(R.id.layout_active);
                    renderActivePanelAsync();
                } else if (activeType != null && ("COMPLETED".equals(status) || "FAILED".equals(status))) {
                    // 已完成或失败状态：先展示 Picker，同时弹出结果弹窗
                    switchView(R.id.layout_picker);
                    renderPickerPanel();
                    showChallengeResultDialog(activeType, status);
                } else {
                    switchView(R.id.layout_picker);
                    renderPickerPanel();
                }
            });
        });
    }

    private void switchView(int viewId) {
        binding.layoutActive.setVisibility(viewId == R.id.layout_active ? View.VISIBLE : View.GONE);
        binding.layoutPicker.setVisibility(viewId == R.id.layout_picker ? View.VISIBLE : View.GONE);
        binding.layoutCreate.setVisibility(viewId == R.id.layout_create ? View.VISIBLE : View.GONE);
    }

    // ─────────────────────── 面板一：进行中的挑战 ───────────────────────

    private void renderActivePanelAsync() {
        Context context = getContext();
        if (context == null) return;

        // 先用 SP 中的数据立刻渲染基础信息（不涉及 DB）
        String name = ChallengeManager.getActiveName(context);
        String desc = ChallengeManager.getActiveDesc(context);
        String emoji = ChallengeManager.getActiveEmoji(context);
        int maxFails = ChallengeManager.getActiveMaxFails(context);
        int progressDays = ChallengeManager.getProgressDays(context);
        int failDays = ChallengeManager.getFailDays(context);
        String bindCard = context.getSharedPreferences("challenge_prefs", Context.MODE_PRIVATE)
                .getString("active_bind_card", "NONE");

        binding.tvActiveTitle.setText(name);
        binding.tvActiveDesc.setText(desc);
        binding.tvActiveEmoji.setText(emoji);

        // Emoji 背景色按板块区分
        int emojiBgColor = 0xFFFFF3E0;
        if ("FAT_LOSS".equals(bindCard))      emojiBgColor = 0xFFFFEBEE;
        else if ("MUSCLE_GAIN".equals(bindCard)) emojiBgColor = 0xFFE3F2FD;
        else if ("EARLY_SLEEP".equals(bindCard)) emojiBgColor = 0xFFEDE7F6;
        else if ("WATER_MASTER".equals(bindCard)) emojiBgColor = 0xFFE0F7FA;
        else if ("STEP".equals(bindCard))     emojiBgColor = 0xFFE8F5E9;
        binding.cardActiveEmojiBg.setCardBackgroundColor(emojiBgColor);

        binding.tvActiveProgressTxt.setText("第 " + Math.min(progressDays, 21) + " / 21 天");
        binding.tvActiveFailSummary.setText("已失败 " + failDays + " / " + maxFails + " 次");
        binding.progressActiveChallenge.setMax(21);
        binding.progressActiveChallenge.setProgress(Math.min(progressDays, 21));

        // 初始化轨迹格为全未开始（稍后异步更新）
        setupTrackingGrid(new int[21]);

        // 绑定类型判断
        if ("NONE".equals(bindCard)) {
            binding.tvActiveAutoHint.setVisibility(View.GONE);
            binding.btnActiveCheckin.setVisibility(View.VISIBLE);
            binding.btnActiveCheckin.setEnabled(false);
            binding.btnActiveCheckin.setText("加载中…");
        } else {
            binding.btnActiveCheckin.setVisibility(View.GONE);
            binding.tvActiveAutoHint.setVisibility(View.VISIBLE);
            binding.tvActiveAutoHint.setText("💡 已绑定数据卡片，完成当日目标后自动计入");
        }

        binding.btnActiveAbandon.setOnClickListener(v -> showAbandonConfirm());

        // 子线程：异步获取打卡轨迹 + 今日打卡状态（涉及 DB）
        Executors.newSingleThreadExecutor().execute(() -> {
            int[] tracking = ChallengeManager.getDayTrackingStatusSync(context);
            boolean checkedToday = ChallengeManager.isCheckedTodaySync(context);

            mainHandler.post(() -> {
                if (!isAdded() || binding == null) return;
                setupTrackingGrid(tracking);
                if ("NONE".equals(bindCard)) {
                    if (checkedToday) {
                        binding.btnActiveCheckin.setEnabled(false);
                        binding.btnActiveCheckin.setText("✅  今日已打卡");
                    } else {
                        binding.btnActiveCheckin.setEnabled(true);
                        binding.btnActiveCheckin.setText("今日打卡");
                        binding.btnActiveCheckin.setOnClickListener(v -> {
                            ChallengeManager.checkInToday(context);
                            Toast.makeText(context, "🎉 打卡成功，继续保持！", Toast.LENGTH_SHORT).show();
                            renderActivePanelAsync();
                        });
                    }
                }
            });
        });
    }

    private void setupTrackingGrid(int[] tracking) {
        if (!isAdded() || binding == null) return;
        binding.gridActiveTracking.removeAllViews();
        Context context = requireContext();
        int size = dp(34);
        int margin = dp(4);
        int radius = dp(10);

        for (int i = 0; i < 21; i++) {
            MaterialCardView card = new MaterialCardView(context);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(lp);
            card.setRadius(radius);
            card.setCardElevation(0);
            card.setStrokeWidth(dp(1));

            TextView tv = new TextView(context);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTypeface(null, Typeface.BOLD);

            int status = (tracking != null && i < tracking.length) ? tracking[i] : 0;
            if (status == 1) {
                card.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                card.setStrokeColor(Color.parseColor("#66BB6A"));
                tv.setText("✔");
                tv.setTextColor(Color.parseColor("#2E7D32"));
                tv.setTextSize(12);
            } else if (status == 2) {
                card.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                card.setStrokeColor(Color.parseColor("#EF9A9A"));
                tv.setText("✕");
                tv.setTextColor(Color.parseColor("#C62828"));
                tv.setTextSize(12);
            } else {
                card.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                card.setStrokeColor(Color.parseColor("#E0E0E0"));
                tv.setText(String.valueOf(i + 1));
                tv.setTextColor(Color.parseColor("#BDBDBD"));
                tv.setTextSize(10);
            }

            card.addView(tv);
            binding.gridActiveTracking.addView(card);
        }
    }

    private void showAbandonConfirm() {
        Context context = getContext();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle("确认放弃挑战")
                .setMessage("放弃后所有进度将清零，确定要放弃吗？")
                .setPositiveButton("放弃", (d, w) -> {
                    ChallengeManager.reset(context);
                    Toast.makeText(context, "已放弃当前挑战", Toast.LENGTH_SHORT).show();
                    refreshViewStateAsync();
                })
                .setNegativeButton("继续坚持", null)
                .show();
    }

    // ─────── 挑战完成/失败结果弹窗（含 COMPLETED 庆祝）───────

    private void showChallengeResultDialog(String activeType, String status) {
        Context context = getContext();
        if (context == null) return;

        String name = ChallengeManager.getActiveName(context);

        if ("COMPLETED".equals(status)) {
            // 庆祝弹窗 View
            View celebView = LayoutInflater.from(context).inflate(R.layout.dialog_challenge_celebrate, null, false);
            TextView tvCelebEmoji = celebView.findViewById(R.id.tv_celeb_emoji);
            TextView tvCelebTitle = celebView.findViewById(R.id.tv_celeb_title);
            TextView tvCelebSubtitle = celebView.findViewById(R.id.tv_celeb_subtitle);

            String emoji = ChallengeManager.getActiveEmoji(context);
            if (tvCelebEmoji != null) tvCelebEmoji.setText(emoji.isEmpty() ? "🏆" : emoji);
            if (tvCelebTitle != null) tvCelebTitle.setText("挑战完成！");
            if (tvCelebSubtitle != null) tvCelebSubtitle.setText("你已坚持完成了 21 天的「" + name + "」！这是真正的坚持，恭喜你！");

            // Emoji 弹出动画
            if (tvCelebEmoji != null) {
                tvCelebEmoji.setScaleX(0f);
                tvCelebEmoji.setScaleY(0f);
                tvCelebEmoji.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(600)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .setStartDelay(200)
                        .start();
            }

            new MaterialAlertDialogBuilder(context)
                    .setView(celebView)
                    .setPositiveButton("太棒了！开启新挑战", (d, w) -> {
                        ChallengeManager.reset(context);
                        // 触发成就解锁检测
                        triggerChallengeCompletedAchievement(context);
                        renderPickerPanel();
                    })
                    .setNegativeButton("关闭", (d, w) -> ChallengeManager.reset(context))
                    .show();
        } else {
            // FAILED
            new MaterialAlertDialogBuilder(context)
                    .setTitle("😔 挑战失败")
                    .setMessage("「" + name + "」挑战中失败次数已超限，本次挑战结束。别气馁，重新开始！")
                    .setPositiveButton("重新挑战", (d, w) -> {
                        ChallengeManager.reset(context);
                        renderPickerPanel();
                    })
                    .setNegativeButton("关闭", (d, w) -> ChallengeManager.reset(context))
                    .show();
        }
    }

    private void triggerChallengeCompletedAchievement(Context context) {
        // 通知成就系统：完成了一次21天挑战
        // 使用广播通知 AchievementCenterViewModel 刷新成就
        android.content.Intent intent = new android.content.Intent("com.cz.fitnessdiary.CHALLENGE_COMPLETED");
        context.sendBroadcast(intent);
    }

    // ─────────────────────── 面板二：选择挑战库 ───────────────────────

    private void setupTabs() {
        binding.chipCatAll.setOnClickListener(v -> { currentCategoryFilter = 0; renderPickerPanel(); });
        binding.chipCatDiet.setOnClickListener(v -> { currentCategoryFilter = 1; renderPickerPanel(); });
        binding.chipCatSport.setOnClickListener(v -> { currentCategoryFilter = 2; renderPickerPanel(); });
        binding.chipCatSleep.setOnClickListener(v -> { currentCategoryFilter = 3; renderPickerPanel(); });
        binding.chipCatCustom.setOnClickListener(v -> { currentCategoryFilter = 4; renderPickerPanel(); });
    }

    private void renderPickerPanel() {
        if (!isAdded() || binding == null) return;
        binding.layoutPickerList.removeAllViews();
        Context context = getContext();
        if (context == null) return;

        List<ChallengeManager.Challenge> presets = ChallengeManager.getPresetChallenges();
        List<ChallengeManager.Challenge> customs = ChallengeManager.getCustomChallenges(context);
        List<ChallengeManager.Challenge> filteredList = new ArrayList<>();

        if (currentCategoryFilter == 0) {
            filteredList.addAll(presets);
            filteredList.addAll(customs);
        } else if (currentCategoryFilter == 4) {
            filteredList.addAll(customs);
        } else {
            int catValue = currentCategoryFilter - 1;
            for (ChallengeManager.Challenge c : presets) {
                if (c.category == catValue) filteredList.add(c);
            }
            for (ChallengeManager.Challenge c : customs) {
                if (c.category == catValue) filteredList.add(c);
            }
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (ChallengeManager.Challenge c : filteredList) {
            ItemChallengeCardBinding cardBinding = ItemChallengeCardBinding.inflate(inflater, binding.layoutPickerList, false);
            bindChallengeCard(cardBinding, c, context, inflater);
            binding.layoutPickerList.addView(cardBinding.getRoot());
        }

        // 底部：新建自定义挑战卡片
        ItemChallengeCardBinding addBinding = ItemChallengeCardBinding.inflate(inflater, binding.layoutPickerList, false);
        addBinding.tvItemTitle.setText("新建自定义挑战");
        addBinding.tvItemDesc.setText("根据个人习惯，量身定制一个21天挑战");
        addBinding.tvItemEmoji.setText("✏️");
        addBinding.cardItemEmojiBg.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
        addBinding.btnDeleteCustomChallenge.setVisibility(View.GONE);
        addBinding.cardChallengeItem.setOnClickListener(v -> {
            switchView(R.id.layout_create);
            setupEmojiSelector();
        });
        binding.layoutPickerList.addView(addBinding.getRoot());
    }

    private void bindChallengeCard(ItemChallengeCardBinding cardBinding,
                                   ChallengeManager.Challenge c,
                                   Context context,
                                   LayoutInflater inflater) {
        cardBinding.tvItemTitle.setText(c.name);
        cardBinding.tvItemDesc.setText(c.desc);
        cardBinding.tvItemEmoji.setText(c.emoji);

        // 按板块设置渐变底色
        int bgColor;
        switch (c.category) {
            case 0:  bgColor = Color.parseColor("#FFF8F8"); break; // 饮食 粉白
            case 1:  bgColor = Color.parseColor("#F0F8FF"); break; // 运动 淡蓝
            case 2:  bgColor = Color.parseColor("#F5F0FF"); break; // 作息 淡紫
            default: bgColor = Color.parseColor("#F0FFF4"); break; // 自定义 淡绿
        }
        cardBinding.cardChallengeItem.setCardBackgroundColor(bgColor);
        cardBinding.cardItemEmojiBg.setCardBackgroundColor(Color.parseColor("#FFFFFF"));

        // 点击：开启挑战
        cardBinding.cardChallengeItem.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("开启 21 天挑战")
                    .setMessage("准备好接受「" + c.name + "」挑战了吗？\n\n" + c.desc)
                    .setPositiveButton("立刻开始 🚀", (d, w) -> {
                        ChallengeManager.start(context, c);
                        Toast.makeText(context, "「" + c.name + "」挑战已开启！", Toast.LENGTH_SHORT).show();
                        switchView(R.id.layout_active);
                        renderActivePanelAsync();
                    })
                    .setNegativeButton("再想想", null)
                    .show();
        });

        // 自定义挑战：显示删除按钮
        if (c.id != null && c.id.startsWith("CUSTOM_")) {
            cardBinding.btnDeleteCustomChallenge.setVisibility(View.VISIBLE);
            cardBinding.btnDeleteCustomChallenge.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(context)
                        .setTitle("删除自定义挑战")
                        .setMessage("确定要删除「" + c.name + "」吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            ChallengeManager.deleteCustomChallenge(context, c.id);
                            renderPickerPanel();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        } else {
            cardBinding.btnDeleteCustomChallenge.setVisibility(View.GONE);
        }
    }

    // ─────────────────────── 面板三：新建自定义挑战 ───────────────────────

    private void setupCreateForm() {
        binding.seekCreateFails.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                selectedFailsLimit = progress;
                binding.tvCreateFailsIndicator.setText(progress + " 天");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        binding.btnCreateSubmit.setOnClickListener(v -> {
            String name = binding.etCreateName.getText().toString().trim();
            String desc = binding.etCreateDesc.getText().toString().trim();
            if (name.isEmpty()) { binding.etCreateName.setError("请输入名称"); return; }
            if (desc.isEmpty()) { binding.etCreateDesc.setError("请输入规则说明"); return; }

            // 读取绑定类型
            int checkedId = binding.chipGroupBindings.getCheckedChipId();
            if (checkedId == R.id.chip_bind_water)  selectedBinding = "WATER_MASTER";
            else if (checkedId == R.id.chip_bind_fat)   selectedBinding = "FAT_LOSS";
            else if (checkedId == R.id.chip_bind_sport) selectedBinding = "MUSCLE_GAIN";
            else if (checkedId == R.id.chip_bind_sleep) selectedBinding = "EARLY_SLEEP";
            else if (checkedId == R.id.chip_bind_step)  selectedBinding = "STEP";
            else selectedBinding = "NONE";

            int category = 3;
            if ("WATER_MASTER".equals(selectedBinding) || "FAT_LOSS".equals(selectedBinding)) category = 0;
            else if ("MUSCLE_GAIN".equals(selectedBinding) || "STEP".equals(selectedBinding)) category = 1;
            else if ("EARLY_SLEEP".equals(selectedBinding)) category = 2;

            ChallengeManager.Challenge newChallenge = new ChallengeManager.Challenge(
                    null, name, "21天" + name + " · " + desc,
                    selectedEmoji, category, selectedFailsLimit, selectedBinding);

            Context context = getContext();
            if (context == null) return;

            ChallengeManager.addCustomChallenge(context, newChallenge);
            ChallengeManager.start(context, newChallenge);
            Toast.makeText(context, "「" + name + "」挑战已开启！", Toast.LENGTH_SHORT).show();

            // 重置表单
            binding.etCreateName.setText("");
            binding.etCreateDesc.setText("");
            binding.seekCreateFails.setProgress(3);
            binding.chipBindNone.setChecked(true);
            selectedEmoji = "🔥";

            switchView(R.id.layout_active);
            renderActivePanelAsync();
        });

        binding.btnCreateCancel.setOnClickListener(v -> {
            switchView(R.id.layout_picker);
            renderPickerPanel();
        });
    }

    private void setupEmojiSelector() {
        if (!isAdded() || binding == null) return;
        binding.gridEmojiSelector.removeAllViews();
        Context context = getContext();
        if (context == null) return;

        String[] emojis = {"🔥", "💪", "🌙", "💧", "🏃", "🥗", "🧘", "📚",
                "☕", "🍎", "⏰", "💊", "🥦", "🚲", "💤", "🥤",
                "🚶", "🥛", "✍️", "🍵", "🍉", "☀️", "🛌", "🎯"};
        int size = dp(44);
        int margin = dp(5);

        for (String emoji : emojis) {
            MaterialCardView card = new MaterialCardView(context);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(lp);
            card.setRadius(dp(22));
            card.setCardElevation(0);
            card.setStrokeWidth(dp(1.5f));

            boolean isSelected = emoji.equals(selectedEmoji);
            card.setCardBackgroundColor(isSelected ? Color.parseColor("#E8F5E9") : Color.parseColor("#F5F5F5"));
            card.setStrokeColor(isSelected ? Color.parseColor("#2E7D32") : Color.parseColor("#E0E0E0"));

            TextView tv = new TextView(context);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setText(emoji);
            tv.setTextSize(20);
            card.addView(tv);

            card.setOnClickListener(v2 -> {
                selectedEmoji = emoji;
                setupEmojiSelector();
            });
            binding.gridEmojiSelector.addView(card);
        }
    }

    // ─────────────────────── 工具函数 ───────────────────────

    private int dp(float value) {
        if (getContext() == null) return (int) value;
        return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getParentFragment() instanceof OnChallengeChangedListener) {
            ((OnChallengeChangedListener) getParentFragment()).onChallengeChanged();
        } else if (getActivity() instanceof OnChallengeChangedListener) {
            ((OnChallengeChangedListener) getActivity()).onChallengeChanged();
        }
    }

    public interface OnChallengeChangedListener {
        void onChallengeChanged();
    }
}
