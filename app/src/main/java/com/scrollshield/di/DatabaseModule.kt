package com.scrollshield.di

import android.content.Context
import androidx.room.Room
import com.scrollshield.data.db.ProfileDao
import com.scrollshield.data.db.ScrollShieldDatabase
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.db.SignatureDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScrollShieldDatabase(
        @ApplicationContext context: Context
    ): ScrollShieldDatabase {
        // Optional SQLCipher support — OFF by default.
        // To enable:
        //   1. Add to build.gradle.kts:
        //        implementation("net.zetetic:android-database-sqlcipher:4.5.4")
        //        implementation("androidx.sqlite:sqlite-ktx:2.4.0")
        //   2. Uncomment:
        // val passphrase: ByteArray = SQLiteDatabase.getBytes("your-passphrase".toCharArray())
        // val factory = SupportFactory(passphrase)
        // return Room.databaseBuilder(context, ScrollShieldDatabase::class.java, "scrollshield.db")
        //     .openHelperFactory(factory)
        //     .fallbackToDestructiveMigration()
        //     .build()

        return Room.databaseBuilder(
            context,
            ScrollShieldDatabase::class.java,
            "scrollshield.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(database: ScrollShieldDatabase): ProfileDao =
        database.profileDao()

    @Provides
    fun provideSessionDao(database: ScrollShieldDatabase): SessionDao =
        database.sessionDao()

    @Provides
    fun provideSignatureDao(database: ScrollShieldDatabase): SignatureDao =
        database.signatureDao()
}
