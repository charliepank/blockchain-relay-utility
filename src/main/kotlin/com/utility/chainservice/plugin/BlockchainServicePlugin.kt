package com.utility.chainservice.plugin

import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

interface BlockchainServicePlugin {
    fun getPluginName(): String
    fun getApiPrefix(): String
    fun getOpenApiTags(): List<Tag>
    fun initialize(relayService: BlockchainRelayService, authProvider: AuthenticationProvider)
    fun getGasOperations(): List<Pair<String, String>>
}