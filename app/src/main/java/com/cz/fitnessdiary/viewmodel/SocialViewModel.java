package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.model.FriendUiModel;
import com.cz.fitnessdiary.model.SocialPostUiModel;
import com.cz.fitnessdiary.repository.SocialRepository;
import com.cz.fitnessdiary.utils.TextUtilsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SocialViewModel extends AndroidViewModel {
    private final SocialRepository repository;
    private final MutableLiveData<List<FriendUiModel>> friends =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<FriendUiModel>> requests =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<SocialPostUiModel>> posts =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FriendUiModel> searchResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public SocialViewModel(@NonNull Application application) {
        super(application);
        repository = new SocialRepository(application);
    }

    public LiveData<List<FriendUiModel>> getFriends() { return friends; }
    public LiveData<List<FriendUiModel>> getRequests() { return requests; }
    public LiveData<List<SocialPostUiModel>> getPosts() { return posts; }
    public LiveData<FriendUiModel> getSearchResult() { return searchResult; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getMessage() { return message; }

    public void searchFriend(String code) {
        if (code == null || !code.trim().toUpperCase(Locale.ROOT).matches("[A-Z0-9]{6,12}")) {
            message.postValue("请输入 6–12 位好友码");
            return;
        }
        loading.postValue(true);
        repository.searchFriend(code, callback(value -> {
            searchResult.postValue(value);
            if (TextUtilsCompat.isBlank(value.getUserId())) message.postValue("没有找到该用户");
        }));
    }

    public void sendRequest(String friendCode) {
        loading.postValue(true);
        repository.sendFriendRequest(friendCode, callback(ignored -> message.postValue("好友申请已发送")));
    }

    public void loadFriends() {
        loading.postValue(true);
        repository.loadFriends(callback(friends::postValue));
    }

    public void loadRequests() {
        loading.postValue(true);
        repository.loadRequests(callback(requests::postValue));
    }

    public void respond(String requestId, boolean accept) {
        loading.postValue(true);
        repository.respondToRequest(requestId, accept, callback(ignored -> {
            message.postValue(accept ? "已添加为好友" : "已拒绝申请");
            loadRequests();
        }));
    }

    public void loadFeed() {
        loading.postValue(true);
        repository.loadFeed(null, callback(posts::postValue));
    }

    public void setLiked(SocialPostUiModel post) {
        boolean desired = !post.isLiked();
        repository.setLiked(post.getPostId(), desired, callback(ignored -> {
            List<SocialPostUiModel> current = posts.getValue();
            if (current == null) return;
            List<SocialPostUiModel> updated = new ArrayList<>();
            for (SocialPostUiModel item : current) {
                if (item.getPostId().equals(post.getPostId())) {
                    int count = Math.max(0, item.getLikeCount() + (desired ? 1 : -1));
                    updated.add(new SocialPostUiModel(item.getPostId(), item.getAuthorName(),
                            item.getAvatarUrl(), item.getContent(), item.getTimeLabel(),
                            item.getSummaryLines(), count, desired, item.isOwnedByCurrentUser()));
                } else {
                    updated.add(item);
                }
            }
            posts.postValue(updated);
        }));
    }

    public void createPost(String text, Map<String, Object> summary) {
        Map<String, Object> safeSummary = summary == null ? Collections.emptyMap() : summary;
        if (TextUtilsCompat.isBlank(text) && safeSummary.isEmpty()) {
            message.postValue("请填写动态内容或选择健康摘要");
            return;
        }
        if (text != null && text.length() > 500) {
            message.postValue("动态文字不能超过 500 字");
            return;
        }
        loading.postValue(true);
        repository.createPost(text == null ? "" : text, safeSummary,
                callback(ignored -> message.postValue("动态发布成功")));
    }

    public void deletePost(String postId) {
        repository.deletePost(postId, callback(ignored -> {
            message.postValue("动态已删除");
            loadFeed();
        }));
    }

    public void reportPost(String postId) {
        repository.reportContent("post", postId, "inappropriate",
                callback(ignored -> message.postValue("举报已提交，我们会尽快处理")));
    }

    public void removeFriend(String userId) {
        loading.postValue(true);
        repository.removeFriend(userId, callback(ignored -> {
            message.postValue("已删除好友");
            loadFriends();
        }));
    }

    public void blockUser(String userId) {
        loading.postValue(true);
        repository.blockUser(userId, callback(ignored -> {
            message.postValue("已拉黑该用户");
            loadFriends();
        }));
    }

    private <T> SocialRepository.Callback<T> callback(Success<T> success) {
        return new SocialRepository.Callback<T>() {
            @Override
            public void onSuccess(T value) {
                loading.postValue(false);
                success.accept(value);
            }

            @Override
            public void onError(Throwable error) {
                loading.postValue(false);
                String text = error.getMessage();
                message.postValue(TextUtilsCompat.isBlank(text)
                        ? "网络开小差了，请稍后重试" : text);
            }
        };
    }

    private interface Success<T> {
        void accept(T value);
    }
}
