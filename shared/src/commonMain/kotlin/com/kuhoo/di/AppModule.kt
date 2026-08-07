package com.kuhoo.di

import com.kuhoo.db.DatabaseDriverFactory
import com.kuhoo.db.KuhooDatabase
import com.kuhoo.db.MusicRepository
import com.kuhoo.innertube.InnerTubeService
import com.kuhoo.media.AudioPlayer
import com.kuhoo.media.createAudioPlayer
import org.koin.dsl.module

val appModule = module {
    single<AudioPlayer> { createAudioPlayer() }
    single { InnerTubeService() }
    single<DatabaseDriverFactory> { DatabaseDriverFactory() }
    single { KuhooDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { MusicRepository(get()) }
}
