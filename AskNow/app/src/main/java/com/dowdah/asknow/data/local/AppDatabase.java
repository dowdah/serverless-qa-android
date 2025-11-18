package com.dowdah.asknow.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.dao.UserDao;
import com.dowdah.asknow.data.local.entity.MessageEntity;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.data.local.entity.UserEntity;

/**
 * 应用数据库
 * 
 * 版本 8：移除 PendingMessageEntity 和 PendingMessageDao
 * 原因：不再通过 WebSocket 发送消息，改用 HTTP API
 */
@Database(
    entities = {
        UserEntity.class,
        QuestionEntity.class,
        MessageEntity.class
    },
    version = 8,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract QuestionDao questionDao();
    public abstract MessageDao messageDao();
}

