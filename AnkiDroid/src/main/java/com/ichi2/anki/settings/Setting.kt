package com.ichi2.anki.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import kotlin.jvm.Throws
import kotlin.reflect.KProperty


open class SettingRegistry(
    val context: Context,
    val preferences: SharedPreferences,
) {
    private val keyToSetting = mutableMapOf<String, Setting<*>>()
    private val keyToChangeListeners = mutableMapOf<String, MutableList<() -> Unit>>()

    // Stored in a field as preference manager doesn't keep strong references to listeners
    private val registryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        keyToChangeListeners[key]?.forEach { it() }
    }

    init { preferences.registerOnSharedPreferenceChangeListener(registryListener) }

    @Throws(NoSuchElementException::class) fun getSetting(key: String) = keyToSetting.getValue(key)

    fun Setting<*>.register() {
        keyToSetting[key] = this
    }

    fun Setting<*>.whenChanged(block: () -> Unit) {
        keyToChangeListeners.getOrPut(key, ::mutableListOf).add(block)
    }
}


context(SettingRegistry) abstract class Setting<T>(
    val key: String,
    val default: T,
) {
    init {
        register()

        whenChanged {
            reset()
            Timber.v("Setting %s was changed to %s", key, value)
        }
    }

    @Throws(Exception::class) protected abstract fun retrieve(): T

    protected abstract fun store(value: T)

    @Throws(Exception::class) abstract fun validate(storedValue: Any?)

    @Suppress("UNCHECKED_CAST")
    var value: T = Unit as T
        private set
        get() {
            if (field == Unit) {
                reset()
            }
            return field
        }

    private fun reset() {
        value = try {
            retrieve()
        } catch (e: Exception) {
            Timber.e(e, "Error while retrieving setting %s", key)
            default
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        store(value)
        this.value = value
    }
}


context(SettingRegistry) fun @receiver:StringRes Int.asString() = context.getString(this)


/**************************************** Simple settings *****************************************/


fun SettingRegistry.booleanSetting(@StringRes keyResource: Int, default: Boolean) =
    object : Setting<Boolean>(keyResource.asString(), default) {
        override fun retrieve() = preferences.getBoolean(key, default)
        override fun store(value: Boolean) { preferences.edit { putBoolean(key, value) } }
        override fun validate(storedValue: Any?) { storedValue as Boolean }
    }


fun SettingRegistry.intSetting(@StringRes keyResource: Int, default: Int) =
    object : Setting<Int>(keyResource.asString(), default) {
        override fun retrieve() = preferences.getInt(key, default)
        override fun store(value: Int) { preferences.edit { putInt(key, value) } }
        override fun validate(storedValue: Any?) { storedValue as Int }
    }


fun SettingRegistry.stringSetting(@StringRes keyResource: Int, default: String) =
    object : Setting<String>(keyResource.asString(), default) {
        override fun retrieve() = preferences.getString(key, null) ?: default
        override fun store(value: String) { preferences.edit { putString(key, value) } }
        override fun validate(storedValue: Any?) { storedValue as String }
    }


fun SettingRegistry.stringSetSetting(@StringRes keyResource: Int, default: Set<String>) =
    object : Setting<Set<String>>(keyResource.asString(), default) {
        override fun retrieve() = preferences.getStringSet(key, null) ?: default
        override fun store(value: Set<String>) { preferences.edit { putStringSet(key, value) } }
        override fun validate(storedValue: Any?) { storedValue as Set<*> } // For simplicity
    }


/************************************** Enum-based settings ***************************************/


sealed interface StoredValueOrStoredValueResource

interface StoredValue : StoredValueOrStoredValueResource { val storedValue: String }

interface StoredValueResource : StoredValueOrStoredValueResource { val storedValueResource: Int }


inline fun <reified E> SettingRegistry.computeStringValueToEnumValue():
        Map<String, E> where E : Enum<E>, E : StoredValueOrStoredValueResource =
    enumValues<E>().associateBy { enumValue: StoredValueOrStoredValueResource ->
        when (enumValue) {
            is StoredValue -> enumValue.storedValue
            is StoredValueResource -> enumValue.storedValueResource.asString()
        }
    }


inline fun <reified E> SettingRegistry.enumSetting(@StringRes keyResource: Int, default: E):
        Setting<E> where E : Enum<E>, E : StoredValueOrStoredValueResource =
    object : Setting<E>(keyResource.asString(), default) {
        private val stringValueToEnumValue = computeStringValueToEnumValue<E>()
        private val enumValueToStringValue = stringValueToEnumValue.map { (k, v) -> v to k }.toMap()

        override fun retrieve(): E {
            val storedValue = preferences.getString(key, null) ?: return default
            return stringValueToEnumValue.getValue(storedValue)
        }

        override fun store(value: E) {
            preferences.edit { putString(key, enumValueToStringValue[value]) }
        }

        override fun validate(storedValue: Any?) {
            stringValueToEnumValue.getValue(storedValue as String)
        }
    }


inline fun <reified E> SettingRegistry.enumSetSetting(@StringRes keyResource: Int, default: Set<E>):
        Setting<Set<E>> where E : Enum<E>, E : StoredValueOrStoredValueResource =
    object : Setting<Set<E>>(keyResource.asString(), default) {
        private val stringValueToEnumValue = computeStringValueToEnumValue<E>()
        private val enumValueToStringValue = stringValueToEnumValue.map { (k, v) -> v to k }.toMap()

        override fun retrieve(): Set<E> {
            val storedValue = preferences.getStringSet(key, null) ?: return default
            return storedValue.map { stringValueToEnumValue.getValue(it) }.toSet()
        }

        override fun store(value: Set<E>) {
            preferences.edit { putStringSet(key, value.map { enumValueToStringValue[it] }.toSet()) }
        }

        override fun validate(storedValue: Any?) {
            (storedValue as Set<*>).forEach {
                stringValueToEnumValue.getValue(it as String)
            }
        }
    }


/**************************************** Other settings ******************************************/


fun SettingRegistry.httpUrlSetting(@StringRes keyResource: Int, default: HttpUrl?) =
    object : Setting<HttpUrl?>(keyResource.asString(), default) {
        override fun retrieve(): HttpUrl? {
            val storedValue = preferences.getString(key, null) ?: return default
            return storedValue.toHttpUrl()
        }

        override fun store(value: HttpUrl?) {
            preferences.edit { putString(key, value?.toString()) }
        }

        override fun validate(storedValue: Any?) {
            (storedValue as String?)?.toHttpUrl()
        }
    }
