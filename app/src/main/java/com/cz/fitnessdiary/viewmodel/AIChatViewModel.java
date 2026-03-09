package com.cz.fitnessdiary.viewmodel;

import com.cz.fitnessdiary.database.entity.ChatMessageEntity;
import com.cz.fitnessdiary.database.entity.ChatSessionEntity;
import com.cz.fitnessdiary.model.ChatMessage;
import com.cz.fitnessdiary.repository.ChatRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.service.AICallback;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.utils.FoodCategoryUtils;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 聊天 ViewModel
 */
public class AIChatViewModel extends AndroidViewModel {
    private final ChatRepository repository;
    private final UserRepository userRepository;

    private final MediatorLiveData<List<ChatMessage>> messages = new MediatorLiveData<>();
    private final MutableLiveData<Long> currentSessionId = new MutableLiveData<>(1L);
    private final LiveData<List<ChatSessionEntity>> allSessions;

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isDeepThinking = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSearchEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<String> attachedFileUri = new MutableLiveData<>(null);
    private final MutableLiveData<String> currentThinkingModel = new MutableLiveData<>(null);

    private LiveData<List<ChatMessageEntity>> currentDbSource = null;

    public AIChatViewModel(@NonNull Application application) {
        super(application);
        repository = new ChatRepository(application);
        userRepository = new UserRepository(application);
        allSessions = repository.getAllSessionsLive();

        // 动态根据当前会话 ID 加载消息
        messages.addSource(currentSessionId, sessionId -> {
            if (currentDbSource != null) {
                messages.removeSource(currentDbSource);
            }
            currentDbSource = repository.getMessagesBySessionLive(sessionId);
            messages.addSource(currentDbSource,
                    entities -> updateMessagesList(entities, isLoading.getValue(), currentThinkingModel.getValue()));
        });

        messages.addSource(isLoading, loading -> {
            if (currentDbSource != null) {
                updateMessagesList(currentDbSource.getValue(), loading, currentThinkingModel.getValue());
            }
        });

        messages.addSource(currentThinkingModel, model -> {
            if (currentDbSource != null) {
                updateMessagesList(currentDbSource.getValue(), isLoading.getValue(), model);
            }
        });
    }

    private long thinkingStartTime = 0;

    private void updateMessagesList(List<ChatMessageEntity> entities, Boolean loading, String modelName) {
        List<ChatMessage> list = new ArrayList<>();
        if (entities != null) {
            list = entities.stream()
                    .map(e -> new ChatMessage(e.getId(), e.getContent(), e.getReasoning(), e.isUser(),
                            e.getTimestamp(), e.getMediaPath()))
                    .collect(Collectors.toList());
        }

        if (Boolean.TRUE.equals(loading)) {
            if (thinkingStartTime == 0)
                thinkingStartTime = System.currentTimeMillis();
            String msg = (modelName != null ? modelName : "AI") + "_THINKING";
            list.add(new ChatMessage(-1, msg, null, false, thinkingStartTime));
        } else {
            thinkingStartTime = 0;
        }
        messages.setValue(list);
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<List<ChatSessionEntity>> getAllSessions() {
        return allSessions;
    }

    public LiveData<Long> getCurrentSessionId() {
        return currentSessionId;
    }

    public LiveData<User> getUser() {
        return userRepository.getUser();
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsDeepThinking() {
        return isDeepThinking;
    }

    public LiveData<Boolean> getIsSearchEnabled() {
        return isSearchEnabled;
    }

    public LiveData<String> getAttachedFileUri() {
        return attachedFileUri;
    }

    public void selectSession(long sessionId) {
        currentSessionId.setValue(sessionId);
    }

    public void createNewSession() {
        ChatSessionEntity session = new ChatSessionEntity("新对话", System.currentTimeMillis());
        repository.insertSession(session, id -> {
            currentSessionId.postValue(id);
        });
    }

    public void deleteMessage(ChatMessage message) {
        if (message.getId() <= 0)
            return;
        ChatMessageEntity entity = new ChatMessageEntity(message.getContent(), message.getReasoning(), message.isUser(),
                message.getTimestamp(), currentSessionId.getValue(), message.getMediaPath());
        entity.setId(message.getId());
        repository.delete(entity);
    }

    public void deleteMessages(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            return;
        repository.deleteMessages(ids);
    }

    public void editMessage(ChatMessage message, String newContent) {
        if (message.getId() <= 0)
            return;
        ChatMessageEntity entity = new ChatMessageEntity(newContent, message.getReasoning(), message.isUser(),
                message.getTimestamp(), currentSessionId.getValue(), message.getMediaPath());
        entity.setId(message.getId());
        repository.update(entity);
    }

    public void deleteSession(ChatSessionEntity session) {
        repository.deleteSession(session);
        // 如果删除的是当前会话，回到默认会话
        if (session.getId() == (currentSessionId.getValue() != null ? currentSessionId.getValue() : 1L)) {
            currentSessionId.postValue(1L);
        }
    }

    public void renameSession(long sessionId, String newTitle) {
        repository.renameSession(sessionId, newTitle);
    }

    public void updateSessionFolder(long sessionId, String folderName) {
        repository.updateSessionFolder(sessionId, folderName);
    }

    public void deleteAllSessions() {
        repository.deleteAllSessions();
        currentSessionId.postValue(1L);
    }

    public void setDeepThinking(boolean enabled) {
        isDeepThinking.setValue(enabled);
    }

    public void setSearchEnabled(boolean enabled) {
        isSearchEnabled.setValue(enabled);
    }

    public void setAttachedFileUri(String uri) {
        attachedFileUri.setValue(uri);
    }

    public void sendMessage(String content) {
        sendMessageWithAttachment(content, null, null);
    }

    /** 上下文历史消息的最大条数（控制 token 消耗） */
    private static final int MAX_HISTORY_MESSAGES = 20;

    public void sendMessageWithAttachment(String content, String mediaPath, android.graphics.Bitmap image) {
        if (content == null || content.trim().isEmpty())
            return;

        Long sessionId = currentSessionId.getValue();
        if (sessionId == null)
            sessionId = 1L;
        final long finalSessionId = sessionId;

        // 如果是新对话，更新标题
        new Thread(() -> {
            ChatSessionEntity session = repository.sessionDao.getSessionById(finalSessionId);
            if (session != null && "新对话".equals(session.getTitle())) {
                session.setTitle(content.length() > 20 ? content.substring(0, 20) + "..." : content);
                repository.updateSession(session);
            }
        }).start();

        // 1. 添加用户消息 (包含媒体路径)
        repository.insert(
                new ChatMessageEntity(content, null, true, System.currentTimeMillis(), finalSessionId, mediaPath));

        // 2. 获取回复
        isLoading.setValue(true);
        boolean search = Boolean.TRUE.equals(isSearchEnabled.getValue());

        new Thread(() -> {
            User user = userRepository.getUserSync();
            String searchContext = search ? com.cz.fitnessdiary.service.WebSearchService.searchSummary(content) : "";
            String systemInstruction = buildSystemInstruction(user, search, searchContext);

            // 加载对话历史（取最近 N 条，不含刚发的那条，因为还未入库完成）
            List<ChatMessageEntity> allMessages = repository.getMessagesBySessionSync(finalSessionId);
            List<ChatMessageEntity> history = trimHistory(allMessages);

            // 智能调度：图片走千问，纯文本走 DeepSeek
            if (image != null) {
                sendToQwen(content, systemInstruction, image, history);
            } else if (Boolean.TRUE.equals(isDeepThinking.getValue())) {
                sendToDeepSeek(content, systemInstruction, true, history);
            } else {
                sendToDeepSeek(content, systemInstruction, false, history);
            }
        }).start();
    }

    /**
     * 裁剪历史消息，控制上下文窗口大小以节省 token 消耗。
     * 策略：取最近 MAX_HISTORY_MESSAGES 条消息（不含本次用户消息，因为会单独发送）。
     */
    private List<ChatMessageEntity> trimHistory(List<ChatMessageEntity> allMessages) {
        if (allMessages == null || allMessages.isEmpty()) {
            return new ArrayList<>();
        }
        int size = allMessages.size();
        if (size <= MAX_HISTORY_MESSAGES) {
            return new ArrayList<>(allMessages);
        }
        // 只保留最近的 N 条
        return new ArrayList<>(allMessages.subList(size - MAX_HISTORY_MESSAGES, size));
    }

    private String buildSystemInstruction(User user, boolean searchEnabled, String searchContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 FitnessDiary 应用的 AI 私教。语气亲和轻松但专业克制，避免夸张营销口吻。\n");
        sb.append("输出规则：正文最多120个中文字符、最多4行；最多使用1个 emoji；避免使用“灵魂所在/太诱人/拉满”等表达。\n");
        sb.append("饮食场景固定结构：1句结论 + 热量区间 + 1条可执行建议；禁止表格、长编号列表和大段科普。\n");
        sb.append("重点：正文只保留用户决策需要的信息，不重复罗列会写入 <action> 的字段。\n");
        if (searchEnabled) {
            sb.append("【联网搜索模式已开启】你已拿到实时检索摘要，请优先基于摘要回答，并在结尾用“来源：”给出 1-2 个链接。\n");
            if (searchContext != null && !searchContext.trim().isEmpty()) {
                sb.append("【实时检索摘要】\n").append(searchContext).append("\n");
            } else {
                sb.append("【实时检索摘要】未获取到有效结果。请明确说明“未检索到可靠来源”，避免编造。\n");
            }
        }
        if (user != null) {
            sb.append("当前用户信息如下：\n");
            sb.append("- 姓名/昵称：").append(user.getNickname()).append("\n");
            sb.append("- 身高：").append(user.getHeight()).append("cm\n");
            sb.append("- 体重：").append(user.getWeight()).append("kg\n");
            sb.append("- 目标：").append(user.getGoal()).append("\n");
            sb.append("请根据这些数据提供专业的建议。\n");
        }
        sb.append("\n【重要：智能功能识别】\n");
        sb.append(
                "1. 当用户询问具体的食物、想知道热量、或者提到要记录餐饮时，务必在回复末尾附加标签：<action>{\"type\":\"FOOD\",\"items\":[{\"name\":\"食物名\",\"calories\":数值,\"protein\":数值,\"carbs\":数值,\"unit\":\"克\",\"category\":\"类别\"}, ...]}</action>\n");
        sb.append("   注意：哪怕是单一食物，也请放入 items 数组中。\n");
        sb.append("   分类必须严格匹配数据库已有分类（选最接近的）：\n");
        sb.append("   - 可选分类：")
                .append(String.join("、", FoodCategoryUtils.getCanonicalCategories()))
                .append("\n");
        sb.append(
                "2. 当建议具体的动作、组数或制定训练方案时，务必附加：<action>{\"type\":\"PLAN\",\"name\":\"练习名\",\"sets\":4,\"reps\":12,\"desc\":\"动作描述\",\"category\":\"胸部/腿部/背部/有氧\"}</action>\n");
        sb.append("3. 哪怕用户只是随口问一句“这个热量高吗？”也要智能弹出入库按钮，不要吝啬。如果是多个食物，请在 items 列表中一次性列出。\n");
        sb.append(
                "4. 如果是盖饭、套餐、便当、拼盘等整餐场景，请优先给出 meal_name（如“鸡腿盖饭”），并保持 items 为该餐组成。\n5. 【核心指令：图片主动识别】如果用户上传了图片，你必须**优先识别**图片中的所有食物，并**主动**提供 <action> 标签进行记录，即便用户没有在文字中明确要求识别。识别要全面且专业，给出预估热量和营养素。\n");
        sb.append("6. 不要向用户解释这些标签的存在。");
        return sb.toString();
    }

    private void sendToDeepSeek(final String content, final String systemInstruction, boolean thinking,
            List<ChatMessageEntity> history) {
        currentThinkingModel.postValue(thinking ? "DeepSeek-R1" : "DeepSeek-V3");
        com.cz.fitnessdiary.service.DeepSeekService.sendMessage(content, systemInstruction, thinking, history,
                new AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        String safeResponse = response == null ? "" : response.trim();
                        String safeReasoning = reasoning == null ? "" : reasoning.trim();

                        if (safeResponse.isEmpty() && safeReasoning.isEmpty()) {
                            handleError("本次未返回有效内容，请重试");
                            return;
                        }
                        addAiMessage(response, reasoning);
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {
                    }

                    @Override
                    public void onError(String error) {
                        handleError("私教连接中断: " + error);
                    }
                });
    }

    private void sendToQwen(String content, String systemInstruction, android.graphics.Bitmap image,
            List<ChatMessageEntity> history) {
        currentThinkingModel.postValue("Qwen-VL-Plus");
        com.cz.fitnessdiary.service.QwenService.sendMessage(content, systemInstruction, image, history,
                new AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        addAiMessage(response, reasoning);
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {
                    }

                    @Override
                    public void onError(String error) {
                        handleError("千问私教连线失败: " + error);
                    }
                });
    }

    private void addAiMessage(String response, String reasoning) {
        repository.insert(new ChatMessageEntity(response, reasoning, false, System.currentTimeMillis(),
                currentSessionId.getValue()));
        currentThinkingModel.setValue(null);
        isLoading.setValue(false);
    }

    private void handleError(String error) {
        String userFriendlyError = "⚠️ 私教正在整理器材: " + error;
        repository.insert(new ChatMessageEntity(userFriendlyError, false, System.currentTimeMillis(),
                currentSessionId.getValue()));
        currentThinkingModel.setValue(null);
        isLoading.setValue(false);
    }
}


