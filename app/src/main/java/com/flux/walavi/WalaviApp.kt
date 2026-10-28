package com.flux.walavi

import android.app.Application
import com.flux.walavi.data.WalaviStore
import com.flux.walavi.gateway.GatewayController

class WalaviApp : Application() {

    lateinit var store: WalaviStore
        private set

    lateinit var controller: GatewayController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = WalaviStore(this)
        controller = GatewayController(this, store)
    }

    companion object {
        lateinit var instance: WalaviApp
            private set
    }
}
