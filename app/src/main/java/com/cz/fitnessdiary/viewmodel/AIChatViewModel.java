package com.cz.fitnessdiary.viewmodel;

import com.cz.fitnessdiary.database.entity.ChatMessageEntity;
import com.cz.fitnessdiary.database.entity.ChatSessionEntity;
import com.cz.fitnessdiary.model.ChatMessage;
import com.cz.fitnessdiary.repository.ChatRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.service.AICallback;
import com.cz.fitnessdiary.database.entity.User;

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
            String systemInstruction = buildSystemInstruction(user);

            // 智能调度：图片走千问，纯文本走 DeepSeek
            if (image != null) {
                sendToQwen(content, systemInstruction, image);
            } else if (Boolean.TRUE.equals(isDeepThinking.getValue())) {
                sendToDeepSeek(content, systemInstruction, true);
            } else {
                sendToDeepSeek(content, systemInstruction, false);
            }
        }).start();
    }

    private String buildSystemInstruction(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 FitnessDiary 应用的 AI 私教。性格阳光、积极，像朋友一样。请多使用 Emoji 🏋️‍♂️🥗✨。\n");
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
        sb.append("   - 主食类：主食: 精选米面、主食: 飘香汤面、主食: 面点包子、主食: 根茎薯类（如蒸红薯）\n");
        sb.append("   - 菜肴类：菜肴: 精选荤菜、菜肴: 清爽素菜（注意：炒土豆丝、地三鲜等虽含淀粉但属于此类）\n");
        sb.append("   - 蛋白类：蛋白: 蛋奶豆制品、蛋白: 肉类海鲜、蛋白: 健身补剂\n");
        sb.append("   - 蔬果类：蔬菜: 新鲜时蔬、水果: 时令水果\n");
        sb.append("   - 其它：零食: 包装小吃、饮料: 咖啡奶茶、调料/油脂 (Condiments)\n");
        sb.append(
                "2. 当建议具体的动作、组数或制定训练方案时，务必附加：<action>{\"type\":\"PLAN\",\"name\":\"练习名\",\"sets\":4,\"reps\":12,\"desc\":\"动作描述\",\"category\":\"胸部/腿部/背部/有氧\"}</action>\n");
        sb.append("3. 哪怕用户只是随口问一句“这个热量高吗？”也要智能弹出入库按钮，不要吝啬。如果是多个食物，请在 items 列表中一次性列出。\n");
        sb.append(
                "4. 【核心指令：图片主动识别】如果用户上传了图片，你必须**优先识别**图片中的所有食物，并**主动**提供 <action> 标签进行记录，即便用户没有在文字中明确要求识别。识别要全面且专业，给出预估热量和营养素。\n");
        sb.append("5. 不要向用户解释这些标签的存在。");
        return sb.toString();
    }

    private void sendToDeepSeek(final String content, final String systemInstruction, boolean thinking) {
        currentThinkingModel.postValue("DeepSeek-V3");
        com.cz.fitnessdiary.service.DeepSeekService.sendMessage(content, systemInstruction, thinking,
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
                        handleError("教练连接中断: " + error);
                    }
                });
    }

    private void sendToQwen(String content, String systemInstruction, android.graphics.Bitmap image) {
        currentThinkingModel.postValue("Qwen-VL-Plus");
        com.cz.fitnessdiary.service.QwenService.sendMessage(content, systemInstruction, image,
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
                        handleError("千问教练连线失败: " + error);
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
        String userFriendlyError = "⚠️ 教练正在整理器材: " + error;
        repository.insert(new ChatMessageEntity(userFriendlyError, false, System.currentTimeMillis(),
                currentSessionId.getValue()));
        currentThinkingModel.setValue(null);
        isLoading.setValue(false);
    }
}
