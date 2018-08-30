package com.geckour.q.util

import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

val glideNoCacheOption =
        RequestOptions().diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .signature { System.currentTimeMillis().toString() }