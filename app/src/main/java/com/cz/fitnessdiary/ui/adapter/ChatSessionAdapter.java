package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ChatSessionEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 聊天会话列表适配器
 */
public class ChatSessionAdapter extends ListAdapter<ChatSessionEntity, ChatSessionAdapter.SessionViewHolder> {

    private final OnSessionClickListener listener;
    private final OnSessionLongClickListener longClickListener;
    private long currentSessionId = -1;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSessionEntity session);
    }

    public interface OnSessionLongClickListener {
        void onSessionLongClick(ChatSessionEntity session);
    }

    public ChatSessionAdapter(OnSessionClickListener listener, OnSessionLongClickListener longClickListener) {
        super(new DiffUtil.ItemCallback<ChatSessionEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull ChatSessionEntity oldItem, @NonNull ChatSessionEntity newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull ChatSessionEntity oldItem, @NonNull ChatSessionEntity newItem) {
                String oldTitle = oldItem.getTitle() != null ? oldItem.getTitle() : "";
                String newTitle = newItem.getTitle() != null ? newItem.getTitle() : "";
                return oldTitle.equals(newTitle)
                        && oldItem.getLastUpdated() == newItem.getLastUpdated();
            }
        });
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setCurrentSessionId(long id) {
        this.currentSessionId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(getItem(position), listener, longClickListener, currentSessionId);
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvTime;
        private final TextView tvFolderName;
        private final View root;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvTime = itemView.findViewById(R.id.tvSessionTime);
            tvFolderName = itemView.findViewById(R.id.tvFolderName);
            root = itemView;
        }

        public void bind(ChatSessionEntity session, OnSessionClickListener listener,
                OnSessionLongClickListener longClickListener, long currentSessionId) {
            tvTitle.setText(session.getTitle());

            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            tvTime.setText(sdf.format(new Date(session.getLastUpdated())));

            if (session.getFolderName() != null && !session.getFolderName().isEmpty()) {
                tvFolderName.setText(session.getFolderName());
                tvFolderName.setVisibility(View.VISIBLE);
            } else {
                tvFolderName.setVisibility(View.GONE);
            }

            // 选中状态
            if (session.getId() == currentSessionId) {
                root.setBackgroundResource(R.drawable.bg_session_selected);
            } else {
                root.setBackgroundResource(android.R.color.transparent);
            }

            root.setOnClickListener(v -> listener.onSessionClick(session));
            root.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onSessionLongClick(session);
                    return true;
                }
                return false;
            });
        }
    }
}
