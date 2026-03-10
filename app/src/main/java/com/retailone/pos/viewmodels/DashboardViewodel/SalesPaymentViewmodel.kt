package com.retailone.pos.viewmodels.DashboardViewodel

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.retailone.pos.models.ProgressModel.ProgressData
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.CancelSaleResponse
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.CancelSaleitemRequest
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.InvoiceReq
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.InvoiceRes
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptReq
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptRes
import com.retailone.pos.models.PosSalesDetailsModel.SaleReceiptRes
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsReq
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsRes
import com.retailone.pos.models.SalesPaymentModel.SalesList.SalesListReq
import com.retailone.pos.models.SalesPaymentModel.SalesList.SalesListRes
import com.retailone.pos.network.ApiClient
import com.retailone.pos.ui.Activity.DashboardActivity.SalesAndPaymentActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SalesPaymentViewmodel : ViewModel() {

    // Loading State LiveData
    private val loading = MutableLiveData<ProgressData>()
    val loadingLiveData: LiveData<ProgressData>
        get() = loading

    // Sales List LiveData
    private val saleslist_data = MutableLiveData<SalesListRes>()
    val saleslist_liveData: LiveData<SalesListRes>
        get() = saleslist_data

    // Sales Details LiveData
    private val salesdetails_data = MutableLiveData<SalesDetailsRes>()
    val salesdetails_liveData: LiveData<SalesDetailsRes>
        get() = salesdetails_data

    // Invoice LiveData
    private val invoice_data = MutableLiveData<InvoiceRes>()
    val invoice_livedata: LiveData<InvoiceRes>
        get() = invoice_data

    // Copy Receipt LiveData
    private val copyReceiptData = MutableLiveData<CopyReceiptRes>()
    val copyReceiptLiveData: LiveData<CopyReceiptRes>
        get() = copyReceiptData

    // Sale Receipt LiveData
    private val saleReceipt_data = MutableLiveData<SaleReceiptRes>()
    val saleReceiptLiveData: LiveData<SaleReceiptRes>
        get() = saleReceipt_data


    companion object {
        private const val TAG = "SalesPaymentViewModel"
    }

    /**
     * Cancel Sale API Call
     */
    fun callCancelSaleAPI(request: CancelSaleitemRequest, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        val gson = Gson()
        Log.d("$TAG-CancelSale", "REQUEST: ${gson.toJson(request)}")

        ApiClient().getApiService(context).cancelItemAPI(request)
            .enqueue(object : Callback<CancelSaleResponse> {
                override fun onResponse(
                    call: Call<CancelSaleResponse>,
                    response: Response<CancelSaleResponse>
                ) {
                    loading.postValue(ProgressData(isProgress = false))
                    Log.d("$TAG-CancelSale", "RESPONSE CODE: ${response.code()}")

                    if (response.isSuccessful && response.body() != null) {
                        val res = response.body()!!
                        Log.d("$TAG-CancelSale", "RESPONSE: ${gson.toJson(res)}")

                        if (res.status == 1) {
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()

                            // Redirect to Sales and Payment Activity
                            val intent = Intent(context, SalesAndPaymentActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("$TAG-CancelSale", "ERROR: $errorBody")
                        Toast.makeText(context, "Failed to cancel sale", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<CancelSaleResponse>, t: Throwable) {
                    loading.postValue(ProgressData(isProgress = false))
                    Log.e("$TAG-CancelSale", "FAILURE: ${t.message}", t)
                    Toast.makeText(
                        context,
                        "Something went wrong: ${t.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    /**
     * Sales List API Call
     */
    fun callSalesListApi(salesListReq: SalesListReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        Log.d("SalesList", "REQUEST: ${Gson().toJson(salesListReq)}")

        ApiClient().getApiService(context).getSalesListAPI(salesListReq)
            .enqueue(object : Callback<SalesListRes> {
                override fun onResponse(
                    call: Call<SalesListRes>,
                    response: Response<SalesListRes>
                ) {
                    Log.d("$TAG-SalesList", "RESPONSE CODE: ${response.code()}")

                    if (response.isSuccessful && response.body() != null) {
                        saleslist_data.postValue(response.body())
                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        Log.e("$TAG-SalesList", "ERROR: ${response.errorBody()?.string()}")
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = "Failed to fetch data, Try again"
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<SalesListRes>, t: Throwable) {
                    Log.e("$TAG-SalesList", "FAILURE: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = "Something Went Wrong"
                        )
                    )
                }
            })
    }

    /**
     * Sales Details API Call
     */
    fun callSalesDetailsApi(salesDetailsReq: SalesDetailsReq, context: Context) {
        val gson = Gson()
        val start = System.currentTimeMillis()

        Log.d("SalesDetails", "REQUEST BODY: ${gson.toJson(salesDetailsReq)}")
        loading.postValue(ProgressData(isProgress = true))

        val api = ApiClient().getApiService(context)
        val call = api.getSalesDetailsAPI(salesDetailsReq)

        // Log request details
        runCatching {
            val req = call.request()
            Log.d("SalesDetails", "REQUEST URL: ${req.url}")
            Log.d("SalesDetails", "REQUEST METHOD: ${req.method}")
            Log.d("SalesDetails", "REQUEST HEADERS:\n${req.headers}")
        }

        call.enqueue(object : Callback<SalesDetailsRes> {
            override fun onResponse(
                call: Call<SalesDetailsRes>,
                response: Response<SalesDetailsRes>
            ) {
                val tookMs = System.currentTimeMillis() - start
                Log.d(
                    "$TAG-SalesDetails",
                    "RESPONSE CODE: ${response.code()} (${response.message()}) in ${tookMs}ms"
                )
                Log.d("$TAG-SalesDetails", "RESPONSE HEADERS:\n${response.headers()}")

                if (response.isSuccessful && response.body() != null) {
                    // Log successful response
                    runCatching {
                        Log.d("SalesDetails", "RESPONSE BODY: ${gson.toJson(response.body())}")
                    }.onFailure {
                        Log.w(
                            "$TAG-SalesDetails",
                            "Failed to serialize response body: ${it.message}"
                        )
                    }

                    salesdetails_data.postValue(response.body())
                    loading.postValue(ProgressData(isProgress = false))
                } else {
                    // Log error body
                    val errorStr = runCatching {
                        response.errorBody()?.string() ?: ""
                    }.getOrElse {
                        "errorBody read failed: ${it.message}"
                    }
                    Log.e("$TAG-SalesDetails", "RESPONSE ERROR BODY: $errorStr")

                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = "Failed to fetch data, Try again"
                        )
                    )
                }
            }

            override fun onFailure(call: Call<SalesDetailsRes>, t: Throwable) {
                val tookMs = System.currentTimeMillis() - start
                Log.e("$TAG-SalesDetails", "CALL FAILED in ${tookMs}ms: ${t.message}", t)
                loading.postValue(
                    ProgressData(
                        isProgress = false,
                        isMessage = true,
                        message = "Something Went Wrong"
                    )
                )
            }
        })
    }

    /**
     * Invoice API Call
     */
    fun callInvoiceApi(invoiceReq: InvoiceReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        Log.d("Invoice", "REQUEST: ${Gson().toJson(invoiceReq)}")

        ApiClient().getApiService(context).getInvoiceAPI(invoiceReq)
            .enqueue(object : Callback<InvoiceRes> {
                override fun onResponse(
                    call: Call<InvoiceRes>,
                    response: Response<InvoiceRes>
                ) {
                    Log.d("Invoice", "RESPONSE CODE: ${response.code()}")

                    if (response.isSuccessful && response.body() != null) {
                        invoice_data.postValue(response.body())
                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        Log.e("$TAG-Invoice", "ERROR: ${response.errorBody()?.string()}")
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = "Failed to fetch data, Try again"
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<InvoiceRes>, t: Throwable) {
                    Log.e("$TAG-Invoice", "FAILURE: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = "Something Went Wrong"
                        )
                    )
                }
            })
    }

    /**
     * Copy Receipt API Call
     */
    fun callCopyReceiptApi(salesId: String, storeId: String, context: Context, type: String = "Sale"){
        loading.postValue(ProgressData(isProgress = true))

        Log.d("$TAG-CopyReceipt", "==========================================")
        Log.d("$TAG-CopyReceipt", "Requesting copy receipt")
        Log.d("$TAG-CopyReceipt", "Sales ID: '$salesId'")
        Log.d("$TAG-CopyReceipt", "Store ID: '$storeId'")
        Log.d("$TAG-CopyReceipt", "==========================================")

        val request = CopyReceiptReq(
            sales_id = salesId,
            store_id = storeId,
            type = type
        )

        val gson = Gson()
        Log.d("$TAG-CopyReceipt", "REQUEST BODY: ${gson.toJson(request)}")

        val call = ApiClient().getApiService(context).copyReceipt(request)

        // Log request details
        runCatching {
            val req = call.request()
            Log.d("$TAG-CopyReceipt", "REQUEST URL: ${req.url}")
            Log.d("$TAG-CopyReceipt", "REQUEST METHOD: ${req.method}")
            Log.d("$TAG-CopyReceipt", "REQUEST HEADERS:\n${req.headers}")
        }

        call.enqueue(object : Callback<CopyReceiptRes> {
            override fun onResponse(
                call: Call<CopyReceiptRes>,
                response: Response<CopyReceiptRes>
            ) {
                Log.d("$TAG-CopyReceipt", "RESPONSE CODE: ${response.code()}")
                Log.d("$TAG-CopyReceipt", "RESPONSE HEADERS:\n${response.headers()}")

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Log.d("CopyReceipt", "==========================================")
                    Log.d("CopyReceipt", "FULL RESPONSE FROM BACKEND:")
                    Log.d("CopyReceipt", "Status: ${body.status}")
                    Log.d("CopyReceipt", "Message: ${body.message}")
                    Log.d("CopyReceipt", "Raw JSON: ${gson.toJson(body)}")
                    Log.d("CopyReceipt", "==========================================")

                    copyReceiptData.postValue(body)
                    loading.postValue(ProgressData(isProgress = false))
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("$TAG-CopyReceipt", "RESPONSE ERROR BODY: $errorBody")

                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = "Failed to copy receipt"
                        )
                    )

                    // Post error response to LiveData
                    copyReceiptData.postValue(
                        CopyReceiptRes(
                            status = 0,
                            message = "Failed to copy receipt",
                            data = null
                        )
                    )
                }
            }

            override fun onFailure(call: Call<CopyReceiptRes>, t: Throwable) {
                Log.e("$TAG-CopyReceipt", "CALL FAILED: ${t.message}", t)

                loading.postValue(
                    ProgressData(
                        isProgress = false,
                        isMessage = true,
                        message = "Network error: ${t.localizedMessage}"
                    )
                )

                // Post failure response to LiveData
                copyReceiptData.postValue(
                    CopyReceiptRes(
                        status = 0,
                        message = "Network error: ${t.localizedMessage}",
                        data = null
                    )
                )
            }
        })
    }
    fun callSaleReceiptApi(salesId: String, storeId: String, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        val request = CopyReceiptReq(
            sales_id = salesId,
            store_id = storeId,
            type = "Sale"
        )

        Log.d("$TAG-SaleReceipt", "REQUEST BODY: ${Gson().toJson(request)}")

        ApiClient().getApiService(context).copyReceiptSale(request)
            .enqueue(object : Callback<SaleReceiptRes> {
                override fun onResponse(
                    call: Call<SaleReceiptRes>,
                    response: Response<SaleReceiptRes>
                ) {
                    Log.d("$TAG-SaleReceipt", "RESPONSE CODE: ${response.code()}")
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        Log.d("$TAG-SaleReceipt", "Raw JSON: ${Gson().toJson(body)}")
                        saleReceipt_data.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        Log.e("$TAG-SaleReceipt", "ERROR: ${response.errorBody()?.string()}")
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = "Failed to fetch receipt"
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<SaleReceiptRes>, t: Throwable) {
                    Log.e("$TAG-SaleReceipt", "FAILURE: ${t.message}", t)
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = "Something Went Wrong"
                        )
                    )
                }
            })
    }

}
