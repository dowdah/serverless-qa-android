package com.dowdah.asknow.di;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.dowdah.asknow.data.local.AppDatabase;
import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.dao.UserDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * 数据库模块
 * 提供数据库相关的依赖注入（AppDatabase、各种DAO等）
 */
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {
    
    private static final String DATABASE_NAME = "asknow_database";
    
    // 数据库迁移：版本4到版本5
    // 为messages表添加isRead列
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 为 messages 表添加 isRead 列，默认值为 0 (未读)
            database.execSQL("ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0");
        }
    };
    
    // 数据库迁移：版本5到版本6
    // 为messages表添加sendStatus列
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 为 messages 表添加 sendStatus 列，默认值为 'sent' (已发送)
            database.execSQL("ALTER TABLE messages ADD COLUMN sendStatus TEXT NOT NULL DEFAULT 'sent'");
        }
    };
    
    // 数据库迁移：版本7到版本8
    // 删除pending_messages表，因为不再通过WebSocket发送消息
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 删除 pending_messages 表
            database.execSQL("DROP TABLE IF EXISTS pending_messages");
        }
    };
    
    @Provides
    @Singleton
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                context,
                AppDatabase.class,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build();
    }
    
    @Provides
    @Singleton
    public UserDao provideUserDao(AppDatabase database) {
        return database.userDao();
    }
    
    @Provides
    @Singleton
    public QuestionDao provideQuestionDao(AppDatabase database) {
        return database.questionDao();
    }
    
    @Provides
    @Singleton
    public MessageDao provideMessageDao(AppDatabase database) {
        return database.messageDao();
    }
}

