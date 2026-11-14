package app.yrp.sypapps

import android.app.Application

/** Just here so Auth0Session is initialised exactly once, before any Activity touches it —
 * avoids every Activity needing its own "have we set this up yet" check. */
class SypApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Auth0Session.init(this)
    }
}
