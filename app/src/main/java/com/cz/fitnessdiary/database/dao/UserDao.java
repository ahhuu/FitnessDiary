package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.User;

/**
 * 用户数据访问对象
 * 提供用户信息的增删改查操作
 */
@Dao
public interface UserDao {

    /**
     * 插入新用户
     */
    @Insert
    void insert(User user);

    /**
     * 更新用户信息
     */
    @Update
    void update(User user);

    /**
     * 获取第一个用户（用于检查是否已注册）
     * 使用 LiveData 实现数据变化的自动更新
     */
    @Query("SELECT * FROM user LIMIT 1")
    LiveData<User> getUser();
    
    /**
     * 同步获取用户（用于后台线程）
     */
    @Query("SELECT * FROM user LIMIT 1")
    User getUserSync();


    /**
     * 检查是否有注册用户
     */
    @Query("SELECT COUNT(*) FROM user WHERE is_registered = 1")
    int getRegisteredUserCount();
}
