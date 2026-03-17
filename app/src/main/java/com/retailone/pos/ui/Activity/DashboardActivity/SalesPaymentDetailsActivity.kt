package com.retailone.pos.ui.Activity.DashboardActivity

import NumberFormatter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.retailone.pos.R
import com.retailone.pos.adapter.SalesDetailsAdapter
import com.retailone.pos.databinding.ActivitySalesPaymentDetailsBinding
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.Dispatch.DispatchRequest
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.CancelSaleitemRequest
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsReq
import com.retailone.pos.models.PosSalesDetailsModel.SaleReceiptRes
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptReq
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptRes
import com.retailone.pos.utils.DateTimeFormatting
import com.retailone.pos.utils.PrinterUtil
import com.retailone.pos.viewmodels.DashboardViewodel.SalesPaymentViewmodel
import java.math.BigDecimal
import java.math.RoundingMode

class SalesPaymentDetailsActivity : AppCompatActivity() {

    lateinit var  binding: ActivitySalesPaymentDetailsBinding
    lateinit var viewmodel: SalesPaymentViewmodel
    lateinit var salesDetailsAdapter: SalesDetailsAdapter
    lateinit var  localizationData: LocalizationData
    private var printerUtil: PrinterUtil? = null

    // Store current sale and store IDs for copy receipt
    private var currentSalesId: String = ""
    private var currentStoreId: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesPaymentDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewmodel = ViewModelProvider(this)[SalesPaymentViewmodel::class.java]
        localizationData = LocalizationHelper(this).getLocalizationData()
        enableBackButton()
        setToolbarImage()
        prepareRecycleview()

        // Setup Copy Receipt button
        setupCopyReceiptButton()
        printerUtil = PrinterUtil(this)


        val saleid = intent?.getStringExtra("sale_id")
        saleid?.let {
            currentSalesId = it
            viewmodel.callSalesDetailsApi(SalesDetailsReq(it),this)
        }

        viewmodel.loadingLiveData.observe(this){
            binding.progress.isVisible = it.isProgress

            if(it.isMessage)
                showMessage(it.message)
        }

        // Add this NEW observer for copy receipt response
        viewmodel.saleReceiptLiveData.observe(this) { response ->
            Log.d("SaleReceipt_Observer", "Status: ${response.status}, Message: ${response.message}")
            Log.d("SaleReceipt_Observer", "==========================================")
            Log.d("SaleReceipt_Observer", "RAW RESPONSE RECEIVED")
            Log.d("SaleReceipt_Observer", "Status: ${response.status}")
            Log.d("SaleReceipt_Observer", "Message: ${response.message}")
            Log.d("SaleReceipt_Observer", "Full Response Object: $response")
            Log.d("SaleReceipt_Observer", "==========================================")

            if (response.status == 1 && response.data != null) {
                Toast.makeText(this, "Printing sale receipt...", Toast.LENGTH_SHORT).show()

                Thread {
                    try {
                        printerUtil?.printSaleReceipt(response)  // <-- uses new method
                        runOnUiThread {
                            Toast.makeText(this, "Receipt printed successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("SaleReceipt_Observer", "Print error: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            } else {
                val errorMsg = response.message ?: "Failed to retrieve receipt data"
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                Log.e("SaleReceipt_Observer", "Error: $errorMsg")
            }
        }


        viewmodel.salesdetails_liveData.observe(this){

            if(it.status==1){
                val salesdata = it.data[0]

                // Store store_id for copy receipt
                currentStoreId = salesdata.store_id?.toString() ?: ""

                // 🔍 Log the API response to check discount_amount per item
                salesdata.sales_items.forEachIndexed { index, item ->
                    Log.d("SalesDetails", "Item $index: ${item.product_name}")
                    Log.d("SalesDetails", "  Sub Total: ${item.sub_total}")
                    Log.d("SalesDetails", "  Total: ${item.total_amount}")
                    // Check if discount_amount exists in the response
                    Log.d("SalesDetails", "  Raw JSON: $item")
                }

                val formattedPrice = NumberFormatter().formatPrice(salesdata.grand_total.toString(),localizationData)
                val sum = salesdata.summary
                val subtotalValue = (sum?.total_sub_total ?: salesdata.sub_total)
                val taxValue      = (sum?.total_tax_amount ?: salesdata.tax_amount)
                val totalValue    = (salesdata.grand_total)

                val safeTax = taxValue ?: 0.0
                binding.apply {
                    orderId.text = "ID: "+salesdata?.invoice_id?.toString()
                    date.text = "Date: "+DateTimeFormatting.formatGlobalTime(salesdata.created_at,localizationData.timezone)
                    val spotPercent = salesdata.spot_discount_percentage?.toDoubleOrNull() ?: 0.0
                    val spotAmount = salesdata.spot_discount_amount?.toDoubleOrNull() ?: 0.0
                    if (spotPercent > 0.0 || spotAmount > 0.0) {
                        val discountPercent = if (spotPercent % 1.0 == 0.0) spotPercent.toInt().toString() else spotPercent.toString()
                        spotDiscountPercentText.isVisible = true
                        spotDiscountAmountText.isVisible = true
                        spotDiscountPercentText.text = "Spot Discount: $discountPercent%"
                        spotDiscountAmountText.text = "Spot Discount Amount: " + NumberFormatter().formatPrice(salesdata.spot_discount_amount.toString(), localizationData)
                    } else {
                        spotDiscountPercentText.isVisible = false
                        spotDiscountAmountText.isVisible = false
                    }


                    grandtotal.text = "Grand total: $formattedPrice"
                    paymenttype.text = "Payment type: "+salesdata.payment_type.toString()
                    storename.text = "Store name: "+(salesdata.store_details.store_name?:"")
                    //  vat.text = "(+) Tax @"+(salesdata.tax?:"")+"%"+":   "+"ZWL"+(salesdata.tax_amount?:"")
                    //customername.text = "Customer name: "+(salesdata.customer.customer_name?:"")
                    customername.text = "Customer name: " + (salesdata.customer?.customer_name ?: "N/A")
                    // binding.btnConfirmcancel.isVisible = salesdata.grand_total >= 0
                    binding.btnConfirmcancel.isVisible =
                        salesdata.grand_total >= 0 && salesdata.total_refunded_amount <= 0.0


                    val roundedSubtotal = BigDecimal.valueOf(subtotalValue)
                        .setScale(0, RoundingMode.HALF_UP)

                    tvSubtotalValue.text = NumberFormatter().formatPrice(
                        roundedSubtotal.toPlainString(),
                        localizationData
                    )
                    //tvSubtotalValue.text = NumberFormatter().formatPrice(subtotalValue.toString(), localizationData)
                    val roundedTaxStr = BigDecimal.valueOf(safeTax)
                        .setScale(0, RoundingMode.HALF_UP)
                        .toPlainString()

                    tvTaxValue.text = NumberFormatter().formatPrice(
                        roundedTaxStr,
                        localizationData
                    )
                    tvTotalValue.text    = NumberFormatter().formatPrice(totalValue.toString(), localizationData)

                }

                salesDetailsAdapter = SalesDetailsAdapter(this,salesdata)

                binding.itemsRcv.adapter = salesDetailsAdapter


                binding.btnConfirmcancel.setOnClickListener {
                    //var invoiceId = binding.orderId.text.toString().trim()
                    val invoiceIdRaw = binding.orderId.text.toString().trim()
                    val invoiceId = invoiceIdRaw.replace("ID:", "").trim()  // ✅ clean prefix


                    if (invoiceId.isEmpty()) {
                        Toast.makeText(this, "Invoice ID is required", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val request = CancelSaleitemRequest(
                        invoiceID = invoiceId

                    )
                    Log.d("CancelSale", "Calling API with invoiceId: $invoiceId")

                    viewmodel.callCancelSaleAPI(request, this)
                }

            }


        }

    }

    private fun setupCopyReceiptButton() {
        binding.btnCopyReceipt.setOnClickListener {
            Log.d("CopyReceipt_Button", "==========================================")
            Log.d("CopyReceipt_Button", "Copy Receipt Button CLICKED!")
            Log.d("CopyReceipt_Button", "Current Sales ID: '$currentSalesId'")
            Log.d("CopyReceipt_Button", "Current Store ID: '$currentStoreId'")
            Log.d("CopyReceipt_Button", "==========================================")

            if (currentSalesId.isEmpty()) {
                Toast.makeText(this, "No sale selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentStoreId.isEmpty()) {
                Toast.makeText(this, "Store information not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if printer is available
            if (printerUtil == null) {
                Toast.makeText(this, "Printer not initialized", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("CopyReceipt", "Requesting copy receipt - Sales ID: $currentSalesId, Store ID: $currentStoreId")

            // Disable button during API call
            binding.btnCopyReceipt.isEnabled = false

            // Call API - response will be handled by observer
            // For Sale type — pass "Sale" explicitly
            viewmodel.callSaleReceiptApi(currentSalesId, currentStoreId, this)

            // Re-enable button after a delay
            binding.btnCopyReceipt.postDelayed({
                binding.btnCopyReceipt.isEnabled = true
            }, 2000)
        }
    }






    private fun showMessage(msg: String) {
        Toast.makeText(this@SalesPaymentDetailsActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun prepareRecycleview() {
        binding.itemsRcv.apply {
            layoutManager = LinearLayoutManager(this@SalesPaymentDetailsActivity,
                RecyclerView.VERTICAL,false)

        }
    }



    private fun setToolbarImage() {
        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter() // Add center crop
            .placeholder(R.drawable.mlogo) // Add a placeholder drawable
            .error(R.drawable.mlogo) // Add an error drawable (if needed)
            .into(binding.image)
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
    override fun onResume() {
        super.onResume()
        printerUtil?.registerBatteryReceiver()
    }

    override fun onPause() {
        super.onPause()
        printerUtil?.unregisterBatteryReceiver()
    }


}
