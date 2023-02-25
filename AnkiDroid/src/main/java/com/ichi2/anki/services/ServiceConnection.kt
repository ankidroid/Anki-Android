/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.services

import android.app.Service
import android.content.*
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Simplifies the interface for binding to a running service
 * Access the service via [instance].
 *
 *
 * @param T The service to encapsulate. Note: service's [Service.onBind] must return a [SimpleBinder]
 */
open class ServiceConnection<T : Service> {
    var instance: T? = null
        private set

    /** Whether [bind] was called and succeeded */
    private var serviceBound = false

    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (service !is SimpleBinder<*>) {
                throw IllegalStateException("$name must return a binder implementing SimpleBinder")
            }
            val binder = service as SimpleBinder<*>
            Timber.d("%s connected", name.shortClassName)
            @Suppress("UNCHECKED_CAST")
            this@ServiceConnection.instance = (binder.getService() as T).also {
                onServiceConnected(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("%s disconnected", name.shortClassName)
            instance?.let {
                onServiceDisconnected(it)
                instance = null
            }
        }
    }

    fun startForeground(context: Context, clazz: Class<T>) {
        ContextCompat.startForegroundService(context, Intent(context, clazz))
        bind(context, clazz)
    }

    fun unbind(context: Context) {
        instance?.let { onServiceDisconnected(it) }
        if (serviceBound) {
            context.unbindService(connection)
        }
        serviceBound = false
    }

    /**
     * @param flags @see `Context.BindServiceFlags`
     */
    fun bind(context: Context, clazz: Class<T>, flags: Int = Context.BIND_AUTO_CREATE) {
        Intent(context, clazz).also { intent ->
            context.bindService(intent, connection, flags)
            serviceBound = true
        }
    }

    open fun onServiceConnected(service: T) {}
    open fun onServiceDisconnected(service: T) {}
}

/**
 * Marker interface to allow a [Service] to be usable with [ServiceConnection]
 */
interface SimpleBinder<T : Service> {
    fun getService(): T
}
