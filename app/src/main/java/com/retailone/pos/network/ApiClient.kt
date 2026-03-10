package com.retailone.pos.network

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

// Retrofit instance class

class ApiClient {
    private lateinit var apiService: ApiService

    fun getApiService(context: Context): ApiService {
        // Initialize ApiService if not initialized yet
        if (!::apiService.isInitialized) {
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okhttpClient(context)) // Add our Okhttp client
                .build()

            apiService = retrofit.create(ApiService::class.java)
        }

        return apiService
    }


    // Initialize OkhttpClient with our interceptor
    private fun okhttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .addInterceptor(ErrorResponseInterceptor()) // Add error handling interceptor
            .build()
    }


    // Without Token
    fun getApiServiceNoToken(): ApiService {

        if (!::apiService.isInitialized) {
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(ApiService::class.java)
        }

        return apiService
    }

    // Custom interceptor to handle backend errors with status = 0
    inner class ErrorResponseInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)

            // ✅ ADD THIS: Handle all response codes, not just successful ones
            if (!response.isSuccessful) {
                // For HTTP errors (4xx, 5xx), just log and return - don't throw
                Log.e("ErrorInterceptor", "HTTP Error: ${response.code} for ${request.url}")
                return response
            }

            // Original logic starts here (only for successful HTTP responses)
            val responseBody = response.body
            val bodyString = responseBody?.string() ?: ""

            // ✅ List of APIs where status=0 is NOT an error (just "not found")
            val excludedEndpoints = listOf("getcustomer", "searchcustomer", "copyreciept") // Add more as needed

            val shouldSkipErrorCheck = excludedEndpoints.any { endpoint ->
                request.url.toString().contains(endpoint)
            }

            if (shouldSkipErrorCheck) {
                Log.d("ErrorInterceptor", "Skipping error check for: ${request.url}")
                return response.newBuilder()
                    .body(ResponseBody.create(responseBody?.contentType(), bodyString))
                    .build()
            }

            // Rest of your interceptor logic stays the same
            Log.e("INTERCEPTOR_REQUEST", "URL: ${request.url}")
            Log.e("INTERCEPTOR_RESPONSE", "Response body: $bodyString")

            try {
                if (bodyString.isNotEmpty()) {
                    val jsonObject = JSONObject(bodyString)
                    val status = jsonObject.optInt("status", -1)

                    Log.e("INTERCEPTOR_STATUS", "Backend returned status = $status")

                    if (status == 0 || status == 2) {
                        val message = jsonObject.optString("message", "")
                        val data = jsonObject.optString("data", "")

                        val errorMsg = when {
                            message.isNotEmpty() && message != "null" -> message
                            data.isNotEmpty() && data != "null" -> data
                            else -> "An error occurred"
                        }

                        Log.e("ErrorInterceptor", "❌ Backend ERROR (status=0): $errorMsg")
                        throw IOException(errorMsg)
                    } else if (status == 1 || status == 200) {
                        Log.e("INTERCEPTOR_STATUS", "✅ Backend SUCCESS (status=$status)")
                    } else if (status == -1) {
                        // ✅ ADD THIS: Handle cases where status field is missing or unrecognized
                        Log.w("ErrorInterceptor", "⚠️ No 'status' field found in response JSON")
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                Log.e("ErrorInterceptor", "Error parsing response: ${e.message}", e)
            }

            return response.newBuilder()
                .body(ResponseBody.create(responseBody?.contentType(), bodyString))
                .build()
        }
    }
}
