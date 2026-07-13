package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.databinding.ItemSocialFriendBinding;
import com.cz.fitnessdiary.model.FriendUiModel;
import com.cz.fitnessdiary.utils.TextUtilsCompat;

import java.util.ArrayList;
import java.util.List;

public final class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.Holder> {
    public interface Listener {
        void onPrimaryAction(FriendUiModel friend);
        void onSecondaryAction(FriendUiModel friend);
    }

    private final Listener listener;
    private final boolean requestMode;
    private List<FriendUiModel> items = new ArrayList<>();

    public FriendAdapter(boolean requestMode, Listener listener) {
        this.requestMode = requestMode;
        this.listener = listener;
    }

    public void submitList(List<FriendUiModel> value) {
        items = value == null ? new ArrayList<>() : new ArrayList<>(value);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSocialFriendBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemSocialFriendBinding binding;

        Holder(ItemSocialFriendBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FriendUiModel item) {
            if (!TextUtilsCompat.isBlank(item.getAvatarUrl())) {
                binding.tvAvatarText.setVisibility(android.view.View.GONE);
                binding.ivAvatar.setVisibility(android.view.View.VISIBLE);
                com.bumptech.glide.Glide.with(itemView.getContext())
                     .load(item.getAvatarUrl())
                     .placeholder(com.cz.fitnessdiary.R.drawable.bg_circle_light_primary)
                     .into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setVisibility(android.view.View.GONE);
                binding.tvAvatarText.setVisibility(android.view.View.VISIBLE);
                binding.tvAvatarText.setText(item.getNickname().isEmpty() ? "友" : item.getNickname().substring(0, 1));
            }
            binding.tvName.setText(item.getNickname());
            binding.tvBio.setText(item.getBio());
            binding.btnPrimary.setText(requestMode ? "接受" : "查看");
            binding.btnSecondary.setText(requestMode ? "忽略" : "更多");
            binding.btnPrimary.setOnClickListener(v -> listener.onPrimaryAction(item));
            binding.btnSecondary.setOnClickListener(v -> listener.onSecondaryAction(item));
        }
    }
}
