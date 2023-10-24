/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.services

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleCoroutineScope
import com.ichi2.anki.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class SimpleBinder<S : Service>(val service: S) : Binder()

interface ServiceWithASimpleBinder<S : Service> {
    fun onBind(intent: Intent): SimpleBinder<S>
}

/**
 * A service with a [lifecycleScope] that is cancelled when the service is destroyed.
 * It is not an actual [LifecycleCoroutineScope],
 * as a service can't meaningfully be in a started or resumed state.
 */
abstract class ServiceWithALifecycleScope : Service() {
    protected val lifecycleScope = CoroutineScope(Dispatchers.Main)

    @CallSuper
    override fun onDestroy() {
        lifecycleScope.coroutineContext.cancel()
    }
}

/**
 * Bind to a service and run [block],
 * unbinding from the service after the block has finished, or if the coroutine is cancelled.
 *
 *     withBoundTo<MigrationService> { service ->
 *         // Bound to service
 *     }
 *     // Unbound from service
 *
 * Note: the block will not be executed in sync, but *very* soon in practice;
 * if at the same time we post on some view, the posted block will run after this block.
 */
suspend inline fun <reified S> Context.withBoundTo(block: (S) -> Unit)
        where S : Service, S : ServiceWithASimpleBinder<S> {
    lateinit var continuation: Continuation<S>

    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            @Suppress("UNCHECKED_CAST")
            continuation.resume((binder as SimpleBinder<S>).service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    bindService(Intent(this, S::class.java), connection, Context.BIND_AUTO_CREATE)

    val service = suspendCancellableCoroutine { continuation = it }

    try {
        block(service)
    } finally {
        unbindService(connection)
    }
}
