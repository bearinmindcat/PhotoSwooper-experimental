package com.example.photoswooper.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreInterface(val dataStore: DataStore<Preferences>) {
    fun getBooleanSettingValue(setting: String): Flow<Boolean?> {
        val settingKey = booleanPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey]
            }
    }
    suspend fun setBooleanSettingValue(newValue: Boolean, setting: String) {
        dataStore.edit { settings ->
            val settingKey = booleanPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }

    fun getIntSettingValue(setting: String): Flow<Int?> {
        val settingKey = intPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey]
            }
    }
    suspend fun setIntSettingValue(newValue: Int, setting: String) {
        dataStore.edit { settings ->
            val settingKey = intPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }
}