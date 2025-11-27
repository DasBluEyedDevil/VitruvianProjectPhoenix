package com.example.vitruvianredux.di

import android.content.Context
import com.example.vitruvianredux.data.ble.KableBleScanner
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.KableBleRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Kable BLE dependencies.
 *
 * To switch from Nordic to Kable:
 * 1. Comment out the provideBleRepository binding in AppModule.kt (lines 742-745)
 * 2. Uncomment the provideBleRepository binding in this module (lines 30-37)
 * 3. Rebuild the project
 *
 * The KableBleScanner is always provided as it's needed for future migration work.
 */
@Module
@InstallIn(SingletonComponent::class)
object KableBleModule {

    @Provides
    @Singleton
    fun provideKableBleScanner(): KableBleScanner {
        return KableBleScanner()
    }

    // Uncomment to use Kable implementation:
    // @Provides
    // @Singleton
    // fun provideBleRepository(
    //     @ApplicationContext context: Context,
    //     scanner: KableBleScanner
    // ): BleRepository {
    //     return KableBleRepositoryImpl(context, scanner)
    // }
}
