package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentAiChatBinding;
import com.cz.fitnessdiary.model.ChatMessage;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.ui.adapter.AIChatAdapter;
import com.cz.fitnessdiary.ui.adapter.ChatSessionAdapter;
import com.cz.fitnessdiary.utils.FoodCategoryUtils;
import com.cz.fitnessdiary.viewmodel.AIChatViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private FoodRecordRepository foodRecordRepository;
    private TrainingPlanRepository trainingRepository;

    private android.net.Uri photoUri;

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> cameraLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
            success -> {
                if (success && photoUri != null && viewModel != null) {
                    viewModel.setAttachedFileUri(photoUri.toString());
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> mediaPickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && viewModel != null) {
                    viewModel.setAttachedFileUri(uri.toString());
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && viewModel != null) {
                    viewModel.setAttachedFileUri(uri.toString());
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> requestCameraPermissionLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    Toast.makeText(requireContext(), "📷 需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
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
        foodRecordRepository = new FoodRecordRepository(requireActivity().getApplication());
        trainingRepository = new TrainingPlanRepository(requireActivity().getApplication());

        setupRecyclerView();
        setupHistorySidebar();
        setupListeners();
        observeViewModel();
    }

    private long selectedRecordDate = System.currentTimeMillis();

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
        String[] options = { "重命名", "归档至文件夹", "删除会话" };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("管理会话")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameSessionDialog(session);
                    } else if (which == 1) {
                        showArchiveToFolderDialog(session);
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

    private void showArchiveToFolderDialog(ChatSessionEntity session) {
        List<String> folders = collectExistingFolders();
        if (folders.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("暂无文件夹")
                    .setMessage("当前没有可用文件夹，请先新建一个文件夹。")
                    .setPositiveButton("新建文件夹", (dialog, which) -> showCreateFolderDialog(session))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        List<String> options = new ArrayList<>(folders);
        options.add("➕ 新建文件夹");
        String[] items = options.toArray(new String[0]);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("归档至文件夹")
                .setItems(items, (dialog, which) -> {
                    if (which == items.length - 1) {
                        showCreateFolderDialog(session);
                        return;
                    }
                    String folder = items[which];
                    viewModel.updateSessionFolder(session.getId(), folder);
                    Toast.makeText(getContext(), "已归档到：" + folder, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCreateFolderDialog(ChatSessionEntity session) {
        final EditText et = new EditText(requireContext());
        et.setHint("输入新文件夹名称");
        et.setPadding(48, 48, 48, 48);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建文件夹")
                .setView(et)
                .setPositiveButton("保存", (dialog, which) -> {
                    String folderName = et.getText().toString().trim();
                    if (folderName.isEmpty()) {
                        Toast.makeText(getContext(), "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.updateSessionFolder(session.getId(), folderName);
                    Toast.makeText(getContext(), "已归档到：" + folderName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private List<String> collectExistingFolders() {
        LinkedHashSet<String> folderSet = new LinkedHashSet<>();
        for (ChatSessionEntity item : sessionAdapter.getCurrentList()) {
            if (item == null) {
                continue;
            }
            String folder = item.getFolderName();
            if (folder != null) {
                folder = folder.trim();
            }
            if (folder != null && !folder.isEmpty()) {
                folderSet.add(folder);
            }
        }
        return new ArrayList<>(folderSet);
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

    private void handleMessageLongClick(ChatMessage message) {
        if (message == null || message.getId() <= 0) {
            return;
        }

        if (message.isUser()) {
            showUserMessageActions(message);
        } else {
            showAiMessageActions(message);
        }
    }

    private void showUserMessageActions(ChatMessage message) {
        String[] options = { "复制", "编辑并重发", "删除", "多选" };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("用户消息")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        copyToClipboard("用户消息", message.getContent());
                    } else if (which == 1) {
                        showEditAndResendDialog(message);
                    } else if (which == 2) {
                        viewModel.deleteMessage(message);
                    } else {
                        enterSelectionMode(message);
                    }
                })
                .show();
    }

    private void showAiMessageActions(ChatMessage message) {
        String[] options = { "复制", "重新生成", "删除", "多选" };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("AI 消息")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        copyToClipboard("AI 消息", sanitizeAiContent(message.getContent()));
                    } else if (which == 1) {
                        regenerateFromMessage(message);
                    } else if (which == 2) {
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
        // 用 INVISIBLE 保留顶部占位，避免消息列表顶上去遮挡多选操作栏
        binding.toolbar.setVisibility(View.INVISIBLE);
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

    private void setupListeners() {
        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                sendChatMessage(text);
                binding.etInput.setText("");
            }
        });

        binding.btnAttach.setOnClickListener(v -> showAttachmentMenu());
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

    private void showAttachmentMenu() {
        android.view.ContextThemeWrapper wrapper = new android.view.ContextThemeWrapper(requireContext(),
                com.google.android.material.R.style.Widget_Material3_PopupMenu_ListPopupWindow);
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(wrapper, binding.btnAttach);

        popup.getMenu().add(0, 3, 0, "📷 拍照识别");
        popup.getMenu().add(0, 1, 1, "📄 上传文档");
        popup.getMenu().add(0, 2, 2, "🖼️ 上传图片");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 3) {
                launchCamera();
            } else if (id == 1) {
                filePickerLauncher.launch("*/*");
            } else {
                mediaPickerLauncher.launch("image/*");
            }
            return true;
        });
        popup.show();
    }

    private void launchCamera() {
        // 检查权限
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            return;
        }

        try {
            java.io.File storageDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs();
            }

            java.io.File imageFile = java.io.File.createTempFile(
                    "IMG_" + System.currentTimeMillis() + "_",
                    ".jpg",
                    storageDir);

            photoUri = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                    "com.cz.fitnessdiary.fileprovider",
                    imageFile);

            cameraLauncher.launch(photoUri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "相机启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendChatMessage(String text) {
        if (text == null || text.trim().isEmpty())
            return;

        String uriStr = viewModel.getAttachedFileUri().getValue();
        if (uriStr != null) {
            try {
                android.graphics.Bitmap bitmap = decodeScaledBitmap(android.net.Uri.parse(uriStr), 1280);
                viewModel.sendMessageWithAttachment(text, uriStr, bitmap);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "图片读取失败，已按文本发送", Toast.LENGTH_SHORT).show();
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
            boolean enabled = Boolean.TRUE.equals(thinking);
            binding.btnDeepThinking.setChecked(enabled);
            updateAiModeToggleStyle(binding.btnDeepThinking, enabled);
        });

        viewModel.getIsSearchEnabled().observe(getViewLifecycleOwner(), search -> {
            boolean enabled = Boolean.TRUE.equals(search);
            binding.btnSearch.setChecked(enabled);
            updateAiModeToggleStyle(binding.btnSearch, enabled);
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
            // 已删除全局头像显示，保持清洁
        });
    }

    private void updateAiModeToggleStyle(com.google.android.material.button.MaterialButton button, boolean enabled) {
        int primary = getResources().getColor(R.color.ai_primary);
        int secondary = getResources().getColor(R.color.fitnessdiary_text_secondary);
        int white = getResources().getColor(R.color.white);

        button.setTextColor(enabled ? white : secondary);
        button.setIconTint(android.content.res.ColorStateList.valueOf(enabled ? white : secondary));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(primary));
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(enabled ? primary : android.graphics.Color.TRANSPARENT));
    }

    private void handleSmartAction(JSONObject actionJson) {
        String type = actionJson.optString("type");
        if ("MULTI".equals(type)) {
            org.json.JSONArray actions = actionJson.optJSONArray("actions");
            if (actions == null || actions.length() == 0) {
                Toast.makeText(getContext(), "未识别到可执行操作", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject foodAction = null;
            JSONObject planAction = null;
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null)
                    continue;
                String actionType = action.optString("type");
                if ("FOOD".equals(actionType)) {
                    foodAction = action;
                } else if ("PLAN".equals(actionType)) {
                    planAction = action;
                }
            }
            JSONObject finalFoodAction = foodAction;
            JSONObject finalPlanAction = planAction;
            String[] options = { "全部执行", "仅记录饮食", "仅添加计划" };
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("检测到多个智能操作")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0 || which == 1) {
                            if (finalFoodAction != null) {
                                org.json.JSONArray items = finalFoodAction.optJSONArray("items");
                                if (items != null && items.length() > 0) {
                                    handleMultiFoodLogging(items);
                                }
                            }
                        }
                        if (which == 0 || which == 2) {
                            if (finalPlanAction != null) {
                                handlePlanAction(finalPlanAction);
                            }
                        }
                    })
                    .show();
        } else if ("FOOD".equals(type)) {
            org.json.JSONArray items = actionJson.optJSONArray("items");
            if (items == null || items.length() == 0)
                return;
            handleMultiFoodLogging(items);
        } else if ("PLAN".equals(type)) {
            handlePlanAction(actionJson);
        }
    }

    private void handlePlanAction(JSONObject actionJson) {
        String name = actionJson.optString("name");
        int sets = Math.max(1, actionJson.optInt("sets", 3));
        int reps = Math.max(1, actionJson.optInt("reps", 10));
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

    private void showEditAndResendDialog(ChatMessage message) {
        EditText editText = new EditText(requireContext());
        editText.setText(message.getContent());
        editText.setPadding(40, 40, 40, 40);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑并重发")
                .setView(editText)
                .setPositiveButton("发送", (dialog, which) -> {
                    String newContent = editText.getText().toString().trim();
                    if (!newContent.isEmpty()) {
                        sendChatMessage(newContent);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void regenerateFromMessage(ChatMessage aiMessage) {
        List<ChatMessage> list = adapter.getMessages();
        int aiIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            ChatMessage msg = list.get(i);
            if (msg.getId() == aiMessage.getId()) {
                aiIndex = i;
                break;
            }
        }

        if (aiIndex <= 0) {
            Toast.makeText(getContext(), "找不到可重试的上一条提问", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = aiIndex - 1; i >= 0; i--) {
            ChatMessage prev = list.get(i);
            if (prev.isUser() && prev.getContent() != null && !prev.getContent().trim().isEmpty()) {
                sendChatMessage(prev.getContent().trim());
                Toast.makeText(getContext(), "已重新生成回复", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(getContext(), "找不到可重试的上一条提问", Toast.LENGTH_SHORT).show();
    }

    private String sanitizeAiContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("<action>(?s:.*?)</action>", "").trim();
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(getContext(), "没有可复制的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleMultiFoodLogging(org.json.JSONArray items) {
        int count = items.length();
        String[] foodNames = new String[count];
        boolean[] checkedItems = new boolean[count];
        for (int i = 0; i < count; i++) {
            foodNames[i] = items.optJSONObject(i).optString("name");
            checkedItems[i] = true; // 默认全选
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("智能识别：" + count + " 种食物")
                .setMultiChoiceItems(foodNames, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setNeutralButton("分别入库", (dialog, which) -> {
                    int success = 0;
                    int skipped = 0;
                    for (int i = 0; i < count; i++) {
                        if (checkedItems[i]) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) {
                                skipped++;
                                continue;
                            }
                            String name = item.optString("name", "").trim();
                            int calories = Math.max(0, item.optInt("calories", 0));
                            if (name.isEmpty() || calories <= 0) {
                                skipped++;
                                continue;
                            }
                            com.cz.fitnessdiary.database.entity.FoodLibrary food = new com.cz.fitnessdiary.database.entity.FoodLibrary(
                                    name, calories,
                                    Math.max(0d, item.optDouble("protein", 0d)),
                                    Math.max(0d, item.optDouble("carbs", 0d)),
                                    item.optString("unit", "克"), 100,
                                    FoodCategoryUtils.normalizeCategory(item.optString("category", "其他")));
                            foodRepository.insert(food);
                            success++;
                        }
                    }
                    Toast.makeText(getContext(),
                            "入库完成：成功 " + success + " 条，跳过 " + skipped + " 条",
                            Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("一键记录", (dialog, which) -> {
                    selectedRecordDate = System.currentTimeMillis(); // 重置为当前时间
                    showMealTypeAndDatePickerDialog(items, checkedItems);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMealTypeAndDatePickerDialog(org.json.JSONArray items, boolean[] checkedItems) {
        String[] types = { "早餐", "午餐", "晚餐", "加餐" };
        final int[] selectedType = { 1 }; // 默认午餐

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_meal_date_picker, null);
        com.google.android.material.button.MaterialButton btnDate = dialogView.findViewById(R.id.btn_select_date);
        RadioGroup groupType = dialogView.findViewById(R.id.group_meal_type);

        // 初始化显示
        btnDate.setText("日期: " + com.cz.fitnessdiary.utils.DateUtils.formatDate(selectedRecordDate));

        btnDate.setOnClickListener(v -> {
            com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder
                    .datePicker()
                    .setTitleText("选择记录日期")
                    .setSelection(com.cz.fitnessdiary.utils.DateUtils.localToUtcDayStart(selectedRecordDate))
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedRecordDate = selection;
                btnDate.setText("日期: " + com.cz.fitnessdiary.utils.DateUtils.formatDate(selectedRecordDate));
            });
            datePicker.show(getChildFragmentManager(), "DATE_PICKER");
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认记录详情")
                .setView(dialogView)
                .setPositiveButton("确定记录", (dialog, which) -> {
                    int id = groupType.getCheckedRadioButtonId();
                    int mealType = 1;
                    if (id == R.id.radio_breakfast)
                        mealType = 0;
                    else if (id == R.id.radio_lunch)
                        mealType = 1;
                    else if (id == R.id.radio_dinner)
                        mealType = 2;
                    else if (id == R.id.radio_snack)
                        mealType = 3;

                    saveFoodRecords(items, checkedItems, mealType, selectedRecordDate);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveFoodRecords(org.json.JSONArray items, boolean[] checkedItems, int mealType, long timestamp) {
        String[] types = { "早餐", "午餐", "晚餐", "加餐" };
        int success = 0;
        int skipped = 0;
        for (int i = 0; i < items.length(); i++) {
            if (checkedItems[i]) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    skipped++;
                    continue;
                }
                String name = item.optString("name", "").trim();
                int calories = Math.max(0, item.optInt("calories", 0));
                if (name.isEmpty() || calories <= 0) {
                    skipped++;
                    continue;
                }

                // 创建记录，使用用户选择的时间戳
                com.cz.fitnessdiary.database.entity.FoodRecord record = new com.cz.fitnessdiary.database.entity.FoodRecord(
                        name, calories, timestamp);
                record.setProtein(Math.max(0d, item.optDouble("protein", 0d)));
                record.setCarbs(Math.max(0d, item.optDouble("carbs", 0d)));
                record.setMealType(mealType);
                record.setServings(1.0f);
                record.setServingUnit(item.optString("unit", "份"));
                foodRecordRepository.insert(record);
                success++;
            }
        }
        Toast.makeText(getContext(),
                "记录完成：成功 " + success + " 条（" + types[mealType] + "），日期："
                        + com.cz.fitnessdiary.utils.DateUtils.formatDate(timestamp),
                Toast.LENGTH_SHORT).show();
    }

    private android.graphics.Bitmap decodeScaledBitmap(android.net.Uri uri, int maxSide) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder
                    .createSource(requireContext().getContentResolver(), uri);
            return android.graphics.ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                int maxDim = Math.max(width, height);
                if (maxDim > maxSide) {
                    float scale = (float) maxSide / (float) maxDim;
                    decoder.setTargetSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
                }
            });
        }

        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(is, null, bounds);
        }
        int inSampleSize = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / inSampleSize > maxSide) {
            inSampleSize *= 2;
        }
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, inSampleSize);
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is, null, opts);
            if (bitmap == null) {
                throw new IllegalStateException("无法解析图片");
            }
            return bitmap;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
