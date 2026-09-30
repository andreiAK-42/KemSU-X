package com.example.x.data.remote

import android.content.Context
import com.example.x.data.local.PrefsManager
import com.example.x.data.remote.ApiConfig.XIAIS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.parser.Parser
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.nio.charset.Charset
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class KemsuApi(private val appContext: Context? = null) {

    companion object {
        private const val BASE_URL = "https://xiais.kemsu.ru"
        private const val EIOS_URL = "https://eios.kemsu.ru"
        private const val API_NEXT_URL = "https://api-next.kemsu.ru"

        private const val BRIDGE_URL = "$BASE_URL/dekanat/eios-next-bridge/auth.htm"
        private const val LOGIN_URL = "$BASE_URL/dekanat/restricted/index_next.htm"
        private const val DISCIPLINES_URL = "$BASE_URL/proc/stud/index.shtm"
        private const val DISCIPLINES_PAGE_URL =
            "$BASE_URL/proc/stud/?backToNewEios=$EIOS_URL/main/personal-area"
        private const val TASKS_URL = "$BASE_URL/proc/stud/course_st/tasks_st.htm"
        private const val TASKS_REFERER = "$BASE_URL/proc/stud/course_st/index.htm"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/108.0.5359.125 Safari/537.36"

        private const val COOKIE_DOMAIN = ".kemsu.ru"
        private const val COOKIE_URI = "https://xiais.kemsu.ru/"
        private const val ACCESS_TOKEN_COOKIE = "accessToken"
        private const val REFRESH_TOKEN_COOKIE = "refreshToken"
        private const val PASSWORD_COOKIE = "password"

        private val sharedCookieManager = CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }
    }

    private val cp1251: Charset = Charset.forName("windows-1251")

    private val cookieManager = sharedCookieManager

    private val prefs by lazy {
        appContext?.let(::PrefsManager)
    }

    val client: OkHttpClient = createHttpClient()

    /**
     * Сертификат xiais.kemsu.ru просрочен.
     *
     * Принимаем только просроченную цепочку сертификатов.
     * Ошибки самоподписанного сертификата, неверного hostname
     * или повреждённой цепочки по-прежнему отклоняются.
     */
    private fun createLenientSsl(): Pair<SSLSocketFactory, X509TrustManager> {
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        trustManagerFactory.init(null as java.security.KeyStore?)

        val defaultTrustManager =
            trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .first()

        val lenientTrustManager = object : X509TrustManager {

            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                defaultTrustManager.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                try {
                    defaultTrustManager.checkServerTrusted(chain, authType)
                } catch (exception: CertificateException) {
                    if (!isExpiredCertificate(exception)) {
                        throw exception
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return defaultTrustManager.acceptedIssuers
            }
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            null,
            arrayOf<TrustManager>(lenientTrustManager),
            java.security.SecureRandom()
        )

        return sslContext.socketFactory to lenientTrustManager
    }

    private fun isExpiredCertificate(exception: Throwable): Boolean {
        var cause: Throwable? = exception

        while (cause != null) {
            if (
                cause is CertificateExpiredException ||
                (
                        cause is CertPathValidatorException &&
                                cause.reason == CertPathValidatorException.BasicReason.EXPIRED
                        )
            ) {
                return true
            }

            cause = cause.cause
        }

        return false
    }

    private fun createHttpClient(): OkHttpClient {
        val (sslSocketFactory, trustManager) = createLenientSsl()

        return OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .sslSocketFactory(sslSocketFactory, trustManager)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Удаляет дубликаты cookies с одинаковыми name/domain/path.
     */
    private fun dedupeCookies() {
        val store = cookieManager.cookieStore
        val seen = mutableSetOf<String>()
        val uri = URI(COOKIE_URI)

        store.cookies
            .reversed()
            .forEach { cookie ->
                val key = "${cookie.name}|${cookie.domain}|${cookie.path}"

                if (!seen.add(key)) {
                    store.remove(uri, cookie)
                }
            }
    }

    /**
     * Синхронизирует сохранённые токены приложения с CookieStore.
     */
    private fun syncAuthCookies() {
        val preferences = prefs ?: return
        val accessToken = preferences.getAccessToken()
        val refreshToken = preferences.getRefreshToken()

        if (accessToken.isNullOrBlank() && refreshToken.isNullOrBlank()) {
            return
        }

        try {
            val uri = URI(COOKIE_URI)
            val store = cookieManager.cookieStore

            removeAuthCookies(store, uri)
            addAuthCookie(store, uri, ACCESS_TOKEN_COOKIE, accessToken)
            addAuthCookie(store, uri, REFRESH_TOKEN_COOKIE, refreshToken)

            addCookie(store, uri, PASSWORD_COOKIE, "")
            dedupeCookies()
        } catch (_: Exception) {
            // CookieStore не должен ломать основной сетевой запрос.
        }
    }

    private fun removeAuthCookies(
        store: java.net.CookieStore,
        uri: URI
    ) {
        store.cookies
            .filter {
                it.name == ACCESS_TOKEN_COOKIE ||
                        it.name == REFRESH_TOKEN_COOKIE ||
                        it.name == PASSWORD_COOKIE
            }
            .forEach { store.remove(uri, it) }
    }

    private fun addAuthCookie(
        store: java.net.CookieStore,
        uri: URI,
        name: String,
        value: String?
    ) {
        if (!value.isNullOrBlank()) {
            addCookie(store, uri, name, value)
        }
    }

    private fun addCookie(
        store: java.net.CookieStore,
        uri: URI,
        name: String,
        value: String
    ) {
        val cookie = HttpCookie(name, value).apply {
            domain = COOKIE_DOMAIN
            path = "/"
        }

        store.add(uri, cookie)
    }

    private fun responseBodyAsCp1251(response: Response): String {
        val bytes = response.body?.bytes() ?: return ""
        val raw = String(bytes, cp1251)

        return try {
            Parser.unescapeEntities(raw, false)
        } catch (_: Exception) {
            raw
        }
    }

    suspend fun loginJson(
        login: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            performJsonLogin(login, password)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun performJsonLogin(
        login: String,
        password: String
    ): Result<String> {
        performLegacyPreLogin(login, password)

        val request = buildJsonLoginRequest(login, password)

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@use Result.failure(
                    Exception("HTTP ${response.code}: $text")
                )
            }

            Result.success(text)
        }
    }

    private fun performLegacyPreLogin(
        login: String,
        password: String
    ) {
        val form = FormBody.Builder()
            .add("login", login)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(LOGIN_URL)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", BASE_URL)
            .header("Referer", BRIDGE_URL)
            .post(form)
            .build()

        client.newCall(request).execute().close()
    }

    private fun buildJsonLoginRequest(
        login: String,
        password: String
    ): Request {
        val json = JSONObject()
            .put("login", login)
            .put("password", password)
            .put("lifetime", "3m")
            .toString()

        val body = json.toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )

        return Request.Builder()
            .url(ApiConfig.LOGIN_URL)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/plain, */*")
            .header("origin", EIOS_URL)
            .header("referer", "$EIOS_URL/")
            .post(body)
            .build()
    }

    suspend fun fetchLoginPage(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$XIAIS/proc/")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use(::responseBodyAsCp1251)
    }

    suspend fun login(
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val page = fetchLoginPage()
        val (action, defaults) =
            KemsuParser.parseLoginForm(page) ?: ("/" to emptyMap())

        val form = FormBody.Builder()

        defaults.forEach { (key, value) ->
            form.add(key, value)
        }

        form
            .add("login", username)
            .add("password", password)

        val finalUrl = buildLegacyLoginUrl(action)
        val request = buildLegacyLoginRequest(finalUrl, form.build())

        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    private fun buildLegacyLoginUrl(action: String): String {
        val postUrl = if (action.startsWith("http")) {
            action
        } else {
            "$XIAIS/proc/$action"
                .replace("//", "/")
                .replace("https:/", "https://")
        }

        return if (postUrl.contains("://")) {
            postUrl
        } else {
            "$XIAIS/proc/"
        }
    }

    private fun buildLegacyLoginRequest(
        url: String,
        form: FormBody
    ): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", "$XIAIS/proc/")
            .post(form)
            .build()
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
            .url(BRIDGE_URL)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        executeSuccessfully(request)
    }

    /**
     * Повторно проходит старый dekanat bridge с сохранёнными логином и паролем.
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
            .url(LOGIN_URL)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", BASE_URL)
            .header("Referer", BRIDGE_URL)
            .post(form)
            .build()

        executeSuccessfully(request)
    }

    suspend fun fetchDisciplinesHtml(
        studyYear: String = ""
    ): String = withContext(Dispatchers.IO) {
        prepareLegacySession()

        val request = buildDisciplinesRequest(studyYear)

        performDisciplinesPreRequest()

        client.newCall(request).execute().use { response ->
            dedupeCookies()
            responseBodyAsCp1251(response)
        }
    }

    private suspend fun prepareLegacySession() {
        fetchEiosNextBridge()
        ensureDekanatBridge()
        syncAuthCookies()
    }

    private fun buildDisciplinesRequest(studyYear: String): Request {
        val form = FormBody.Builder()
            .add("studyYear", studyYear)
            .build()

        return Request.Builder()
            .url(DISCIPLINES_URL)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", BASE_URL)
            .header(
                "Referer",
                "$BASE_URL/proc/stud/?backToNewEios=$EIOS_URL/main/personal-area"
            )
            .post(form)
            .build()
    }

    private fun performDisciplinesPreRequest() {
        val request = Request.Builder()
            .url(DISCIPLINES_PAGE_URL)
            .header("User-Agent", USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9," +
                        "image/avif,image/webp,image/apng,*/*;q=0.8"
            )
            .header("Upgrade-Insecure-Requests", "1")
            .header("Referer", "$BASE_URL/")
            .get()
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // Основной POST может работать и без предварительного GET.
        }
    }

    suspend fun fetchTasks(cId: String): String = withContext(Dispatchers.IO) {
        syncAuthCookies()
        dedupeCookies()

        val request = buildTasksRequest(cId)

        client.newCall(request).execute().use(::responseBodyAsCp1251)
    }

    private fun buildTasksRequest(cId: String): Request {
        val form = FormBody.Builder()
            .add("c_id", cId)
            .build()

        return Request.Builder()
            .url(TASKS_URL)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", BASE_URL)
            .header("Referer", TASKS_REFERER)
            .post(form)
            .build()
    }

    fun clearCookies() {
        cookieManager.cookieStore.removeAll()
    }

    /**
     * Выполняет GET-запрос к api-next.kemsu.ru.
     */
    private suspend fun fetchScheduleJson(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            syncAuthCookies()
            dedupeCookies()

            val request = buildScheduleRequest(path)

            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${text.take(200)}")
                        )
                    }

                    Result.success(text)
                }
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }

    private fun buildScheduleRequest(path: String): Request {
        return Request.Builder()
            .url("$API_NEXT_URL$path")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", EIOS_URL)
            .header("Referer", "$EIOS_URL/")
            .get()
            .build()
    }

    private fun executeSuccessfully(request: Request): Boolean {
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchCurrentDayInfo(): Result<String> {
        return fetchScheduleJson("/api/schedule/integration/currentDayInfo")
    }

    suspend fun fetchScheduleTable(): Result<String> {
        return fetchScheduleJson("/api/schedule/integration/shedule")
    }
}