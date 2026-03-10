package com.retailone.pos.viewmodels.DashboardViewodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.retailone.pos.models.AddNewCustomerModel.AddNewCustReq
import com.retailone.pos.models.AddNewCustomerModel.AddNewCustRes
import com.retailone.pos.models.GetCustomerModel.getCustomerReq
import com.retailone.pos.models.GetCustomerModel.getCustomerRes
import com.retailone.pos.models.PointofsaleModel.PointOfSaleItem
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.PosAddToCartReq
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.PosAddToCartRes
import com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSaleReq
import com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSaleRes
import com.retailone.pos.models.PointofsaleModel.SearchStoreProBarcodeModel.SearchStoreProBarcodeReq
import com.retailone.pos.models.PointofsaleModel.SearchStoreProBarcodeModel.SearchStoreProBarcodeRes
import com.retailone.pos.models.PointofsaleModel.SearchStroreProModel.SearchStoreProReq
import com.retailone.pos.models.PointofsaleModel.SearchStroreProModel.SearchStoreProRes
import com.retailone.pos.models.PointofsaleModel.toPatchedJson
import com.retailone.pos.models.PosSalesDetailsModel.PosSalesDetails
import com.retailone.pos.models.PosSalesDetailsModel.ReceiptTypeResponse
import com.retailone.pos.models.ProgressModel.ProgressData
import com.retailone.pos.network.ApiClient
import com.retailone.pos.network.SingleLiveEvent
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class PointofSaleViewmodel: ViewModel() {


    val loading = MutableLiveData<ProgressData>()
    val loadingLiveData : LiveData<ProgressData>
        get() = loading


    val storeProSearchdata = MutableLiveData<SearchStoreProRes>()
    val storeProSearchLivedata : LiveData<SearchStoreProRes>
        get() = storeProSearchdata

    val storeProSearchBarcodedata = MutableLiveData<SearchStoreProBarcodeRes>()
    val storeProSearchBarcodeLivedata : LiveData<SearchStoreProBarcodeRes>
        get() =  storeProSearchBarcodedata

    val posAddtocartData = MutableLiveData<PosAddToCartRes>()
    val posAddtocartLivedata : LiveData<PosAddToCartRes>
        get() = posAddtocartData

    val posSaleData = SingleLiveEvent<PosSalesDetails>()
    val posSaleLivedata : LiveData<PosSalesDetails>
        get() = posSaleData



    val addNewCustData = MutableLiveData<AddNewCustRes>()
    val addNewCustLivedata : LiveData<AddNewCustRes>
        get() = addNewCustData



    // Using SingleLiveEvent instead of MutableLiveData
    private val get_customerdata = SingleLiveEvent<getCustomerRes>()

    // Exposing the LiveData for observers
    val get_customer_liveData: LiveData<getCustomerRes>
        get() = get_customerdata

    // Function to update the value
    fun updateCustomerData(customerData: getCustomerRes) {
        get_customerdata.value = customerData
    }

    private val receiptTypeData = MutableLiveData<ReceiptTypeResponse>()
    val receiptTypeLiveData: LiveData<ReceiptTypeResponse>
        get() = receiptTypeData

    // Helper function to extract error message from Response
    private fun getErrorMessage(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody.isNullOrEmpty()) return "Something went wrong"

            val jsonObject = JSONObject(errorBody)

            // Check for your custom error format with status = 0
            val status = jsonObject.optInt("status", -1)
            if (status == 0) {
                // Try to get message first, fallback to data field
                val message = jsonObject.optString("message", "")
                val data = jsonObject.optString("data", "")

                return when {
                    message.isNotEmpty() && message != "null" -> message
                    data.isNotEmpty() && data != "null" -> data
                    else -> "An error occurred"
                }
            }

            // Fallback to standard error message
            jsonObject.optString("message", "Something went wrong")
        } catch (e: Exception) {
            Log.e("ErrorParsing", "Failed to parse error: ${e.message}")
            "Something went wrong. Please try again."
        }
    }

    // Overload for errorBody string
    private fun getErrorMessage(errorBody: String?): String {
        return try {
            if (errorBody.isNullOrEmpty()) {
                Log.e("ErrorParsing", "Error body is null or empty")
                return "Something went wrong"
            }

            Log.d("ErrorParsing", "Parsing error body: $errorBody")
            val jsonObject = JSONObject(errorBody)

            // Try to parse as PosSalesDetails structure
            val status = jsonObject.optInt("status", -1)
            val message = jsonObject.optString("message", "")
            val data = jsonObject.optString("data", "")

            Log.d("ErrorParsing", "status=$status, message='$message', data='$data'")

            when {
                message.isNotEmpty() && message != "null" -> message
                data.isNotEmpty() && data != "null" -> data
                else -> "An error occurred"
            }
        } catch (e: Exception) {
            Log.e("ErrorParsing", "Failed to parse error: ${e.message}", e)
            "Something went wrong. Please try again."
        }
    }






//    fun callSearchStoreProductApi( searchname: String,storeid:Int,customerid:Int,context: Context){
//        loading.postValue(ProgressData(isProgress = true))
//        Log.d("requestsearchstoreproduct", searchname+storeid+customerid)
//        ApiClient().getApiService(context).searchStoreProduct(SearchStoreProReq(searchname,storeid,customerid)).enqueue(object :
//            Callback<SearchStoreProRes> {
//            override fun onResponse(call: Call<SearchStoreProRes>, response: Response<SearchStoreProRes>) {
//
//                if(response.isSuccessful && response.body()!=null){
//                    storeProSearchdata.postValue(response.body())
//                    loading.postValue(ProgressData(isProgress = false))
//                }else{
//                    loading.postValue(ProgressData(isProgress = false,isMessage = true, message ="Failed to fetch data, Try again" ))
//                }
//            }
//
//            override fun onFailure(call: Call<SearchStoreProRes>, t: Throwable) {
//                loading.postValue(ProgressData(isProgress = false,isMessage = true, message = "Something Went Wrong"))
//            }
//        })
//    }


    fun callSearchStoreProductApi(
        searchname: String,
        storeid: Int,
        customerid: Int,
        context: Context
    ) {
        loading.postValue(ProgressData(isProgress = true))
        Log.d("POS_SEARCH_API", "Request -> name=$searchname, storeId=$storeid, customerId=$customerid")

        ApiClient().getApiService(context)
            .searchStoreProduct(SearchStoreProReq(searchname, storeid, customerid))
            .enqueue(object : Callback<SearchStoreProRes> {

                override fun onResponse(
                    call: Call<SearchStoreProRes>,
                    response: Response<SearchStoreProRes>
                ) {
                    Log.d("POS_SEARCH_API", "HTTP code = ${response.code()}, success = ${response.isSuccessful}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Print full response as JSON
                        Log.d("POS_SEARCH_API", "Response body = ${Gson().toJson(body)}")

                        // Check if backend sent status = 0 (custom error in success response)
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("POS_SEARCH_API", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case - status is not 0
                        storeProSearchdata.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        // Handle HTTP error responses (4xx, 5xx)
                        val error = response.errorBody()?.string()
                        Log.e("POS_SEARCH_API", "Error body = $error")
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

                override fun onFailure(call: Call<SearchStoreProRes>, t: Throwable) {
                    Log.e("POS_SEARCH_API", "API failed: ${t.localizedMessage}", t)
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


    fun callSearchStoreProductBarcodeApi(
        searchname: String,
        storeid: Int,
        customerid: Int,
        context: Context
    ) {
        loading.postValue(ProgressData(isProgress = true))
        Log.d("BARCODE_SEARCH_API", "Request -> barcode=$searchname, storeId=$storeid, customerId=$customerid")

        val requestObj = SearchStoreProBarcodeReq(
            customer_id = customerid,
            search_string = searchname,
            store_id = storeid
        )

        // Print Request JSON
        val requestJson = Gson().toJson(requestObj)
        Log.d("BARCODE_SEARCH_API", "Request JSON: $requestJson")

        ApiClient().getApiService(context)
            .searchStoreProductBarcode(requestObj)
            .enqueue(object : Callback<SearchStoreProBarcodeRes> {

                override fun onResponse(
                    call: Call<SearchStoreProBarcodeRes>,
                    response: Response<SearchStoreProBarcodeRes>
                ) {
                    Log.d("BARCODE_SEARCH_API", "HTTP code = ${response.code()}")
                    Log.d("BARCODE_SEARCH_API", "Response body = ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()

                        // Check if backend sent status = 0 (custom error in success response)
                        if (body?.status == 0) {
                            val errorMsg = body.message ?: body.data?.toString() ?: "An error occurred"
                            Log.e("BARCODE_SEARCH_API", "Backend error (status=0): $errorMsg")

                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = errorMsg
                                )
                            )
                            return
                        }

                        // Success case - status is not 0
                        storeProSearchBarcodedata.postValue(body)
                        loading.postValue(ProgressData(isProgress = false))

                    } else {
                        // Handle HTTP error responses (4xx, 5xx)
                        val error = response.errorBody()?.string()
                        Log.e("BARCODE_SEARCH_API", "Error body = $error")
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

                override fun onFailure(call: Call<SearchStoreProBarcodeRes>, t: Throwable) {
                    Log.e("BARCODE_SEARCH_API", "API failed: ${t.localizedMessage}", t)
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



    fun callAddtoCartPosApi(posAddToCartReq: PosAddToCartReq,context: Context){
        loading.postValue(ProgressData(isProgress = true))
        //vhdvdbvdbvn
        //called requst and response
        val requestJson = GsonBuilder().setPrettyPrinting().create().toJson(posAddToCartReq)
        Log.d("📝 AddToCart Request JSON", requestJson)
        println("📝 Final Request JSON:\n$requestJson")
        if (posAddToCartReq == null) {
            Log.e("🛑 RequestError", "posAddToCartReq is NULL")
            return
        }
        ApiClient().getApiService(context).addToCartPos(posAddToCartReq).enqueue(object :
            Callback<PosAddToCartRes> {
            override fun onResponse(call: Call<PosAddToCartRes>, response: Response<PosAddToCartRes>) {
                Log.d("response:new api", Gson().toJson(response.body()))
                // Log.d("yyy", Gson().toJson(SearchStoreProBarcodeReq(customerid,searchname,storeid)))
                Log.d("APIResponse new ", "Status Code: ${response.code()}")
                Log.d("APIResponse new ", "Body: ${Gson().toJson(response.body())}")

                if(response.isSuccessful && response.body()!=null){
                    posAddtocartData.postValue(response.body())
                    loading.postValue(ProgressData(isProgress = false))
                } else {
                    val error = response.errorBody()?.string()
                    val errorMsg = getErrorMessage(error)
                    loading.postValue(ProgressData(isProgress = false,isMessage = true, message = errorMsg))
                }
            }

            override fun onFailure(call: Call<PosAddToCartRes>, t: Throwable) {
                Log.d("xxx",t.message.toString())
                loading.postValue(ProgressData(isProgress = false,isMessage = true, message = t.localizedMessage ?: "Network error occurred"))
            }
        })
    }

   /* fun callposSaleApi( posSaleReq: PosSaleReq,context: Context){
        loading.postValue(ProgressData(isProgress = true))
        //vhdvdbvdbvn
        //called requst and response
        val requestJson = GsonBuilder().setPrettyPrinting().create().toJson(posSaleReq)
        Log.d("📝 POSNEW Request JSON", requestJson)
        println("📝 Final Request JSON:\n$requestJson")
        ApiClient().getApiService(context).posSale(posSaleReq).enqueue(object :
            Callback<PosSalesDetails> {
            override fun onResponse(call: Call<PosSalesDetails>, response: Response<PosSalesDetails>) {
                Log.d("response:pos api", Gson().toJson(response.body()))
                // Log.d("yyy", Gson().toJson(SearchStoreProBarcodeReq(customerid,searchname,storeid)))
                Log.d("APIResponse pos ", "Status Code: ${response.code()}")
                Log.d("APIResponse pos ", "Body: ${Gson().toJson(response.body())}")

                if(response.isSuccessful && response.body()!=null){
                    posSaleData.postValue(response.body())
                    loading.postValue(ProgressData(isProgress = false))
                }else{
                    loading.postValue(ProgressData(isProgress = false,isMessage = true, message ="Failed to fetch data, Try again" ))
                }
            }

            override fun onFailure(call: Call<PosSalesDetails>, t: Throwable) {
                loading.postValue(ProgressData(isProgress = false,isMessage = true, message = "Something Went Wrong"))
            }
        })
    }
*/
  /* fun callposSaleApi(posSaleReq: PosSaleReq, context: Context) {
       loading.postValue(ProgressData(isProgress = true))
       val requestJson = GsonBuilder().setPrettyPrinting().create().toJson(posSaleReq)
       Log.d("📝 POSNEW Request JSON", requestJson)

       ApiClient().getApiService(context).posSale(posSaleReq).enqueue(object : Callback<PosSalesDetails> {
           override fun onResponse(call: Call<PosSalesDetails>, response: Response<PosSalesDetails>) {
               Log.d("APIResponse pos", "Status Code: ${response.code()}")
               Log.d("APIResponse pos", "Body: ${Gson().toJson(response.body())}")

               loading.postValue(ProgressData(isProgress = false))

               if (response.isSuccessful && response.body() != null) {
                   posSaleData.postValue(response.body())
               } else {
                   // Try to parse error body if possible
                   val errorBody = response.errorBody()?.string()
                   Log.e("APIResponse pos", "Error Body: $errorBody")

                   try {
                       val errorResponse = Gson().fromJson(errorBody, PosSalesDetails::class.java)
                       posSaleData.postValue(errorResponse)
                   } catch (e: Exception) {
                       Log.e("API Parsing Error", "Could not parse error response")
                       loading.postValue(ProgressData(isProgress = false, isMessage = true, message = "Something test went wrong!"))
                   }
               }
           }

           override fun onFailure(call: Call<PosSalesDetails>, t: Throwable) {
               Log.e("API Failure", "Error: ${t.localizedMessage}")
                    //if customer already exsit messege need then pls change this sentences... already invoice number validation handle in android side

               loading.postValue(ProgressData(isProgress = false, isMessage = true, message = "Invoice Number is already exists"))

           }
       })
   }
*/
   fun callposSaleApiPatched(req: PosSaleReq, ctx: Context) {
       loading.postValue(ProgressData(isProgress = true))
       val body = req.toPatchedJson()

       // Log the complete request details
       Log.d("PosSale_API", "========== SALE REQUEST START ==========")
       Log.d("PosSale_API", "Request body: $body")
       Log.d("PosSale_API", "Customer ID: ${req.customer_id}")
       Log.d("PosSale_API", "Store ID: ${req.store_id}")
//       Log.d("PosSale_API", "Items count: ${req.items?.size ?: 0}")
       Log.d("PosSale_API", "========== SALE REQUEST END ==========")

       Log.d("PosSale_API", "Request body: $body")

       ApiClient().getApiService(ctx).posSale(body).enqueue(object :
           Callback<PosSalesDetails> {
           override fun onResponse(
               call: Call<PosSalesDetails>,
               response: Response<PosSalesDetails>
           ) {
               Log.d("PosSale_API", "HTTP code = ${response.code()}")

               if (response.isSuccessful && response.body() != null) {
                   val body = response.body()
                   Log.d("PosSale_API", "Response: ${Gson().toJson(body)}")

                   // Success case - interceptor already filtered out status = 0 errors
                   posSaleData.postValue(body)
                   loading.postValue(ProgressData(isProgress = false))

               } else {
                   // Handle HTTP error responses (4xx, 5xx)
                   val errorBody = response.errorBody()?.string()
                   val errorMsg = getErrorMessage(errorBody)
                   Log.e("PosSale_API", "HTTP Error: $errorMsg")

                   loading.postValue(
                       ProgressData(isProgress = false, isMessage = true, message = errorMsg)
                   )
               }
           }

           override fun onFailure(call: Call<PosSalesDetails>, t: Throwable) {
               Log.e("PosSale_API", "Failure: ${t.message}", t)

               // The interceptor throws IOException with the backend error message
               val errorMsg = when {
                   t is IOException && !t.message.isNullOrEmpty() -> {
                       // Check if it's a network error or our custom backend error
                       when {
                           t.message?.contains("Unable to resolve host") == true -> "No internet connection"
                           t.message?.contains("timeout") == true -> "Request timed out. Please try again."
                           t.message?.contains("Failed to connect") == true -> "Connection failed. Please check your network."
                           else -> t.message!! // This is our backend error message from interceptor
                       }
                   }
                   t is com.google.gson.JsonSyntaxException -> "Failed to process response"
                   else -> "Network error occurred. Please check your connection."
               }

               loading.postValue(
                   ProgressData(isProgress = false, isMessage = true, message = errorMsg)
               )
           }
       })
   }


    fun callAddNewCustApi(addNewCustReq: AddNewCustReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        Log.d("AddNewCust_API", "Request: ${Gson().toJson(addNewCustReq)}")

        ApiClient().getApiService(context).addNewCustAPI(addNewCustReq).enqueue(object :
            Callback<AddNewCustRes> {
            override fun onResponse(call: Call<AddNewCustRes>, response: Response<AddNewCustRes>) {
                Log.d("AddNewCust_API", "HTTP code = ${response.code()}")
                Log.d("AddNewCust_API", "Response: ${Gson().toJson(response.body())}")

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()

                    // Success case - interceptor already filtered out status = 0 errors
                    addNewCustData.postValue(body)
                    loading.postValue(ProgressData(isProgress = false))

                } else {
                    // Handle HTTP error responses (4xx, 5xx)
                    val errorBody = response.errorBody()?.string()
                    Log.e("AddNewCust_API", "Error body: $errorBody")

                    // Handle specific 409 conflict error
                    val errorMsg = if (response.code() == 409) {
                        // Check if backend provides a message, otherwise use default
                        val parsedMsg = getErrorMessage(errorBody)
                        if (parsedMsg != "Something went wrong" && parsedMsg != "An error occurred") {
                            parsedMsg
                        } else {
                            "Customer Already Registered"
                        }
                    } else {
                        getErrorMessage(errorBody)
                    }

                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = errorMsg
                        )
                    )
                }
            }

            override fun onFailure(call: Call<AddNewCustRes>, t: Throwable) {
                Log.e("AddNewCust_API", "Failure: ${t.message}", t)

                // The interceptor throws IOException with the backend error message
                val errorMsg = when {
                    t is IOException && !t.message.isNullOrEmpty() -> {
                        when {
                            t.message?.contains("Unable to resolve host") == true -> "No internet connection"
                            t.message?.contains("timeout") == true -> "Request timed out. Please try again."
                            t.message?.contains("Failed to connect") == true -> "Connection failed. Please check your network."
                            else -> t.message!! // Backend error message from interceptor
                        }
                    }
                    t is com.google.gson.JsonSyntaxException -> "Failed to process response"
                    else -> "Network error occurred. Please check your connection."
                }

                loading.postValue(
                    ProgressData(
                        isProgress = false,
                        isMessage = true,
                        message = errorMsg
                    )
                )
            }
        })
    }

    fun callGetCustomerDetailsApi(getCustomerReq: getCustomerReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        Log.d("GetCustomer_API", "Request: ${Gson().toJson(getCustomerReq)}")

        ApiClient().getApiService(context).getCustomerInfoAPI(getCustomerReq)
            .enqueue(object : Callback<getCustomerRes> {
                override fun onResponse(
                    call: Call<getCustomerRes>,
                    response: Response<getCustomerRes>
                ) {
                    Log.d("GetCustomer_API", "HTTP code = ${response.code()}")
                    Log.d("GetCustomer_API", "Response: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        val customerRes = response.body()

                        if (customerRes != null) {
                            // Success case - interceptor already filtered out status = 0 errors
                            updateCustomerData(customerRes)
                            loading.postValue(ProgressData(isProgress = false))
                        } else {
                            loading.postValue(
                                ProgressData(
                                    isProgress = false,
                                    isMessage = true,
                                    message = "No customer data received"
                                )
                            )
                        }

                    } else {
                        // Handle HTTP error responses (4xx, 5xx)
                        val errorBody = response.errorBody()?.string()
                        Log.e("GetCustomer_API", "Error body: $errorBody")
                        val errorMsg = getErrorMessage(errorBody)

                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = errorMsg
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<getCustomerRes>, t: Throwable) {
                    Log.e("GetCustomer_API", "Failure: ${t.message}", t)

                    // The interceptor throws IOException with the backend error message
                    val errorMsg = when {
                        t is IOException && !t.message.isNullOrEmpty() -> {
                            when {
                                t.message?.contains("Unable to resolve host") == true -> "No internet connection"
                                t.message?.contains("timeout") == true -> "Request timed out. Please try again."
                                t.message?.contains("Failed to connect") == true -> "Connection failed. Please check your network."
                                else -> t.message!! // Backend error message from interceptor
                            }
                        }
                        t is com.google.gson.JsonSyntaxException -> "Failed to process response"
                        else -> "Network error occurred. Please check your connection."
                    }

                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = errorMsg
                        )
                    )
                }
            })
    }
    fun callGetReceiptTypesApi(context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        Log.d("ReceiptType_API", "Fetching receipt types...")

        ApiClient().getApiService(context).getReceiptTypes()
            .enqueue(object : Callback<ReceiptTypeResponse> {
                override fun onResponse(
                    call: Call<ReceiptTypeResponse>,
                    response: Response<ReceiptTypeResponse>
                ) {
                    Log.d("ReceiptType_API", "Status Code: ${response.code()}")
                    Log.d("ReceiptType_API", "Response: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful && response.body() != null) {
                        receiptTypeData.postValue(response.body())
                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ReceiptType_API", "Error Body: $errorBody")
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = true,
                                message = "Failed to load receipt types"
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<ReceiptTypeResponse>, t: Throwable) {
                    Log.e("ReceiptType_API", "Failure: ${t.localizedMessage}")
                    loading.postValue(
                        ProgressData(
                            isProgress = false,
                            isMessage = true,
                            message = "Network error: ${t.localizedMessage}"
                        )
                    )
                }
            })
    }


}