package com.dccleaner.app.storage

import android.content.Context
import android.util.Log
import com.dccleaner.app.model.SavedAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREFERENCES_NAME = "saved_accounts_encrypted"
private const val ACCOUNTS_KEY = "accounts"
private val savedAccountListType = object : TypeToken<List<SavedAccount>>() {}.type

@Synchronized
fun getSavedAccounts(context: Context): List<SavedAccount>? = try {
    readSavedAccounts(context)
} catch (error: Exception) {
    // A read failure may be transient. Preserve the encrypted data for a later retry.
    Log.e("SavedAccountStore", "암호화된 계정 정보를 읽지 못했습니다. 기존 데이터는 보존합니다.", error)
    null
}

@Synchronized
fun addSavedAccount(context: Context, account: SavedAccount): List<SavedAccount>? {
    val accounts = getSavedAccounts(context)?.toMutableList() ?: return null
    accounts.removeAll { it.id == account.id }
    accounts.add(0, account)
    return accounts.takeIf { writeSavedAccounts(context, it) }
}

@Synchronized
fun removeSavedAccount(context: Context, accountId: String): List<SavedAccount>? {
    val accounts = getSavedAccounts(context)?.toMutableList() ?: return null
    accounts.removeAll { it.id == accountId }
    return accounts.takeIf { writeSavedAccounts(context, it) }
}

private fun readSavedAccounts(context: Context): List<SavedAccount> {
    val json = encryptedPreferences(context, PREFERENCES_NAME)
        .getString(ACCOUNTS_KEY, "[]")
    return requireNotNull(Gson().fromJson<List<SavedAccount>>(json, savedAccountListType))
}

private fun writeSavedAccounts(context: Context, accounts: List<SavedAccount>): Boolean {
    return try {
        val json = Gson().toJson(accounts)
        val saved = encryptedPreferences(context, PREFERENCES_NAME)
            .edit()
            .putString(ACCOUNTS_KEY, json)
            .commit()
        if (!saved) {
            Log.e("SavedAccountStore", "암호화된 계정 정보를 디스크에 저장하지 못했습니다.")
        }
        saved
    } catch (e: Exception) {
        Log.e("SavedAccountStore", "암호화된 계정 정보 저장 실패", e)
        false
    }
}
