package com.zrx.cloud

import android.app.Application
import com.zrx.cloud.data.Session

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}
