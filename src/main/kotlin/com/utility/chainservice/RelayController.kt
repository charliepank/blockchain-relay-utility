package com.utility.chainservice

import com.utility.chainservice.models.OperationGasCost
import com.utility.chainservice.plugin.PluginConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/relay")
@Tag(name = "Blockchain Relay Utility", description = "Generic blockchain transaction relay and gas management")
class RelayController(
    private val blockchainRelayService: BlockchainRelayService,
    private val pluginConfiguration: PluginConfiguration
) {

    private val logger = LoggerFactory.getLogger(RelayController::class.java)

    data class GasCostsResponse(
        val operations: List<OperationGasCost>,
        val timestamp: String
    )

    data class ErrorResponse(
        val error: String,
        val message: String,
        val timestamp: String
    )

    @GetMapping("/health")
    @Operation(
        summary = "Health Check",
        description = "Returns the health status of the blockchain relay service"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Service is healthy",
            content = [Content(schema = Schema(implementation = Map::class))]
        )
    ])
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return try {
            // TODO: Add actual health checks (blockchain connectivity, relayer balance, etc.)
            val healthStatus = mapOf(
                "status" to "healthy",
                "timestamp" to Instant.now().toString(),
                "service" to "blockchain-relay-utility",
                "plugins" to pluginConfiguration.getActivePlugins().map { it.getPluginName() }
            )
            ResponseEntity.ok(healthStatus)
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            val errorStatus = mapOf(
                "status" to "unhealthy",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to Instant.now().toString(),
                "service" to "blockchain-relay-utility"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus)
        }
    }

    @GetMapping("/gas-costs")
    @Operation(
        summary = "Get Gas Costs (Generic Utility)",
        description = "Returns current gas costs for operations across all active plugins. This is the generic utility endpoint - plugins may have their own specific gas cost endpoints."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Gas costs retrieved successfully",
            content = [Content(schema = Schema(implementation = GasCostsResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    ])
    fun getGasCosts(): ResponseEntity<GasCostsResponse> {
        return try {
            logger.info("Generic gas costs request received")
            
            // Collect gas operations from all active plugins
            val allOperations = mutableListOf<Pair<String, String>>()
            pluginConfiguration.getActivePlugins().forEach { plugin ->
                allOperations.addAll(plugin.getGasOperations())
            }
            
            val operationCosts = if (allOperations.isNotEmpty()) {
                blockchainRelayService.getOperationGasCosts(allOperations)
            } else {
                emptyList()
            }
            
            val response = GasCostsResponse(
                operations = operationCosts,
                timestamp = Instant.now().toString()
            )
            
            logger.info("Generic gas costs retrieved successfully for ${operationCosts.size} operations from ${pluginConfiguration.getActivePlugins().size} plugins")
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Error in generic gas costs endpoint", e)
            val errorResponse = ErrorResponse(
                error = "Internal server error",
                message = e.message ?: "Failed to retrieve gas costs",
                timestamp = Instant.now().toString()
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                GasCostsResponse(emptyList(), Instant.now().toString())
            )
        }
    }
}