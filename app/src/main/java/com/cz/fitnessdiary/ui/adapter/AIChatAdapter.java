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
    private String userAvatarUri;
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
                                (oldMsg.getReasoning() != null && oldMsg.getReasoning().equals(newMsg.getReasoning())));
            }
        });
        this.messages = new ArrayList<>(newMessages);
        result.dispatchUpdatesTo(this);
    }

    public void setUserAvatarUri(String uri) {
        this.userAvatarUri = uri;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.isUser())
            return TYPE_USER;
        // 约定：如果内容是 "THINKING_INDICATOR"，显示思考动画
        if ("THINKING_INDICATOR".equals(msg.getContent()))
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

            // 多选逻辑
            userHolder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            userHolder.cbSelect.setChecked(isSelected);
            userHolder.itemView.setAlpha(isSelected ? 0.6f : 1.0f);

            // 加载用户头像
            Glide.with(userHolder.itemView.getContext())
                    .load(userAvatarUri)
                    .placeholder(R.drawable.ic_nav_profile_filled)
                    .error(R.drawable.ic_nav_profile_filled)
                    .apply(RequestOptions.circleCropTransform())
                    .into(userHolder.ivAvatar);

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
            Glide.with(thinkingHolder.itemView.getContext())
                    .load(R.drawable.ic_app_logo)
                    .apply(RequestOptions.circleCropTransform())
                    .into(thinkingHolder.ivAvatar);
        } else {
            AIViewHolder aiHolder = (AIViewHolder) holder;

            // 多选逻辑
            aiHolder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            aiHolder.cbSelect.setChecked(isSelected);
            aiHolder.itemView.setAlpha(isSelected ? 0.6f : 1.0f);

            // 加载 AI 头像 (APP 图标风格)
            Glide.with(aiHolder.itemView.getContext())
                    .load(R.drawable.ic_app_logo)
                    .apply(RequestOptions.circleCropTransform())
                    .into(aiHolder.ivAvatar);

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

            // 解析 <action> 标签
            Pattern actionPattern = Pattern.compile("<action>(.*?)</action>", Pattern.DOTALL);
            Matcher matcher = actionPattern.matcher(rawContent);

            if (matcher.find()) {
                String actionStr = matcher.group(1);
                String cleanContent = rawContent.replace(matcher.group(0), "").trim();
                aiHolder.tvContent.setText(cleanContent);

                try {
                    JSONObject actionJson = new JSONObject(actionStr);
                    aiHolder.btnAction.setVisibility(View.VISIBLE);
                    String type = actionJson.optString("type");
                    String name = actionJson.optString("name");

                    if ("FOOD".equals(type)) {
                        aiHolder.btnAction.setText("🍽️ 一键录入：" + name);
                    } else if ("PLAN".equals(type)) {
                        aiHolder.btnAction.setText("📅 添加到计划：" + name);
                    }

                    aiHolder.btnAction.setOnClickListener(v -> {
                        if (!isSelectionMode && actionClickListener != null) {
                            actionClickListener.onActionClick(actionJson);
                        }
                    });
                } catch (Exception e) {
                    aiHolder.btnAction.setVisibility(View.GONE);
                }
            } else {
                aiHolder.tvContent.setText(rawContent);
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
        ImageView ivAvatar;
        android.widget.CheckBox cbSelect;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        Button btnAction;
        ImageView ivAvatar;
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
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            cbSelect = itemView.findViewById(R.id.cb_select);

            layoutReasoning = itemView.findViewById(R.id.layout_reasoning);
            btnToggleReasoning = itemView.findViewById(R.id.btn_toggle_reasoning);
            tvReasoning = itemView.findViewById(R.id.tv_reasoning);
            ivReasoningArrow = itemView.findViewById(R.id.iv_reasoning_arrow);
        }
    }

    static class ThinkingViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;

        ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
        }
    }
}
