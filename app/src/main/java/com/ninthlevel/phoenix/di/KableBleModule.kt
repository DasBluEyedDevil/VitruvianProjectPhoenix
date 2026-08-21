package com.ninthlevel.phoenix.di

// NOTE: Kable BLE dependencies are disabled pending migration work.
// The KableBleScanner class was deleted but references remained.
// To re-enable Kable support:
// 1. Re-create KableBleScanner.kt
// 2. Uncomment the code below
// 3. Comment out the provideBleRepository binding in AppModule.kt

// import android.content.Context
// import com.ninthlevel.phoenix.data.ble.KableBleScanner
// import com.ninthlevel.phoenix.data.repository.BleRepository
// import com.ninthlevel.phoenix.data.repository.KableBleRepositoryImpl
// import dagger.Module
// import dagger.Provides
// import dagger.hilt.InstallIn
// import dagger.hilt.android.qualifiers.ApplicationContext
// import dagger.hilt.components.SingletonComponent
// import javax.inject.Singleton

// /**
//  * Hilt module for Kable BLE dependencies.
//  *
//  * To switch from Nordic to Kable:
//  * 1. Comment out the provideBleRepository binding in AppModule.kt (lines 742-745)
//  * 2. Uncomment the provideBleRepository binding in this module (lines 30-37)
//  * 3. Rebuild the project
//  *
//  * The KableBleScanner is always provided as it's needed for future migration work.
//  */
// @Module
// @InstallIn(SingletonComponent::class)
// object KableBleModule {
//
//     @Provides
//     @Singleton
//     fun provideKableBleScanner(): KableBleScanner {
//         return KableBleScanner()
//     }
//
//     // Uncomment to use Kable implementation:
//     // @Provides
//     // @Singleton
//     // fun provideBleRepository(
//     //     @ApplicationContext context: Context,
//     //     scanner: KableBleScanner
//     // ): BleRepository {
//     //     return KableBleRepositoryImpl(context, scanner)
//     // }
// }
