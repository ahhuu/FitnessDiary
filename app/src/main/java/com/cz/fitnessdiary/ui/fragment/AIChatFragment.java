package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ChatSessionEntity;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentAiChatBinding;
import com.cz.fitnessdiary.model.ChatMessage;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.ui.adapter.AIChatAdapter;
import com.cz.fitnessdiary.ui.adapter.ChatSessionAdapter;
import com.cz.fitnessdiary.viewmodel.AIChatViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.List;

/**
 * AI 健身教练聊天页面 - 支持多轮对话历史与消息管理
 */
public class AIChatFragment extends Fragment {

    private FragmentAiChatBinding binding;
    private AIChatViewModel viewModel;
    private AIChatAdapter adapter;
    private ChatSessionAdapter sessionAdapter;
    private FoodLibraryRepository foodRepository;
    private TrainingPlanRepository trainingRepository;

    private final androidx.activity.result.ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && viewModel != null) {
                    viewModel.setAttachedFileUri(uri.toString());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAiChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AIChatViewModel.class);
        foodRepository = new FoodLibraryRepository(requireContext());
        trainingRepository = new TrainingPlanRepository(requireActivity().getApplication());

        setupRecyclerView();
        setupHistorySidebar();
        setupListeners();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new AIChatAdapter();
        adapter.setOnActionClickListener(this::handleSmartAction);
        adapter.setOnMessageLongClickListener(this::handleMessageLongClick);
        adapter.setOnSelectionChangeListener(count -> {
            binding.tvSelectionCount.setText("已选择 " + count + " 项");

            // 动态更新全选按钮文本
            int selectableCount = 0;
            for (ChatMessage msg : adapter.getMessages()) {
                if (msg.getId() > 0)
                    selectableCount++;
            }
            binding.btnSelectAll.setText((count > 0 && count == selectableCount) ? "取消全选" : "全选");
        });

        binding.recyclerViewMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewMessages.setAdapter(adapter);
    }

    private void setupHistorySidebar() {
        sessionAdapter = new ChatSessionAdapter(
                session -> {
                    viewModel.selectSession(session.getId());
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                },
                this::handleSessionLongClick);
        binding.recyclerViewSessions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewSessions.setAdapter(sessionAdapter);
    }

    private void handleSessionLongClick(ChatSessionEntity session) {
        String[] options = { "重命名", "移动至文件夹", "删除会话" };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("管理会话")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameSessionDialog(session);
                    } else if (which == 1) {
                        showMoveToFolderDialog(session);
                    } else {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("删除确认")
                                .setMessage("确定要删除该会话及其所有消息吗？")
                                .setPositiveButton("删除", (d, w) -> viewModel.deleteSession(session))
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    private void showMoveToFolderDialog(ChatSessionEntity session) {
        final EditText et = new EditText(requireContext());
        et.setHint("输入文件夹名称 (如：计划、饮食)");
        et.setText(session.getFolderName());
        et.setPadding(48, 48, 48, 48);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("移动至文件夹")
                .setView(et)
                .setPositiveButton("保存", (dialog, which) -> {
                    String folderName = et.getText().toString().trim();
                    viewModel.updateSessionFolder(session.getId(), folderName);
                })
                .setNegativeButton("清空文件夹", (dialog, which) -> {
                    viewModel.updateSessionFolder(session.getId(), null);
                })
                .show();
    }

    private void showRenameSessionDialog(ChatSessionEntity session) {
        final EditText et = new EditText(requireContext());
        et.setText(session.getTitle());
        et.setPadding(48, 48, 48, 48);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("重命名会话")
                .setView(et)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newTitle = et.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        viewModel.renameSession(session.getId(), newTitle);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 处理消息长按事件：用户消息显示编辑/删除，AI 消息（暂时）显示食物解析
     */
    private void handleMessageLongClick(ChatMessage message) {
        String[] options = { "编辑", "删除", "多选" };
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditDialog(message);
                    } else if (which == 1) {
                        viewModel.deleteMessage(message);
                    } else {
                        enterSelectionMode(message);
                    }
                })
                .show();
    }

    private void enterSelectionMode(ChatMessage firstMessage) {
        adapter.setSelectionMode(true);
        if (firstMessage.getId() > 0) {
            adapter.toggleSelection(firstMessage.getId());
        }
        binding.toolbar.setVisibility(View.GONE);
        binding.selectionToolbar.setVisibility(View.VISIBLE);
        // 隐藏输入法和快捷键
        binding.inputContainer.setVisibility(View.GONE);
        binding.shortcutScroll.setVisibility(View.GONE);
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        binding.toolbar.setVisibility(View.VISIBLE);
        binding.selectionToolbar.setVisibility(View.GONE);
        binding.inputContainer.setVisibility(View.VISIBLE);
        binding.shortcutScroll.setVisibility(View.VISIBLE);
    }

    private void showEditDialog(ChatMessage message) {
        EditText editText = new EditText(requireContext());
        editText.setText(message.getContent());
        editText.setPadding(40, 40, 40, 40);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑消息")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newContent = editText.getText().toString().trim();
                    if (!newContent.isEmpty()) {
                        viewModel.editMessage(message, newContent);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupListeners() {
        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                sendChatMessage(text);
                binding.etInput.setText("");
            }
        });

        binding.btnAttach.setOnClickListener(v -> imagePickerLauncher.launch("*/*"));
        binding.btnRemoveAttachment.setOnClickListener(v -> viewModel.setAttachedFileUri(null));

        binding.btnDeepThinking.setOnClickListener(v -> {
            boolean current = Boolean.TRUE.equals(viewModel.getIsDeepThinking().getValue());
            viewModel.setDeepThinking(!current);
        });

        binding.btnSearch.setOnClickListener(v -> {
            boolean current = Boolean.TRUE.equals(viewModel.getIsSearchEnabled().getValue());
            viewModel.setSearchEnabled(!current);
        });

        // 多选管理栏监听
        binding.btnCancelSelection.setOnClickListener(v -> exitSelectionMode());
        binding.btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        binding.btnDeleteSelection.setOnClickListener(v -> {
            List<Long> selectedIds = adapter.getSelectedMessageIds();
            if (selectedIds.isEmpty()) {
                Toast.makeText(getContext(), "未选择任何消息", Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("批量删除确认")
                    .setMessage("确定要删除选中的 " + selectedIds.size() + " 条消息吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        viewModel.deleteMessages(selectedIds);
                        exitSelectionMode();
                        Toast.makeText(getContext(), "已删除 " + selectedIds.size() + " 条消息", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 快捷键监听
        binding.chipPlan.setOnClickListener(v -> sendChatMessage("请帮我制定一份训练计划"));
        binding.chipDiet.setOnClickListener(v -> sendChatMessage("帮我分析一下这顿饭的热量"));
        binding.chipEvaluate.setOnClickListener(v -> sendChatMessage("根据最近的打卡评估我的进度"));
        binding.chipAdvice.setOnClickListener(v -> sendChatMessage("给我一些今日运动建议"));

        binding.btnHistory.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));

        binding.btnNewChat.setOnClickListener(v -> {
            viewModel.createNewSession();
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        });

        binding.btnClearHistory.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认清空")
                    .setMessage("是否清空所有历史对话？此操作不可撤销。")
                    .setPositiveButton("清空", (dialog, which) -> {
                        viewModel.deleteAllSessions();
                        Toast.makeText(getContext(), "历史纪录已清空", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void sendChatMessage(String text) {
        if (text == null || text.trim().isEmpty())
            return;

        String uriStr = viewModel.getAttachedFileUri().getValue();
        if (uriStr != null) {
            try {
                android.net.Uri uri = android.net.Uri.parse(uriStr);
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                        requireContext().getContentResolver(), uri);
                viewModel.sendMessageWithAttachment(text, bitmap);
            } catch (Exception e) {
                viewModel.sendMessage(text);
            }
        } else {
            viewModel.sendMessage(text);
        }
        viewModel.setAttachedFileUri(null);
        binding.etInput.setText("");
    }

    private boolean isFirstLoad = true;

    private void observeViewModel() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            if (messages == null)
                return;

            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerViewMessages.getLayoutManager();
            boolean isAtBottom = false;
            if (layoutManager != null && adapter.getItemCount() > 0) {
                isAtBottom = layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 2;
            }

            adapter.setMessages(messages);

            // 滚动处理
            if (isFirstLoad) {
                binding.recyclerViewMessages.scrollToPosition(messages.size() - 1);
                isFirstLoad = false;
            } else if (isAtBottom || (messages.size() > 0 && messages.get(messages.size() - 1).isUser())) {
                binding.recyclerViewMessages.smoothScrollToPosition(messages.size() - 1);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> binding.btnSend.setEnabled(!loading));

        viewModel.getIsDeepThinking().observe(getViewLifecycleOwner(), thinking -> {
            int color = thinking ? getResources().getColor(R.color.fitnessdiary_primary)
                    : getResources().getColor(R.color.fitnessdiary_text_secondary);
            binding.btnDeepThinking.setTextColor(color);
            binding.btnDeepThinking.setIconTint(android.content.res.ColorStateList.valueOf(color));
        });

        viewModel.getIsSearchEnabled().observe(getViewLifecycleOwner(), search -> {
            int color = search ? getResources().getColor(R.color.fitnessdiary_primary)
                    : getResources().getColor(R.color.fitnessdiary_text_secondary);
            binding.btnSearch.setTextColor(color);
            binding.btnSearch.setIconTint(android.content.res.ColorStateList.valueOf(color));
        });

        viewModel.getAttachedFileUri().observe(getViewLifecycleOwner(), uri -> {
            binding.attachmentScroll.setVisibility(uri != null ? View.VISIBLE : View.GONE);
            if (uri != null) {
                com.bumptech.glide.Glide.with(this).load(uri).into(binding.ivAttachedImage);
            }
        });

        viewModel.getAllSessions().observe(getViewLifecycleOwner(), sessions -> {
            sessionAdapter.submitList(sessions);
        });

        viewModel.getCurrentSessionId().observe(getViewLifecycleOwner(), id -> {
            sessionAdapter.setCurrentSessionId(id);
            // 切换会话时标记为初次加载以直接定位到底部
            isFirstLoad = true;
        });

        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && user.getAvatarUri() != null) {
                adapter.setUserAvatarUri(user.getAvatarUri());
            }
        });
    }

    private void handleSmartAction(JSONObject actionJson) {
        // ... (保持原有的 handleSmartAction 逻辑，处理 FOOD 和 PLAN)
        String type = actionJson.optString("type");
        if ("FOOD".equals(type)) {
            String name = actionJson.optString("name");
            int calories = actionJson.optInt("calories");
            double protein = actionJson.optDouble("protein");
            double carbs = actionJson.optDouble("carbs");
            String unit = actionJson.optString("unit", "克");
            String category = actionJson.optString("category", "其他");

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("一键录入食物")
                    .setMessage(String.format("是否将“%s”添加至您的食物库？\n🔥 热量：%d\n🥩 蛋白质：%.1f\n🍞 碳水：%.1f\n📏 单位：%s\n📂 分类：%s",
                            name, calories, protein, carbs, unit, category))
                    .setPositiveButton("确定", (dialog, which) -> {
                        com.cz.fitnessdiary.database.entity.FoodLibrary food = new com.cz.fitnessdiary.database.entity.FoodLibrary(
                                name, calories, protein, carbs, unit, 100, category);
                        foodRepository.insert(food);
                        Toast.makeText(getContext(), "已添加 " + name, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else if ("PLAN".equals(type)) {
            String name = actionJson.optString("name");
            int sets = actionJson.optInt("sets");
            int reps = actionJson.optInt("reps");
            String desc = actionJson.optString("desc");
            String category = actionJson.optString("category", "自定义-其他");
            if (!category.startsWith("自定义-")) {
                category = "自定义-" + category;
            }

            final String finalCategory = category;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("添加训练计划")
                    .setMessage(String.format("是否将“%s”添加至您的计划？\n🔢 组数：%d\n🔁 次数：%d\n📂 分类：%s\n📝 描述：%s",
                            name, sets, reps, category, desc))
                    .setPositiveButton("确定", (dialog, which) -> {
                        com.cz.fitnessdiary.database.entity.TrainingPlan plan = new com.cz.fitnessdiary.database.entity.TrainingPlan(
                                name, desc, System.currentTimeMillis());
                        plan.setSets(sets);
                        plan.setReps(reps);
                        plan.setCategory(finalCategory);
                        trainingRepository.insert(plan);
                        Toast.makeText(getContext(), "已添加计划 " + name, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    private void handleAiMessageLongClick(String content) {
        com.cz.fitnessdiary.database.entity.FoodLibrary parsedFood = com.cz.fitnessdiary.service.FoodParser
                .parseFirstFood(content);
        if (parsedFood == null) {
            Toast.makeText(getContext(), "未能识别到食物信息", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("智能识别建议")
                .setMessage(String.format("解析到以下食物：\n名称：%s\n热量：%d 大卡\n蛋白质：%.1f g\n碳水：%.1f g\n\n是否添加至您的食物库？",
                        parsedFood.getName(), parsedFood.getCaloriesPer100g(),
                        parsedFood.getProteinPer100g(), parsedFood.getCarbsPer100g()))
                .setPositiveButton("一键入库", (dialog, which) -> {
                    foodRepository.insert(parsedFood);
                    Toast.makeText(getContext(), "已成功添加到食物库！", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
