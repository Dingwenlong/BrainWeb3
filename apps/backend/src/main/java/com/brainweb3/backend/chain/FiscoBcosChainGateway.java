package com.brainweb3.backend.chain;

import com.brainweb3.backend.config.ChainProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class FiscoBcosChainGateway implements ChainGateway, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(FiscoBcosChainGateway.class);
  private static final Pattern PEERS_PATTERN = Pattern.compile("peers\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);
  private static final Pattern DISABLE_SSL_PATTERN = Pattern.compile("disableSsl\\s*=\\s*\"?(true|false)\"?");

  private final ChainProperties chainProperties;
  private final ResourceLoader resourceLoader;
  private final Object monitor = new Object();

  private volatile BcosSDK sdk;
  private volatile Client client;
  private volatile AssembleTransactionProcessor transactionProcessor;
  private volatile String contractAddress;

  public FiscoBcosChainGateway(ChainProperties chainProperties, ResourceLoader resourceLoader) {
    this.chainProperties = chainProperties;
    this.resourceLoader = resourceLoader;
    this.contractAddress = normalize(chainProperties.getContractAddress());
  }

  @Override
  public ChainRegistrationReceipt registerDataAsset(ChainRegistrationCommand command) {
    try {
      ensureReady();
      String activeContractAddress = resolveContractAddress();
      String ownerDid = "did:brainweb3:%s".formatted(slugify(command.ownerOrganization()));
      String chainHash = buildChainHash(command.fingerprint());

      TransactionResponse response = transactionProcessor.sendTransactionAndGetResponse(
          activeContractAddress,
          loadAbi(),
          "registerDataAsset",
          List.of(
              command.datasetId(),
              ownerDid,
              chainHash,
              command.offChainReference()
          )
      );

      TransactionReceipt receipt = response.getTransactionReceipt();
      if (receipt == null || !receipt.isStatusOK()) {
        throw new IllegalStateException(
            "FISCO transaction failed: %s".formatted(receipt == null ? "no receipt" : receipt.getMessage())
        );
      }

      return new ChainRegistrationReceipt(
          normalize(chainProperties.getProvider()),
          normalize(chainProperties.getGroup()).isBlank() ? "group0" : chainProperties.getGroup(),
          normalize(chainProperties.getContractName()).isBlank() ? "DataNotary" : chainProperties.getContractName(),
          activeContractAddress,
          chainHash,
          command.offChainReference(),
          command.offChainReference(),
          receipt.getTransactionHash(),
          ownerDid,
          "owner-review required before training",
          "fisco-registered"
      );
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to register dataset on FISCO BCOS.", exception);
    }
  }

  private void ensureReady() throws Exception {
    if (transactionProcessor != null) {
      return;
    }

    synchronized (monitor) {
      if (transactionProcessor != null) {
        return;
      }

      String configPath = materializeConfigPath(chainProperties.getConfigPath());
      sdk = BcosSDK.build(configPath);
      client = sdk.getClient(normalize(chainProperties.getGroup()).isBlank()
          ? "group0"
          : chainProperties.getGroup());

      client.getBlockNumber();

      CryptoSuite cryptoSuite = client.getCryptoSuite();
      CryptoKeyPair keyPair = cryptoSuite.getCryptoKeyPair();
      if (keyPair == null) {
        throw new IllegalStateException("No crypto key pair was loaded from the FISCO config.");
      }

      transactionProcessor = TransactionProcessorFactory.createAssembleTransactionProcessor(client, keyPair);
    }
  }

  private String resolveContractAddress() throws Exception {
    if (!normalize(contractAddress).isBlank()) {
      return contractAddress;
    }

    synchronized (monitor) {
      if (!normalize(contractAddress).isBlank()) {
        return contractAddress;
      }
      if (!chainProperties.isAutoDeploy()) {
        throw new IllegalStateException("CHAIN_CONTRACT_ADDRESS is required when auto deployment is disabled.");
      }

      TransactionResponse deployResponse = transactionProcessor.deployAndGetResponse(
          loadAbi(),
          loadBin(),
          List.of()
      );
      TransactionReceipt receipt = deployResponse.getTransactionReceipt();
      if (receipt == null || !receipt.isStatusOK()) {
        throw new IllegalStateException(
            "Failed to deploy DataNotary: %s".formatted(receipt == null ? "no receipt" : receipt.getMessage())
        );
      }

      contractAddress = normalize(deployResponse.getContractAddress());
      log.info("Auto deployed {} to {}", chainProperties.getContractName(), contractAddress);
      return contractAddress;
    }
  }

  private String buildChainHash(String fingerprint) {
    String hashValue = client.getCryptoSuite().hash(fingerprint);
    if (client.getCryptoType() != null && client.getCryptoType() == 1) {
      return "SM3:%s".formatted(hashValue);
    }
    return "HASH:%s".formatted(hashValue);
  }

  @Override
  public ChainRuntimeStatus describeStatus() {
    String mode = transactionProcessor == null ? "configured" : "connected";
    if (chainProperties.isEnabled() && transactionProcessor == null) {
      try {
        ensureReady();
        mode = "connected";
      } catch (Exception exception) {
        log.warn("Failed to establish FISCO connection while describing runtime status.", exception);
        mode = "connection-error";
      }
    }

    return new ChainRuntimeStatus(
        normalize(chainProperties.getProvider()),
        chainProperties.isEnabled(),
        mode,
        normalize(chainProperties.getGroup()).isBlank() ? "group0" : chainProperties.getGroup(),
        normalize(chainProperties.getContractName()).isBlank() ? "DataNotary" : chainProperties.getContractName(),
        normalize(contractAddress),
        resolveRpcPeers(),
        resolveTransportSecurity()
    );
  }

  private String loadAbi() throws IOException {
    return readResource("classpath:fisco/contracts/DataNotary.abi");
  }

  private String loadBin() throws IOException {
    return readResource("classpath:fisco/contracts/DataNotary.bin");
  }

  private String readResource(String location) throws IOException {
    Resource resource = resourceLoader.getResource(location);
    if (!resource.exists()) {
      throw new IOException("Missing resource: " + location);
    }
    return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
  }

  private String materializeConfigPath(String originalPath) throws IOException {
    if (!originalPath.startsWith("classpath:")) {
      return originalPath;
    }

    String relativePath = originalPath.substring("classpath:".length());
    if (relativePath.startsWith("/")) {
      relativePath = relativePath.substring(1);
    }

    Resource configResource = resourceLoader.getResource("classpath:" + relativePath);
    if (!configResource.exists()) {
      throw new IOException("Missing classpath config: " + originalPath);
    }

    Path tempDir = Files.createTempDirectory("brainweb3-fisco-config");
    Path targetPath = tempDir.resolve(Path.of(relativePath).getFileName().toString());
    Files.writeString(
        targetPath,
        new String(configResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
        StandardCharsets.UTF_8
    );
    return targetPath.toAbsolutePath().toString();
  }

  private List<String> resolveRpcPeers() {
    try {
      String content = readConfigText(chainProperties.getConfigPath());
      Matcher matcher = PEERS_PATTERN.matcher(content);
      if (!matcher.find()) {
        return List.of();
      }

      return List.of(matcher.group(1).split(",")).stream()
          .map(value -> value.replace("\"", "").trim())
          .filter(value -> !value.isBlank())
          .toList();
    } catch (IOException exception) {
      log.warn("Failed to resolve FISCO RPC peers from config.", exception);
      return List.of();
    }
  }

  private String resolveTransportSecurity() {
    try {
      String content = readConfigText(chainProperties.getConfigPath());
      Matcher matcher = DISABLE_SSL_PATTERN.matcher(content);
      if (matcher.find() && "true".equalsIgnoreCase(matcher.group(1))) {
        return "plaintext-rpc";
      }
    } catch (IOException exception) {
      log.warn("Failed to resolve FISCO transport security from config.", exception);
    }

    return "tls-rpc";
  }

  private String readConfigText(String originalPath) throws IOException {
    if (originalPath.startsWith("classpath:")) {
      String relativePath = originalPath.substring("classpath:".length());
      if (relativePath.startsWith("/")) {
        relativePath = relativePath.substring(1);
      }

      Resource configResource = resourceLoader.getResource("classpath:" + relativePath);
      if (!configResource.exists()) {
        throw new IOException("Missing classpath config: " + originalPath);
      }

      return new String(configResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    return Files.readString(Path.of(originalPath), StandardCharsets.UTF_8);
  }

  private String slugify(String input) {
    return input.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  @Override
  public void close() {
    if (sdk != null) {
      sdk.stopAll();
    }
  }
}
