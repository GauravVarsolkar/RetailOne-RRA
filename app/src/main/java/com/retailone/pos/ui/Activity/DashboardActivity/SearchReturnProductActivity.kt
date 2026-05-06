package com.retailone.pos.ui.Activity.DashboardActivity

import NumberFormatter
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.retailone.pos.R
import com.retailone.pos.adapter.ReturnReasonAdapter
import com.retailone.pos.adapter.ReturnSalesItemAdapter
import com.retailone.pos.databinding.ActivitySearchReturnProductBinding
import com.retailone.pos.interfaces.OnReturnQuantityChangeListener
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.LocalReturnCartHelper
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.PosSalesDetailsModel.ReceiptType
import com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnedItem
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.Store
import com.retailone.pos.models.ReturnSalesItemModel.SalesItem
import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.ReturnReasonData
import com.retailone.pos.ui.Activity.MPOSDashboardActivity
import com.retailone.pos.utils.PrinterUtil
import com.retailone.pos.viewmodels.DashboardViewodel.PointofSaleViewmodel
import com.retailone.pos.viewmodels.DashboardViewodel.ReturnSalesDetailsViewmodel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SearchReturnProductActivity : AppCompatActivity(), OnReturnQuantityChangeListener {

    lateinit var binding: ActivitySearchReturnProductBinding
    lateinit var returnsale_viewmodel: ReturnSalesDetailsViewmodel
    lateinit var pos_viewmodel: PointofSaleViewmodel  // ✅ ADD THIS
    lateinit var returnSalesItemAdapter: ReturnSalesItemAdapter
    var returnItemList = mutableListOf<SalesItem>()
    var returnReasonList: MutableList<ReturnReasonData> = mutableListOf()
    lateinit var returnItemData: ReturnItemData
    var reasonid = -1
    var storeid = 0
    var store_manager_id = "0"
    lateinit var localizationData: LocalizationData
    private var printerUtil: PrinterUtil? = null
    private var returnbatchItemList = mutableListOf<BatchReturnItem>()
    private var currentBatchList = mutableListOf<BatchReturnItem>()
    private var currentSalesId: String = ""
    private var currentStoreId: String = ""
    private var currentReturnedInvoiceId: String = ""


    // ✅ ADD THIS: Receipt Type Selection Variables
    private var receiptTypeList = mutableListOf<ReceiptType>()
    private var selectedReceiptType: ReceiptType? = null
    private var enteredInvoiceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchReturnProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.relativeLayout.isVisible = false
        binding.relativeLayout2.isVisible = false

        val invoiceIdFromIntent = intent.getStringExtra("invoice_id")

        // ✅ Initialize both viewmodels
        returnsale_viewmodel = ViewModelProvider(this)[ReturnSalesDetailsViewmodel::class.java]
        pos_viewmodel = ViewModelProvider(this)[PointofSaleViewmodel::class.java]  // ✅ ADD THIS

        localizationData = LocalizationHelper(this).getLocalizationData()
        val loginSession = LoginSession.getInstance(this)

        lifecycleScope.launch {
            storeid = loginSession.getStoreID().first().toInt()
            store_manager_id = loginSession.getStoreManagerID().first().toString()
            if (!invoiceIdFromIntent.isNullOrEmpty()) {
                binding.searchBar.setQuery(invoiceIdFromIntent, false)
                returnsale_viewmodel.callReturnSalesDetailsApi(
                    ReturnItemReq(invoice_id = invoiceIdFromIntent),
                    this@SearchReturnProductActivity
                )
            }
        }

        binding.addcart.setOnClickListener {
            returnbatchItemList.clear()
            currentBatchList.clear()
            if (binding.searchBar.query.toString().trim() == "") {
                showMessage("Enter a valid Invoice ID")
            } else {
                returnsale_viewmodel.callReturnSalesDetailsApi(
                    ReturnItemReq(
                        invoice_id = binding.searchBar.query.toString().trim()
                    ), this@SearchReturnProductActivity
                )
            }
        }

        binding.addproductLayout.setOnClickListener {
            showMessage("Enter Invoice ID and Search")
        }

        returnsale_viewmodel.loadingLiveData.observe(this) {
            binding.progress.isVisible = it.isProgress
            if (it.isMessage) {
                binding.nextlayout.isEnabled = true
                showMessage(it.message)
            }
        }

        printerUtil = PrinterUtil(this)
        enableBackButton()
        preparePositemRCV()
        returnsale_viewmodel.callSaleReturnReasonApi(this)

        returnsale_viewmodel.returnitem_liveData.observe(this) {
            Log.d("BACKEND_RAW_RESPONSE", "========== Return Sales Details API Response ==========")
            Log.d("BACKEND_RAW_RESPONSE", "Raw JSON: ${Gson().toJson(it)}")
            Log.d("BACKEND_RAW_RESPONSE", "Status: ${it.status}")
            Log.d("BACKEND_RAW_RESPONSE", "Message: ${it.message}")
            Log.d("BACKEND_RAW_RESPONSE", "Data size: ${it.data.size}")
            Log.d("BACKEND_RAW_RESPONSE", "======================================================")

            if (it.data.isNotEmpty()) {
                val data = it.data[0]


                if (data.total_refunded_amount > 0) {
                    currentSalesId = data.id.toString()
                    currentStoreId = storeid.toString()

                    showMessage("This invoice has already been returned and cannot be returned again.")
                    returnItemData = data
                    returnItemList = data.salesItems.toMutableList()
                    returnSalesItemAdapter = ReturnSalesItemAdapter(
                        it.data,
                        this@SearchReturnProductActivity,
                        this
                    ) { batchList ->
                        Log.d("rtn", batchList.toString())
                        returnbatchItemList = batchList.toMutableList()
                        currentBatchList = batchList.toMutableList()
                    }

                    binding.positemRcv.adapter = returnSalesItemAdapter
                    binding.positemRcv.isVisible = true
                    binding.addproductLayout.isVisible = false
                    binding.reasonLayout.isVisible = false
                    binding.invoiceIdLayout.isVisible = false
                    binding.btnCopyReceipt.isVisible = true

                    // ✅ Hide receipt type dropdown for already returned invoices
                    binding.receiptTypeLayout.isVisible = false

                    binding.summaryCard.isVisible = true
                    val normalDiscountAmt = data.discount_amount.toSafeDouble()
                    val spotDiscountAmt = data.spot_discount_amount.toSafeDouble()
                    val spotDiscountPct = data.spot_discount_percentage.toSafeDouble()

                    updateSummaryCard(
                        subtotal = data.sub_total.toSafeDouble(),
                        tax = data.tax_amount.toSafeDouble(),
                        total = data.grand_total.toSafeDouble(),
                        discount = normalDiscountAmt,
                        spotDiscountAmount = spotDiscountAmt,
                        spotDiscountPercent = spotDiscountPct
                    )

                    binding.paymentcard.isVisible = false
                    binding.relativeLayout.isVisible = false
                    binding.relativeLayout2.isVisible = false

                } else {
                    returnItemData = data
                    returnItemList = data.salesItems.toMutableList()
                    currentSalesId = data.id.toString()
                    currentStoreId = storeid.toString()  // already fetched from LoginSession in lifecycleScope

                    returnSalesItemAdapter = ReturnSalesItemAdapter(
                        it.data,
                        this@SearchReturnProductActivity,
                        this
                    ) { batchList ->
                        Log.d("rtn", batchList.toString())
                        returnbatchItemList = batchList.toMutableList()
                        currentBatchList = batchList.toMutableList()
                        recalculateTotalsFromBatches()
                    }

                    binding.positemRcv.adapter = returnSalesItemAdapter
                    binding.positemRcv.isVisible = true
                    binding.addproductLayout.isVisible = false
                    binding.reasonLayout.isVisible = true

                    // ✅ Show receipt type dropdown for normal returns
                    binding.receiptTypeLayout.isVisible = true
                    binding.invoiceIdLayout.isVisible = true

                    binding.summaryCard.isVisible = false
                    binding.relativeLayout.isVisible = true
                    binding.relativeLayout2.isVisible = true
                    binding.paymentcard.isVisible = false

                    val subtotalValue = data.sub_total.toSafeDouble()
                    val taxValue = data.tax_amount.toSafeDouble()
                    val grandValue = data.grand_total.toSafeDouble()

                    val roundedSubtotal = BigDecimal.valueOf(subtotalValue)
                        .setScale(0, RoundingMode.HALF_UP)
                    val roundedTax = BigDecimal.valueOf(taxValue)
                        .setScale(0, RoundingMode.HALF_UP)
                    val roundedGrand = BigDecimal.valueOf(grandValue)
                        .setScale(0, RoundingMode.HALF_UP)

                    binding.subtotal.setText(roundedSubtotal.toPlainString())

                    val taxDisplay = formatTaxForDisplay(data.tax)
                    binding.taxfield.setText("(+) Tax @$taxDisplay")

                    binding.taxAmount.setText(roundedTax.toPlainString())
                    binding.alltotalAmount.setText(
                        NumberFormatter().formatPrice(
                            roundedGrand.toPlainString(),
                            localizationData
                        )
                    )

                    updateSeparateDiscountUI(
                        normalDiscountAmount = data.discount_amount.toSafeDouble(),
                        spotDiscountPercent = data.spot_discount_percentage.toSafeDouble(),
                        spotDiscountAmount = data.spot_discount_amount.toSafeDouble()
                    )
                }

                Log.d("VISIBILITY_DEBUG", "summaryCard.isVisible = ${binding.summaryCard.isVisible}")
                Log.d("VISIBILITY_DEBUG", "paymentcard.isVisible = ${binding.paymentcard.isVisible}")
                Log.d("VISIBILITY_DEBUG", "relativeLayout.isVisible = ${binding.relativeLayout.isVisible}")
                Log.d("VISIBILITY_DEBUG", "relativeLayout2.isVisible = ${binding.relativeLayout2.isVisible}")

            } else {
                showMessage("No Invoice Found")

                binding.positemRcv.isVisible = false
                binding.addproductLayout.isVisible = true
                binding.reasonLayout.isVisible = false
                binding.receiptTypeLayout.isVisible = false  // ✅ Hide receipt type dropdown
                binding.summaryCard.isVisible = false
                binding.paymentcard.isVisible = false
                binding.relativeLayout.isVisible = false
                binding.relativeLayout2.isVisible = false
            }
        }

        returnsale_viewmodel.returnsalesubmit_liveData.observe(this) {
            binding.nextlayout.isEnabled = true
            if (it.status == 1) {
                showSucessDialog(it.message, it)
            } else {
                showMessage(it.message)
            }
        }


        setToolbarImage()

        returnsale_viewmodel.salesreturnreason_liveData.observe(this) {
            returnReasonList = it.data.toMutableList()
            binding.reasonInput.setAdapter(ReturnReasonAdapter(this, 0, returnReasonList))
        }

        binding.reasonInput.setOnClickListener {
            if (returnReasonList.isEmpty()) {
                showMessage("Return reason not found, try after sometime")
            }
        }

        binding.reasonInput.setOnItemClickListener { parent, view, position, id ->
            binding.reasonInput.setText(returnReasonList[position].reason_name, false)
            reasonid = returnReasonList[position].id
        }

        // ✅ ADD THIS: Observer for receipt types
        pos_viewmodel.receiptTypeLiveData.observe(this) { response ->
            if (response.status == 1) {
                receiptTypeList.clear()
                receiptTypeList.addAll(response.data ?: emptyList())
                populateReceiptTypeDropdown()
            } else {
                showMessage("Failed to load receipt types")
            }
        }

        binding.nextlayout.setOnClickListener {
            if (returnbatchItemList.isNotEmpty()) {
                binding.nextlayout.isEnabled = false
                callReturnAPI(returnbatchItemList)
            } else {
                showMessage("You haven't Return anything")
            }
        }
        // Observer for copy receipt response
        returnsale_viewmodel.copyReceiptLiveData.observe(this) { response ->
            Log.d("CopyReceipt_DEBUG", "Observer fired!")
            Log.d("CopyReceipt_Observer", "==========================================")
            Log.d("CopyReceipt_Observer", "Copy Receipt Response Received")
            Log.d("CopyReceipt_Observer", "Status: ${response.status}")
            Log.d("CopyReceipt_Observer", "Message: ${response.message}")
            Log.d("CopyReceipt_Observer", "Data null?: ${response.data == null}")
            Log.d("CopyReceipt_Observer", "Full Response: ${Gson().toJson(response)}")  // ← ADD THIS
            Log.d("CopyReceipt_Observer", "==========================================")

            if (response.status == 1 && response.data != null) {
                Toast.makeText(this, "Printing copy receipt...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        printerUtil?.printCopyReceipt(response)
                        runOnUiThread {
                            Toast.makeText(this, "Receipt printed successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("CopyReceipt_Observer", "Print error: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            } else {
                val errorMsg = response.message ?: "Failed to retrieve receipt data"
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }


        // ✅ ADD THESE: Setup and fetch receipt types
        setupReceiptTypeDropdown()
        fetchReceiptTypes()
        setupCopyReceiptButton()
    }

    private fun updateSummaryCard(
        subtotal: Double,
        tax: Double,
        total: Double,
        discount: Double = 0.0,
        spotDiscountAmount: Double = 0.0,
        spotDiscountPercent: Double = 0.0
    ) {
        // Round to whole numbers as per your existing logic
        val roundedSubtotal = BigDecimal.valueOf(subtotal).setScale(0, RoundingMode.HALF_UP).toDouble()
        val roundedTotal = BigDecimal.valueOf(total).setScale(0, RoundingMode.HALF_UP).toDouble()

        // 📌 FIX: Calculate exact tax to display so that the math perfectly adds up on screen.
        // Mathematical rule: Subtotal - Discounts + Tax = Total
        // Therefore: Tax = Total - Subtotal + Discounts
        val calculatedTax = roundedTotal - roundedSubtotal + discount + spotDiscountAmount

        binding.tvSubtotalValue.text = NumberFormatter().formatPrice(
            String.format(Locale.US, "%.2f", roundedSubtotal),
            localizationData
        )

        binding.tvTaxValue.text = NumberFormatter().formatPrice(
            String.format(Locale.US, "%.2f", calculatedTax),
            localizationData
        )

        binding.tvTotalValue.text = NumberFormatter().formatPrice(
            String.format(Locale.US, "%.2f", roundedTotal),
            localizationData
        )

        // Normal discount row
        if (discount > 0.0) {
            binding.discountSummaryRow.isVisible = true
            binding.tvDiscountValue.text = NumberFormatter().formatPrice(
                String.format(Locale.US, "%.2f", discount), localizationData
            )
        } else {
            binding.discountSummaryRow.isVisible = false
        }

        // Spot discount row
        if (spotDiscountAmount > 0.0) {
            binding.spotDiscountSummaryRow.isVisible = true
            if (spotDiscountPercent > 0.0) {
                binding.tvSpotDiscountLabel.text =
                    "(-) Spot Discount ${"%.2f".format(Locale.US, spotDiscountPercent)}%"
            } else {
                binding.tvSpotDiscountLabel.text = "(-) Spot Discount"
            }
            binding.tvSpotDiscountValue.text = NumberFormatter().formatPrice(
                String.format(Locale.US, "%.2f", spotDiscountAmount), localizationData
            )
        } else {
            binding.spotDiscountSummaryRow.isVisible = false
        }
    }
    private fun setupCopyReceiptButton() {
        Log.d("CopyReceipt_Button", "setupCopyReceiptButton() called - registering listener")  // ← ADD
        binding.btnCopyReceipt.setOnClickListener {
            Log.d("CopyReceipt_Button", "Copy Receipt Button CLICKED!")
            Log.d("CopyReceipt_Button", "Current Sales ID: '$currentSalesId'")
            Log.d("CopyReceipt_Button", "Current Store ID: '$currentStoreId'")

            if (currentSalesId.isEmpty()) {
                Toast.makeText(this, "No sale selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentStoreId.isEmpty()) {
                Toast.makeText(this, "Store information not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (printerUtil == null) {
                Toast.makeText(this, "Printer not initialized", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnCopyReceipt.isEnabled = false

            returnsale_viewmodel.callCopyReceiptApi(currentSalesId, currentStoreId, this)

            binding.btnCopyReceipt.postDelayed({
                binding.btnCopyReceipt.isEnabled = true
            }, 2000)
        }
    }



    private fun formatTaxForDisplay(raw: Any?): String {
        val s0 = raw?.toString()?.trim().orEmpty()
        if (s0.isEmpty()) return "0"

        val s1 = s0.replace(Regex("[^0-9.,]"), "").replace(',', '.')
        if (s1.isEmpty() || s1 == ".") return "0"

        if (s1.contains('.')) {
            return try {
                val value = BigDecimal(s1)
                val rounded = value.setScale(0, RoundingMode.HALF_UP)
                rounded.toPlainString()
            } catch (_: Exception) {
                s1
            }
        }

        val n = s1.toLongOrNull() ?: return s1
        val scaled = n / 10.0
        return BigDecimal.valueOf(scaled).setScale(0, RoundingMode.HALF_UP).toPlainString()
    }

    private fun purchasedQtyFor(salesItemId: Int): Int {
        val q = returnItemData.sales_items.orEmpty().firstOrNull { it.id == salesItemId }?.quantity
            ?: 0.0
        return kotlin.math.ceil(q).toInt()
    }

    private fun buildReplaceLines(): List<ReturnedItem> {
        val defectMap: Map<Int, Pair<Int, Int>> = returnbatchItemList
            .groupBy { it.sales_item_id }
            .mapValues { (_, batches) ->
                val totalBoxes = batches.sumOf { it.defective_boxes ?: 0 }
                val totalPacks = batches.sumOf { it.defective_bottles ?: 0 }
                totalBoxes to totalPacks
            }

        val output = mutableListOf<ReturnedItem>()

        val cartLines = LocalReturnCartHelper.getCartItems(this)
        if (cartLines.isNotEmpty()) {
            cartLines.forEach { line ->
                val (boxes, packs) = defectMap[line.id] ?: (0 to 0)
                output.add(
                    ReturnedItem(
                        id = line.id,
                        return_quantity = line.return_quantity,
                        defective_boxes = boxes,
                        defective_bottles = packs
                    )
                )
            }
            return output
        }

        if (defectMap.isNotEmpty()) {
            val qtyMap = returnbatchItemList
                .groupBy { it.sales_item_id }
                .mapValues { (_, batches) ->
                    batches.sumOf { it.return_quantity ?: 0 }
                }

            defectMap.forEach { (salesItemId, boxesPacks) ->
                val (boxes, packs) = boxesPacks
                val userQty = qtyMap[salesItemId] ?: 0

                output.add(
                    ReturnedItem(
                        id = salesItemId,
                        return_quantity = userQty,
                        defective_boxes = boxes,
                        defective_bottles = packs
                    )
                )
            }

            return output.filter {
                (it.defective_boxes ?: 0) > 0 ||
                        (it.defective_bottles ?: 0) > 0 ||
                        (it.return_quantity ?: 0) > 0
            }
        }

        val detailed = returnItemData.sales_items.orEmpty()
        detailed.forEach { si ->
            output.add(
                ReturnedItem(
                    id = si.id,
                    return_quantity = kotlin.math.ceil(si.quantity).toInt(),
                    defective_boxes = 0,
                    defective_bottles = 0
                )
            )
        }

        return output
    }

    private fun callReturnAPI(returnbatchItemList: MutableList<BatchReturnItem>) {
        val savedItems = buildReplaceLines()
        if (savedItems.isEmpty()) {
            showMessage("No items saved for return.")
            return
        }

        // ✅ ADD THIS: Validate receipt type selection
        if (selectedReceiptType == null) {
            showMessage("Please Select Receipt Type")
            return
        }
        enteredInvoiceId = binding.invoiceIdInput.text.toString().trim()

        if (reasonid != -1) {
            val return_data = ReturnSaleReq(
                store_id = storeid,
                store_manager_id = store_manager_id.toInt(),
                reason_id = reasonid,
                sales_id = returnItemData.id,
                returned_items = savedItems,
                trxn_code = selectedReceiptType?.code ?: "" ,
                invoice_id = enteredInvoiceId
            )
            returnsale_viewmodel.callReturnSalesSubmitApi(
                return_data, this@SearchReturnProductActivity
            )
            LocalReturnCartHelper.clearCart(this)
        } else {
            showMessage("please select any reason for return")
        }
    }

    private fun setToolbarImage() {
        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()
        Glide.with(this).load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter()
            .placeholder(R.drawable.mlogo)
            .error(R.drawable.mlogo)
            .into(binding.image)
    }

    private fun preparePositemRCV() {
        binding.positemRcv.apply {
            layoutManager = LinearLayoutManager(
                this@SearchReturnProductActivity, RecyclerView.VERTICAL, false
            )
        }
    }

    private fun enableBackButton() {
        setSupportActionBar(binding.toolbar)
        val actionbar = supportActionBar
        actionbar!!.title = "New Activity"
        actionbar.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_back_arrow_white)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this@SearchReturnProductActivity, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onReturnQuantityChange(position: Int, newQuantity: Int) {
        Log.d("QUANTITY_CHANGE_DEBUG", "========== onReturnQuantityChange ==========")
        Log.d("QUANTITY_CHANGE_DEBUG", "position: $position")
        Log.d("QUANTITY_CHANGE_DEBUG", "newQuantity: $newQuantity")
        returnItemList[position].return_quantity = newQuantity
        returnItemList[position].refund_amount = newQuantity * returnItemList[position].retail_price

        Log.d("QUANTITY_CHANGE_DEBUG", "Calling recalculateTotals()")
        recalculateTotals()
    }

    private fun recalculateTotalsFromBatches() {
        Log.e("RETURN_CALC_DEBUG", "========== recalculateTotalsFromBatches() CALLED ==========")

        if (!this::returnItemData.isInitialized) {
            Log.e("RETURN_CALC_DEBUG", "returnItemData not initialized")
            return
        }

        binding.relativeLayout.isVisible = true
        binding.relativeLayout2.isVisible = true

        val taxPercentage = returnItemData.tax.toSafeDouble()
        val spotDiscountPercent = returnItemData.spot_discount_percentage.toSafeDouble()
        val originalNormalDiscount = returnItemData.discount_amount.toSafeDouble()
        val originalSubtotal = returnItemData.sub_total.toSafeDouble()

        // No batch selected -> show original invoice values from API
        if (currentBatchList.isEmpty() || currentBatchList.all { (it.batch_return_quantity ?: 0) == 0 }) {
            val subtotalValue = returnItemData.sub_total.toSafeDouble()
            val taxValue = returnItemData.tax_amount.toSafeDouble()
            val grandValue = returnItemData.grand_total.toSafeDouble()

            val apiSpotDiscountAmount = returnItemData.spot_discount_amount.toSafeDouble()

            val roundedSubtotal = BigDecimal.valueOf(subtotalValue).setScale(0, RoundingMode.HALF_UP)
            val roundedTax = BigDecimal.valueOf(taxValue).setScale(0, RoundingMode.HALF_UP)
            val roundedGrandTotal = BigDecimal.valueOf(grandValue).setScale(0, RoundingMode.HALF_UP)

            // Update Old bottom views (if visible)
            binding.subtotal.setText(roundedSubtotal.toPlainString())
            binding.taxAmount.setText(roundedTax.toPlainString())
            binding.alltotalAmount.setText(
                NumberFormatter().formatPrice(
                    roundedGrandTotal.toPlainString(),
                    localizationData
                )
            )

            // 📌 Call the unified update function here
            updateSummaryCard(
                subtotal = subtotalValue,
                tax = taxValue,
                total = grandValue,
                discount = originalNormalDiscount,
                spotDiscountAmount = apiSpotDiscountAmount,
                spotDiscountPercent = spotDiscountPercent
            )

            updateSeparateDiscountUI(
                normalDiscountAmount = originalNormalDiscount,
                spotDiscountPercent = spotDiscountPercent,
                spotDiscountAmount = apiSpotDiscountAmount
            )

            Log.d("RETURN_CALC_DEBUG", "No batch selected - original API values shown")
            return
        }

        // Step 1: Calculate subtotal from selected return batches (tax exclusive)
        var subtotal = 0.0

        currentBatchList.forEachIndexed { index, batch ->
            val qty = batch.batch_return_quantity ?: 0
            val retailPrice = batch.retail_price ?: 0.0

            if (qty > 0) {
                val itemDetails = returnItemData.sales_items.orEmpty()
                    .firstOrNull { it.id == batch.sales_item_id }

                val taxExclusivePrice = itemDetails?.tax_exclusive_price
                    ?: run {
                        val divisor = 1 + (taxPercentage / 100.0)
                        retailPrice / divisor
                    }

                val itemSubtotal = taxExclusivePrice * qty
                subtotal += itemSubtotal
            }
        }

        // Step 2: Spot discount on subtotal
        val spotDiscountAmount = subtotal * spotDiscountPercent / 100.0

        // Step 3: Proportional normal discount
        val normalDiscountAmount =
            if (originalSubtotal > 0) {
                (subtotal / originalSubtotal) * originalNormalDiscount
            } else {
                0.0
            }

        // Step 4: Subtotal after all discounts
        val subtotalAfterDiscount = subtotal - spotDiscountAmount - normalDiscountAmount

        // Step 5: Tax on discounted subtotal
        val taxAmount = subtotalAfterDiscount * taxPercentage / 100.0

        // Step 6: Grand total
        val grandTotal = subtotalAfterDiscount + taxAmount

        val roundedSubtotal = BigDecimal.valueOf(subtotal).setScale(0, RoundingMode.HALF_UP)
        val roundedTax = BigDecimal.valueOf(taxAmount).setScale(0, RoundingMode.HALF_UP)
        val roundedGrandTotal = BigDecimal.valueOf(grandTotal).setScale(0, RoundingMode.HALF_UP)

        // Update old bottom views (if visible)
        binding.subtotal.setText(roundedSubtotal.toPlainString())
        binding.taxAmount.setText(roundedTax.toPlainString())
        binding.alltotalAmount.setText(
            NumberFormatter().formatPrice(
                roundedGrandTotal.toPlainString(),
                localizationData
            )
        )

        // 📌 Call the unified update function here too
        updateSummaryCard(
            subtotal = subtotal,
            tax = taxAmount,
            total = grandTotal,
            discount = normalDiscountAmount,
            spotDiscountAmount = spotDiscountAmount,
            spotDiscountPercent = spotDiscountPercent
        )

        updateSeparateDiscountUI(
            normalDiscountAmount = normalDiscountAmount,
            spotDiscountPercent = spotDiscountPercent,
            spotDiscountAmount = spotDiscountAmount
        )

        Log.d("RETURN_CALC_DEBUG", "========== recalculateTotalsFromBatches() END ==========")
    }
    private fun recalculateTotals() {
        recalculateTotalsFromBatches()
    }
    private fun Any?.toSafeDouble(): Double {
        return when (this) {
            null -> 0.0
            is Number -> this.toDouble()
            is String -> this.trim().toDoubleOrNull() ?: 0.0
            else -> this.toString().trim().toDoubleOrNull() ?: 0.0
        }
    }

    private fun updateSeparateDiscountUI(
        normalDiscountAmount: Double,
        spotDiscountPercent: Double,
        spotDiscountAmount: Double
    ) {
        // Normal discount row
        if (normalDiscountAmount > 0) {
            binding.delChargeLayout.isVisible = true
            binding.discountvalue.text = NumberFormatter().formatPrice(
                String.format(Locale.US, "%.2f", normalDiscountAmount),
                localizationData
            )
        } else {
            binding.delChargeLayout.isVisible = false
            binding.discountvalue.text = NumberFormatter().formatPrice("0.00", localizationData)
        }

        // Spot discount row
        if (spotDiscountAmount > 0) {
            binding.spotDiscountRow.isVisible = true
            binding.spotDiscountPercentField.text =
                "(-) Spot Discount ${"%.2f".format(Locale.US, spotDiscountPercent)}%"
            binding.spotDiscountAmountValue.text = NumberFormatter().formatPrice(
                String.format(Locale.US, "%.2f", spotDiscountAmount),
                localizationData
            )
        } else {
            binding.spotDiscountRow.isVisible = false
            binding.spotDiscountAmountValue.text =
                NumberFormatter().formatPrice("0.00", localizationData)
        }
    }

    private fun showSucessDialog(msg: String, returnSaleRes: ReturnSaleRes) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.pos_sucess_dialog)
        dialog.setCancelable(false)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
        val print_receipt = dialog.findViewById<MaterialButton>(R.id.print_receipt)

        logoutMsg.text = msg
        logoutMsg.textSize = 16F

        confirm.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this@SearchReturnProductActivity, MPOSDashboardActivity::class.java)
            startActivity(intent)
            finish()
        }


        print_receipt.setOnClickListener {
            printerUtil?.printReturnReceiptData(returnSaleRes)
        }
        dialog.show()
    }

    fun dismissKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // ✅ ADD THESE THREE FUNCTIONS: Receipt Type Implementation

    /**
     * Setup receipt type dropdown listener
     */
    private fun setupReceiptTypeDropdown() {
        binding.receiptTypeInput.setOnItemClickListener { parent, view, position, id ->
            selectedReceiptType = receiptTypeList[position]
            Log.d("ReceiptType", "Selected: ${selectedReceiptType?.name} (ID: ${selectedReceiptType?.id}, Code: ${selectedReceiptType?.code})")
        }
    }

    /**
     * Fetch receipt types from API using PointofSaleViewmodel
     */
    private fun fetchReceiptTypes() {
        pos_viewmodel.callGetReceiptTypesApi(this)
    }

    /**
     * Populate dropdown with receipt type names and set default selection
     */
    private fun populateReceiptTypeDropdown() {
        val receiptTypeNames = receiptTypeList.map { it.name }

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            receiptTypeNames
        )

        binding.receiptTypeInput.setAdapter(adapter)

        // Set default to "Normal" (code "N")
        val defaultType = receiptTypeList.find { it.code == "N" }
        defaultType?.let {
            binding.receiptTypeInput.setText(it.name, false)
            selectedReceiptType = it
            Log.d("ReceiptType", "Default selected: ${it.name} (Code: ${it.code})")
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
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


//package com.retailone.pos.ui.Activity.DashboardActivity
//
//import NumberFormatter
//import android.app.Dialog
//import android.content.Context
//import android.content.Intent
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import android.view.View
//import android.view.ViewGroup
//import android.view.inputmethod.InputMethodManager
//import android.widget.ImageView
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.isVisible
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.google.android.material.button.MaterialButton
//import com.google.gson.Gson
//import com.retailone.pos.R
//import com.retailone.pos.adapter.ReturnReasonAdapter
//import com.retailone.pos.adapter.ReturnSalesItemAdapter
//import com.retailone.pos.adapter.ReturnSalesItemBatchAdapter
//import com.retailone.pos.adapter.SalesListAdapter
//import com.retailone.pos.databinding.ActivityReturnSaleBinding
//import com.retailone.pos.databinding.ActivitySearchReturnProductBinding
//import com.retailone.pos.interfaces.OnReturnQuantityChangeListener
//import com.retailone.pos.localstorage.DataStore.LoginSession
//import com.retailone.pos.localstorage.SharedPreference.LocalReturnCartHelper
//import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
//import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
//import com.retailone.pos.models.LocalizationModel.LocalizationData
//import com.retailone.pos.models.ReplaceModel.ReplaceReturnedItem
//import com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnedItem
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
//import com.retailone.pos.models.ReturnSalesItemModel.SalesItem
//import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.ReturnReasonData
//import com.retailone.pos.ui.Activity.MPOSDashboardActivity
//import com.retailone.pos.utils.PrinterUtil
//import com.retailone.pos.viewmodels.DashboardViewodel.ReturnSalesDetailsViewmodel
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.launch
//import java.math.BigDecimal
//import java.text.DecimalFormat
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.Locale
//import java.util.TimeZone
//import java.math.RoundingMode
//
//import kotlin.math.log
//
//class SearchReturnProductActivity : AppCompatActivity(), OnReturnQuantityChangeListener {
//
//    lateinit var binding: ActivitySearchReturnProductBinding
//    lateinit var returnsale_viewmodel: ReturnSalesDetailsViewmodel
//    lateinit var returnSalesItemAdapter: ReturnSalesItemAdapter
//    lateinit var salesListAdapter: SalesListAdapter
//    var returnItemList = mutableListOf<SalesItem>()
//    var returnReasonList: MutableList<ReturnReasonData> = mutableListOf()
//    lateinit var returnItemData: ReturnItemData
//    var reasonid = -1
//    var storeid = 0
//    var store_manager_id = "0"
//    lateinit var localizationData: LocalizationData
//    private var printerUtil: PrinterUtil? = null
//    private var returnbatchItemList = mutableListOf<BatchReturnItem>()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivitySearchReturnProductBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        binding.relativeLayout.isVisible = false
//        binding.relativeLayout2.isVisible = false
//        val invoiceIdFromIntent = intent.getStringExtra("invoice_id")
//        returnsale_viewmodel = ViewModelProvider(this)[ReturnSalesDetailsViewmodel::class.java]
//        localizationData = LocalizationHelper(this).getLocalizationData()
//        val loginSession = LoginSession.getInstance(this)
//        lifecycleScope.launch {
//            storeid = loginSession.getStoreID().first().toInt()
//            store_manager_id = loginSession.getStoreManagerID().first().toString()
//            if (!invoiceIdFromIntent.isNullOrEmpty()) {
//                binding.searchBar.setQuery(invoiceIdFromIntent, false)
//                returnsale_viewmodel.callReturnSalesDetailsApi(
//                    ReturnItemReq(invoice_id = invoiceIdFromIntent),
//                    this@SearchReturnProductActivity
//                )
//            }
//        }
//
//        binding.addcart.setOnClickListener {
//            returnbatchItemList.clear()
//            if (binding.searchBar.query.toString().trim() == "") {
//                showMessage("Enter a valid Invoice ID")
//            } else {
//                //url
//                returnsale_viewmodel.callReturnSalesDetailsApi(
//                    ReturnItemReq(
//                        invoice_id = binding.searchBar.query.toString().trim()
//                    ), this@SearchReturnProductActivity
//                )
//
//            }
//        }
//
//        binding.addproductLayout.setOnClickListener {
//            showMessage("Enter Invoice ID and Search")
//        }
//
//        returnsale_viewmodel.loadingLiveData.observe(this) {
//            binding.progress.isVisible = it.isProgress
//            if (it.isMessage) showMessage(it.message)
//        }
//        printerUtil = PrinterUtil(this)
//        enableBackButton()
//        //dev
//        preparePositemRCV()
//        //after new deve
//        returnsale_viewmodel.callSaleReturnReasonApi(this)
//
//        //dev
//        returnsale_viewmodel.returnitem_liveData.observe(this) {
//            // ✅ Add this log to see raw response
//            Log.d("BACKEND_RAW_RESPONSE", "========== Return Sales Details API Response ==========")
//            Log.d("BACKEND_RAW_RESPONSE", "Raw JSON: ${Gson().toJson(it)}")
//            Log.d("BACKEND_RAW_RESPONSE", "Status: ${it.status}")
//            Log.d("BACKEND_RAW_RESPONSE", "Message: ${it.message}")
//            Log.d("BACKEND_RAW_RESPONSE", "Data size: ${it.data.size}")
//            Log.d("BACKEND_RAW_RESPONSE", "======================================================")
//
//            if (it.data.isNotEmpty()) {
//                val data = it.data[0]
//
//                if (data.total_refunded_amount > 0) {
//                    // ✅ ALREADY RETURNED - Show NEW summary card only
//                    showMessage("This invoice has already been returned and cannot be returned again.")
//                    returnItemData = data
//                    returnItemList = data.salesItems.toMutableList()
//                    returnSalesItemAdapter = ReturnSalesItemAdapter(
//                        it.data,
//                        this@SearchReturnProductActivity,
//                        this
//                    ) {
//                        Log.d("rtn", it.toString())
//                        returnbatchItemList = it.toMutableList()
//                    }
//
//                    binding.positemRcv.adapter = returnSalesItemAdapter
//                    binding.positemRcv.isVisible = true
//                    binding.addproductLayout.isVisible = false
//                    binding.reasonLayout.isVisible = false
//
//                    // ✅ Show ONLY the new summary card
//                    binding.summaryCard.isVisible = true
//                    updateSummaryCard(
//                        subtotal = data.sub_total.toDouble(),
//                        tax = data.tax_amount.toDouble(),
//                        total = data.grand_total.toDouble()
//                    )
//
//                    // ❌ Hide old payment card and bottom layouts
//                    binding.paymentcard.isVisible = false
//                    binding.relativeLayout.isVisible = false
//                    binding.relativeLayout2.isVisible = false
//
//                } else {
//                    // ✅ NORMAL RETURN FLOW - Keep old UI (NO summary card)
//                    returnItemData = data
//                    returnItemList = data.salesItems.toMutableList()
//                    returnSalesItemAdapter = ReturnSalesItemAdapter(
//                        it.data,
//                        this@SearchReturnProductActivity,
//                        this
//                    ) {
//                        Log.d("rtn", it.toString())
//                        returnbatchItemList = it.toMutableList()
//                    }
//
//                    binding.positemRcv.adapter = returnSalesItemAdapter
//                    binding.positemRcv.isVisible = true
//                    binding.addproductLayout.isVisible = false
//                    binding.reasonLayout.isVisible = true
//
//                    // ❌ HIDE new summary card for normal returns
//                    binding.summaryCard.isVisible = false
//
//                    // ✅ Show OLD payment card and bottom layouts
//                    binding.relativeLayout.isVisible = true
//                    binding.relativeLayout2.isVisible = true
//                    binding.paymentcard.isVisible = false
//
//                    // ✅ Direct conversion from Int to Double
//                    val subtotalValue = data.sub_total.toDouble()
//                    val taxValue = data.tax_amount.toDouble()
//                    val grandValue = data.grand_total.toDouble()
//
//                    // Round subtotal to nearest whole number
//                    val roundedSubtotal = BigDecimal.valueOf(subtotalValue)
//                        .setScale(0, RoundingMode.HALF_UP)
//
//                    // Round tax to nearest whole number
//                    val roundedTax = BigDecimal.valueOf(taxValue)
//                        .setScale(0, RoundingMode.HALF_UP)
//
//                    binding.subtotal.setText(roundedSubtotal.toPlainString())
//
//                    val taxDisplay = formatTaxForDisplay(data.tax)
//                    binding.taxfield.setText("(+) Tax @$taxDisplay")
//
//                    binding.taxAmount.setText(roundedTax.toPlainString())
//                    binding.alltotalAmount.setText(
//                        NumberFormatter().formatPrice(grandValue.toString(), localizationData)
//                    )
//                }
//
//                // ✅ Logging
//                Log.d("VISIBILITY_DEBUG", "summaryCard.isVisible = ${binding.summaryCard.isVisible}")
//                Log.d("VISIBILITY_DEBUG", "paymentcard.isVisible = ${binding.paymentcard.isVisible}")
//                Log.d("VISIBILITY_DEBUG", "relativeLayout.isVisible = ${binding.relativeLayout.isVisible}")
//                Log.d("VISIBILITY_DEBUG", "relativeLayout2.isVisible = ${binding.relativeLayout2.isVisible}")
//
//            } else {
//                showMessage("No Invoice Found")
//
//                binding.positemRcv.isVisible = false
//                binding.addproductLayout.isVisible = true
//                binding.reasonLayout.isVisible = false
//                binding.summaryCard.isVisible = false
//                binding.paymentcard.isVisible = false
//                binding.relativeLayout.isVisible = false
//                binding.relativeLayout2.isVisible = false
//            }
//        }
//
//
//
//        returnsale_viewmodel.returnsalesubmit_liveData.observe(this) {
//
//            if (it.status == 1) {
//                //showMessage(it.message)
//                showSucessDialog(it.message, it)
//            } else {
//                showMessage(it.message)
//            }
//        }
//
//        returnsale_viewmodel.loadingLiveData.observe(this) {
//            binding.progress.isVisible = it.isProgress
//
//            if (it.isMessage) showMessage(it.message)
//        }
//
//        setToolbarImage()
//
//        returnsale_viewmodel.salesreturnreason_liveData.observe(this) {
//            returnReasonList = it.data.toMutableList()
//            binding.reasonInput.setAdapter(ReturnReasonAdapter(this, 0, returnReasonList))
//        }
//
//        binding.reasonInput.setOnClickListener {
//            if (returnReasonList.isEmpty()) {
//                showMessage("Return reason not found, try after sometime")
//            }
//        }
//
//        binding.reasonInput.setOnItemClickListener { parent, view, position, id ->
//            binding.reasonInput.setText(returnReasonList[position].reason_name, false)
//            reasonid = returnReasonList[position].id
//        }
//
//
//        binding.nextlayout.setOnClickListener {
//            if (returnbatchItemList.isNotEmpty()) {
//                callReturnAPI(returnbatchItemList)
//            } else {
//                showMessage("You haven't Return anything")
//            }
//        }
//    }
//    /**
//     * Updates the summary card with subtotal, tax, and total
//     */
//    private fun updateSummaryCard(subtotal: Double, tax: Double, total: Double, discount: Double = 0.0) {
//        // Round values
//        val roundedSubtotal = BigDecimal.valueOf(subtotal).setScale(0, RoundingMode.HALF_UP)
//        val roundedTax = BigDecimal.valueOf(tax).setScale(0, RoundingMode.HALF_UP)
//        val roundedTotal = BigDecimal.valueOf(total).setScale(0, RoundingMode.HALF_UP)
//
//        // Update summary card TextViews
//        binding.tvSubtotalValue.text = NumberFormatter().formatPrice(
//            roundedSubtotal.toPlainString(),
//            localizationData
//        )
//
//        binding.tvTaxValue.text = NumberFormatter().formatPrice(
//            roundedTax.toPlainString(),
//            localizationData
//        )
//
//        binding.tvTotalValue.text = NumberFormatter().formatPrice(
//            roundedTotal.toPlainString(),
//            localizationData
//        )
//
//        // Show/hide discount row
//        if (discount > 0.0) {
//            binding.discountSummaryRow.isVisible = true
//            binding.tvDiscountValue.text = NumberFormatter().formatPrice(
//                String.format(Locale.US, "%.2f", discount),
//                localizationData
//            )
//        } else {
//            binding.discountSummaryRow.isVisible = false
//        }
//    }
//
//
//    // Converts "16.5", "16,5", "16.50%", or "165" -> "16.5", and rounds "18.02" -> "18"
//    private fun formatTaxForDisplay(raw: Any?): String {
//        val s0 = raw?.toString()?.trim().orEmpty()
//        if (s0.isEmpty()) return "0"
//
//        // keep digits and one decimal separator; accept comma or dot
//        val s1 = s0.replace(Regex("[^0-9.,]"), "").replace(',', '.')
//        if (s1.isEmpty() || s1 == ".") return "0"
//
//        // already has decimal separator — format nicely
//        if (s1.contains('.')) {
//            return try {
//                val value = BigDecimal(s1)
//                // ✅ Round to nearest integer
//                val rounded = value.setScale(0, RoundingMode.HALF_UP)
//                rounded.toPlainString()
//            } catch (_: Exception) {
//                s1
//            }
//        }
//
//        // no decimal point -> legacy value (e.g., 165 should be 16.5)
//        val n = s1.toLongOrNull() ?: return s1
//
//        // Heuristic: older code multiplied % by 10 (16.5 -> 165)
//        // If your data was multiplied by 100 instead, change 10.0 to 100.0 below.
//        val scaled = n / 10.0
//
//        // ✅ Round to nearest integer
//        return BigDecimal.valueOf(scaled).setScale(0, RoundingMode.HALF_UP).toPlainString()
//    }
//
//
//    /** Purchased (sold) qty for a given sales_item_id (ceil). */
//    private fun purchasedQtyFor(salesItemId: Int): Int {
//        val q = returnItemData.sales_items.orEmpty().firstOrNull { it.id == salesItemId }?.quantity
//            ?: 0.0
//        return kotlin.math.ceil(q).toInt()
//    }
//
//    private fun buildReplaceLines(): List<ReturnedItem> {
//
//        // 1️⃣ Aggregate defect info per sales_item_id from live batch list
//        val defectMap: Map<Int, Pair<Int, Int>> = returnbatchItemList
//            .groupBy { it.sales_item_id }
//            .mapValues { (_, batches) ->
//                val totalBoxes = batches.sumOf { it.defective_boxes ?: 0 }
//                val totalPacks = batches.sumOf { it.defective_bottles ?: 0 }
//                totalBoxes to totalPacks
//            }
//
//        val output = mutableListOf<ReturnedItem>()
//
//        // 2️⃣ Items saved in local cart (user pressed pencil/save) → always send
//        val cartLines = LocalReturnCartHelper.getCartItems(this)
//        if (cartLines.isNotEmpty()) {
//
//            cartLines.forEach { line ->
//                val (boxes, packs) = defectMap[line.id] ?: (0 to 0)
//
//                output.add(
//                    ReturnedItem(
//                        id = line.id,
//                        return_quantity = line.return_quantity,  // qty user confirmed
//                        defective_boxes = boxes,                // total boxes from all batches
//                        defective_bottles = packs               // total packs from all batches
//                    )
//                )
//            }
//            return output
//        }
//
//        // 3️⃣ No cart, but we have edits in batches → derive qty + defects from batches
//        if (defectMap.isNotEmpty()) {
//            // sales_item_id -> total Qty from UI
//            val qtyMap = returnbatchItemList
//                .groupBy { it.sales_item_id }
//                .mapValues { (_, batches) ->
//                    batches.sumOf { it.return_quantity ?: 0 }
//                }
//
//            defectMap.forEach { (salesItemId, boxesPacks) ->
//                val (boxes, packs) = boxesPacks
//                val userQty = qtyMap[salesItemId] ?: 0
//
//                output.add(
//                    ReturnedItem(
//                        id = salesItemId,
//                        return_quantity = userQty,
//                        defective_boxes = boxes,
//                        defective_bottles = packs
//                    )
//                )
//            }
//
//            // If we want to send only rows where user actually entered something:
//            return output.filter {
//                (it.defective_boxes ?: 0) > 0 ||
//                        (it.defective_bottles ?: 0) > 0 ||
//                        (it.return_quantity ?: 0) > 0
//            }
//        }
//
//        // 4️⃣ Fallback: no edits at all → return full invoice with 0 defects
//        val detailed = returnItemData.sales_items.orEmpty()
//        detailed.forEach { si ->
//            output.add(
//                ReturnedItem(
//                    id = si.id,
//                    return_quantity = kotlin.math.ceil(si.quantity).toInt(),
//                    defective_boxes = 0,
//                    defective_bottles = 0
//                )
//            )
//        }
//
//        return output
//    }
//
//
//    private fun callReturnAPI(returnbatchItemList: MutableList<BatchReturnItem>) {
//        val savedItems = buildReplaceLines()
//        if (savedItems.isEmpty()) {
//            showMessage("No items saved for return.")
//            return
//        }
//        if (reasonid != -1) {
//            val return_data = ReturnSaleReq(
//                store_id = storeid,
//                store_manager_id = store_manager_id.toInt(),
//                reason_id = reasonid,
//                sales_id = returnItemData.id,
//                returned_items = savedItems
//            )
//            returnsale_viewmodel.callReturnSalesSubmitApi(
//                return_data, this@SearchReturnProductActivity
//            )
//            LocalReturnCartHelper.clearCart(this)
//        } else {
//            showMessage("please select any reason for return")
//        }
//    }
//
//    private fun getReturnDateTime(): String {
//
//        val zone = localizationData.timezone
//        lateinit var timezone: String
//
//        if (zone == "IST") {
//            timezone = "Asia/Kolkata"
//        } else if (zone == "CAT") {
//            timezone = "Africa/Lusaka"
//        } else {
//            timezone = "Africa/Lusaka"
//        }
//
//        val calendar = Calendar.getInstance()
//
//        // Set the time zone to Zambia (Africa/Lusaka)
//        val zambiaTimeZone = TimeZone.getTimeZone(timezone)
//        calendar.timeZone = zambiaTimeZone
//
//        val currentDateTime = calendar.time
//
//        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
//        dateFormat.timeZone = zambiaTimeZone
//
//        val formattedDateTime = dateFormat.format(currentDateTime)
//
//        return formattedDateTime
//
//    }
//
//    private fun setToolbarImage() {
//        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()
//
//        Glide.with(this).load(organisation_data.image_url + organisation_data.fabicon)
//            .fitCenter() // Add center crop
//            .placeholder(R.drawable.mlogo) // Add a placeholder drawable
//            .error(R.drawable.mlogo) // Add an error drawable (if needed)
//            .into(binding.image)
//    }
//
//
//    //development
//    private fun preparePositemRCV() {
//
//        binding.positemRcv.apply {
//            layoutManager = LinearLayoutManager(
//                this@SearchReturnProductActivity, RecyclerView.VERTICAL, false
//            )
//        }
//    }
//
//    private fun enableBackButton() {
//        setSupportActionBar(binding.toolbar)
//        //actionbar
//        val actionbar = supportActionBar
//        //set actionbar title
//        actionbar!!.title = "New Activity"
//        //set back button
//        actionbar.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_back_arrow_white)
//    }
//
//
//    override fun onSupportNavigateUp(): Boolean {
//        onBackPressed()
//        return true
//    }
//
//    private fun showMessage(msg: String) {
//        Toast.makeText(this@SearchReturnProductActivity, msg, Toast.LENGTH_SHORT).show()
//    }
//
//
//    override fun onReturnQuantityChange(position: Int, newQuantity: Int) {
//        Log.d("QUANTITY_CHANGE_DEBUG", "========== onReturnQuantityChange ==========")
//        Log.d("QUANTITY_CHANGE_DEBUG", "position: $position")
//        Log.d("QUANTITY_CHANGE_DEBUG", "newQuantity: $newQuantity")
//        returnItemList[position].return_quantity = newQuantity
//        returnItemList[position].refund_amount = newQuantity * returnItemList[position].retail_price
//
//        Log.d("QUANTITY_CHANGE_DEBUG", "Calling recalculateTotals()")
//        // Trigger real-time price calculation
//        recalculateTotals()
//    }
//
//    // ✅ FIXED: Now shows summary card even when no items are selected for return
//    private fun recalculateTotals() {
//        var subtotal = 0.0
//        var taxAmount = 0.0
//
//        fun df(value: Double, decimalPlaces: Int = 0): Double {
//            val pattern = if (decimalPlaces == 0) "#" else "#.${"#".repeat(decimalPlaces)}"
//            val decimalFormat = DecimalFormat(pattern)
//            decimalFormat.roundingMode = RoundingMode.HALF_UP
//            return decimalFormat.format(value).toDouble()
//        }
//
//        // ❌ DON'T show summary card here (only for already returned invoices)
//        // binding.summaryCard.isVisible = false  // Keep it hidden
//
//        // ✅ Keep showing old payment card layout
//        binding.relativeLayout.isVisible = true
//        binding.relativeLayout2.isVisible = true
//
//        Log.d("TAX_FIX_DEBUG", "========== recalculateTotals() START ==========")
//        Log.d("TAX_FIX_DEBUG", "returnItemList.size: ${returnItemList.size}")
//
//        // ✅ DEBUG: Check tax value from returnItemData
//        Log.d("TAX_FIX_DEBUG", "returnItemData.tax: '${returnItemData.tax}'")
//        val taxPercentage = returnItemData.tax.toDoubleOrNull() ?: 0.0
//        Log.d("TAX_FIX_DEBUG", "taxPercentage parsed: $taxPercentage")
//
//        // Check if any items have been selected for return
//        val hasReturnItems = returnItemList.any {
//            !it.readonlyMode && !it.isExpired && it.return_quantity > 0
//        }
//
//        if (hasReturnItems) {
//            // User has selected items - calculate based on selected items
//            returnItemList.forEachIndexed { index, item ->
//                Log.d("TAX_FIX_DEBUG", "--- Item $index ---")
//                Log.d("TAX_FIX_DEBUG", "  product_name: ${item.product_name}")
//                Log.d("TAX_FIX_DEBUG", "  retail_price: ${item.retail_price}")
//                Log.d("TAX_FIX_DEBUG", "  return_quantity: ${item.return_quantity}")
//                Log.d("TAX_FIX_DEBUG", "  readonlyMode: ${item.readonlyMode}")
//                Log.d("TAX_FIX_DEBUG", "  isExpired: ${item.isExpired}")
//
//                if (!item.readonlyMode && !item.isExpired && item.return_quantity > 0) {
//                    val retailPrice = item.retail_price
//                    val qty = item.return_quantity
//
//                    // ✅ NEW CALCULATION LOGIC
//                    // Calculate tax-exclusive price: price / (1 + tax%)
//                    // Example: 5000 / (1 + 0.18) = 5000 / 1.18 = 4237.288135593220
//                    val divisor = 1 + (taxPercentage / 100.0)
//                    Log.d("TAX_FIX_DEBUG", "  divisor (1 + tax/100): $divisor")
//
//                    val taxExclusivePrice = retailPrice / divisor
//                    Log.d("TAX_FIX_DEBUG", "  taxExclusivePrice = $retailPrice / $divisor = $taxExclusivePrice")
//
//                    // Calculate subtotal: tax-exclusive price × quantity
//                    // Example: 4237.288135593220 × 300 = 1,271,186.440677966
//                    val itemSubtotal = taxExclusivePrice * qty
//                    Log.d("TAX_FIX_DEBUG", "  itemSubtotal (before adding) = $taxExclusivePrice × $qty = $itemSubtotal")
//
//                    subtotal += itemSubtotal
//                    Log.d("TAX_FIX_DEBUG", "  subtotal (running total): $subtotal")
//
//                    // Calculate tax: (retail_price - tax_exclusive_price) × quantity
//                    val taxPerUnit = retailPrice - taxExclusivePrice
//                    Log.d("TAX_FIX_DEBUG", "  taxPerUnit = $retailPrice - $taxExclusivePrice = $taxPerUnit")
//
//                    val itemTax = taxPerUnit * qty
//                    Log.d("TAX_FIX_DEBUG", "  itemTax = $taxPerUnit × $qty = $itemTax")
//
//                    taxAmount += itemTax
//                    Log.d("TAX_FIX_DEBUG", "  taxAmount (running total): $taxAmount")
//
//                    Log.d("TAX_FIX_DEBUG", "  ✅ INCLUDED in calculation")
//                } else {
//                    Log.d("TAX_FIX_DEBUG", "  ❌ SKIPPED")
//                }
//            }
//
//            Log.d("TAX_FIX_DEBUG", "FINAL CALCULATED subtotal (before rounding): $subtotal")
//            Log.d("TAX_FIX_DEBUG", "FINAL CALCULATED taxAmount (before rounding): $taxAmount")
//
//            // ✅ Round subtotal to nearest whole number (1,271,186.4 → 1,271,186)
//            val roundedSubtotal = BigDecimal.valueOf(subtotal)
//                .setScale(0, RoundingMode.HALF_UP)
//
//            val roundedTax = BigDecimal.valueOf(taxAmount)
//                .setScale(0, RoundingMode.HALF_UP)
//
//            val grandTotal = roundedSubtotal.add(roundedTax)
//
//            Log.d("TAX_FIX_DEBUG", "ROUNDED subtotal: $roundedSubtotal")
//            Log.d("TAX_FIX_DEBUG", "ROUNDED tax: $roundedTax")
//            Log.d("TAX_FIX_DEBUG", "GRAND TOTAL: $grandTotal")
//
//            binding.subtotal.setText(roundedSubtotal.toPlainString())
//            binding.taxAmount.setText(roundedTax.toPlainString())
//            binding.alltotalAmount.setText(grandTotal.toPlainString())
//
//            // ✅ Update formatted display fields as well
//            binding.tvSubtotalValue.text = NumberFormatter().formatPrice(
//                roundedSubtotal.toPlainString(), localizationData
//            )
//            binding.tvTaxValue.text = NumberFormatter().formatPrice(
//                roundedTax.toPlainString(), localizationData
//            )
//            binding.tvTotalValue.text = NumberFormatter().formatPrice(
//                grandTotal.toPlainString(), localizationData
//            )
//
//        } else {
//            // No items selected - show original invoice totals
//            Log.d("TAX_FIX_DEBUG", "No items selected - showing original invoice totals")
//
//            // ✅ Convert Int to Double directly (since data class fields are Int)
//            val subtotalValue = returnItemData.sub_total.toDouble()
//            val taxValue = returnItemData.tax_amount.toDouble()
//            val grandValue = returnItemData.grand_total.toDouble()
//
//            Log.d("TAX_FIX_DEBUG", "Original invoice - subtotal: $subtotalValue")
//            Log.d("TAX_FIX_DEBUG", "Original invoice - tax: $taxValue")
//            Log.d("TAX_FIX_DEBUG", "Original invoice - grand total: $grandValue")
//
//            val roundedSubtotal = BigDecimal.valueOf(subtotalValue)
//                .setScale(0, RoundingMode.HALF_UP)
//
//            val roundedTax = BigDecimal.valueOf(taxValue)
//                .setScale(0, RoundingMode.HALF_UP)
//
//            val roundedGrandTotal = BigDecimal.valueOf(grandValue)
//                .setScale(0, RoundingMode.HALF_UP)
//
//            binding.subtotal.setText(roundedSubtotal.toPlainString())
//            binding.taxAmount.setText(roundedTax.toPlainString())
//            binding.alltotalAmount.setText(roundedGrandTotal.toPlainString())
//
//            // ✅ Update formatted display fields
//            binding.tvSubtotalValue.text = NumberFormatter().formatPrice(
//                roundedSubtotal.toPlainString(), localizationData
//            )
//            binding.tvTaxValue.text = NumberFormatter().formatPrice(
//                roundedTax.toPlainString(), localizationData
//            )
//            binding.tvTotalValue.text = NumberFormatter().formatPrice(
//                roundedGrandTotal.toPlainString(), localizationData
//            )
//        }
//
//        Log.d("TAX_FIX_DEBUG", "========== recalculateTotals() END ==========")
//    }
//
//
//
//
//    private fun showSucessDialog(msg: String, returnSaleRes: ReturnSaleRes) {
//
//        val dialog = Dialog(this)
//        dialog.setContentView(R.layout.pos_sucess_dialog)
//        dialog.setCancelable(false)
//        dialog.window?.setLayout(
//            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
//        )
//        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
//        dialog.setCanceledOnTouchOutside(false)
//
//        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
//        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
//        val logoutImg = dialog.findViewById<ImageView>(R.id.dialog_logo)
//        val print_receipt = dialog.findViewById<MaterialButton>(R.id.print_receipt)
//
//        logoutMsg.text = msg
//        logoutMsg.textSize = 16F
//
//        confirm.setOnClickListener {
//            dialog.dismiss()
//
//            val intent = Intent(this@SearchReturnProductActivity, MPOSDashboardActivity::class.java)
//            startActivity(intent)
//            finish()
//        }
//
//        print_receipt.setOnClickListener {
//
//            printerUtil?.printReturnReceiptData(returnSaleRes)
//
//        }
//        dialog.show()
//
//    }
//
//
//    fun dismissKeyboard(view: View) {
//        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//        imm.hideSoftInputFromWindow(view.windowToken, 0)
//    }
//
//
//    override fun onBackPressed() {
//        super.onBackPressed()
//    }
//
//    override fun onResume() {
//        super.onResume()
//        printerUtil?.registerBatteryReceiver()
//    }
//
//    override fun onPause() {
//        super.onPause()
//        printerUtil?.unregisterBatteryReceiver()
//    }
//
//}
