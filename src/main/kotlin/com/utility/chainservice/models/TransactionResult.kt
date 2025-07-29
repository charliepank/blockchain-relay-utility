package com.utility.chainservice.models

data class TransactionResult(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)