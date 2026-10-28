package com.massomangom.courantdouala

import android.app.Application
import com.massomangom.courantdouala.work.Notifier
import com.massomangom.courantdouala.work.StatusWorker

class CourantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        StatusWorker.schedule(this)
    }
}
