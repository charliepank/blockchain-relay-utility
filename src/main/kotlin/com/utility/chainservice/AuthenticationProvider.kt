package com.utility.chainservice

import com.utility.chainservice.models.AuthenticationResult
import reactor.core.publisher.Mono

interface AuthenticationProvider {
    fun validateToken(authToken: String, httpOnlyToken: String?): Mono<AuthenticationResult>
    fun isAuthEnabled(): Boolean
}