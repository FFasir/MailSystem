package com.mailsystem.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    
    private val TOKEN_KEY = stringPreferencesKey("token")
    /*
    // ========== 修改：username → email（适配后端邮箱登录） ==========
    private val EMAIL_KEY = stringPreferencesKey("email")
    */
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val PASSWORD_KEY = stringPreferencesKey("password")  // 添加密码存储（用于 POP3/SMTP）
    private val ROLE_KEY = stringPreferencesKey("role")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    
    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }
    
    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY]
    }
    
    val password: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PASSWORD_KEY]
    }
    
    val role: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ROLE_KEY]
    }

    val userId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_ID_KEY]
    }
/*
    // ========== 修改：保存用户信息时存储email ==========
    suspend fun saveUserData(token: String, email: String, role: String, userId: Int, password: String = "") {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[EMAIL_KEY] = email  // 存储邮箱而非用户名
            prefs[PASSWORD_KEY] = password
            prefs[ROLE_KEY] = role
            prefs[USER_ID_KEY] = userId.toString()
        }
    }

 */
    suspend fun saveUserData(token: String, username: String, role: String, userId: Int, password: String = "") {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USERNAME_KEY] = username
            prefs[PASSWORD_KEY] = password
            prefs[ROLE_KEY] = role
            prefs[USER_ID_KEY] = userId.toString()
        }
    }
    
    suspend fun clearUserData() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun getToken(): String? {
        var result: String? = null
        context.dataStore.data.map { prefs ->
            result = prefs[TOKEN_KEY]
        }.firstOrNull()  // 修复：补充firstOrNull，避免流未收集
        return result
    }

    suspend fun userIdFirst(): Int? = userId.firstOrNull()?.toIntOrNull()

/*
    // ========== 新增：同步获取邮箱（用于拼接发件人地址） ==========
    suspend fun getEmailFirst(): String? = email.firstOrNull()
    */

}
