package com.github.catvod.crawler

import android.util.Log

object SpiderDebug {
    private const val TAG = "MovieCatSpider"

    @JvmStatic
    fun log(message: String?) {
        Log.d(TAG, message.orEmpty())
    }

    @JvmStatic
    fun log(error: Throwable?) {
        Log.e(TAG, error?.message, error)
    }
}

