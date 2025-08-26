/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.LongPreference
import com.example.photoswooper.data.uistates.StringPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreInterface(val dataStore: DataStore<Preferences>) {
    fun getBooleanSettingValue(setting: String): Flow<Boolean> {
        val settingKey = booleanPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey] ?: BooleanPreference.valueOf(setting.uppercase()).default
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
                preferences[settingKey] ?: IntPreference.valueOf(setting.uppercase()).default
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
                preferences[settingKey] ?: LongPreference.valueOf(setting.uppercase()).default
            }
    }
    suspend fun setLongSettingValue(newValue: Long, setting: String) {
        dataStore.edit { settings ->
            val settingKey = longPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }

    suspend fun setStringSettingValue(newValue: String, setting: String) {
        dataStore.edit { settings ->
            val settingKey = stringPreferencesKey(setting)
            settings[settingKey] = newValue
        }
    }
    fun getStringSettingValue(setting: String): Flow<String> {
        val settingKey = stringPreferencesKey(setting)
        return dataStore.data
            .map { preferences ->
                preferences[settingKey] ?: StringPreference.valueOf(setting.uppercase()).default
            }
    }
}