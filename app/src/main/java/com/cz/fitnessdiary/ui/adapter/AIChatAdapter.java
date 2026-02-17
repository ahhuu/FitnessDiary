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

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class AIChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;
    private static final int TYPE_THINKING = 3;

    private List<ChatMessage> messages = new ArrayList<>();
    private OnMessageLongClickListener longClickListener;
    private OnActionClickListener actionClickListener;

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
            userHolder.tvContent.setText(message.getContent());

            // 多模式内容预览 (图片)
            if (message.getMediaPath() != null && !message.getMediaPath().isEmpty()) {
                userHolder.ivMedia.setVisibility(View.VISIBLE);
                Glide.with(userHolder.itemView.getContext())
                        .load(message.getMediaPath())
                        .into(userHolder.ivMedia);
            } else {
                userHolder.ivMedia.setVisibility(View.GONE);
            }

            // 多选逻辑
            userHolder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
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

            // 多选逻辑

            String rawContent = message.getContent();
            String reasoning = message.getReasoning();

            // 处理思考过程显示
            if (reasoning != null && !reasoning.trim().isEmpty()) {
                aiHolder.layoutReasoning.setVisibility(View.VISIBLE);
                aiHolder.tvReasoning.setText(reasoning);

                // 非流式模式下，所有已收到的历史思考过程默认收起，保持页面整洁
                aiHolder.tvReasoning.setVisibility(View.GONE);
                aiHolder.ivReasoningArrow.setRotation(0);

                aiHolder.btnToggleReasoning.setOnClickListener(v -> {
                    boolean isVisible = aiHolder.tvReasoning.getVisibility() == View.VISIBLE;
                    aiHolder.tvReasoning.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    aiHolder.ivReasoningArrow.animate().rotation(isVisible ? 0 : 180).setDuration(200).start();
                });
            } else {
                aiHolder.layoutReasoning.setVisibility(View.GONE);
            }

            // 解析 <action> 标签 - 支持多个标签汇总
            Pattern actionPattern = Pattern.compile("<action>(.*?)</action>", Pattern.DOTALL);
            Matcher matcher = actionPattern.matcher(rawContent);

            // 汇总所有识别到的食物
            org.json.JSONArray allFoodItems = new org.json.JSONArray();
            JSONObject finalActionJson = null;

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
                    } else if ("PLAN".equals(type) && finalActionJson == null) {
                        // 计划暂取第一个
                        finalActionJson = actionJson;
                    }
                } catch (Exception e) {
                    // 忽略无效 JSON
                }
            }

            // 如果有食物，构造统一的 Action JSON
            if (allFoodItems.length() > 0) {
                try {
                    finalActionJson = new JSONObject();
                    finalActionJson.put("type", "FOOD");
                    finalActionJson.put("items", allFoodItems);
                } catch (Exception e) {
                }
            }

            // 彻底移除所有 <action> 标签及其内容，避免渲染到界面
            String cleanContent = rawContent.replaceAll("<action>(?s:.*?)</action>", "").trim();
            aiHolder.tvContent.setText(cleanContent);

            if (finalActionJson != null) {
                aiHolder.btnAction.setVisibility(View.VISIBLE);
                String type = finalActionJson.optString("type");

                if ("FOOD".equals(type)) {
                    org.json.JSONArray items = finalActionJson.optJSONArray("items");
                    if (items != null && items.length() > 0) {
                        if (items.length() == 1) {
                            aiHolder.btnAction.setText("🍽️ 一键录入：" + items.optJSONObject(0).optString("name"));
                        } else {
                            aiHolder.btnAction.setText("🍽️ 智能识别：" + items.length() + " 种食物");
                        }
                    } else {
                        aiHolder.btnAction.setText("🍽️ 录入识别出的食物");
                    }
                } else if ("PLAN".equals(type)) {
                    String name = finalActionJson.optString("name");
                    aiHolder.btnAction.setText("📅 添加到计划：" + name);
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
