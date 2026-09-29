package com.example.x.data.remote

import android.content.Context
import com.example.x.data.remote.ApiConfig.XIAIS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.parser.Parser
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class KemsuApi(private val appContext: Context? = null) {

    companion object {
        private val sharedCookieManager = CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }
    }

    private val cp1251: Charset = Charset.forName("windows-1251")
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/108.0.5359.125 Safari/537.36"

    private val cookieManager = sharedCookieManager
    private val prefs by lazy {
        appContext?.let { com.example.x.data.local.PrefsManager(it) }
    }

    val client: OkHttpClient = run {
        val (sf, tm) = createLenientSsl()
        OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .sslSocketFactory(sf, tm)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Сертификат xiais.kemsu.ru просрочен — стандартный TrustManager режет все запросы.
     * Терпим ТОЛЬКО просрочку: цепочка и hostname проверяются как обычно,
     * принимаем цепь лишь если причина отказа — CertificateExpiredException
     * (или CertPathValidatorException.EXPIRED). Остальное (самоподпись,
     * чужой CN, битая цепь) по-прежнему отклоняется.
     */
    private fun createLenientSsl(): Pair<SSLSocketFactory, X509TrustManager> {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        val defaultTm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        val lenient = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) =
                defaultTm.checkClientTrusted(chain, authType)

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                defaultTm.acceptedIssuers

            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                try {
                    defaultTm.checkServerTrusted(chain, authType)
                } catch (e: java.security.cert.CertificateException) {
                    var cause: Throwable? = e
                    var expired = false
                    while (cause != null && !expired) {
                        expired = cause is java.security.cert.CertificateExpiredException ||
                                (cause is java.security.cert.CertPathValidatorException &&
                                        cause.reason == java.security.cert.CertPathValidatorException.BasicReason.EXPIRED)
                        cause = cause.cause
                    }
                    if (!expired) throw e
                }
            }
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(lenient), java.security.SecureRandom())
        return ctx.socketFactory to lenient
    }

    /**
     * Удаляет дубликаты cookies с одинаковыми name/domain/path.
     * JavaNetCookieJar может накапливать несколько экземпляров одной cookie.
     */
    private fun dedupeCookies() {
        val store = cookieManager.cookieStore
        val seen = mutableSetOf<String>()
        val uri = URI("https://xiais.kemsu.ru/")

        store.cookies.reversed().forEach { cookie ->
            val key = "${cookie.name}|${cookie.domain}|${cookie.path}"
            if (!seen.add(key)) {
                store.remove(uri, cookie)
            }
        }
    }

    /**
     * Синхронизирует сохранённые токены приложения с CookieStore перед запросами
     * к старому API КемГУ.
     */
    private fun syncAuthCookies() {
        val preferences = prefs ?: return
        val accessToken = preferences.getAccessToken()
        val refreshToken = preferences.getRefreshToken()

        if (accessToken.isNullOrBlank() && refreshToken.isNullOrBlank()) {
            return
        }

        try {
            val uri = URI("https://xiais.kemsu.ru/")
            val store = cookieManager.cookieStore

            store.cookies
                .filter {
                    it.name == "accessToken" ||
                            it.name == "refreshToken" ||
                            it.name == "password"
                }
                .forEach { store.remove(uri, it) }

            fun addCookie(name: String, value: String) {
                val cookie = HttpCookie(name, value).apply {
                    domain = ".kemsu.ru"
                    path = "/"
                }
                store.add(uri, cookie)
            }

            if (!accessToken.isNullOrBlank()) {
                addCookie("accessToken", accessToken)
            }

            if (!refreshToken.isNullOrBlank()) {
                addCookie("refreshToken", refreshToken)
            }

            addCookie("password", "")
            dedupeCookies()
        } catch (_: Exception) {
        }
    }

    private fun responseBodyAsCp1251(response: okhttp3.Response): String {
        val bytes = response.body?.bytes() ?: return ""
        val raw = String(bytes, cp1251)

        return try {
            Parser.unescapeEntities(raw, false)
        } catch (_: Exception) {
            raw
        }
    }

    suspend fun loginJson(login: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val preForm = FormBody.Builder()
                    .add("login", login)
                    .add("password", password)
                    .build()

                val preRequest = Request.Builder()
                    .url("https://xiais.kemsu.ru/dekanat/restricted/index_next.htm")
                    .header("User-Agent", userAgent)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Origin", "https://xiais.kemsu.ru")
                    .header(
                        "Referer",
                        "https://xiais.kemsu.ru/dekanat/eios-next-bridge/auth.htm"
                    )
                    .post(preForm)
                    .build()

                client.newCall(preRequest).execute().close()

                val json = JSONObject().apply {
                    put("login", login)
                    put("password", password)
                    put("lifetime", "3m")
                }.toString()

                val requestBody = json.toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

                val request = Request.Builder()
                    .url(ApiConfig.LOGIN_URL)
                    .header("User-Agent", userAgent)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("origin", "https://eios.kemsu.ru")
                    .header("referer", "https://eios.kemsu.ru/")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: $text")
                        )
                    }

                    Result.success(text)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchLoginPage(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$XIAIS/proc/")
            .header("User-Agent", userAgent)
            .build()

        client.newCall(request).execute().use(::responseBodyAsCp1251)
    }

    suspend fun login(username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val page = fetchLoginPage()
            val (action, defaults) =
                KemsuParser.parseLoginForm(page) ?: ("/" to emptyMap())

            val form = FormBody.Builder()
            defaults.forEach { (key, value) -> form.add(key, value) }
            form.add("login", username)
            form.add("password", password)

            val postUrl = if (action.startsWith("http")) {
                action
            } else {
                "$XIAIS/proc/$action"
                    .replace("//", "/")
                    .replace("https:/", "https://")
            }

            val finalUrl = if (postUrl.contains("://")) postUrl else "$XIAIS/proc/"

            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "$XIAIS/proc/")
                .post(form.build())
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        }

    /**
     * Открывает bridge-страницу, чтобы сервер установил необходимые session cookies.
     */
    suspend fun fetchEiosNextBridge(): Boolean = withContext(Dispatchers.IO) {
        syncAuthCookies()

        if (prefs?.getAccessToken().isNullOrBlank()) {
            return@withContext false
        }

        val request = Request.Builder()
            .url("https://xiais.kemsu.ru/dekanat/eios-next-bridge/auth.htm")
            .header("User-Agent", userAgent)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Повторно проходит старый dekanat bridge с сохранёнными логином и паролем.
     * Нужен перед запросами, которым требуется авторизованная session.
     */
    suspend fun ensureDekanatBridge(): Boolean = withContext(Dispatchers.IO) {
        val preferences = prefs ?: return@withContext false
        val login = preferences.getLogin()
        val password = preferences.getPassword()

        if (login.isBlank() || password.isBlank()) {
            return@withContext false
        }

        syncAuthCookies()

        val form = FormBody.Builder()
            .add("login", login)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("https://xiais.kemsu.ru/dekanat/restricted/index_next.htm")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "https://xiais.kemsu.ru")
            .header(
                "Referer",
                "https://xiais.kemsu.ru/dekanat/eios-next-bridge/auth.htm"
            )
            .post(form)
            .build()

        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Получает список дисциплин. studyYear пустой — сервер возвращает все годы;
     * фильтрация выполняется на стороне приложения.
     */
    suspend fun fetchDisciplinesHtml(studyYear: String = ""): String =
        withContext(Dispatchers.IO) {
            fetchEiosNextBridge()
            ensureDekanatBridge()
            syncAuthCookies()

            val form = FormBody.Builder()
                .add("studyYear", studyYear)
                .build()

            val request = Request.Builder()
                .url("$XIAIS/proc/stud/index.shtm")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://xiais.kemsu.ru")
                .header(
                    "Referer",
                    "https://xiais.kemsu.ru/proc/stud/" +
                            "?backToNewEios=https://eios.kemsu.ru/main/personal-area"
                )
                .post(form)
                .build()

            // Предварительный GET устанавливает session state, необходимый старому endpoint.
            try {
                val preRequest = Request.Builder()
                    .url(
                        "$XIAIS/proc/stud/?" +
                                "backToNewEios=https://eios.kemsu.ru/main/personal-area"
                    )
                    .header("User-Agent", userAgent)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9," +
                                "image/avif,image/webp,image/apng,*/*;q=0.8"
                    )
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Referer", "https://xiais.kemsu.ru/")
                    .get()
                    .build()

                client.newCall(preRequest).execute().close()
            } catch (_: Exception) {
                // POST ниже может быть достаточен даже без предварительного GET.
            }

            client.newCall(request).execute().use {
                dedupeCookies()
                val txt = responseBodyAsCp1251(it)
                txt
            }
        }

    suspend fun fetchTasks(
        cId: String
    ): String = withContext(Dispatchers.IO) {
        syncAuthCookies()
        dedupeCookies()

        val form = FormBody.Builder()
            .add("c_id", cId)
            .build()

        val request = Request.Builder()
            .url("$XIAIS/proc/stud/course_st/tasks_st.htm")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "https://xiais.kemsu.ru")
            .header(
                "Referer",
                "https://xiais.kemsu.ru/proc/stud/course_st/index.htm"
            )
            .post(form)
            .build()

        client.newCall(request).execute().use {
            val txt = responseBodyAsCp1251(it)
            txt
        }
    }

    fun clearCookies() {
        cookieManager.cookieStore.removeAll()
    }
}