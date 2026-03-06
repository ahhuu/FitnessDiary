package com.cz.fitnessdiary.ui.adapter;

import com.cz.fitnessdiary.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class AIChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;
    private static final int TYPE_THINKING = 3;
    private static final int COLLAPSE_LIMIT = 220;

    private List<ChatMessage> messages = new ArrayList<>();
    private OnMessageLongClickListener longClickListener;
    private OnActionClickListener actionClickListener;
    private final Set<Long> expandedMessageKeys = new HashSet<>();

    // 多选相关
    private boolean isSelectionMode = false;
    private final java.util.Set<Long> selectedMessageIds = new java.util.HashSet<>();
    private OnSelectionChangeListener selectionChangeListener;

    public interface OnMessageLongClickListener {
        void onMessageLongClick(ChatMessage message);
    }

    public interface OnActionClickListener {
        void onActionClick(JSONObject actionJson);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChange(int count);
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnActionClickListener(OnActionClickListener listener) {
        this.actionClickListener = listener;
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    // --- 多选方法 ---
    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        if (this.isSelectionMode == selectionMode)
            return;
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedMessageIds.clear();
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(long messageId) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId);
        } else {
            selectedMessageIds.add(messageId);
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChange(selectedMessageIds.size());
        }
    }

    public void selectAll() {
        int selectableCount = 0;
        for (ChatMessage msg : messages) {
            if (msg.getId() > 0)
                selectableCount++;
        }

        if (selectedMessageIds.size() == selectableCount && selectableCount > 0) {
            // 已全选，则取消全选
            selectedMessageIds.clear();
        } else {
            // 否则全选
            for (ChatMessage msg : messages) {
                if (msg.getId() > 0) {
                    selectedMessageIds.add(msg.getId());
                }
            }
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChange(selectedMessageIds.size());
        }
    }

    public List<Long> getSelectedMessageIds() {
        return new ArrayList<>(selectedMessageIds);
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        // 清理已不在消息列表中的展开状态，避免集合增长
        Set<Long> validKeys = new HashSet<>();
        for (ChatMessage message : newMessages) {
            validKeys.add(getStableMessageKey(message));
        }
        expandedMessageKeys.retainAll(validKeys);

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return messages.size();
            }

            @Override
            public int getNewListSize() {
                return newMessages.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                ChatMessage oldMsg = messages.get(oldItemPosition);
                ChatMessage newMsg = newMessages.get(newItemPosition);
                return oldMsg.getTimestamp() == newMsg.getTimestamp();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChatMessage oldMsg = messages.get(oldItemPosition);
                ChatMessage newMsg = newMessages.get(newItemPosition);
                return oldMsg.getContent().equals(newMsg.getContent()) &&
                        ((oldMsg.getReasoning() == null && newMsg.getReasoning() == null) ||
                                (oldMsg.getReasoning() != null && oldMsg.getReasoning().equals(newMsg.getReasoning())))
                        &&
                        ((oldMsg.getMediaPath() == null && newMsg.getMediaPath() == null) ||
                                (oldMsg.getMediaPath() != null && oldMsg.getMediaPath().equals(newMsg.getMediaPath())));
            }
        });
        this.messages = new ArrayList<>(newMessages);
        result.dispatchUpdatesTo(this);
    }

    private long getStableMessageKey(ChatMessage message) {
        if (message == null) {
            return Long.MIN_VALUE;
        }
        return message.getId() > 0 ? message.getId() : message.getTimestamp();
    }

    private String sanitizeAiDisplayContent(String rawContent) {
        if (rawContent == null) {
            return "";
        }

        String text = rawContent.replaceAll("<action>(?s:.*?)</action>", "");
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*\\|?\\s*:?-{3,}:?(\\s*\\|\\s*:?-{3,}:?)*\\s*\\|?\\s*$", "");
        text = text.replaceAll("(?m)^\\s*\\|.*\\|\\s*$", "");
        text = text.replaceAll("(?m)^\\s*[\\-*+]\\s{2,}", "- ");
        text = text.replaceAll("(?m)^\\s*\\d+\\.\\s{2,}", "1. ");
        text = text.replace("\r", "");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private String buildCollapsedText(String cleanContent) {
        if (cleanContent == null || cleanContent.length() <= COLLAPSE_LIMIT) {
            return cleanContent == null ? "" : cleanContent;
        }
        return cleanContent.substring(0, COLLAPSE_LIMIT).trim() + "...\n展开";
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.isUser())
            return TYPE_USER;
        // 约定：如果内容是 "THINKING_INDICATOR" 或以 "_THINKING" 结尾，显示思考动画
        String content = msg.getContent();
        if ("THINKING_INDICATOR".equals(content) || (content != null && content.endsWith("_THINKING")))
            return TYPE_THINKING;
        return TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_user, parent,
                    false);
            return new UserViewHolder(view);
        } else if (viewType == TYPE_THINKING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_ai_thinking, parent,
                    false);
            return new ThinkingViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_ai, parent, false);
            return new AIViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean isSelected = selectedMessageIds.contains(message.getId());

        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.tvContent.setText(message.getContent() == null ? "" : message.getContent());
            if (message.getMediaPath() != null && !message.getMediaPath().isEmpty()) {
                userHolder.ivMedia.setVisibility(View.VISIBLE);
                Glide.with(userHolder.itemView.getContext())
                        .load(message.getMediaPath())
                        .into(userHolder.ivMedia);
            } else {
                userHolder.ivMedia.setVisibility(View.GONE);
            }

            // 多选逻辑
            userHolder.cbSelect.setVisibility((isSelectionMode && message.getId() > 0) ? View.VISIBLE : View.GONE);
            userHolder.cbSelect.setChecked(isSelected);
            userHolder.itemView.setAlpha(isSelected ? 0.6f : 1.0f);

            userHolder.itemView.setOnClickListener(v -> {
                if (isSelectionMode && message.getId() > 0) {
                    toggleSelection(message.getId());
                }
            });

            userHolder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode && longClickListener != null) {
                    longClickListener.onMessageLongClick(message);
                    return true;
                }
                return false;
            });
        } else if (holder instanceof ThinkingViewHolder) {
            ThinkingViewHolder thinkingHolder = (ThinkingViewHolder) holder;
            String modelMsg = message.getContent();
            if (modelMsg != null && modelMsg.endsWith("_THINKING")) {
                String modelName = modelMsg.replace("_THINKING", "");
                thinkingHolder.tvModelName.setText(modelName);
            } else {
                thinkingHolder.tvModelName.setText("AI");
            }
        } else {
            AIViewHolder aiHolder = (AIViewHolder) holder;

            // 多模式内容预览 (图片)
            if (message.getMediaPath() != null && !message.getMediaPath().isEmpty()) {
                aiHolder.ivMedia.setVisibility(View.VISIBLE);
                Glide.with(aiHolder.itemView.getContext())
                        .load(message.getMediaPath())
                        .into(aiHolder.ivMedia);
            } else {
                aiHolder.ivMedia.setVisibility(View.GONE);
            }

            String rawContent = message.getContent();
            String safeRawContent = rawContent == null ? "" : rawContent;
            String reasoning = message.getReasoning();
            long messageKey = getStableMessageKey(message);

            // 思考过程默认收起，仅在有内容时展示
            if (reasoning != null && !reasoning.trim().isEmpty()) {
                aiHolder.layoutReasoning.setVisibility(View.VISIBLE);
                aiHolder.tvReasoning.setText(reasoning);
                boolean reasoningOnly = safeRawContent.trim().isEmpty();
                aiHolder.tvReasoning.setVisibility(reasoningOnly ? View.VISIBLE : View.GONE);
                aiHolder.ivReasoningArrow.setRotation(reasoningOnly ? 180f : 0f);
                aiHolder.btnToggleReasoning.setClickable(true);
                aiHolder.btnToggleReasoning.setFocusable(true);
                aiHolder.btnToggleReasoning.setOnClickListener(v -> {
                    boolean isVisible = aiHolder.tvReasoning.getVisibility() == View.VISIBLE;
                    aiHolder.tvReasoning.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    aiHolder.ivReasoningArrow.animate().rotation(isVisible ? 0f : 180f).setDuration(200).start();
                });
            } else {
                aiHolder.layoutReasoning.setVisibility(View.GONE);
                aiHolder.tvReasoning.setText("");
                aiHolder.tvReasoning.setVisibility(View.GONE);
                aiHolder.ivReasoningArrow.setRotation(0f);
                aiHolder.btnToggleReasoning.setOnClickListener(null);
                aiHolder.btnToggleReasoning.setClickable(false);
                aiHolder.btnToggleReasoning.setFocusable(false);
            }

            aiHolder.cbSelect.setVisibility((isSelectionMode && message.getId() > 0) ? View.VISIBLE : View.GONE);
            aiHolder.cbSelect.setChecked(isSelected);
            aiHolder.itemView.setAlpha(isSelected ? 0.6f : 1.0f);

            // 解析 <action> 标签 - 支持多个标签汇总
            Pattern actionPattern = Pattern.compile("<action>(.*?)</action>", Pattern.DOTALL);
            Matcher matcher = actionPattern.matcher(safeRawContent);

            // 汇总所有识别到的食物
            org.json.JSONArray allFoodItems = new org.json.JSONArray();
            JSONObject finalActionJson = null;
            JSONObject planActionJson = null;

            while (matcher.find()) {
                String actionStr = matcher.group(1);
                try {
                    JSONObject actionJson = new JSONObject(actionStr);
                    String type = actionJson.optString("type");

                    if ("FOOD".equals(type)) {
                        org.json.JSONArray items = actionJson.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                allFoodItems.put(items.get(i));
                            }
                        } else if (actionJson.has("name")) {
                            // 兼容旧格式或独立标签格式
                            allFoodItems.put(actionJson);
                        }
                    } else if ("PLAN".equals(type) && planActionJson == null) {
                        // 计划暂取第一个
                        planActionJson = actionJson;
                    }
                } catch (Exception e) {
                    // 忽略无效 JSON
                }
            }

            // 如果有食物，构造统一的 Action JSON
            if (allFoodItems.length() > 0) {
                try {
                    JSONObject foodActionJson = new JSONObject();
                    foodActionJson.put("type", "FOOD");
                    foodActionJson.put("items", allFoodItems);

                    if (planActionJson != null) {
                        // 同一条回复里允许同时保留计划+饮食动作
                        org.json.JSONArray actions = new org.json.JSONArray();
                        actions.put(foodActionJson);
                        actions.put(planActionJson);
                        finalActionJson = new JSONObject();
                        finalActionJson.put("type", "MULTI");
                        finalActionJson.put("actions", actions);
                    } else {
                        finalActionJson = foodActionJson;
                    }
                } catch (Exception e) {
                    finalActionJson = planActionJson;
                }
            } else {
                finalActionJson = planActionJson;
            }

            // 轻清洗：移除 action、Markdown 表格/标题噪音，并做长度折叠
            String cleanContent = sanitizeAiDisplayContent(safeRawContent);
            boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
            boolean reasoningOnly = cleanContent.isEmpty() && hasReasoning;
            aiHolder.tvContent.setVisibility(reasoningOnly ? View.GONE : View.VISIBLE);
            boolean canCollapse = cleanContent.length() > COLLAPSE_LIMIT;
            boolean isExpanded = expandedMessageKeys.contains(messageKey);
            if (!reasoningOnly && canCollapse) {
                aiHolder.tvContent.setText(isExpanded ? cleanContent + "\n收起" : buildCollapsedText(cleanContent));
                aiHolder.tvContent.setOnClickListener(v -> {
                    if (isSelectionMode) {
                        return;
                    }
                    if (expandedMessageKeys.contains(messageKey)) {
                        expandedMessageKeys.remove(messageKey);
                    } else {
                        expandedMessageKeys.add(messageKey);
                    }
                    int positionToUpdate = holder.getBindingAdapterPosition();
                    if (positionToUpdate != RecyclerView.NO_POSITION) {
                        notifyItemChanged(positionToUpdate);
                    }
                });
            } else if (!reasoningOnly) {
                aiHolder.tvContent.setText(cleanContent);
                aiHolder.tvContent.setOnClickListener(null);
            }

            aiHolder.tvContent.setOnLongClickListener(v -> {
                if (!isSelectionMode && longClickListener != null) {
                    longClickListener.onMessageLongClick(message);
                    return true;
                }
                return false;
            });

            aiHolder.layoutReasoning.setOnLongClickListener(v -> {
                if (!isSelectionMode && longClickListener != null) {
                    longClickListener.onMessageLongClick(message);
                    return true;
                }
                return false;
            });

            if (!reasoningOnly && finalActionJson != null) {
                aiHolder.btnAction.setVisibility(View.VISIBLE);
                String type = finalActionJson.optString("type");

                if ("MULTI".equals(type)) {
                    aiHolder.btnAction.setText("执行2项");
                } else if ("FOOD".equals(type)) {
                    org.json.JSONArray items = finalActionJson.optJSONArray("items");
                    if (items != null && items.length() > 0) {
                        aiHolder.btnAction.setText("记录这餐（" + items.length() + "项）");
                    } else {
                        aiHolder.btnAction.setText("记录这餐");
                    }
                } else if ("PLAN".equals(type)) {
                    aiHolder.btnAction.setText("添加计划");
                }

                final JSONObject finalAction = finalActionJson;
                aiHolder.btnAction.setOnClickListener(v -> {
                    if (!isSelectionMode && actionClickListener != null) {
                        actionClickListener.onActionClick(finalAction);
                    }
                });
            } else {
                aiHolder.btnAction.setVisibility(View.GONE);
            }

            // 监听器
            aiHolder.itemView.setOnClickListener(v -> {
                if (isSelectionMode && message.getId() > 0) {
                    toggleSelection(message.getId());
                }
            });

            aiHolder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode && longClickListener != null) {
                    longClickListener.onMessageLongClick(message);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        ImageView ivMedia;
        android.widget.CheckBox cbSelect;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            ivMedia = itemView.findViewById(R.id.iv_media);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        Button btnAction;
        ImageView ivMedia;
        android.widget.CheckBox cbSelect;

        // 新增思维链组件
        View layoutReasoning;
        View btnToggleReasoning;
        TextView tvReasoning;
        ImageView ivReasoningArrow;

        AIViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            btnAction = itemView.findViewById(R.id.btn_action);
            ivMedia = itemView.findViewById(R.id.iv_media);
            cbSelect = itemView.findViewById(R.id.cb_select);

            layoutReasoning = itemView.findViewById(R.id.layout_reasoning);
            btnToggleReasoning = itemView.findViewById(R.id.btn_toggle_reasoning);
            tvReasoning = itemView.findViewById(R.id.tv_reasoning);
            ivReasoningArrow = itemView.findViewById(R.id.iv_reasoning_arrow);
        }
    }

    static class ThinkingViewHolder extends RecyclerView.ViewHolder {
        TextView tvModelName;
        TextView tvThinking;

        ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvModelName = itemView.findViewById(R.id.tv_model_name);
            tvThinking = itemView.findViewById(R.id.tv_thinking);
        }
    }
}
