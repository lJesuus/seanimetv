package com.seanime.tv.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("seanime_prefs", Context.MODE_PRIVATE)

    fun saveServerUrl(url: String) {
        sharedPreferences.edit().putString("server_url", url).apply()
    }

    fun getServerUrl(): String? {
        return sharedPreferences.getString("server_url", null)
    }

    fun clearServerUrl() {
        sharedPreferences.edit().remove("server_url").apply()
    }
}
