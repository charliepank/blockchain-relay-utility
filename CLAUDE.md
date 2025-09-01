# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **generic Kotlin/Spring Boot utility for blockchain transaction relaying** with gas management and pluggable business logic. The library provides core blockchain interaction capabilities while allowing business-specific functionality to be added through a plugin system.

## Architecture

### Core Components

- **BlockchainRelayService** (`src/main/kotlin/com/utility/chainservice/BlockchainRelayService.kt`): Main service handling transaction relaying, gas transfers, and blockchain operations using Web3j
- **BlockchainServicePlugin** (`src/main/kotlin/com/utility/chainservice/plugin/BlockchainServicePlugin.kt`): Interface for implementing business-specific plugins
- **PluginConfiguration** (`src/main/kotlin/com/utility/chainservice/plugin/PluginConfiguration.kt`): Auto-discovery and initialization of plugins
- **AuthenticationProvider** (`src/main/kotlin/com/utility/chainservice/AuthenticationProvider.kt`): Interface for pluggable authentication systems
- **UtilityAutoConfiguration** (`src/main/kotlin/com/utility/chainservice/UtilityAutoConfiguration.kt`): Spring Boot auto-configuration with blockchain and authentication setup

### Plugin System

The utility implements a plugin architecture where:
- **Generic Utility**: Core blockchain operations, gas management, authentication
- **Business Plugins**: Implement `BlockchainServicePlugin` interface to add domain-specific functionality
- **Auto-Discovery**: Plugins are automatically detected and initialized via Spring component scanning

### Transaction Flow

1. **Transaction Decoding**: Decodes user's signed transaction to extract gas limit, value, and contract data
2. **Gas Validation**: Validates user's gas limits and prices against configured maximum thresholds
3. **Balance Check & Contract Funding**: Checks user's AVAX balance and uses Gas Payer Contract to fund if insufficient
   - Calls `calculateFee(gasAmount)` to determine service fee
   - Sends `gasAmount + fee` to contract via `fundAndRelay(userAddress, gasAmount)`
   - Contract transfers `gasAmount` to user and retains fee automatically
4. **Balance Confirmation**: Waits for user's balance to update before proceeding
5. **Transaction Forwarding**: Forwards original signed transaction unchanged to the blockchain
6. **Receipt Waiting**: Monitors transaction status and provides success/failure results

## Development Commands

### Build and Test
```bash
./gradlew build                    # Build project with tests and jar
./gradlew test                     # Run tests only
./gradlew test jacocoTestReport   # Run tests with coverage report
./gradlew bootRun                 # Run Spring Boot application (library mode)
./gradlew clean                   # Clean build directory
```

### Single Test Execution
```bash
./gradlew test --tests "com.utility.chainservice.BlockchainRelayServiceTest"
./gradlew test --tests "*RelayServiceTest*"
./gradlew test --tests "*.shouldCalculateOperationGasCosts*"
```

### Publishing and Distribution
```bash
./gradlew publishToMavenLocal     # Publish to local Maven repository
./gradlew jar                     # Create JAR artifact (bootJar disabled)
```

## Configuration

### Required Environment Variables
- `RPC_URL`: Blockchain RPC endpoint (e.g., Avalanche network)
- `RELAYER_PRIVATE_KEY`: Private key for gas-paying wallet (0x-prefixed hex, 66 chars)
- `RELAYER_WALLET_ADDRESS`: Address of the relayer wallet
- `GAS_PAYER_CONTRACT_ADDRESS`: Address of the Gas Payer Contract for fee collection (0x-prefixed hex, 42 chars)
- `USER_SERVICE_URL`: URL for user authentication service (if using HTTP auth)

### Security Configuration File
The service uses a JSON configuration file for API key and IP whitelist management. By default, this file should be located at `./config/security-config.json`, but the path is configurable.

### Application Configuration (application.yml)
```yaml
blockchain:
  rpc-url: "${RPC_URL}"
  chain-id: 43113  # Auto-detected from RPC if not specified
  relayer:
    private-key: "${RELAYER_PRIVATE_KEY}"
    wallet-address: "${RELAYER_WALLET_ADDRESS}"
    gas-payer-contract-address: "${GAS_PAYER_CONTRACT_ADDRESS}"
  gas:
    price-multiplier: 1.2  # Multiply network gas price by this factor (default: 1.2)
    minimum-gas-price-wei: 6  # Minimum gas price in wei (default: 6)
    max-gas-cost-wei: 540000000  # Maximum total cost gasLimit*gasPrice in wei (default: ~$0.014)
    max-gas-limit: 1000000  # Maximum gas limit allowed (default: 1M)
    max-gas-price-multiplier: 3  # Maximum gas price as multiplier of current network price (default: 3x)

auth:
  user-service-url: "${USER_SERVICE_URL}"
  enabled: true  # Set to false to disable authentication

security:
  enabled: true  # Set to false to disable API key security
  config-path: "./config/security-config.json"  # Path to security configuration file
```

## Key Features

### Gas Management with Fee Collection
- **Security-First Gas Validation**: Validates user-provided gas limits and prices against configurable maximum thresholds
- Extracts exact gas costs from user-signed transactions (supports both legacy and EIP-1559)
- **Gas Payer Contract Integration**: Uses a smart contract to transfer AVAX to user wallets while collecting service fees
- Contract calculates fees automatically (percentage-based with minimum fee floor)
- Backend pays `gasAmount + calculatedFee` to contract; contract transfers `gasAmount` to user and keeps fee
- Three-tier validation: total cost limit, maximum gas limit, and maximum gas price multiplier

### API Key & IP Whitelist Security System
- **Hot-Reloadable Configuration**: JSON configuration file that updates without server restart
- **API Key Authentication**: Validates requests using `X-API-Key` header or `Authorization: Bearer` header
- **IP Whitelist Support**: Per-API key and global IP restrictions with CIDR notation support
- **Flexible IP Patterns**: Supports exact IPs, wildcards (`192.168.1.*`), and CIDR ranges (`10.0.0.0/24`)
- **File Watcher**: Automatically detects configuration file changes and reloads security settings
- **Security Logging**: Configurable logging of authentication attempts and failures

### Legacy Authentication System
- Pluggable authentication via `AuthenticationProvider` interface  
- HTTP-based authentication using external user service
- JWT token validation with reactive (Mono) responses

### Plugin Development
To create a plugin:
1. Implement `BlockchainServicePlugin` interface
2. Define plugin name, API prefix, and OpenAPI documentation
3. Implement `initialize()` method to access relay service and auth provider
4. Define gas operations for cost estimation
5. Create REST controllers using the plugin's API prefix

### Gas Payer Contract Integration
- **Contract Interface**: Automatically loads and initializes the Gas Payer Contract using the configured address
- **Fee Calculation**: Calls `calculateFee(gasAmount)` to determine the service fee before each transaction
- **Atomic Funding**: Uses `fundAndRelay(signerAddress, gasAmount)` with total payment (`gasAmount + fee`)
- **Error Handling**: Comprehensive error handling for contract failures, insufficient funds, and network issues
- **Contract Requirements**: 
  - Contract must implement `fundAndRelay(address, uint256) payable` function
  - Contract must implement `calculateFee(uint256) view returns (uint256)` function
  - Contract handles fee distribution and user funding automatically

### Security Configuration File Format

The `security-config.json` file controls API key authentication and IP whitelisting:

```json
{
  "apiKeys": [
    {
      "key": "your_api_key_here",
      "name": "Client Name",
      "allowedIps": ["192.168.1.100", "10.0.0.0/24"],
      "enabled": true,
      "description": "Description of this client"
    }
  ],
  "globalIpWhitelist": ["127.0.0.1", "::1"],
  "settings": {
    "requireApiKey": true,
    "enforceIpWhitelist": true,
    "logFailedAttempts": true,
    "rateLimitEnabled": false,
    "rateLimitRequestsPerMinute": 60
  }
}
```

**API Key Configuration:**
- `key`: The API key string that clients must provide
- `name`: Human-readable name for the client
- `allowedIps`: List of allowed IP addresses/patterns (empty = no restrictions)
- `enabled`: Whether this API key is currently active
- `description`: Optional description

**IP Address Patterns:**
- Exact IP: `"192.168.1.100"`
- Wildcard: `"192.168.1.*"` (matches 192.168.1.0-255)
- CIDR: `"10.0.0.0/24"` (matches 10.0.0.0-255)

**Client Usage:**
```bash
# Using X-API-Key header
curl -H "X-API-Key: your_api_key_here" http://localhost:8080/api/endpoint

# Using Authorization header
curl -H "Authorization: Bearer your_api_key_here" http://localhost:8080/api/endpoint

# Using query parameter
curl "http://localhost:8080/api/endpoint?api_key=your_api_key_here"
```

### Web3j Integration
- Full Ethereum-compatible blockchain support
- Transaction decoding and encoding
- Gas estimation and price calculation
- Receipt monitoring with configurable retry logic

## Testing

### Test Configuration
- Test configuration in `src/test/resources/application-test.yml`
- Uses Avalanche Fuji testnet (chain ID 43113) for testing
- Mock authentication disabled in test environment (`auth.enabled: false`)
- Mock Gas Payer Contract address configured for testing
- Debug logging enabled for `com.utility.chainservice` package

### Testing Framework
- **JUnit 5** with `@Test` annotations
- **Mockito Kotlin** for mocking with `mock()`, `whenever()`, `verify()`
- **Coroutines Testing** with `runBlocking` for suspend functions
- **Spring Boot Test** with `@SpringBootTest` for integration tests

### Test Patterns
- **Suspend Function Testing**: Use `runBlocking { }` to test suspend methods
- **Web3j Mocking**: Mock `Request<*, ResponseType>` objects and their `.send()` methods
- **Exception Testing**: Test error scenarios with `assertFalse(result.success)` and `assertNotNull(result.error)`
- **Coverage Reports**: Generated in `build/reports/jacoco/test/html/index.html`

## Library Usage

This project is designed to be used as a dependency in other Spring Boot applications:

1. Add dependency to your `build.gradle.kts`
2. Configure application properties for blockchain and authentication
3. Implement business-specific plugins by extending `BlockchainServicePlugin`
4. Enable component scanning for `com.utility.chainservice` package
5. Create REST controllers that utilize the initialized plugins

### Important Implementation Notes
- This is a **library project** (not standalone application) - `bootJar` is disabled, `jar` is enabled
- Uses **Spring Boot auto-configuration** via `UtilityAutoConfiguration.kt`
- **Plugin auto-discovery** happens through Spring component scanning
- All blockchain operations are **suspend functions** requiring coroutines support
- **JitPack publishing** is configured for easy distribution as GitHub dependency