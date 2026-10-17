package com.opendroid.ai.di

import android.content.Context
import androidx.room.Room
import com.opendroid.ai.data.db.Saathi AIDatabase
import com.opendroid.ai.data.db.dao.ChatSessionDao
import com.opendroid.ai.data.db.dao.ConversationDao
import com.opendroid.ai.data.db.dao.MacroDao
import com.opendroid.ai.data.db.dao.MemoryDao
import com.opendroid.ai.data.db.dao.PlanDao
import com.opendroid.ai.data.db.dao.NotificationDao
import com.opendroid.ai.data.db.dao.TaskHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.opendroid.ai.data.db.dao.CrashLogDao
import com.opendroid.ai.data.db.dao.ModelDao
import com.opendroid.ai.data.db.dao.UnknownActionDao
import com.opendroid.ai.data.db.dao.HabitDao
import com.opendroid.ai.data.crash.CrashLogRepository
import com.opendroid.ai.data.crash.RoomCrashLogSink

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Saathi AIDatabase {
        return Room.databaseBuilder(
            context,
            Saathi AIDatabase::class.java,
            "opendroid_database"
        )
        .addMigrations(
            Saathi AIDatabase.MIGRATION_1_2,
            Saathi AIDatabase.MIGRATION_2_3,
            Saathi AIDatabase.MIGRATION_3_4,
            Saathi AIDatabase.MIGRATION_4_5,
            Saathi AIDatabase.MIGRATION_5_6,
            Saathi AIDatabase.MIGRATION_6_7,
            Saathi AIDatabase.MIGRATION_7_8,
            Saathi AIDatabase.MIGRATION_8_9
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(db: Saathi AIDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideChatSessionDao(db: Saathi AIDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    @Singleton
    fun providePlanDao(db: Saathi AIDatabase): PlanDao = db.planDao()

    @Provides
    @Singleton
    fun provideMemoryDao(db: Saathi AIDatabase): MemoryDao = db.memoryDao()

    @Provides
    @Singleton
    fun provideTaskHistoryDao(db: Saathi AIDatabase): TaskHistoryDao = db.taskHistoryDao()

    @Provides
    @Singleton
    fun provideMacroDao(db: Saathi AIDatabase): MacroDao = db.macroDao()

    @Provides
    @Singleton
    fun provideUnknownActionDao(db: Saathi AIDatabase): UnknownActionDao = db.unknownActionDao()

    @Provides
    @Singleton
    fun provideNotificationDao(db: Saathi AIDatabase): NotificationDao = db.notificationDao()

    @Provides
    @Singleton
    fun provideModelDao(db: Saathi AIDatabase): ModelDao = db.modelDao()

    @Provides
    @Singleton
    fun provideCrashLogDao(db: Saathi AIDatabase): CrashLogDao = db.crashLogDao()

    @Provides
    @Singleton
    fun provideHabitDao(db: Saathi AIDatabase): HabitDao = db.habitDao()

    @Provides
    @Singleton
    fun provideCrashLogRepository(dao: CrashLogDao): CrashLogRepository =
        RoomCrashLogSink(dao)

    @Provides
    @Singleton
    fun provideSocialAccountDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialAccountDao =
        db.socialAccountDao()

    @Provides
    @Singleton
    fun provideSocialPostDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialPostDao =
        db.socialPostDao()

    @Provides
    @Singleton
    fun provideSocialCommentDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialCommentDao =
        db.socialCommentDao()

    @Provides
    @Singleton
    fun provideSocialInteractionDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialInteractionDao =
        db.socialInteractionDao()

    @Provides
    @Singleton
    fun provideSocialAnalyticsDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialAnalyticsDao =
        db.socialAnalyticsDao()

    @Provides
    @Singleton
    fun provideSocialCampaignDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialCampaignDao =
        db.socialCampaignDao()

    @Provides
    @Singleton
    fun provideSocialAutomationRuleDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialAutomationRuleDao =
        db.socialAutomationRuleDao()

    @Provides
    @Singleton
    fun provideSocialAuditLogDao(db: Saathi AIDatabase): com.opendroid.ai.data.db.dao.SocialAuditLogDao =
        db.socialAuditLogDao()
}
