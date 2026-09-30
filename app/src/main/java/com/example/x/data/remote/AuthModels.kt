package com.example.x.data.remote

import com.example.x.data.model.User
import org.json.JSONObject

object AuthParser {
    fun parseLoginResponse(jsonStr: String): Result<Pair<User, String>> {
        return try {
            val root = JSONObject(jsonStr)
            val success = root.optBoolean("success", false)
            if (!success) return Result.failure(Exception("success=false: $jsonStr"))
            val userInfo = root.optJSONObject("userInfo") ?: return Result.failure(Exception("no userInfo"))
            val access = root.optString("accessToken", "")
            val refresh = root.optString("refreshToken", "")

            // id приходит числом (38341), остальное строками/nullable
            val id = userInfo.opt("id")?.toString().orEmpty()
            val login = userInfo.optString("login", "")
            val first = userInfo.optString("firstName", "")
            val last = userInfo.optString("lastName", "")
            val middle = userInfo.optString("middleName", "")
            val email = userInfo.optString("email", "")
            val town = userInfo.optString("town", "")
            val type = userInfo.optString("userType", "")
            // собираем ФИО
            val fullName = listOf(last, first, middle).filter { it.isNotBlank() }.joinToString(" ").ifBlank { login }

            val user = User(
                id = id,
                login = login,
                name = fullName,
                firstName = first,
                lastName = last,
                middleName = middle,
                email = email,
                town = town,
                group = type
            )
            Result.success(user to access)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractTokens(jsonStr: String): Pair<String, String> {
        return try {
            val o = JSONObject(jsonStr)
            o.optString("accessToken", "") to o.optString("refreshToken", "")
        } catch (_: Exception) { "" to "" }
    }
}
