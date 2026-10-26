package com.dccleaner.app.desktop

import com.dccleaner.app.model.SavedAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.charset.StandardCharsets

private const val SERVICE = "com.dccleaner.app.desktop"
private const val ACCOUNTS = "saved-accounts"
private const val CAPTCHA_KEY = "2captcha-key"
private const val PROXY_REFRESH_TOKEN = "proxy-refresh-token"
private val accountListType = object : TypeToken<List<SavedAccount>>() {}.type

internal interface CredentialBackend {
    fun read(name: String): String?
    fun write(name: String, value: String)
    fun delete(name: String)
}

internal class DesktopCredentialStore(private val backend: CredentialBackend = platformCredentialBackend()) {
    @Synchronized
    fun getAccounts(): List<SavedAccount> = backend.read(ACCOUNTS)?.let {
        requireNotNull(Gson().fromJson<List<SavedAccount>>(it, accountListType))
    } ?: emptyList()

    @Synchronized
    fun addAccount(account: SavedAccount): List<SavedAccount> = getAccounts().let { existing ->
        val updated = listOf(account) + existing.filterNot { it.id == account.id }
        backend.write(ACCOUNTS, Gson().toJson(updated))
        updated
    }

    @Synchronized
    fun removeAccount(id: String): List<SavedAccount> = getAccounts().filterNot { it.id == id }.also {
        if (it.isEmpty()) backend.delete(ACCOUNTS) else backend.write(ACCOUNTS, Gson().toJson(it))
    }

    fun getTwoCaptchaKey(): String = backend.read(CAPTCHA_KEY).orEmpty()
    fun saveTwoCaptchaKey(key: String) = backend.write(CAPTCHA_KEY, key)
    fun removeTwoCaptchaKey() = backend.delete(CAPTCHA_KEY)

    fun getProxyRefreshToken(): String? = backend.read(PROXY_REFRESH_TOKEN)
    fun saveProxyRefreshToken(token: String) = backend.write(PROXY_REFRESH_TOKEN, token)
}

internal fun credentialStorageSupported(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    return os.contains("mac") || os.contains("win")
}

private fun platformCredentialBackend(): CredentialBackend = when {
    System.getProperty("os.name").lowercase().contains("mac") -> MacKeychainBackend()
    System.getProperty("os.name").lowercase().contains("win") -> WindowsCredentialBackend()
    else -> error("이 운영체제에서는 보안 저장소를 지원하지 않습니다.")
}

private interface SecurityFramework : com.sun.jna.Library {
    fun SecKeychainFindGenericPassword(
        keychain: Pointer?, serviceLength: Int, service: ByteArray,
        accountLength: Int, account: ByteArray, passwordLength: IntByReference?,
        passwordData: PointerByReference?, item: PointerByReference?
    ): Int

    fun SecKeychainAddGenericPassword(
        keychain: Pointer?, serviceLength: Int, service: ByteArray,
        accountLength: Int, account: ByteArray, passwordLength: Int,
        password: ByteArray, item: PointerByReference?
    ): Int

    fun SecKeychainItemModifyAttributesAndData(item: Pointer, attributes: Pointer?, length: Int, data: ByteArray): Int
    fun SecKeychainItemDelete(item: Pointer): Int
    fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer): Int
}

private interface CoreFoundation : com.sun.jna.Library {
    fun CFRelease(value: Pointer)
}

internal class MacKeychainBackend : CredentialBackend {
    private val security = Native.load("Security", SecurityFramework::class.java)
    private val coreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)
    private val service = SERVICE.toByteArray(StandardCharsets.UTF_8)

    override fun read(name: String): String? {
        val account = name.toByteArray(StandardCharsets.UTF_8)
        val length = IntByReference()
        val data = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            null, service.size, service, account.size, account, length, data, null
        )
        if (status == -25300) return null // errSecItemNotFound
        checkStatus(status)
        val pointer = requireNotNull(data.value)
        return try {
            String(pointer.getByteArray(0, length.value), StandardCharsets.UTF_8)
        } finally {
            checkStatus(security.SecKeychainItemFreeContent(null, pointer))
        }
    }

    override fun write(name: String, value: String) {
        val account = name.toByteArray(StandardCharsets.UTF_8)
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            null, service.size, service, account.size, account, null, null, item
        )
        if (status == -25300) {
            checkStatus(security.SecKeychainAddGenericPassword(
                null, service.size, service, account.size, account, bytes.size, bytes, null
            ))
            return
        }
        checkStatus(status)
        val pointer = requireNotNull(item.value)
        try {
            checkStatus(security.SecKeychainItemModifyAttributesAndData(pointer, null, bytes.size, bytes))
        } finally {
            coreFoundation.CFRelease(pointer)
        }
    }

    override fun delete(name: String) {
        val account = name.toByteArray(StandardCharsets.UTF_8)
        val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            null, service.size, service, account.size, account, null, null, item
        )
        if (status == -25300) return
        checkStatus(status)
        val pointer = requireNotNull(item.value)
        try {
            checkStatus(security.SecKeychainItemDelete(pointer))
        } finally {
            coreFoundation.CFRelease(pointer)
        }
    }

    private fun checkStatus(status: Int) {
        check(status == 0) { "macOS 키체인 오류: $status" }
    }
}

@Structure.FieldOrder(
    "flags", "type", "targetName", "comment", "lastWritten", "credentialBlobSize",
    "credentialBlob", "persist", "attributeCount", "attributes", "targetAlias", "userName"
)
internal class WindowsCredential(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var flags = 0
    @JvmField var type = 1 // CRED_TYPE_GENERIC
    @JvmField var targetName: WString? = null
    @JvmField var comment: WString? = null
    @JvmField var lastWritten = 0L
    @JvmField var credentialBlobSize = 0
    @JvmField var credentialBlob: Pointer? = null
    @JvmField var persist = 2 // CRED_PERSIST_LOCAL_MACHINE
    @JvmField var attributeCount = 0
    @JvmField var attributes: Pointer? = null
    @JvmField var targetAlias: WString? = null
    @JvmField var userName: WString? = null
}

private interface WinCred : StdCallLibrary {
    fun CredWriteW(credential: WindowsCredential, flags: Int): Boolean
    fun CredReadW(target: WString, type: Int, flags: Int, result: PointerByReference): Boolean
    fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean
    fun CredFree(buffer: Pointer)
}

internal class WindowsCredentialBackend : CredentialBackend {
    private val api = Native.load("Advapi32", WinCred::class.java)

    override fun read(name: String): String? {
        val result = PointerByReference()
        if (!api.CredReadW(WString("$SERVICE/$name"), 1, 0, result)) {
            if (Native.getLastError() == 1168) return null // ERROR_NOT_FOUND
            fail()
        }
        val pointer = requireNotNull(result.value)
        return try {
            val credential = WindowsCredential(pointer).apply { read() }
            val bytes = credential.credentialBlob?.getByteArray(0, credential.credentialBlobSize)
                ?: ByteArray(0)
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            api.CredFree(pointer)
        }
    }

    override fun write(name: String, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 2560) { "저장할 계정 정보가 Windows 보안 저장소의 크기 제한을 초과했습니다." }
        val blob = Memory(bytes.size.coerceAtLeast(1).toLong())
        try {
            blob.write(0, bytes, 0, bytes.size)
            val credential = WindowsCredential().apply {
                targetName = WString("$SERVICE/$name")
                userName = WString(SERVICE)
                credentialBlobSize = bytes.size
                credentialBlob = blob
            }
            if (!api.CredWriteW(credential, 0)) fail()
        } finally {
            blob.clear()
        }
    }

    override fun delete(name: String) {
        if (!api.CredDeleteW(WString("$SERVICE/$name"), 1, 0) && Native.getLastError() != 1168) fail()
    }

    private fun fail(): Nothing = error("Windows 자격 증명 관리자 오류: ${Native.getLastError()}")
}
