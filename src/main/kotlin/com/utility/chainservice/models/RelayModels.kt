package com.utility.chainservice.models

import java.math.BigInteger

data class GasTransferRequest(
    val userWalletAddress: String,
    val signedTransactionHex: String,
    val fallbackGasOperation: String
)

data class OperationGasCost(
    val operation: String,
    val gasLimit: Long,
    val gasPriceWei: BigInteger,
    val totalCostWei: BigInteger,
    val totalCostAvax: String
)