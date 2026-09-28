package com.example.healthplan

import android.content.SharedPreferences

/** SharedPreferences в памяти для unit-тестов хранилища (семантика как у Android: копии множеств, apply = commit). */
class FakePrefs : SharedPreferences {
    val map = LinkedHashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = LinkedHashMap(map)
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val puts = LinkedHashMap<String, Any?>(); val removes = mutableSetOf<String>(); var clear = false
        override fun putString(k: String, v: String?) = apply { puts[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { puts[k] = v?.toSet() }
        override fun putInt(k: String, v: Int) = apply { puts[k] = v }
        override fun putLong(k: String, v: Long) = apply { puts[k] = v }
        override fun putFloat(k: String, v: Float) = apply { puts[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { puts[k] = v }
        override fun remove(k: String) = apply { removes += k }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { if (clear) map.clear(); removes.forEach { map.remove(it) }; puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }; return true }
        override fun apply() { commit() }
    }
}
