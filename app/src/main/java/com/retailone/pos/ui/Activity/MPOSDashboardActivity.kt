package com.retailone.pos.ui.Activity

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.retailone.pos.R
import com.retailone.pos.adapter.PastRequDetailsAdapter
import com.retailone.pos.databinding.ActivityMposdashboardBinding
import com.retailone.pos.databinding.BottomsheetPastRequisitionLayoutBinding
import com.retailone.pos.databinding.CustomerDetailsBottomsheetBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.InventoryStockHelper
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper
import com.retailone.pos.localstorage.SharedPreference.TimeoutHelper
import com.retailone.pos.models.GetCustomerModel.getCustomerReq
import com.retailone.pos.models.StockRequisitionModel.PastReqDetailsModel.PastReqDetailsList
import com.retailone.pos.network.Constants
import com.retailone.pos.ui.Activity.DashboardActivity.CashUpActivity
import com.retailone.pos.ui.Activity.DashboardActivity.CashUpDetailsActivity
import com.retailone.pos.ui.Activity.DashboardActivity.CrashLogsActivity
import com.retailone.pos.ui.Activity.DashboardActivity.ExpenseRegisterActivity
import com.retailone.pos.ui.Activity.DashboardActivity.GoodsReturnToWarehouseActivity
import com.retailone.pos.ui.Activity.DashboardActivity.MaterialReceivingActivity
import com.retailone.pos.ui.Activity.DashboardActivity.MaterialRecivingItemsActivity
import com.retailone.pos.ui.Activity.DashboardActivity.PointOfSaleActivity
import com.retailone.pos.ui.Activity.DashboardActivity.PointofSaleDetailsActivity
import com.retailone.pos.ui.Activity.DashboardActivity.ProductInventoryActivity
import com.retailone.pos.ui.Activity.DashboardActivity.ProfileAttendanceActivity
import com.retailone.pos.ui.Activity.DashboardActivity.ReplacedSaleActivity
import com.retailone.pos.ui.Activity.DashboardActivity.ReturnCustomer
import com.retailone.pos.ui.Activity.DashboardActivity.ReturnSaleActivity
import com.retailone.pos.ui.Activity.DashboardActivity.SalesAndPaymentActivity
import com.retailone.pos.ui.Activity.DashboardActivity.StockRequisitionActivity
import com.retailone.pos.ui.Activity.DashboardActivity.proceedToDispatchActivity
import com.retailone.pos.utils.CrashHandler
import com.retailone.pos.utils.DateTimeFormatting
import com.retailone.pos.viewmodels.DashboardViewodel.HomeDashboardViewmodel
import com.retailone.pos.viewmodels.DashboardViewodel.PointofSaleViewmodel
import com.retailone.pos.viewmodels.MPOSLoginViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MPOSDashboardActivity : AppCompatActivity() {

    lateinit var  binding :ActivityMposdashboardBinding
    lateinit var  actionBarDrawerToggle: ActionBarDrawerToggle
    lateinit var drawer : DrawerLayout
    lateinit var  loginSession: LoginSession
    lateinit var sharedPrefHelper: SharedPrefHelper
    lateinit var inventoryStockHelper: InventoryStockHelper
    lateinit var  viewmodel: HomeDashboardViewmodel
    lateinit var  loginViewmodel: MPOSLoginViewmodel
    lateinit var localizationHelper: LocalizationHelper
    lateinit var organisationDetailsHelper: OrganisationDetailsHelper

    var storemanager_id=""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding= ActivityMposdashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        drawer = binding.drawer
        actionBarDrawerToggle = ActionBarDrawerToggle(this,drawer, R.string.nav_open, R.string.nav_close)
        drawer.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_menu)

        val headerView = binding.navView.getHeaderView(0)
        val headerImageView = headerView.findViewById<ImageView>(R.id.header_imageView)

        loginSession = LoginSession.getInstance(this)
        sharedPrefHelper = SharedPrefHelper(this)
        inventoryStockHelper = InventoryStockHelper(this)
        localizationHelper = LocalizationHelper(this)
        organisationDetailsHelper = OrganisationDetailsHelper(this)

        viewmodel = ViewModelProvider(this)[HomeDashboardViewmodel::class.java]
        loginViewmodel = ViewModelProvider(this)[MPOSLoginViewmodel::class.java]



        val crashHandler = CrashHandler(this)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)

        lifecycleScope.launch {
            // Check if the user is logged in
            val isLoggedIn = loginSession.getLoginStatus().first()
            val token = loginSession.getToken().first()

            //Log.d("token",token.toString() + " " +isLoggedIn.toString())
            val storeid = loginSession.getStoreID().first().toInt()
            storemanager_id = loginSession.getStoreManagerID().first().toString()


            //default search

            // check timeout
            val timeouthelper = TimeoutHelper(this@MPOSDashboardActivity)

            if(!timeouthelper.isSessionValid()){
                mposLogout()
            }

            viewmodel.callLocalizationApi(storeid,this@MPOSDashboardActivity)
            viewmodel.callOrganizationDetailsApi(storeid,this@MPOSDashboardActivity)
            viewmodel.callNoticesApi("20231120191141", this@MPOSDashboardActivity)

        }

        viewmodel.localization_liveData.observe(this){
            localizationHelper.saveLocalizationData(it.data)
        }

        viewmodel.organization_liveData.observe(this){
            organisationDetailsHelper.saveOrganisationData(it.data)
        }
        viewmodel.notices_liveData.observe(this) { response ->
            if (response != null && response.status == 1 && response.notices.isNotEmpty()) {
                val notice = response.notices[0]
                showNoticeDialog(notice.title, notice.content, notice.detailUrl)
                viewmodel.notices_liveData.value = null  // ← clear after consuming
            }
        }

        loginViewmodel.loadingLiveData.observe(this){
            binding.progress.isVisible = it.isProgress

            if(it.isMessage)
                showMessage(it.message)
        }


        binding.navView.setNavigationItemSelectedListener {
            when(it.itemId){

                R.id.about_us->{
                    Toast.makeText(this@MPOSDashboardActivity, "About us", Toast.LENGTH_SHORT).show()
                }R.id.contact_us->{
                    Toast.makeText(this@MPOSDashboardActivity, "Contact us", Toast.LENGTH_SHORT).show()
                }R.id.log_data->{
                    //Toast.makeText(this@MPOSDashboardActivity, "Contact us", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MPOSDashboardActivity,CrashLogsActivity::class.java))
                }
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }



        binding.poscard.setOnClickListener {
            lifecycleScope.launch {

               val cashupTime = loginSession.getCashupDateTime().first()

                Log.d("cashuTime", cashupTime)
                if (isCashupOutdated(cashupTime)) {
                    Log.d("cashuTimecalled", cashupTime)
                  showCashupPopup(cashupTime)
                } else {
                    customerBottomSheet()
                }
            }
        }


        binding.returncard.setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity,ReturnSaleActivity::class.java)
            startActivity(intent)
            //return customer
        }
//        binding.saleCard.setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity, ReplacedSaleActivity::class.java)
//            startActivity(intent)
//        }

        binding.goodsRWcard.setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity, proceedToDispatchActivity ::class.java)
            startActivity(intent)
        }

        binding.stockcard.setOnClickListener {
            ///first clear preveious stock requsition data
            sharedPrefHelper.clearStockList()
            val intent = Intent(this@MPOSDashboardActivity,StockRequisitionActivity::class.java)
            startActivity(intent)
        }

        binding.materialrcvCard .setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity,MaterialRecivingItemsActivity::class.java)
            startActivity(intent)
        }

        binding.pdtInventoryCard .setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity,ProductInventoryActivity::class.java)
            startActivity(intent)
        }

        binding.cashupcard .setOnClickListener {
            lifecycleScope.launch {

                val cashupTime = loginSession.getCashupDateTime().first()

                Log.d("cashuTime", cashupTime)
                if (isCashupOutdated(cashupTime)) {
                    Log.d("cashuTimecalled", cashupTime)
                  //  showCashupPopup(cashupTime)
                    val intent = Intent(this@MPOSDashboardActivity,CashUpActivity::class.java)
                    intent.putExtra("CASHUP_DATE_TIME", cashupTime)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    Log.d("intentdate",cashupTime)
                    startActivity(intent)
                } else {
                   // customerBottomSheet()
                    val intent = Intent(this@MPOSDashboardActivity,CashUpActivity::class.java)
                    //intent.putExtra("CASHUP_DATE_TIME", cashupTime)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    Log.d("intentdate",cashupTime)
                    startActivity(intent)
                }


            }


        }

        binding.salesPaymentCard .setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity,SalesAndPaymentActivity::class.java)
            startActivity(intent)
        }

        binding.expensecard .setOnClickListener {
            lifecycleScope.launch {
                val cashupTime = loginSession.getCashupDateTime().first()

                val intent = Intent(this@MPOSDashboardActivity,ExpenseRegisterActivity::class.java)
                intent.putExtra("CASHUP_DATE_TIME", cashupTime)
                startActivity(intent)
            }

        }

        binding.profileCard .setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity,ProfileAttendanceActivity::class.java)
            startActivity(intent)
        }

        binding.logout.setOnClickListener {
            showLogoutDialog()
        }

        val showNoticeDialog = intent.getBooleanExtra("SHOW_NOTICE_DIALOG", false)
        if (showNoticeDialog) {
            val lastReqDt = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
            viewmodel.callNoticesApi(lastReqDt, this@MPOSDashboardActivity)
        }


        val organisation_data = organisationDetailsHelper.getOrganisationData()

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter() // Add center crop
            .placeholder(R.drawable.mlogo) // Add a placeholder drawable
            .error(R.drawable.mlogo) // Add an error drawable (if needed)
            .into(binding.toolImage)

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.logo)
            .fitCenter() // Add center crop
            .placeholder(R.drawable.mlogo) // Add a placeholder drawable
            .error(R.drawable.mlogo) // Add an error drawable (if needed)
            .into(headerImageView)

    }

  /* private fun isCashupOutdated(cashupDateTime: String): Boolean {
       return try {
           val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
           val cashupDate = formatter.parse(cashupDateTime)
           val currentDate = Date()

           cashupDate != null && cashupDate.before(currentDate)
       } catch (e: Exception) {
           e.printStackTrace()
           false // fallback to safe behavior
       }
  }*/

//comment on 23 jun

   /* private fun isCashupOutdated(cashupDateTime: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
            val cashupDate = formatter.parse(cashupDateTime) ?: return false

            // Get calendar set to today
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // Subtract 1 day to get yesterday's start
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStart = calendar.time

            // Subtract 1 more day to get "day before yesterday"
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val dayBeforeYesterdayStart = calendar.time

            // If cashupDate is before "day before yesterday", show popup
            cashupDate.before(yesterdayStart)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }*/
//comnet on pallav sir
 /*   private fun isCashupOutdated(cashupDateTime: String): Boolean {

        return try {

            val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())

            val cashupDate = formatter.parse(cashupDateTime) ?: return false

            // Set calendar to today at 00:00

            val calendar = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, 0)

            calendar.set(Calendar.MINUTE, 0)

            calendar.set(Calendar.SECOND, 0)

            calendar.set(Calendar.MILLISECOND, 0)

            // Get start of yesterday

            calendar.add(Calendar.DAY_OF_YEAR, -1)

            val yesterdayStart = calendar.time

            // Check if cashupDate is before yesterdayStart

            cashupDate.before(yesterdayStart)

        } catch (e: Exception) {

            e.printStackTrace()

            false

        }

    }*/

    private fun isCashupOutdated(cashupDateTime: String): Boolean {
        return try {
            Log.d("🧾 isCashupOutdated()", "Raw date string: $cashupDateTime")

            val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
            val cleanDate = cashupDateTime.trim().replace("\"", "")  // ✅ Fix: remove extra quotes
            val cashupDate = formatter.parse(cleanDate) ?: run {
                Log.e("🧾 ParseError", "Date parsing returned null")
                return false
            }

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -1)

            val yesterdayStart = calendar.time

            val isOutdated = cashupDate.before(yesterdayStart)
            Log.d("🧾 OutdatedCheck", "CashupDate=$cashupDate, Outdated=$isOutdated")
            isOutdated

        } catch (e: Exception) {
            Log.e("🧾 Exception", "Parsing failed: ${e.message}")
            false
        }
    }



//pallav sir

    private fun showCashupPopup(cashupDateTime: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cashup, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btnOk).setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity, CashUpDetailsActivity::class.java)
            intent.putExtra("CASHUP_DATE_TIME", cashupDateTime)
            Log.d("intentdate",cashupDateTime)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
    private fun showNoticeDialog(title: String, message: String, detailUrl: String?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notice, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = message

        dialogView.findViewById<Button>(R.id.btnOk).setOnClickListener {
            if (!detailUrl.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detailUrl))
                startActivity(intent)
            }
            dialog.dismiss()
        }

        dialogView.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /* private fun showCashupPopup(cashupDateTime: String) {
         val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cashup, null)

         val alertDialog = AlertDialog.Builder(this)
             .setView(dialogView)
             .setCancelable(false)
             .create()

         // Optional: Transparent rounded corners
         alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

         val btnOk = dialogView.findViewById<Button>(R.id.btnOk)
         btnOk.setOnClickListener {
             val intent = Intent(this@MPOSDashboardActivity, CashUpDetailsActivity::class.java)
             intent.putExtra("CASHUP_DATE_TIME", cashupDateTime)
             intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
             startActivity(intent)
             alertDialog.dismiss()
         }

         alertDialog.show()
     }*/






    private fun customerBottomSheet() {

        val d_binding = CustomerDetailsBottomsheetBinding.inflate(layoutInflater)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(d_binding.root)

        viewmodel.loadingLiveData.removeObservers(this) // Remove previous observers to avoid re-triggering


        // Observing loading state
        viewmodel.loadingLiveData.observe(this) {
            d_binding.progress.isVisible = it.isProgress
        }

        viewmodel.get_customer_liveData.removeObservers(this) // Remove previous observers to avoid re-triggering


        // Observing customer data
//        viewmodel.get_customer_liveData.observe(this) {
//            if (it.status == 1) {
//                if (dialog.isShowing) {
//                    dialog.dismiss()
//                }
//                val intent = Intent(this@MPOSDashboardActivity, PointOfSaleActivity::class.java)
//                intent.putExtra("c_mobile", it.data.id)
//                startActivity(intent)
//            } else {
//                showMessage(it.message)
//            }
//        }

        viewmodel.get_customer_liveData.observe(this) {
            // This block will be triggered only once per update
            if (it.status == 1) {
                // Navigate to the POS details page
                val intent = Intent(this@MPOSDashboardActivity, PointOfSaleActivity::class.java)
                intent.putExtra("c_id", it.data.id)
                intent.putExtra("c_mobile", it.data.mobile_no?:"")
                intent.putExtra("c_name", it.data.customer_name?:"")
                intent.putExtra("c_tpin", it.data.tin_tpin_no?:"")

                startActivity(intent)
                if (dialog.isShowing) {
                   dialog.dismiss()
               }
            } else {
                showMessage(it.message)
            }
        }

        // Save button click listener
        d_binding.saveBtn.setOnClickListener {
            val cust_mobile_tpin = d_binding.mobileInput.text.toString()

            if (cust_mobile_tpin.isEmpty() || cust_mobile_tpin.length < 9 || cust_mobile_tpin.length <9 ) {
                showMessage("Enter Valid Customer Mobile No or TIN")
            } else if(d_binding.toggle.checkedRadioButtonId == R.id.mobile_btn) {
                //then assuming it's mobile no
             //   d_binding.saveBtn.isEnabled = false  // Disable button to prevent multiple clicks
                viewmodel.callGetCustomerDetailsApi(getCustomerReq(mobile_no = cust_mobile_tpin, tin_tpin_no = ""), this@MPOSDashboardActivity)
            }else{
                //then assuming  it's tpin no
                viewmodel.callGetCustomerDetailsApi(getCustomerReq(mobile_no = "", tin_tpin_no = cust_mobile_tpin), this@MPOSDashboardActivity)

            }
        }

        // Skip button click listener
        d_binding.skipBtn.setOnClickListener {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            val intent = Intent(this@MPOSDashboardActivity, PointOfSaleActivity::class.java)

            intent.putExtra("c_id", 0)
            intent.putExtra("c_mobile", "")
            intent.putExtra("c_name", "")
            intent.putExtra("c_tpin", "")

            startActivity(intent)
        }

        // Show the dialog
        dialog.show()
    }

//    private fun customerBottomSheet() {
//
//            val d_binding = CustomerDetailsBottomsheetBinding.inflate(layoutInflater)
//
//            val dialog = BottomSheetDialog(this)
//            dialog.setContentView(d_binding.root)
//            // dialog.setCancelable(false)
//            //dialog.setCanceledOnTouchOutside(false)
//
//        viewmodel.loadingLiveData.observe(this){
//            d_binding.progress.isVisible = it.isProgress
//
////            if(it.isMessage)
////                showMessage(it.message)
//        }
//
//        viewmodel.get_customer_liveData.observe(this){
//            if(it.status == 1){
//                dialog.dismiss()
//                val intent = Intent(this@MPOSDashboardActivity,PointOfSaleActivity::class.java)
//                intent.putExtra("c_mobile",it.data.id);
//                startActivity(intent)
//            }else{
//                showMessage(it.message)
//            }
//        }
//
//        d_binding.saveBtn.setOnClickListener {
//
//           val cust_mobile = d_binding.mobileInput.text.toString()
//
//            if(cust_mobile.isEmpty() || cust_mobile.length<9){
//                showMessage("Enter Valid Customer Mobile No")
//            }else{
//
//                viewmodel.callGetCustomerDetailsApi(getCustomerReq(mobile_no = cust_mobile),this@MPOSDashboardActivity)
//
//
//            }
//        }
//
//        d_binding.skipBtn.setOnClickListener {
//            dialog.dismiss()
//            val intent = Intent(this@MPOSDashboardActivity,PointOfSaleActivity::class.java)
//            intent.putExtra("c_mobile",0);
//            startActivity(intent)
//        }
//
//            dialog.show()
//
//
//    }

    private fun mposLogout() {

        val device_id = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        if(device_id.isEmpty()){
            showMessage("Couldn't fetch device id")
        }else if(storemanager_id .isEmpty()){
            showMessage("Coudn't fetch store manager info")
        }else{
            loginViewmodel.callLogoutApi(this,storemanager_id,device_id)
        }

        loginViewmodel.logoutLiveData.observe(this){
            if(it.status==1){
                //showMessage(it.message)
                CoroutineScope(Dispatchers.IO).launch {
                    loginSession.clearLoginSession()
                    val intent = Intent(this@MPOSDashboardActivity, MPOSLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            }else{
               /// showMessage(it.message)
                //forcefully logout
                CoroutineScope(Dispatchers.IO).launch {
                    loginSession.clearLoginSession()
                    val intent = Intent(this@MPOSDashboardActivity, MPOSLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }

    }

    private fun showLogoutDialog() {

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.logout_dialog_layout)
        dialog.setCancelable(false)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val cancel = dialog.findViewById<MaterialButton>(R.id.prefer_cancel)
        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
        val logoutImg = dialog.findViewById<ImageView>(R.id.dialog_logo)

        logoutMsg.text = "Are you sure you want to Logout ?"
        logoutMsg.textSize = 16F
        logoutImg.setImageResource(R.drawable.svg_off)
        logoutImg.scaleType = ImageView.ScaleType.FIT_CENTER

        confirm.setOnClickListener {
            dialog.dismiss()

            mposLogout()

    /*        CoroutineScope(Dispatchers.IO).launch {
                loginSession.clearLoginSession()
            }

            val intent = Intent(this@MPOSDashboardActivity, MPOSLoginActivity::class.java)
            startActivity(intent)
            finish()
*/

        }

        cancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMessage(msg: String) {

            Toast.makeText(this@MPOSDashboardActivity, msg, Toast.LENGTH_SHORT).show()

    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (actionBarDrawerToggle.onOptionsItemSelected(item)){
            true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if(drawer.isDrawerOpen(GravityCompat.START)){
            drawer.closeDrawer(GravityCompat.START)
        }else{
            super.onBackPressed()
        }
    }
}