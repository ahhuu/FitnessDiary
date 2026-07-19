package com.cz.fitnessdiary.ui.bottomSheet;

import android.app.TimePickerDialog;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentChallengeBottomSheetBinding;
import com.cz.fitnessdiary.databinding.ItemChallengeCardBinding;
import com.cz.fitnessdiary.utils.ChallengeManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.ChallengeEntity;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ChallengeBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentChallengeBottomSheetBinding binding;
    private String selectedEmoji = "🔥";
    private int selectedFailsLimit = 3;
    private int selectedTotalDays = 21;
    private int selectedTargetDays = 21;
    private int selectedFreezeTickets = 2;
    private int reminderHour = -1;
    private int reminderMinute = -1;
    private String selectedBinding = "NONE";
    private int currentCategoryFilter = 0;
    private String editingCustomChallengeId = null;
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

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ChallengeManager.checkTodaySync(context);
            } catch (Exception ignored) {}

            List<ChallengeEntity> actives = ChallengeManager.getActiveChallengesSync(context);

            mainHandler.post(() -> {
                if (!isAdded() || binding == null) return;

                if (actives != null && !actives.isEmpty()) {
                    binding.layoutActive.setVisibility(View.VISIBLE);
                    binding.layoutPicker.setVisibility(View.VISIBLE);
                    binding.layoutCreate.setVisibility(View.GONE);
                    renderActivePanelsAsync(actives);
                    renderPickerPanelAsync();
                } else {
                    switchView(R.id.layout_picker);
                    renderPickerPanelAsync();
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

    private void renderActivePanelsAsync(List<ChallengeEntity> actives) {
        Context context = getContext();
        if (context == null || binding == null) return;

        binding.layoutActive.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Optional Title for multiple challenges
        if (actives.size() > 1) {
            TextView titleView = new TextView(context);
            titleView.setText("当前活跃挑战 (" + actives.size() + ")");
            titleView.setTextSize(16);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setPadding(dp(16), dp(16), dp(16), dp(8));
            binding.layoutActive.addView(titleView);
        }

        for (ChallengeEntity active : actives) {
            com.cz.fitnessdiary.databinding.ItemActiveChallengeBinding itemBinding =
                com.cz.fitnessdiary.databinding.ItemActiveChallengeBinding.inflate(inflater, binding.layoutActive, false);
            bindActiveChallenge(itemBinding, active, context);
            binding.layoutActive.addView(itemBinding.getRoot());
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void bindActiveChallenge(com.cz.fitnessdiary.databinding.ItemActiveChallengeBinding itemBinding, ChallengeEntity active, Context context) {
        String name = active.name;
        String desc = active.desc;
        String emoji = active.emoji;
        int maxFails = active.maxFails;
        int progressDays = ChallengeManager.getProgressDays(active);
        int failDays = active.failsCount;
        String bindCard = active.bindCard;

        itemBinding.tvActiveTitle.setText(name);
        itemBinding.tvActiveDesc.setText(desc);
        itemBinding.tvActiveEmoji.setText(emoji);

        int emojiBgColor = 0xFFFFF3E0;
        if ("FAT_LOSS".equals(bindCard))      emojiBgColor = 0xFFFFEBEE;
        else if ("MUSCLE_GAIN".equals(bindCard)) emojiBgColor = 0xFFE3F2FD;
        else if ("EARLY_SLEEP".equals(bindCard)) emojiBgColor = 0xFFEDE7F6;
        else if ("WATER_MASTER".equals(bindCard)) emojiBgColor = 0xFFE0F7FA;
        else if ("STEP".equals(bindCard))     emojiBgColor = 0xFFE8F5E9;
        else if ("WEIGHT".equals(bindCard))   emojiBgColor = 0xFFF3E5F5;
        else if ("MUSCLE_DIET".equals(bindCard)) emojiBgColor = 0xFFFFE0B2;
        else if ("MEDICATION".equals(bindCard)) emojiBgColor = 0xFFE0F2F1;
        itemBinding.cardActiveEmojiBg.setCardBackgroundColor(emojiBgColor);

        itemBinding.tvActiveProgressTxt.setText("第 " + Math.min(progressDays, active.totalDays) + " / " + active.totalDays + " 天");
        itemBinding.tvActiveFailSummary.setText("失败 " + failDays + " / " + maxFails);
        itemBinding.progressActiveChallenge.setMax(active.totalDays);
        itemBinding.progressActiveChallenge.setProgress(Math.min(progressDays, active.totalDays));

        // Initialize grid
        setupTrackingGrid(new int[active.totalDays], itemBinding.gridActiveTracking, context);

        // Bind logic
        if ("NONE".equals(bindCard)) {
            itemBinding.tvActiveAutoHint.setVisibility(View.GONE);
            itemBinding.btnActiveCheckin.setVisibility(View.VISIBLE);
            itemBinding.btnActiveCheckin.setEnabled(false);
            itemBinding.btnActiveCheckin.setText("加载中…");
        } else {
            itemBinding.btnActiveCheckin.setVisibility(View.GONE);
            itemBinding.tvActiveAutoHint.setVisibility(View.VISIBLE);
            itemBinding.tvActiveAutoHint.setText("💡 已绑定数据卡片，完成当日目标后自动计入");
        }

        itemBinding.btnActiveAbandon.setOnClickListener(v -> showAbandonConfirm(active.id));

        Executors.newSingleThreadExecutor().execute(() -> {
            int[] tracking = ChallengeManager.getDayTrackingStatusSync(context, active);
            boolean checkedToday = ChallengeManager.isCheckedTodaySync(context, active);
            int streak = ChallengeManager.getStreak(context, active);

            mainHandler.post(() -> {
                if (!isAdded() || binding == null) return;
                setupTrackingGrid(tracking, itemBinding.gridActiveTracking, context);

                // Show streak if > 0
                if (streak > 0) {
                    itemBinding.tvActiveStreak.setVisibility(View.VISIBLE);
                    itemBinding.tvActiveStreak.setText("🔥 连击 " + streak + " 天");
                } else {
                    itemBinding.tvActiveStreak.setVisibility(View.GONE);
                }

                // Freeze ticket logic
                if (active.freezeTickets > 0 && !checkedToday && progressDays <= active.totalDays) {
                    itemBinding.btnActiveFreeze.setVisibility(View.VISIBLE);
                    itemBinding.btnActiveFreeze.setText("❄️ 请假 (" + active.freezeTickets + ")");
                    itemBinding.btnActiveFreeze.setOnClickListener(v -> {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                .setTitle("使用请假条")
                                .setMessage("确定要使用请假条吗？当天将免于失败判定。剩余：" + active.freezeTickets + "张")
                                .setPositiveButton("使用", (d, w) -> {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        ChallengeManager.useFreezeTicketTodaySync(context, active.id);
                                        mainHandler.post(() -> refreshViewStateAsync());
                                    });
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    });
                } else {
                    itemBinding.btnActiveFreeze.setVisibility(View.GONE);
                }

                if ("NONE".equals(bindCard)) {
                    if (checkedToday) {
                        itemBinding.btnActiveCheckin.setEnabled(false);
                        itemBinding.btnActiveCheckin.setText("✅  今日已打卡");
                    } else {
                        itemBinding.btnActiveCheckin.setEnabled(true);
                        itemBinding.btnActiveCheckin.setText("今日打卡");
                        itemBinding.btnActiveCheckin.setOnClickListener(v -> {
                            binding.lottieConfetti.setVisibility(View.VISIBLE);
                            binding.lottieConfetti.playAnimation();

                            Executors.newSingleThreadExecutor().execute(() -> {
                                ChallengeManager.checkInTodaySync(context, active.id);
                                mainHandler.postDelayed(() -> {
                                    android.widget.Toast.makeText(context, "🎉 打卡成功，继续保持！", android.widget.Toast.LENGTH_SHORT).show();
                                    binding.lottieConfetti.setVisibility(View.GONE);
                                    refreshViewStateAsync();
                                }, 1500);
                            });
                        });
                    }
                }
            });
        });
    }

    private void setupTrackingGrid(int[] tracking, RecyclerView gridActiveTracking, Context context) {
        if (!isAdded() || gridActiveTracking == null) return;

        gridActiveTracking.setLayoutManager(new GridLayoutManager(context, 7));
        gridActiveTracking.setAdapter(new ChallengeGridAdapter(context, tracking));
    }

    private static class ChallengeGridAdapter extends RecyclerView.Adapter<ChallengeGridAdapter.ViewHolder> {
        private final Context context;
        private final int[] tracking;
        private final int size;
        private final int margin;

        public ChallengeGridAdapter(Context context, int[] tracking) {
            this.context = context;
            this.tracking = tracking;
            this.size = (int) (34 * context.getResources().getDisplayMetrics().density + 0.5f);
            this.margin = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(context);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(lp);
            card.setRadius(size / 2f);
            card.setCardElevation(0);
            card.setStrokeWidth((int) (2 * context.getResources().getDisplayMetrics().density + 0.5f));

            TextView tv = new TextView(context);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTypeface(null, Typeface.BOLD);

            card.addView(tv);
            return new ViewHolder(card, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Snake pattern logic if needed (Visual reordering)
            // But GridLayoutManager handles standard L-to-R ordering.
            // If we want snake pattern visually, we map position -> index
            int row = position / 7;
            int col = position % 7;
            int dataIndex;
            if (row % 2 != 0) {
                dataIndex = row * 7 + (6 - col); // Reverse for odd rows
            } else {
                dataIndex = position;
            }
            if (dataIndex >= tracking.length) {
                holder.card.setVisibility(View.INVISIBLE);
                return;
            }

            holder.card.setVisibility(View.VISIBLE);
            int status = tracking[dataIndex];
            if (status == 1) {
                holder.card.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.card.setStrokeColor(Color.parseColor("#66BB6A"));
                holder.tv.setText("✔");
                holder.tv.setTextColor(Color.parseColor("#2E7D32"));
                holder.tv.setTextSize(12);
            } else if (status == 2) {
                holder.card.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                holder.card.setStrokeColor(Color.parseColor("#EF9A9A"));
                holder.tv.setText("✕");
                holder.tv.setTextColor(Color.parseColor("#C62828"));
                holder.tv.setTextSize(12);
            } else if (status == 3) {
                holder.card.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.card.setStrokeColor(Color.parseColor("#90CAF9"));
                holder.tv.setText("❄️");
                holder.tv.setTextSize(10);
            } else {
                holder.card.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                holder.card.setStrokeColor(Color.parseColor("#E0E0E0"));
                holder.tv.setText(String.valueOf(dataIndex + 1));
                holder.tv.setTextColor(Color.parseColor("#BDBDBD"));
                holder.tv.setTextSize(10);
            }
        }

        @Override
        public int getItemCount() {
            // For snake pattern to look correct, we need full rows
            return (int) Math.ceil(tracking.length / 7.0) * 7;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView tv;
            ViewHolder(MaterialCardView card, TextView tv) {
                super(card);
                this.card = card;
                this.tv = tv;
            }
        }
    }

    private void showAbandonConfirm(int instanceId) {
        Context context = getContext();
        if (context == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("确认放弃挑战")
                .setMessage("放弃后所有进度将清零，确定要放弃吗？")
                .setPositiveButton("放弃", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        ChallengeManager.resetActiveSync(context, instanceId);
                        mainHandler.post(() -> {
                            android.widget.Toast.makeText(context, "已放弃当前挑战", android.widget.Toast.LENGTH_SHORT).show();
                            refreshViewStateAsync();
                        });
                    });
                })
                .setNegativeButton("继续坚持", null)
                .show();
    }

    // ─────── 挑战完成/失败结果弹窗（含 COMPLETED 庆祝）───────

    private void showChallengeResultDialog(ChallengeEntity active) {
        Context context = getContext();
        if (context == null || active == null) return;

        String name = active.name;
        String status = active.status;

        if ("COMPLETED".equals(status)) {
            View celebView = LayoutInflater.from(context).inflate(R.layout.dialog_challenge_celebrate, null, false);
            TextView tvCelebEmoji = celebView.findViewById(R.id.tv_celeb_emoji);
            TextView tvCelebTitle = celebView.findViewById(R.id.tv_celeb_title);
            TextView tvCelebSubtitle = celebView.findViewById(R.id.tv_celeb_subtitle);

            String emoji = active.emoji;
            if (tvCelebEmoji != null) tvCelebEmoji.setText(emoji == null || emoji.isEmpty() ? "🏆" : emoji);
            if (tvCelebTitle != null) tvCelebTitle.setText("挑战完成！");
            if (tvCelebSubtitle != null) tvCelebSubtitle.setText("你已坚持完成了 " + active.totalDays + " 天的「" + name + "」！这是真正的坚持，恭喜你！");

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
                        triggerChallengeCompletedAchievement(context);
                        renderPickerPanelAsync();
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } else {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("😔 挑战失败")
                    .setMessage("「" + name + "」挑战中失败次数已超限，本次挑战结束。别气馁，重新开始！")
                    .setPositiveButton("重新挑战", (d, w) -> {
                        renderPickerPanelAsync();
                    })
                    .setNegativeButton("关闭", null)
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
        binding.chipCatAll.setOnClickListener(v -> { currentCategoryFilter = 0; renderPickerPanelAsync(); });
        binding.chipCatDiet.setOnClickListener(v -> { currentCategoryFilter = 1; renderPickerPanelAsync(); });
        binding.chipCatSport.setOnClickListener(v -> { currentCategoryFilter = 2; renderPickerPanelAsync(); });
        binding.chipCatSleep.setOnClickListener(v -> { currentCategoryFilter = 3; renderPickerPanelAsync(); });
        binding.chipCatMind.setOnClickListener(v -> { currentCategoryFilter = 4; renderPickerPanelAsync(); });
        binding.chipCatCustom.setOnClickListener(v -> { currentCategoryFilter = 5; renderPickerPanelAsync(); });
    }

    private void renderPickerPanelAsync() {
        Context context = getContext();
        if (context == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ChallengeManager.Challenge> customs = ChallengeManager.getCustomChallengesSync(context);
            List<ChallengeEntity> completedHistory = ChallengeManager.getCompletedChallengesSync(context);

            mainHandler.post(() -> renderPickerPanelMain(customs, completedHistory));
        });
    }

    private void renderPickerPanelMain(List<ChallengeManager.Challenge> customs, List<ChallengeEntity> completedHistory) {
        if (!isAdded() || binding == null) return;
        binding.layoutPickerList.removeAllViews();
        Context context = getContext();
        if (context == null) return;

        List<ChallengeManager.Challenge> presets = ChallengeManager.getPresetChallenges();
        List<ChallengeManager.Challenge> filteredList = new ArrayList<>();

        if (currentCategoryFilter == 0) {
            filteredList.addAll(presets);
            filteredList.addAll(customs);
        } else if (currentCategoryFilter == 5) {
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
        addBinding.tvItemDesc.setText("根据个人习惯，量身定制你的挑战");
        addBinding.tvItemEmoji.setText("✏️");
        addBinding.cardItemEmojiBg.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
        addBinding.btnDeleteCustomChallenge.setVisibility(View.GONE);
        addBinding.cardChallengeItem.setOnClickListener(v -> {
            switchView(R.id.layout_create);
            setupEmojiSelector();
        });
        binding.layoutPickerList.addView(addBinding.getRoot());

        // Render Honor Wall if there is history
        if (completedHistory != null && !completedHistory.isEmpty()) {
            TextView title = new TextView(context);
            title.setText("🏆 我的荣誉墙");
            title.setTextSize(16);
            title.setTypeface(null, Typeface.BOLD);
            title.setPadding(dp(4), dp(16), 0, dp(8));
            binding.layoutPickerList.addView(title);

            for (ChallengeEntity c : completedHistory) {
                ItemChallengeCardBinding cardBinding = ItemChallengeCardBinding.inflate(inflater, binding.layoutPickerList, false);
                cardBinding.tvItemTitle.setText(c.name + " 徽章");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault());
                cardBinding.tvItemDesc.setText("于 " + sdf.format(new java.util.Date(c.lastCheckDate)) + " 挑战成功！");
                cardBinding.tvItemEmoji.setText(c.emoji);
                cardBinding.cardChallengeItem.setCardBackgroundColor(Color.parseColor("#FFFDE7")); // Gold tint
                cardBinding.cardItemEmojiBg.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
                cardBinding.btnDeleteCustomChallenge.setVisibility(View.GONE);
                // Non clickable
                cardBinding.cardChallengeItem.setClickable(false);
                cardBinding.cardChallengeItem.setFocusable(false);
                binding.layoutPickerList.addView(cardBinding.getRoot());
            }
        }
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
            case 1:  bgColor = Color.parseColor("#F0F8FF"); break; // 运动 蓝白
            case 2:  bgColor = Color.parseColor("#F5F0FF"); break; // 作息 紫白
            case 3:  bgColor = Color.parseColor("#F0FFF4"); break; // 身心 绿白
            case 4:  bgColor = Color.parseColor("#FFF3E0"); break; // 我的 橙白
            default: bgColor = Color.parseColor("#FAFAFA"); break;
        }
        cardBinding.cardChallengeItem.setCardBackgroundColor(bgColor);
        cardBinding.cardItemEmojiBg.setCardBackgroundColor(Color.parseColor("#FFFFFF"));

        // 点击：开启挑战
        cardBinding.cardChallengeItem.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("开启 " + c.totalDays + " 天挑战")
                    .setMessage("准备好接受「" + c.name + "」挑战了吗？\n\n" + c.desc)
                    .setPositiveButton("立刻开始 🚀", (d, w) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            ChallengeManager.startSync(context, c);
                            mainHandler.post(() -> {
                                Toast.makeText(context, "「" + c.name + "」挑战已开启！", Toast.LENGTH_SHORT).show();
                                refreshViewStateAsync();
                            });
                        });
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
                            Executors.newSingleThreadExecutor().execute(() -> {
                                ChallengeManager.deleteCustomChallengeSync(context, c.id);
                                mainHandler.post(() -> renderPickerPanelAsync());
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        } else {
            cardBinding.btnDeleteCustomChallenge.setVisibility(View.GONE);
        }
        // 编辑挑战功能
        cardBinding.btnEditChallenge.setVisibility(View.VISIBLE);
        cardBinding.btnEditChallenge.setOnClickListener(v -> {
            // Preset challenge gets "Save As" (new id), custom challenge gets updated in place
            if (c.id != null && c.id.startsWith("CUSTOM_")) {
                editingCustomChallengeId = c.id;
                binding.btnCreateSubmit.setText("更新自定义挑战");
            } else {
                editingCustomChallengeId = null;
                binding.btnCreateSubmit.setText("保存为自定义");
            }

            // Populate form
            binding.etCreateName.setText(c.name);
            binding.etCreateDesc.setText(c.desc);
            selectedEmoji = c.emoji;
            setupEmojiSelector();

            selectedTotalDays = c.totalDays;
            binding.seekCreateDuration.setProgress(selectedTotalDays - 7);
            binding.tvCreateDurationIndicator.setText(selectedTotalDays + " 天");

            binding.seekCreateTarget.setMax(selectedTotalDays);
            selectedTargetDays = c.targetDays;
            binding.seekCreateTarget.setProgress(selectedTargetDays);
            binding.tvCreateTargetIndicator.setText(selectedTargetDays + " 次");

            selectedFailsLimit = c.maxFails;
            selectedFreezeTickets = c.freezeTickets;
            binding.seekCreateFreeze.setProgress(selectedFreezeTickets);
            binding.tvCreateFreezeIndicator.setText(selectedFreezeTickets + " 张");

            binding.seekCreateMaxFails.setProgress(selectedFailsLimit);
            binding.tvCreateMaxFailsIndicator.setText(selectedFailsLimit + " 次");

            // Category logic (c.category 0-4)
            int categoryId;
            switch (c.category) {
                case 0: categoryId = R.id.chip_cat_diet_create; break;
                case 1: categoryId = R.id.chip_cat_sport_create; break;
                case 2: categoryId = R.id.chip_cat_sleep_create; break;
                case 3: categoryId = R.id.chip_cat_mind_create; break;
                case 4: categoryId = R.id.chip_cat_my_create; break;
                default: categoryId = R.id.chip_cat_diet_create;
            }
            binding.chipGroupCategory.check(categoryId);

            // Bind Card logic
            int bindId;
            switch (c.bindCard) {
                case "WATER_MASTER": bindId = R.id.chip_bind_water; break;
                case "FAT_LOSS": bindId = R.id.chip_bind_fat; break;
                case "MUSCLE_GAIN": bindId = R.id.chip_bind_sport; break;
                case "EARLY_SLEEP": bindId = R.id.chip_bind_sleep; break;
                case "STEP": bindId = R.id.chip_bind_step; break;
                case "MOOD": bindId = R.id.chip_bind_mood; break;
                case "WEIGHT": bindId = R.id.chip_bind_weight; break;
                case "MUSCLE_DIET": bindId = R.id.chip_bind_muscle_diet; break;
                case "MEDICATION": bindId = R.id.chip_bind_medication; break;
                default: bindId = R.id.chip_bind_none;
            }
            binding.chipGroupBindings.check(bindId);

            switchView(R.id.layout_create);
        });
    }

    // ─────────────────────── 面板三：新建自定义挑战 ───────────────────────

    private void setupCreateForm() {
        // Duration slider
        binding.seekCreateDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                selectedTotalDays = progress + 7;
                binding.tvCreateDurationIndicator.setText(selectedTotalDays + " 天");
                binding.seekCreateTarget.setMax(selectedTotalDays);
                if (selectedTargetDays > selectedTotalDays) {
                    selectedTargetDays = selectedTotalDays;
                    binding.seekCreateTarget.setProgress(selectedTargetDays);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Target slider
        binding.seekCreateTarget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                selectedTargetDays = progress;
                binding.tvCreateTargetIndicator.setText(selectedTargetDays + " 次");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Freeze slider
        binding.seekCreateFreeze.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                selectedFreezeTickets = progress;
                binding.tvCreateFreezeIndicator.setText(progress + " 张");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Max Fails slider
        binding.seekCreateMaxFails.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                selectedFailsLimit = progress;
                binding.tvCreateMaxFailsIndicator.setText(progress + " 次");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Reminder
        binding.tvCreateReminderTime.setOnClickListener(v -> {
            int initHour = reminderHour >= 0 ? reminderHour : 20;
            int initMinute = reminderMinute >= 0 ? reminderMinute : 0;
            new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
                reminderHour = hourOfDay;
                reminderMinute = minute;
                binding.tvCreateReminderTime.setText(String.format("%02d:%02d", hourOfDay, minute));
                binding.switchCreateReminder.setChecked(true);
            }, initHour, initMinute, true).show();
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
            else if (checkedId == R.id.chip_bind_mood)  selectedBinding = "MOOD";
            else if (checkedId == R.id.chip_bind_weight) selectedBinding = "WEIGHT";
            else if (checkedId == R.id.chip_bind_muscle_diet) selectedBinding = "MUSCLE_DIET";
            else if (checkedId == R.id.chip_bind_medication) selectedBinding = "MEDICATION";
            else selectedBinding = "NONE";

            // Category logic based on chip group
            int categoryId = binding.chipGroupCategory.getCheckedChipId();
            int category = 4;
            if (categoryId == R.id.chip_cat_diet_create) category = 0;
            else if (categoryId == R.id.chip_cat_sport_create) category = 1;
            else if (categoryId == R.id.chip_cat_sleep_create) category = 2;
            else if (categoryId == R.id.chip_cat_mind_create) category = 3;

            ChallengeManager.Challenge c = new ChallengeManager.Challenge();
            if (editingCustomChallengeId != null) {
                c.id = editingCustomChallengeId;
            }
            c.name = name;
            c.desc = desc;
            c.emoji = selectedEmoji;
            c.category = category;
            c.maxFails = selectedFailsLimit;
            c.bindCard = selectedBinding;
            c.totalDays = selectedTotalDays;
            c.targetDays = selectedTargetDays;
            c.freezeTickets = selectedFreezeTickets;

            Context context = getContext();
            if (context == null) return;

            boolean setReminder = binding.switchCreateReminder.isChecked() && reminderHour >= 0;

            Executors.newSingleThreadExecutor().execute(() -> {
                if (editingCustomChallengeId != null) {
                    ChallengeManager.updateCustomChallengeSync(context, c);
                } else {
                    ChallengeManager.addCustomChallengeSync(context, c);
                    ChallengeManager.startSync(context, c);
                }

                if (setReminder) {
                    ReminderSchedule schedule = new ReminderSchedule(
                            "CHALLENGE", 0, reminderHour, reminderMinute, "1,2,3,4,5,6,7", true,
                            "打卡提醒: " + name, "到了「" + name + "」的打卡时间啦，快去记录吧！", false, 0);
                    AppDatabase.getInstance(context).reminderScheduleDao().insert(schedule);
                }

                mainHandler.post(() -> {
                    Toast.makeText(context, "「" + name + "」挑战已开启！", Toast.LENGTH_SHORT).show();

                    binding.etCreateName.setText("");
                    binding.etCreateDesc.setText("");
                    binding.seekCreateDuration.setProgress(14);
                    binding.seekCreateTarget.setProgress(21);
                    binding.seekCreateFreeze.setProgress(2);
                    binding.seekCreateMaxFails.setProgress(3);
                    binding.switchCreateReminder.setChecked(false);
                    binding.tvCreateReminderTime.setText("未开启");
                    reminderHour = -1;
                    reminderMinute = -1;
                    binding.chipBindNone.setChecked(true);
                    binding.chipCatMyCreate.setChecked(true);
                    selectedEmoji = "🔥";
                    setupEmojiSelector();

                    refreshViewStateAsync();
                });
            });
        });

        binding.btnCreateCancel.setOnClickListener(v -> {
            switchView(R.id.layout_picker);
            renderPickerPanelAsync();
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
