package com.retailone.pos.viewmodels.DashboardViewodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.retailone.pos.models.ProgressModel.ProgressData
import com.retailone.pos.models.ReplaceModel.ReplaceSaleReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemRes
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.SalesListRequest
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.SalesReturnReasonRes
import com.retailone.pos.models.SalesListResponse
import com.retailone.pos.network.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptReq
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptRes
import com.retailone.pos.models.ReplaceModel.ReturnSaleResMapper
import com.retailone.pos.models.ReplaceModel.ReturnSaleResRaw
import org.json.JSONObject

class ReturnSalesDetailsViewmodel : ViewModel() {

    val returnitem_data = MutableLiveData<ReturnItemRes>()
    val returnitem_liveData: LiveData<ReturnItemRes>
        get() = returnitem_data

    val loading = MutableLiveData<ProgressData>()
    val loadingLiveData: LiveData<ProgressData>
        get() = loading

    val returnsalesubmit_data = MutableLiveData<ReturnSaleRes>()
    val returnsalesubmit_liveData: LiveData<ReturnSaleRes>
        get() = returnsalesubmit_data

    val salesreturnreason_data = MutableLiveData<SalesReturnReasonRes>()
    val salesreturnreason_liveData: LiveData<SalesReturnReasonRes>
        get() = salesreturnreason_data

    val salesListLiveData = MutableLiveData<SalesListResponse>()
    val copyReceipt_data = MutableLiveData<CopyReceiptRes>()
    val copyReceiptLiveData: LiveData<CopyReceiptRes>
        get() = copyReceipt_data


    // Helper function to extract error message from Response
    private fun getErrorMessage(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody.isNullOrEmpty()) return "Something went wrong"

            val jsonObject = JSONObject(errorBody)

            // Check for custom error format with status = 0
            val status = jsonObject.optInt("status", -1)
            if (status == 0) {
                val message = jsonObject.optString("message", "")
                val data = jsonObject.optString("data", "")

                return when {
                    message.isNotEmpty() && message != "null" -> message
                    data.isNotEmpty() && data != "null" -> data
                    else -> "An error occurred"
                }
            }

            jsonObject.optString("message", "Something went wrong")
        } catch (e: Exception) {
            Log.e("ErrorParsing", "Failed to parse error: ${e.message}")
            "Something went wrong. Please try again."
        }
    }

    // Overload for errorBody string
    private fun getErrorMessage(errorBody: String?): String {
        return try {
            if (errorBody.isNullOrEmpty()) return "Something went wrong"

            val jsonObject = JSONObject(errorBody)

            // Check for custom status = 0 format
            val status = jsonObject.optInt("status", -1)
            if (status == 0) {
                val message = jsonObject.optString("message", "")
                val data = jsonObject.optString("data", "")

                return when {
                    message.isNotEmpty() && message != "null" -> message
                    data.isNotEmpty() && data != "null" -> data
                    else -> "An error occurred"
                }
            }

            jsonObject.optString("message", "Something went wrong")
        } catch (e: Exception) {
            Log.e("ErrorParsing", "Failed to parse error: ${e.message}")
            "Something went wrong. Please try again."
        }
    }

    // Helper for long log messages
    private fun logLong(tag: String, msg: String) {
        if (msg.length <= 4000) {
            Log.d(tag, msg)
            return
        }
        var i = 0
        while (i < msg.length) {
            val end = (i + 4000).coerceAtMost(msg.length)
            Log.d(tag, msg.substring(i, end))
            i = end
        }
    }

    fun callSalesListApi(context: Context, storeId: String) {
        loading.postValue(ProgressData(isProgress = true))

        val request = SalesListRequest(store_id = storeId)
        val gsonPretty = GsonBuilder().setPrettyPrinting().create()
        logLong("SalesListAPIRequest", "days=7\nbody=\n${gsonPretty.toJson(request)}")

        ApiClient().getApiService(context).getSalesList(days = 7, request)
            .enqueue(object : Callback<SalesListResponse> {
                override fun onResponse(
                    call: Call<SalesListResponse>,
                    response: Response<SalesListResponse>
                ) {
                    val code = response.code()
                    val headersStr = response.headers().toString()

                    Log.d("SalesListAPI", "HTTP code = $code")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()
                        val json = gsonPretty.toJson(body)

                        logLong("SalesListAPIResponse", "HTTP $code\nHeaders:\n$headersStr\nBody:\n$json")

                        // Check if backend sent status = 0
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("SalesListAPI", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case
                        salesListLiveData.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        val err = try {
                            response.errorBody()?.string()
                        } catch (e: Exception) {
                            "errorBody read failed: ${e.message}"
                        } ?: "null"

                        logLong("SalesListAPIResponse", "HTTP $code\nHeaders:\n$headersStr\nErrorBody:\n$err")

                        val errorMsg = getErrorMessage(err)
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
                    Log.e("SalesListAPI", "Error: ${t.localizedMessage}", t)

                    salesListLiveData.postValue(
                        SalesListResponse(status = 0, message = "Error", data = emptyList())
                    )

                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }

    fun callreplaceSalesListApi(context: Context, storeId: String) {
        loading.postValue(ProgressData(isProgress = true))

        val request = SalesListRequest(store_id = storeId)

        ApiClient().getApiService(context).getReplaceSalesList(days = 7, request)
            .enqueue(object : Callback<SalesListResponse> {
                override fun onResponse(
                    call: Call<SalesListResponse>,
                    response: Response<SalesListResponse>
                ) {
                    Log.d("ReplaceSalesListAPI", "HTTP code = ${response.code()}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Check if backend sent status = 0
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("ReplaceSalesListAPI", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case
                        salesListLiveData.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        val error = response.errorBody()?.string()
                        val errorMsg = getErrorMessage(error)

                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
                    Log.e("ReplaceSalesListAPI", "Error: ${t.localizedMessage}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }

    fun callReturnSalesDetailsApi(returnItemReq: ReturnItemReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))
        Log.d("ReturnSalesDetailsAPI", "Request: ${Gson().toJson(returnItemReq)}")

        ApiClient().getApiService(context).getReturnSalesItemAPI(returnItemReq)
            .enqueue(object : Callback<ReturnItemRes> {
                override fun onResponse(
                    call: Call<ReturnItemRes>,
                    response: Response<ReturnItemRes>
                ) {
                    Log.d("ReturnSalesDetailsAPI", "HTTP code = ${response.code()}")
                    Log.d("ReturnSalesDetailsAPI", "Response: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Check if backend sent status = 0
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("ReturnSalesDetailsAPI", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case
                        returnitem_data.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        val error = response.errorBody()?.string()
                        val errorMsg = getErrorMessage(error)

                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<ReturnItemRes>, t: Throwable) {
                    Log.e("ReturnSalesDetailsAPI", "Error: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }

    fun callReturnSalesSubmitApi(returnSaleReq: ReturnSaleReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))
        Log.d("ReturnSalesSubmitAPI", "Request: ${Gson().toJson(returnSaleReq)}")

        ApiClient().getApiService(context).getReturnSalesSubmitAPI(returnSaleReq)
            .enqueue(object : Callback<ReturnSaleRes> {
                override fun onResponse(
                    call: Call<ReturnSaleRes>,
                    response: Response<ReturnSaleRes>
                ) {
                    Log.d("ReturnSalesSubmitAPI", "HTTP code = ${response.code()}")
                    Log.d("ReturnSalesSubmitAPI", "Response: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Check if backend sent status = 0
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("ReturnSalesSubmitAPI", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case
                        Log.d(
                            "ReturnSalesSubmitAPI",
                            "Return sale SUCCESS raw JSON: ${Gson().toJson(body)}"
                        )
                        returnsalesubmit_data.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        val error = response.errorBody()?.string()
                        val errorMsg = getErrorMessage(error)

                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<ReturnSaleRes>, t: Throwable) {
                    Log.e("ReturnSalesSubmitAPI", "Error: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }

    fun callReplaceSaleApi(req: ReplaceSaleReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))
        Log.d("ReplaceSaleAPI", "Request: ${Gson().toJson(req)}")

        ApiClient().getApiService(context).replaceSale(req)
            .enqueue(object : Callback<ReturnSaleResRaw> {
                override fun onResponse(
                    call: Call<ReturnSaleResRaw>,
                    response: Response<ReturnSaleResRaw>
                ) {
                    val code = response.code()
                    val ct = response.headers()["Content-Type"]
                    val peek = try {
                        response.raw().peekBody(2048).string()
                    } catch (_: Exception) {
                        null
                    }

                    Log.d("ReplaceSaleAPI", "HTTP code = $code")
                    Log.d("API_RAW_RESPONSE", "Raw JSON: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful) {
                        val body = response.body()

                        if (body == null) {
                            Log.e("ReplaceSaleAPI", "Empty body (HTTP $code). Peek=${peek?.take(200)}")
                            loading.postValue(
                                ProgressData(false, true, "Empty server response")
                            )
                            return
                        }

                        try {
                            val normalized: ReturnSaleRes =
                                ReturnSaleResMapper.toReturnSaleRes(body, Gson())

                            // Check if backend sent status = 0 in normalized response
                            if (normalized.status == 0) {
                                val errorMsg = normalized.message ?: normalized.data?.toString() ?: "An error occurred"
                                Log.e("ReplaceSaleAPI", "Backend error (status=0): $errorMsg")

                                loading.postValue(
                                    ProgressData(false, true, errorMsg)
                                )
                                return
                            }

                            // Success case
                            returnsalesubmit_data.postValue(normalized)
                            loading.postValue(ProgressData(isProgress = false))

                        } catch (e: Exception) {
                            Log.e("ReplaceSaleAPI", "JSON parse failed", e)
                            Log.e("ReplaceSaleAPI", "Peek=${peek?.take(300)}")
                            loading.postValue(
                                ProgressData(false, true, "Response parsing failed")
                            )
                        }
                    } else {
                        val err = try {
                            response.errorBody()?.string()
                        } catch (_: Exception) {
                            null
                        }
                        Log.e("ReplaceSaleAPI", "HTTP $code ct=$ct err=${err ?: peek}")

                        val errorMsg = getErrorMessage(err)
                        loading.postValue(
                            ProgressData(false, true, errorMsg)
                        )
                    }
                }

                override fun onFailure(call: Call<ReturnSaleResRaw>, t: Throwable) {
                    Log.e("ReplaceSaleAPI", "Network/IO error", t)
                    loading.postValue(
                        ProgressData(
                            false,
                            true,
                            t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }

    fun callSaleReturnReasonApi(context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        ApiClient().getApiService(context).getReturnReasonAPI()
            .enqueue(object : Callback<SalesReturnReasonRes> {
                override fun onResponse(
                    call: Call<SalesReturnReasonRes>,
                    response: Response<SalesReturnReasonRes>
                ) {
                    Log.d("SaleReturnReasonAPI", "HTTP code = ${response.code()}")
                    Log.d("SaleReturnReasonAPI", "Response: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Check if backend sent status = 0
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("SaleReturnReasonAPI", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case
                        salesreturnreason_data.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        val error = response.errorBody()?.string()
                        val errorMsg = getErrorMessage(error)

                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<SalesReturnReasonRes>, t: Throwable) {
                    Log.e("SaleReturnReasonAPI", "Error: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }
    fun callCopyReceiptApi(salesId: String, storeId: String, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        val request = CopyReceiptReq(
            sales_id = salesId,
            store_id = storeId,
            type = "Refund"
        )

        Log.d("CopyReceiptAPI", "Request: ${Gson().toJson(request)}")

        ApiClient().getApiService(context).copyReceipt(request)
            .enqueue(object : Callback<CopyReceiptRes> {
                override fun onResponse(
                    call: Call<CopyReceiptRes>,
                    response: Response<CopyReceiptRes>
                ) {
                    Log.d("CopyReceiptAPI", "HTTP code = ${response.code()}")
                    Log.d("CopyReceiptAPI", "Response: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Check if backend sent status = 0
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: "An error occurred"
                            Log.e("CopyReceiptAPI", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case
                        copyReceipt_data.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        val error = response.errorBody()?.string()
                        val errorMsg = getErrorMessage(error)

                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<CopyReceiptRes>, t: Throwable) {
                    Log.e("CopyReceiptAPI", "Error: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = t.localizedMessage ?: "Network error occurred"
                        )
                    )
                }
            })
    }

}

//package com.retailone.pos.viewmodels.DashboardViewodel
//
//import android.content.Context
//import android.util.Log
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//
//import com.retailone.pos.models.ProgressModel.ProgressData
//import com.retailone.pos.models.ReplaceModel.ReplaceSaleReq
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemRes
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.SalesListRequest
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
//import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.SalesReturnReasonRes
//import com.retailone.pos.models.SalesListResponse
//import com.retailone.pos.network.ApiClient
//import retrofit2.Call
//import retrofit2.Callback
//import retrofit2.Response
//
//import com.google.gson.Gson
//import com.google.gson.GsonBuilder
//import com.google.gson.JsonElement
//import com.google.gson.JsonObject
//import com.google.gson.JsonParser
//import com.retailone.pos.models.ReplaceModel.ReturnSaleResMapper
//import com.retailone.pos.models.ReplaceModel.ReturnSaleResRaw
//
//
//class ReturnSalesDetailsViewmodel : ViewModel() {
//
//    val returnitem_data = MutableLiveData<ReturnItemRes>()
//    val returnitem_liveData: LiveData<ReturnItemRes>
//        get() = returnitem_data
//
//    val loading = MutableLiveData<ProgressData>()
//    val loadingLiveData: LiveData<ProgressData>
//        get() = loading
//
//    val returnsalesubmit_data = MutableLiveData<ReturnSaleRes>()
//    val returnsalesubmit_liveData: LiveData<ReturnSaleRes>
//        get() = returnsalesubmit_data
//
//    val salesreturnreason_data = MutableLiveData<SalesReturnReasonRes>()
//    val salesreturnreason_liveData: LiveData<SalesReturnReasonRes>
//        get() = salesreturnreason_data
//
//    val salesListLiveData = MutableLiveData<SalesListResponse>()/*val salesList_liveData: LiveData<SalesReturnReasonRes>
//        get() = salesListLiveData*/
//    // val loading = MutableLiveData<ProgressData>()
//
//    //    fun callSalesListApi(context: Context, storeId: String) {
////        loading.postValue(ProgressData(isProgress = true))
////
////        val request = SalesListRequest(store_id = storeId)
////
////        ApiClient().getApiService(context).getSalesList(days = 7, request)
////            .enqueue(object : Callback<SalesListResponse> {
////                override fun onResponse(
////                    call: Call<SalesListResponse>,
////                    response: Response<SalesListResponse>
////                ) {
////                    if (response.isSuccessful && response.body() != null) {
////                        salesListLiveData.postValue(response.body())
////                        loading.postValue(ProgressData(isProgress = false))
////                    } else {
////                        loading.postValue(
////                            ProgressData(
////                                isProgress = false,
////                                isMessage = true,
////                                message = "Failed to fetch sales list, try again"
////                            )
////                        )
////                    }
////                }
////
////                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
////                    Log.e("SalesListAPI", "Error: ${t.localizedMessage}")
////                    loading.postValue(
////                        ProgressData(
////                            isProgress = false,
////                            isMessage = true,
////                            message = "Something went wrong: ${t.message}"
////                        )
////                    )
////                }
////            })
////    }
//// handy for long bodies
//    private fun logLong(tag: String, msg: String) {
//        if (msg.length <= 4000) {
//            Log.d(tag, msg)
//            return
//        }
//        var i = 0
//        while (i < msg.length) {
//            val end = (i + 4000).coerceAtMost(msg.length)
//            Log.d(tag, msg.substring(i, end))
//            i = end
//        }
//    }
//
//
//    fun callSalesListApi(context: Context, storeId: String) {
//        loading.postValue(ProgressData(isProgress = true))
//
//        val request = SalesListRequest(store_id = storeId)
//
//        // optional: log the outgoing request
//        val gsonPretty = GsonBuilder().setPrettyPrinting().create()
//        logLong("SalesListAPIRequest", "days=7\nbody=\n${gsonPretty.toJson(request)}")
//
//        ApiClient().getApiService(context).getSalesList(days = 7, request)
//            .enqueue(object : Callback<SalesListResponse> {
//                override fun onResponse(
//                    call: Call<SalesListResponse>, response: Response<SalesListResponse>
//                ) {
//                    val code = response.code()
//                    val headersStr = response.headers().toString()
//
//                    if (response.isSuccessful && response.body() != null) {
//                        val body = response.body()!!
//                        val json = gsonPretty.toJson(body)
//
//                        // --- print the response you are getting from API ---
//                        logLong(
//                            "SalesListAPIResponse",
//                            "HTTP $code\nHeaders:\n$headersStr\nBody:\n$json"
//                        )
//
//                        salesListLiveData.postValue(body)
//                        loading.postValue(ProgressData(isProgress = false))
//                    } else {
//                        val err = try {
//                            response.errorBody()?.string()
//                        } catch (e: Exception) {
//                            "errorBody read failed: ${e.message}"
//                        } ?: "null"
//
//                        logLong(
//                            "SalesListAPIResponse",
//                            "HTTP $code (isSuccessful=${response.isSuccessful})\nHeaders:\n$headersStr\nErrorBody:\n$err"
//                        )
//
//                        loading.postValue(
//                            ProgressData(
//                                isProgress = false,
//                                isMessage = true,
//                                message = "Failed to fetch sales list, try again"
//                            )
//                        )
//                    }
//                }
//
//                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
//                    Log.e("SalesListAPI", "Error: ${t.localizedMessage}", t)
//
//                    // ✅ ADD THIS LINE - Post empty response so UI updates
//                    salesListLiveData.postValue(
//                        SalesListResponse(status = 0, message = "Error", data = emptyList())
//                    )
//
//                    loading.postValue(
//                        ProgressData(
//                            isProgress = false,
//                            isMessage = true,
//                            message = "Something went wrong: ${t.message}"
//                        )
//                    )
//                }
//
//            })
//    }
//
//    fun callreplaceSalesListApi(context: Context, storeId: String) {
//        loading.postValue(ProgressData(isProgress = true))
//
//        val request = SalesListRequest(store_id = storeId)
//
//        ApiClient().getApiService(context).getReplaceSalesList(days = 7, request)
//            .enqueue(object : Callback<SalesListResponse> {
//                override fun onResponse(
//                    call: Call<SalesListResponse>, response: Response<SalesListResponse>
//                ) {
//                    if (response.isSuccessful && response.body() != null) {
//                        salesListLiveData.postValue(response.body())
//                        loading.postValue(ProgressData(isProgress = false))
//                    } else {
//                        loading.postValue(
//                            ProgressData(
//                                isProgress = false,
//                                isMessage = true,
//                                message = "Failed to fetch sales list, try again"
//                            )
//                        )
//                    }
//                }
//
//                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
//                    Log.e("SalesListAPI", "Error: ${t.localizedMessage}")
//                    loading.postValue(
//                        ProgressData(
//                            isProgress = false,
//                            isMessage = true,
//                            message = "Something went wrong: ${t.message}"
//                        )
//                    )
//                }
//            })
//    }
//
//
//    fun callReturnSalesDetailsApi(returnItemReq: ReturnItemReq, context: Context) {
//        loading.postValue(ProgressData(isProgress = true))
//        Log.e("SalesListAPIRequest", "Request: ${returnItemReq}")
//        ApiClient().getApiService(context).getReturnSalesItemAPI(returnItemReq)
//            .enqueue(object : Callback<ReturnItemRes> {
//                override fun onResponse(
//                    call: Call<ReturnItemRes>, response: Response<ReturnItemRes>
//                ) {
//                    Log.e("SalesListAPIResponse", "Response: ${response.body()}")
//                    Log.e("SalesListAPIResponseX", "Response: ${Gson().toJson(response.body())}")
//                    if (response.isSuccessful && response.body() != null) {
//                        returnitem_data.postValue(response.body())
//                        loading.postValue(ProgressData(isProgress = false))
//                    } else {
//                        loading.postValue(
//                            ProgressData(
//                                isProgress = false,
//                                isMessage = true,
//                                message = "Failed to fetch data, Try again"
//                            )
//                        )
//                    }
//                }
//
//                override fun onFailure(call: Call<ReturnItemRes>, t: Throwable) {
//                    Log.d("rty", t.message.toString())
//                    loading.postValue(
//                        ProgressData(
//                            isProgress = false, isMessage = true, message = "Something Went Wrong"
//                        )
//                    )
//                }
//            })
//    }
//
//
//    fun callReturnSalesSubmitApi(returnSaleReq: ReturnSaleReq, context: Context) {
//        loading.postValue(ProgressData(isProgress = true))
//
//
//        Log.e("SalesListAPIRequestreturn", "Request: ${returnSaleReq}")
//        ApiClient().getApiService(context).getReturnSalesSubmitAPI(returnSaleReq)
//            .enqueue(object : Callback<ReturnSaleRes> {
//                override fun onResponse(
//                    call: Call<ReturnSaleRes>, response: Response<ReturnSaleRes>
//                ) {
//
//                    if (response.isSuccessful && response.body() != null) {
//                        returnsalesubmit_data.postValue(response.body())
//                        loading.postValue(ProgressData(isProgress = false))
//                    } else {
//                        loading.postValue(
//                            ProgressData(
//                                isProgress = false,
//                                isMessage = true,
//                                message = "Failed to fetch data, Try again"
//                            )
//                        )
//                    }
//                }
//
//                override fun onFailure(call: Call<ReturnSaleRes>, t: Throwable) {
//                    Log.d("rty", t.message.toString())
//                    loading.postValue(
//                        ProgressData(
//                            isProgress = false, isMessage = true, message = "Something Went Wrong"
//                        )
//                    )
//                }
//            })
//    }/* fun callReplaceSaleApi(req: ReplaceSaleReq, context: Context) {
//         loading.postValue(ProgressData(isProgress = true))
//
//         Log.e("ReplaceSaleRequest", "Request: $req")
//         ApiClient().getApiService(context).replaceSale(req).enqueue(object :
//             Callback<ReturnSaleRes> {
//             override fun onResponse(
//                 call: Call<ReturnSaleRes>,
//                 response: Response<ReturnSaleRes>
//             ) {
//                 if (response.isSuccessful && response.body() != null) {
//                     returnsalesubmit_data.postValue(response.body())
//                     loading.postValue(ProgressData(isProgress = false))
//                 } else {
//                     loading.postValue(
//                         ProgressData(
//                             isProgress = false,
//                             isMessage = true,
//                             message = "Failed to fetch data, Try again"
//                         )
//                     )
//                 }
//             }
//
//             override fun onFailure(call: Call<ReturnSaleRes>, t: Throwable) {
//                 Log.d("ReplaceSale", t.message.toString())
//                 loading.postValue(ProgressData(isProgress = false, isMessage = true, message = "Something Went Wrong"))
//             }
//         })
//     }
// */
//// ReturnSalesDetailsViewmodel.kt
//
////
////    fun callReplaceSaleApi(req: ReplaceSaleReq, context: Context) {
////        loading.postValue(ProgressData(isProgress = true))
////
////        Log.e("ReplaceSaleRequest", "Request: $req")
////        ApiClient().getApiService(context).replaceSale(req)
////            .enqueue(object : Callback<ReturnSaleResRaw> {
////                override fun onResponse(
////                    call: Call<ReturnSaleResRaw>, response: Response<ReturnSaleResRaw>
////                ) {
////                    if (response.isSuccessful && response.body() != null) {
////                        val raw = response.body()!!
////                        val normalized: ReturnSaleRes =
////                            ReturnSaleResMapper.toReturnSaleRes(raw, Gson())
////
////                        returnsalesubmit_data.postValue(normalized)
////                        loading.postValue(ProgressData(isProgress = false))
////                    } else {
////                        loading.postValue(
////                            ProgressData(
////                                isProgress = false,
////                                isMessage = true,
////                                message = "Failed to fetch data, Try again"
////                            )
////                        )
////                    }
////                }
////
////                override fun onFailure(call: Call<ReturnSaleResRaw>, t: Throwable) {
////                    Log.d("ReplaceSale", t.message.toString())
////                    loading.postValue(
////                        ProgressData(
////                            isProgress = false, isMessage = true, message = "Something Went Wrong"
////                        )
////                    )
////                }
////            })
////    }
//
//
//    fun callReplaceSaleApi(req: ReplaceSaleReq, context: Context) {
//        loading.postValue(ProgressData(isProgress = true))
//        Log.e("ReplaceSaleRequest", "Request: $req")
//
//        ApiClient().getApiService(context).replaceSale(req)
//            .enqueue(object : Callback<ReturnSaleResRaw> {
//                override fun onResponse(
//                    call: Call<ReturnSaleResRaw>, response: Response<ReturnSaleResRaw>
//                ) {
//                    val code = response.code()
//                    val ct = response.headers()["Content-Type"]
//                    val peek = try { response.raw().peekBody(2048).string() } catch (_: Exception) { null }
//
//                    if (response.isSuccessful) {
//                        val body = response.body()
//                        // In your ViewModel or API response observer, add this log:
//                        Log.d("API_RAW_RESPONSE", "Raw JSON: ${Gson().toJson(response)}")
//                        if (body == null) {
//                            Log.e("ReplaceSale", "Empty body (HTTP $code). Peek=${peek?.take(200)}")
//                            loading.postValue(
//                                ProgressData(false, true, "Empty server response")
//                            )
//                            return
//                        }
//
//                        try {
//                            val normalized: ReturnSaleRes =
//                                ReturnSaleResMapper.toReturnSaleRes(body, Gson())
//                            returnsalesubmit_data.postValue(normalized)
//                            loading.postValue(ProgressData(isProgress = false))
//                        } catch (e: Exception) {
//                            Log.e("ReplaceSale", "JSON parse failed", e)
//                            Log.e("ReplaceSale", "Peek=${peek?.take(300)}")
//                            loading.postValue(
//                                ProgressData(false, true, "Response parsing failed")
//                            )
//                        }
//                    } else {
//                        val err = try { response.errorBody()?.string() } catch (_: Exception) { null }
//                        Log.e("ReplaceSale", "HTTP $code ct=$ct err=${err ?: peek}")
//                        loading.postValue(
//                            ProgressData(false, true, "Failed to fetch data, Try again ($code)")
//                        )
//                    }
//                }
//
//                override fun onFailure(call: Call<ReturnSaleResRaw>, t: Throwable) {
//                    Log.e("ReplaceSale", "Network/IO error", t)
//                    loading.postValue(
//                        ProgressData(false, true, "Something Went Wrong")
//                    )
//                }
//            })
//    }
//
//
//
//// ReturnSalesDetailsViewmodel.kt (or wherever callReplaceSaleApi lives)
//
//
//// imports you need at the top of the file:
//
//
//    fun callSaleReturnReasonApi(context: Context) {
//        loading.postValue(ProgressData(isProgress = true))
//
//        ApiClient().getApiService(context).getReturnReasonAPI()
//            .enqueue(object : Callback<SalesReturnReasonRes> {
//                override fun onResponse(
//                    call: Call<SalesReturnReasonRes>, response: Response<SalesReturnReasonRes>
//                ) {
//
//                    if (response.isSuccessful && response.body() != null) {
//                        salesreturnreason_data.postValue(response.body())
//                        loading.postValue(ProgressData(isProgress = false))
//                    } else {
//                        loading.postValue(
//                            ProgressData(
//                                isProgress = false,
//                                isMessage = true,
//                                message = "Failed to fetch data, Try again"
//                            )
//                        )
//                    }
//                }
//
//                override fun onFailure(call: Call<SalesReturnReasonRes>, t: Throwable) {
//                    Log.d("rty", t.message.toString())
//                    loading.postValue(
//                        ProgressData(
//                            isProgress = false, isMessage = true, message = "Something Went Wrong"
//                        )
//                    )
//                }
//            })
//    }
//
//
//}