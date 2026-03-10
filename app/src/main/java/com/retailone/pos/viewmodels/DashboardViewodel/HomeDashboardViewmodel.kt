package com.retailone.pos.viewmodels.DashboardViewodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.Log
import com.retailone.pos.models.GetCustomerModel.getCustomerReq
import com.retailone.pos.models.GetCustomerModel.getCustomerRes
import com.retailone.pos.models.LocalizationModel.LocalizationRes
import com.retailone.pos.models.LoginModels.NoticeResponse
import com.retailone.pos.models.OrganisationDetailsModel.OrganisationDetailsRes
import com.retailone.pos.models.ProgressModel.ProgressData
import com.retailone.pos.network.ApiClient
import com.retailone.pos.network.SingleLiveEvent
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeDashboardViewmodel:ViewModel() {
    val localization_data = MutableLiveData<LocalizationRes>()

    val localization_liveData: LiveData<LocalizationRes>
        get() = localization_data

    val organization_data = MutableLiveData<OrganisationDetailsRes>()

    val organization_liveData: LiveData<OrganisationDetailsRes>
        get() = organization_data

    val loading = MutableLiveData<ProgressData>()
    val loadingLiveData : LiveData<ProgressData>
        get() = loading

    val notices_liveData = MutableLiveData<NoticeResponse?>()



//    val get_customerdata = MutableLiveData<getCustomerRes>()
//    val get_customer_liveData: LiveData<getCustomerRes>
//        get() = get_customerdata

    // Using SingleLiveEvent instead of MutableLiveData
    private val get_customerdata = SingleLiveEvent<getCustomerRes>()

    // Exposing the LiveData for observers
    val get_customer_liveData: LiveData<getCustomerRes>
        get() = get_customerdata

    // Function to update the value
    fun updateCustomerData(customerData: getCustomerRes) {
        get_customerdata.value = customerData
    }




    fun callLocalizationApi(storeid: Int, context: Context) {
        /// loading.postValue(ProgressData(isProgress = true))
        ApiClient().getApiService(context).getLocalizationAPI()
            .enqueue(object : Callback<LocalizationRes> {
                override fun onResponse(
                    call: Call<LocalizationRes>,
                    response: Response<LocalizationRes>
                ) {

                    if (response.isSuccessful && response.body() != null) {
                        localization_data.postValue(response.body())
                        //loading.postValue(ProgressData(isProgress = false))
                    } else {
                        //  loading.postValue(ProgressData(isProgress = false,isMessage = true, message ="Failed to fetch data, Try again" ))
                    }
                }

                override fun onFailure(call: Call<LocalizationRes>, t: Throwable) {
                    //loading.postValue(ProgressData(isProgress = false,isMessage = true, message = "Something Went Wrong"))
                }
            })
    }

    fun callNoticesApi(lastReqDt: String, context: Context) {
        ApiClient().getApiService(context).getNotices(lastReqDt)
            .enqueue(object : Callback<NoticeResponse> {
                override fun onResponse(
                    call: Call<NoticeResponse>,
                    response: Response<NoticeResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        Log.d("NoticesAPI", "Response: ${response.body()}")
                        notices_liveData.postValue(response.body())
                        notices_liveData.postValue(response.body())
                    } else {
                        Log.e("NoticesAPI", "Failed: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<NoticeResponse>, t: Throwable) {
                    Log.e("NoticesAPI", "Error: ${t.message}")
                }
            })
    }


    fun callOrganizationDetailsApi(storeid: Int, context: Context) {
        /// loading.postValue(ProgressData(isProgress = true))
        ApiClient().getApiService(context).getOrganisationDetailsAPI()
            .enqueue(object : Callback<OrganisationDetailsRes> {
                override fun onResponse(
                    call: Call<OrganisationDetailsRes>,
                    response: Response<OrganisationDetailsRes>
                ) {

                    if (response.isSuccessful && response.body() != null) {
                        organization_data.postValue(response.body())
                        //loading.postValue(ProgressData(isProgress = false))
                    } else {
                        //  loading.postValue(ProgressData(isProgress = false,isMessage = true, message ="Failed to fetch data, Try again" ))
                    }
                }

                override fun onFailure(call: Call<OrganisationDetailsRes>, t: Throwable) {
                    //loading.postValue(ProgressData(isProgress = false,isMessage = true, message = "Something Went Wrong"))
                }
            })
    }


    fun callGetCustomerDetailsApi(getCustomerReq: getCustomerReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))
        ApiClient().getApiService(context).getCustomerInfoAPI(getCustomerReq)
            .enqueue(object : Callback<getCustomerRes> {
                override fun onResponse(
                    call: Call<getCustomerRes>,
                    response: Response<getCustomerRes>
                ) {

                    if (response.isSuccessful && response.body() != null) {
                        //get_customerdata.postValue(response.body())
                        //loading.postValue(ProgressData(isProgress = false))
                        //updateCustomerData(response.body())
                        val customerRes = response.body() // Store the result in a local variable

                        if (customerRes != null) {
                            // Update the SingleLiveEvent with the non-null result
                            updateCustomerData(customerRes)

                            // Update loading state
                            loading.postValue(ProgressData(isProgress = false))
                        }


                    } else {
                         loading.postValue(ProgressData(isProgress = false,isMessage = true, message ="Failed to fetch data, Try again" ))
                    }
                }

                override fun onFailure(call: Call<getCustomerRes>, t: Throwable) {
                    loading.postValue(ProgressData(isProgress = false,isMessage = true, message = "Something Went Wrong"))
                }
            })
    }

}