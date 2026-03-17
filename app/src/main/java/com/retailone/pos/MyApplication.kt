package com.retailone.pos

import android.app.Application
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.utils.FeatureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            val modules = LoginSession.getInstance(this@MyApplication).getModules().first()
            FeatureManager.init(modules)
        }
    }
}
