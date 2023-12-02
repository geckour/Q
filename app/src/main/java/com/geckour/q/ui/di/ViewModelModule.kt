package com.geckour.q.ui.di

import com.geckour.q.App
import com.geckour.q.ui.easteregg.EasterEggViewModel
import com.geckour.q.ui.equalizer.EqualizerViewModel
import com.geckour.q.ui.instant.InstantPlayerViewModel
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.setting.SettingViewModel
import com.geckour.q.ui.sheet.BottomSheetViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        MainViewModel(app = androidApplication() as App, sharedPreferences = get())
    }
    viewModel {
        EasterEggViewModel(db = get())
    }
    viewModel {
        EqualizerViewModel()
    }
    viewModel {
        InstantPlayerViewModel(app = androidApplication() as App)
    }
    viewModel {
        SettingViewModel()
    }
    viewModel {
        BottomSheetViewModel(sharedPreferences = get())
    }
}