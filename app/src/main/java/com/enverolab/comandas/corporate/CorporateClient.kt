package com.enverolab.comandas.corporate

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.*

class CorporateHttpError(val status: Int, message: String) : Exception(message)

interface CorporateApi {
    @GET
    fun get(
        @Url url: String,
        @Header("X-Terminal-Token") token: String,
        @Header("Authorization") auth: String,
    ): Call<ResponseBody>

    @POST
    fun post(
        @Url url: String,
        @Header("X-Terminal-Token") token: String,
        @Header("Authorization") auth: String,
        @Body body: RequestBody,
    ): Call<ResponseBody>
}

class CorporateClient(
    private val base: String,
    private val token: String,
    private val access: String = "",
) {
    private val api: CorporateApi

    init {
        require(
            base.startsWith("https://") &&
                !base.contains('@') &&
                !base.contains('?') &&
                !base.contains('#')
        ) {
            "Use una URL HTTPS válida"
        }
        val client =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
        api =
            Retrofit.Builder()
                .baseUrl(base.trimEnd('/') + "/")
                .client(client)
                .build()
                .create(CorporateApi::class.java)
    }

    private fun url(path: String) = base.trimEnd('/') + "/api/v1/" + path

    private fun auth() = if (access.isBlank()) "" else "Bearer $access"

    fun bytes(path: String): ByteArray {
        val r = api.get(url(path), token, auth()).execute()
        if (!r.isSuccessful) throw CorporateHttpError(r.code(), "Solicitud rechazada (${r.code()})")
        return r.body()?.bytes() ?: throw IOException("Respuesta vacía")
    }

    fun json(path: String, data: JSONObject? = null): JSONObject {
        val r =
            if (data == null) api.get(url(path), token, auth()).execute()
            else
                api.post(
                        url(path),
                        token,
                        auth(),
                        data.toString().toRequestBody("application/json".toMediaType()),
                    )
                    .execute()
        val text = (if (r.isSuccessful) r.body() else r.errorBody())?.string().orEmpty()
        if (!r.isSuccessful) {
            val message =
                try {
                    JSONObject(text).optString("detail", text.take(400))
                } catch (_: Exception) {
                    "Solicitud rechazada (${r.code()})"
                }
            throw CorporateHttpError(r.code(), message)
        }
        return JSONObject(text)
    }
}
