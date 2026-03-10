package com.retailone.pos.ui.Activity.DashboardActivity

import NumberFormatter
import android.app.DatePickerDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.retailone.pos.R
import com.retailone.pos.adapter.AttendanceAdapter
import com.retailone.pos.adapter.SalesPaymentAdapter
import com.retailone.pos.adapter.StockSearchAdapter
import com.retailone.pos.databinding.ActivitySalesAndPaymentBinding
import com.retailone.pos.databinding.BottomsheetSalesdetailsFilterBinding
import com.retailone.pos.databinding.CustomerDetailsBottomsheetBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.CashupModel.CashupDetails.CashupDetailsReq
import com.retailone.pos.models.GetCustomerModel.getCustomerReq
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.InvoiceReq
import com.retailone.pos.models.SalesPaymentModel.SalesList.SalesListReq
import com.retailone.pos.viewmodels.DashboardViewodel.CashupDetailsViewmodel
import com.retailone.pos.viewmodels.DashboardViewodel.ProfileAttendanceViewmodel
import com.retailone.pos.viewmodels.DashboardViewodel.SalesPaymentViewmodel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SalesAndPaymentActivity : AppCompatActivity() {
    lateinit var  binding :ActivitySalesAndPaymentBinding
    lateinit var  d_binding :BottomsheetSalesdetailsFilterBinding
    private val calendar = Calendar.getInstance()

    lateinit var  viewmodel: SalesPaymentViewmodel
    lateinit var  sales_adapter: SalesPaymentAdapter

    lateinit var  localizationData: LocalizationData

    var storeid = ""
    var store_manager_id = ""

    var fromdate = ""
    var todate = ""

    var showFromdate = ""
    var showTodate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesAndPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewmodel = ViewModelProvider(this)[SalesPaymentViewmodel::class.java]
        localizationData = LocalizationHelper(this).getLocalizationData()


        enableBackButton()
        prepareSalesPaymentRCV()


        lifecycleScope.launch {
            storeid = LoginSession.getInstance(this@SalesAndPaymentActivity).getStoreID().first().toString()
            store_manager_id = LoginSession.getInstance(this@SalesAndPaymentActivity).getStoreManagerID().first().toString()
           // viewmodel.callSalesListApi(SalesListReq("","",storeid),this@SalesAndPaymentActivity)

            val secondDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
//            val secondDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
//                timeZone = TimeZone.getTimeZone("Africa/Lusaka")
//            }
            // Get current date
            val currentDate = Date()
            val calendar = Calendar.getInstance()

// Set the start date to today's date at 12:00 AM
            calendar.apply {
                time = currentDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startDate = calendar.time // This is today's date at 12:00 AM

// Set the end date to today's date at 11:59 PM
            calendar.apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endDate = calendar.time // This is today's date at 11:59 PM

// Format the dates
            val firstFormattedDate = secondDateFormat.format(startDate)
            val secondFormattedDate = secondDateFormat.format(endDate)

            // Call the API with the modified first date and the current date
            viewmodel.callInvoiceApi(InvoiceReq(storeid.toInt(), firstFormattedDate, secondFormattedDate), this@SalesAndPaymentActivity)

           // Update the calendar text
            binding.calenderTextx.text = "Today's Sales & Payment"
           //  viewmodel.callInvoiceApi(InvoiceReq(storeid.toInt(),"",""),this@SalesAndPaymentActivity)

        }

/*
        viewmodel.saleslist_liveData.observe(this){
            if(it.data.isNotEmpty()){
                sales_adapter = SalesPaymentAdapter(it.data,this)
                binding.salespaymentRcv.adapter = sales_adapter
               binding.salespaymentRcv.isVisible = true
              binding.noDataFound.isVisible = false
            }else{
               binding.salespaymentRcv.isVisible = false
               binding.noDataFound.isVisible = true
            }
        }
*/

        viewmodel.invoice_livedata.observe(this){
            if(it.status==1){
                val invoicevalue = it.data.total_invoice_amount.toString()
                val invoicepaid =it.data.invoices_paid.toString()
                val invoiceunpaid =it.data.invoices_unpaid.toString()
                val paymentrcvd =it.data.payments_received.toString()
                val paymentdue =it.data.payments_due.toString()

                binding.apply{
                    invoiceText.text = NumberFormatter().formatPrice(invoicevalue,localizationData)
                    invoicePaidValue.text = invoicepaid
                    invoiceUnpaidValue.text = invoiceunpaid
                    recivedvalue.text = NumberFormatter().formatPrice(paymentrcvd,localizationData)
                  duevalue.text = NumberFormatter().formatPrice(paymentdue,localizationData)
                }
                val salelist = it.data.sales
                if(salelist.isNotEmpty()){
                    sales_adapter = SalesPaymentAdapter(salesList = salelist,this)
                    binding.salespaymentRcv.adapter = sales_adapter
                    binding.salespaymentRcv.isVisible = true
                    binding.noDataFound.isVisible = false
                }else{
                    binding.salespaymentRcv.isVisible = false
                    binding.noDataFound.isVisible = true
                }

            }
        }


        viewmodel.loadingLiveData.observe(this){
            binding.progress.isVisible = it.isProgress
            if(it.isMessage)
                showMessage(it.message)
        }


     /*   binding.fromCalenderLayout.setOnClickListener {
            showFromDatePicker()
        }
        binding.toCalenderLayout.setOnClickListener {
            if(fromdate==""){
                showMessage("Please select From Date First")
            }else{
                showToDatePicker()

            }
        }*/

        binding.addFab.setOnClickListener {
            FilterBottomSheet()
        }


/*
        binding.search.setOnClickListener {
            if(fromdate.isEmpty()){
                showMessage("Please select From Date")
            }else if(todate.isEmpty()){
                showMessage("Please select To Date")
            }else if(storeid.isEmpty()){
                showMessage("Couldn't fetch store info")
            }else{

                //showMessage(todate +" "+fromdate)
                viewmodel.callInvoiceApi(InvoiceReq(storeid.toInt(),fromdate,todate),this@SalesAndPaymentActivity)


                //viewmodel.callSalesListApi(SalesListReq(todate,fromdate,storeid),this@SalesAndPaymentActivity)

            }
        }
*/

        showToolbarImage()
    }


    private fun FilterBottomSheet() {

        d_binding = BottomsheetSalesdetailsFilterBinding.inflate(layoutInflater)

        todate =""
        fromdate=""

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(d_binding.root)
        d_binding.closeBottomsheet.setOnClickListener {
            dialog.dismiss()
        }

        d_binding.fromCalenderLayout.setOnClickListener {
            showFromDatePicker()
        }
        d_binding.toCalenderLayout.setOnClickListener {
            if(fromdate==""){
                showMessage("Please select From Date First")
            }else{
                showToDatePicker()

            }
        }

        d_binding.filterBtn.setOnClickListener {
            if(fromdate.isEmpty()){
                showMessage("Please select From Date")
            }else if(todate.isEmpty()){
                showMessage("Please select To Date")
            }else if(storeid.isEmpty()){
                showMessage("Couldn't fetch store info")
            }else{

                //showMessage(todate +" "+fromdate)
                viewmodel.callInvoiceApi(InvoiceReq(storeid.toInt(),fromdate,todate),this@SalesAndPaymentActivity)
                binding.calenderTextx.text= "$showFromdate - $showTodate"
                dialog.dismiss()


                //viewmodel.callSalesListApi(SalesListReq(todate,fromdate,storeid),this@SalesAndPaymentActivity)

            }
        }

        d_binding.clearBtn.setOnClickListener {

           // val secondDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
           // val currentDate = secondDateFormat.format(Date())

            viewmodel.callInvoiceApi(InvoiceReq(storeid.toInt(),"",""),this@SalesAndPaymentActivity)
            binding.calenderTextx.text = "All Sales & Payment"
            dialog.dismiss()

        }



        // Show the dialog
        dialog.show()
    }


    private fun showMessage(msg: String) {
        Toast.makeText(this@SalesAndPaymentActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showToolbarImage() {
        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter() // Add center crop
            .placeholder(R.drawable.mlogo) // Add a placeholder drawable
            .error(R.drawable.mlogo) // Add an error drawable (if needed)
            .into(binding.image)
    }

    private fun prepareSalesPaymentRCV() {

        binding.salespaymentRcv.apply {
            layoutManager = LinearLayoutManager(this@SalesAndPaymentActivity,
                RecyclerView.VERTICAL,false)
            //adapter = sales_adapter
        }
    }


    private fun showFromDatePicker() {
        // Create a DatePickerDialog
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                // Create a new Calendar instance to hold the selected date
                val selectedDate = Calendar.getInstance()
                // Set the selected date using the values received from the DatePicker dialog
                selectedDate.set(year, monthOfYear, dayOfMonth)
                // Set time part to "00:00:00"
                selectedDate.set(Calendar.HOUR_OF_DAY, 0)
                selectedDate.set(Calendar.MINUTE, 0)
                selectedDate.set(Calendar.SECOND, 0)
                // Create a SimpleDateFormat to format the date as "dd/MM/yyyy"
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                // Format the selected date into a string
                val formattedDate = dateFormat.format(selectedDate.time)
                showFromdate= formattedDate
                // Update the TextView to display the selected date with the "Selected Date: " prefix
                ///binding.calenderText.text = formattedDate
                d_binding.calenderText.text = formattedDate

                // Create another SimpleDateFormat to format the date as "yyyy-MM-dd HH:mm:ss"
                val secondDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                // Format the selected date into the "yyyy-MM-dd" format
                val formattedDate2023 = secondDateFormat.format(selectedDate.time)

                fromdate = formattedDate2023
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set the maximum date to the current date
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        // Show the DatePicker dialog
        datePickerDialog.show()
    }

    private fun showToDatePicker() {
        // Create a DatePickerDialog
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                // Create a new Calendar instance to hold the selected date
                val selectedDate = Calendar.getInstance()
                // Set the selected date using the values received from the DatePicker dialog
                selectedDate.set(year, monthOfYear, dayOfMonth)

                // Set time part to "23:59:59"
                selectedDate.set(Calendar.HOUR_OF_DAY, 23)
                selectedDate.set(Calendar.MINUTE, 59)
                selectedDate.set(Calendar.SECOND, 59)

                // Create a SimpleDateFormat to format the date as "dd/MM/yyyy"
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                // Format the selected date into a string
                val formattedDate = dateFormat.format(selectedDate.time)
                showTodate = formattedDate
                // Update the TextView to display the selected date with the "Selected Date: " prefix
                ///binding.tocalenderText.text = formattedDate
                d_binding.tocalenderText.text = formattedDate

                // Create another SimpleDateFormat to format the date as "yyyy-MM-dd HH:mm:ss"
                val secondDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                // Format the selected date into the "yyyy-MM-dd" format
                val formattedDate2023 = secondDateFormat.format(selectedDate.time)

                todate = formattedDate2023

                // You can use the formattedDate2023 variable as needed
                //println("Selected Date (2023 format): $formattedDate2023")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set the minimum date to the selected "from" date
        if (fromdate.isNotEmpty()) {
            val fromDate = SimpleDateFormat("yyyy-MM-dd").parse(fromdate)
            datePickerDialog.datePicker.minDate = fromDate.time
        }

        // Set the maximum date to the current date
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

        // Show the DatePicker dialog
        datePickerDialog.show()
    }



    private fun enableBackButton() {
        setSupportActionBar(binding.toolbar)
        //actionbar
        val actionbar = supportActionBar
        //set actionbar title
        actionbar!!.title = "New Activity"
        //set back button
        actionbar.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_back_arrow_white)
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}