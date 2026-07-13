package com.cz.fitnessdiary.model;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.cz.fitnessdiary.utils.TextUtilsCompat;

public final class SocialPostUiModel {
    private final String postId;
    private final String authorName;
    private final String avatarUrl;
    private final String content;
    private final String timeLabel;
    private final List<String> summaryLines;
    private final int likeCount;
    private final boolean liked;
    private final boolean ownedByCurrentUser;

    public SocialPostUiModel(String postId, String authorName, String avatarUrl, String content, String timeLabel,
                             List<String> summaryLines, int likeCount, boolean liked,
                             boolean ownedByCurrentUser) {
        this.postId = TextUtilsCompat.valueOrDefault(postId, "");
        
        if (TextUtilsCompat.isBlank(authorName) || "null".equalsIgnoreCase(authorName)) {
            this.authorName = "健身伙伴";
        } else {
            this.authorName = authorName;
        }

        if (TextUtilsCompat.isBlank(avatarUrl) || "null".equalsIgnoreCase(avatarUrl)) {
            this.avatarUrl = "";
        } else {
            this.avatarUrl = avatarUrl;
        }
        
        this.content = TextUtilsCompat.valueOrDefault(content, "");
        this.timeLabel = TextUtilsCompat.valueOrDefault(timeLabel, "");
        this.summaryLines = summaryLines == null ? java.util.Collections.emptyList() :
                java.util.Collections.unmodifiableList(new java.util.ArrayList<>(summaryLines));
        this.likeCount = Math.max(0, likeCount);
        this.liked = liked;
        this.ownedByCurrentUser = ownedByCurrentUser;
    }

    public String getPostId() { return postId; }
    public String getAuthorName() { return authorName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getContent() { return content; }
    public String getTimeLabel() { return timeLabel; }
    
    public String getFormattedTime() {
        if (TextUtilsCompat.isBlank(timeLabel)) return "刚刚";
        try {
            ZonedDateTime postTime = ZonedDateTime.parse(timeLabel, DateTimeFormatter.ISO_DATE_TIME);
            ZonedDateTime now = ZonedDateTime.now();
            long minutes = ChronoUnit.MINUTES.between(postTime, now);
            if (minutes < 1) return "刚刚";
            if (minutes < 60) return minutes + "分钟前";
            long hours = ChronoUnit.HOURS.between(postTime, now);
            if (hours < 24) return hours + "小时前";
            long days = ChronoUnit.DAYS.between(postTime, now);
            if (days == 1) return "昨天";
            if (days < 30) return days + "天前";
            return postTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return timeLabel;
        }
    }
    
    public List<String> getSummaryLines() { return summaryLines; }
    public int getLikeCount() { return likeCount; }
    public boolean isLiked() { return liked; }
    public boolean isOwnedByCurrentUser() { return ownedByCurrentUser; }
}
