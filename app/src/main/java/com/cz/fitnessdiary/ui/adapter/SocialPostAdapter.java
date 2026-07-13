package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.ItemSocialPostBinding;
import com.cz.fitnessdiary.model.SocialPostUiModel;
import com.cz.fitnessdiary.utils.TextUtilsCompat;

import java.util.ArrayList;
import java.util.List;

public final class SocialPostAdapter extends RecyclerView.Adapter<SocialPostAdapter.Holder> {
    public interface Listener {
        void onLike(SocialPostUiModel post);
        void onMore(SocialPostUiModel post);
    }

    private final Listener listener;
    private List<SocialPostUiModel> items = new ArrayList<>();

    public SocialPostAdapter(Listener listener) { this.listener = listener; }

    public void submitList(List<SocialPostUiModel> value) {
        items = value == null ? new ArrayList<>() : new ArrayList<>(value);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSocialPostBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) { holder.bind(items.get(position)); }

    @Override
    public int getItemCount() { return items.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemSocialPostBinding binding;
        Holder(ItemSocialPostBinding binding) { super(binding.getRoot()); this.binding = binding; }

        void bind(SocialPostUiModel item) {
            String authorName = item.getAuthorName();
            binding.tvAuthor.setText(authorName);
            binding.tvTime.setText(item.getFormattedTime());
            
            if (!TextUtilsCompat.isBlank(item.getAvatarUrl())) {
                binding.tvAvatarText.setVisibility(View.GONE);
                binding.ivAvatar.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                     .load(item.getAvatarUrl())
                     .placeholder(R.drawable.bg_circle_light_primary)
                     .into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setVisibility(View.GONE);
                binding.tvAvatarText.setVisibility(View.VISIBLE);
                binding.tvAvatarText.setText(authorName.isEmpty() ? "友" : authorName.substring(0, 1));
            }

            binding.tvContent.setText(item.getContent());
            binding.tvContent.setVisibility(TextUtilsCompat.isBlank(item.getContent()) ? View.GONE : View.VISIBLE);

            String summary = String.join("\n", item.getSummaryLines());
            binding.tvSummary.setText(summary);
            binding.tvSummary.setVisibility(TextUtilsCompat.isBlank(summary) ? View.GONE : View.VISIBLE);
            
            binding.btnLike.setText(String.valueOf(item.getLikeCount()));
            binding.btnLike.setIconResource(item.isLiked() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            binding.btnLike.setIconTintResource(item.isLiked() ? R.color.error : R.color.text_secondary);
            binding.btnLike.setEnabled(!item.isOwnedByCurrentUser());
            binding.btnLike.setOnClickListener(v -> listener.onLike(item));
            binding.btnMore.setOnClickListener(v -> listener.onMore(item));
        }
    }
}
