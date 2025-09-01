package com.utility.chainservice.contracts

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.Type
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.Contract
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

class GasPayerContract(
    contractAddress: String,
    web3j: Web3j,
    credentials: Credentials,
    gasProvider: ContractGasProvider
) : Contract(
    BINARY,
    contractAddress,
    web3j,
    credentials,
    gasProvider
) {
    
    constructor(
        contractAddress: String,
        web3j: Web3j,
        transactionManager: TransactionManager,
        gasProvider: ContractGasProvider
    ) : this(
        contractAddress,
        web3j,
        transactionManager.fromAddress?.let { Credentials.create("0x0000000000000000000000000000000000000000000000000000000000000001") } 
            ?: throw IllegalArgumentException("Transaction manager must have from address"),
        gasProvider
    ) {
        this.transactionManager = transactionManager
    }

    fun fundAndRelay(
        signerAddress: String,
        gasAmount: BigInteger,
        weiValue: BigInteger
    ): RemoteCall<TransactionReceipt> {
        val function = Function(
            FUNC_FUND_AND_RELAY,
            listOf(
                Address(signerAddress),
                Uint256(gasAmount)
            ) as List<Type<*>>,
            emptyList()
        )
        return executeRemoteCallTransaction(function, weiValue)
    }

    fun calculateFee(gasAmount: BigInteger): RemoteCall<BigInteger> {
        val function = Function(
            FUNC_CALCULATE_FEE,
            listOf(Uint256(gasAmount)) as List<Type<*>>,
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeRemoteCallSingleValueReturn(function, BigInteger::class.java)
    }

    fun feePercentage(): RemoteCall<BigInteger> {
        val function = Function(
            FUNC_FEE_PERCENTAGE,
            emptyList<Type<*>>(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeRemoteCallSingleValueReturn(function, BigInteger::class.java)
    }

    fun minFee(): RemoteCall<BigInteger> {
        val function = Function(
            FUNC_MIN_FEE,
            emptyList<Type<*>>(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeRemoteCallSingleValueReturn(function, BigInteger::class.java)
    }

    fun feeRecipient(): RemoteCall<String> {
        val function = Function(
            FUNC_FEE_RECIPIENT,
            emptyList<Type<*>>(),
            listOf(object : TypeReference<Address>() {})
        )
        return executeRemoteCallSingleValueReturn(function, String::class.java)
    }

    companion object {
        const val FUNC_FUND_AND_RELAY = "fundAndRelay"
        const val FUNC_CALCULATE_FEE = "calculateFee"
        const val FUNC_FEE_PERCENTAGE = "feePercentage"
        const val FUNC_MIN_FEE = "minFee"
        const val FUNC_FEE_RECIPIENT = "feeRecipient"
        
        // Empty binary as we're not deploying, just interacting
        const val BINARY = "0x"

        fun load(
            contractAddress: String,
            web3j: Web3j,
            credentials: Credentials,
            gasProvider: ContractGasProvider
        ): GasPayerContract {
            return GasPayerContract(contractAddress, web3j, credentials, gasProvider)
        }

        fun load(
            contractAddress: String,
            web3j: Web3j,
            transactionManager: TransactionManager,
            gasProvider: ContractGasProvider
        ): GasPayerContract {
            return GasPayerContract(contractAddress, web3j, transactionManager, gasProvider)
        }
    }
}