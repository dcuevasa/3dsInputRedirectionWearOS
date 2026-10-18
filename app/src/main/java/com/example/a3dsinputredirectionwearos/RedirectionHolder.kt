package com.example.a3dsinputredirectionwearos

import android.content.Context

object RedirectionHolder {
    @Volatile
    private var instance: InputRedirectionBridge? = null

    fun get(context: Context): InputRedirectionBridge =
        instance ?: synchronized(this) {
            instance ?: InputRedirectionBridge(context.applicationContext).also { instance = it }
        }
}
