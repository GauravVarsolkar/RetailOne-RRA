package com.retailone.pos.models.SalesPaymentModel.SalesDetails

data class CopyReceiptReq(
    val sales_id: String,
    val store_id: String
)

data class CopyReceiptRes(
    val status: Int,
    val message: String,
    val data: Any? = null  // Adjust based on actual response structure
)