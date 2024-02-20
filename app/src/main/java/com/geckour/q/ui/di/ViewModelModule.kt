package com.geckour.q.ui.di

import com.geckour.q.App
import com.geckour.q.ui.instant.InstantPlayerViewModel
import com.geckour.q.ui.main.MainViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        MainViewModel(app = androidApplication() as App)
    }
    viewModel {
        InstantPlayerViewModel()
    }
}