package com.example.photoswooper.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.LongPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreInterface(val dataStore: DataStore<Preferences>) {
    fun getBooleanSettingValue(setting: String): Flow<Boolean> {
        val settingKey = booleanPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey] ?: BooleanPreference.valueOf(setting).default
            }
    }
    suspend fun setBooleanSettingValue(newValue: Boolean, setting: String) {
        dataStore.edit { settings ->
            val settingKey = booleanPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }

    fun getIntSettingValue(setting: String): Flow<Int> {
        val settingKey = intPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey] ?: IntPreference.valueOf(setting).default
            }
    }
    suspend fun setIntSettingValue(newValue: Int, setting: String) {
        dataStore.edit { settings ->
            val settingKey = intPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }
    fun getLongSettingValue(setting: String): Flow<Long> {
        val settingKey = longPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey] ?: LongPreference.valueOf(setting).default
            }
    }
    suspend fun setLongSettingValue(newValue: Long, setting: String) {
        dataStore.edit { settings ->
            val settingKey = longPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }
}