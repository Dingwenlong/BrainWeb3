package com.brainweb3.backend.chain;

import com.brainweb3.backend.config.ChainProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
  private volatile String dataContractAddress;
  private volatile String businessContractAddress;

  public FiscoBcosChainGateway(ChainProperties chainProperties, ResourceLoader resourceLoader) {
    this.chainProperties = chainProperties;
    this.resourceLoader = resourceLoader;
    this.dataContractAddress = normalize(chainProperties.getContractAddress());
    this.businessContractAddress = normalize(chainProperties.getBusinessContractAddress());
  }

  @Override
  public ChainRegistrationReceipt registerDataAsset(ChainRegistrationCommand command) {
    try {
      ensureReady();
      ContractBinding dataContract = resolveDataContract();
      String ownerDid = "did:brainweb3:%s".formatted(slugify(command.ownerOrganization()));
      String chainHash = buildChainHash(command.fingerprint());

      TransactionResponse response = transactionProcessor.sendTransactionAndGetResponse(
          dataContract.address(),
          loadResource(dataContract.abiPath()),
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
          dataContract.contractName(),
          dataContract.address(),
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

  @Override
  public ChainBusinessEventReceipt recordBusinessEvent(ChainBusinessEventCommand command) {
    try {
      ensureReady();
      ContractBinding businessContract = resolveBusinessContract();
      String eventHash = buildChainHash(buildBusinessEventSeed(command));
      String ownerDid = "did:brainweb3:%s".formatted(slugify(command.actorOrg()));
      String anchorAssetId = buildBusinessAnchorId(command, eventHash);

      TransactionResponse response = transactionProcessor.sendTransactionAndGetResponse(
          businessContract.address(),
          loadResource(businessContract.abiPath()),
          "registerDataAsset",
          List.of(
              anchorAssetId,
              ownerDid,
              eventHash,
              buildBusinessAnchorReference(command)
          )
      );

      TransactionReceipt receipt = response.getTransactionReceipt();
      if (receipt == null || !receipt.isStatusOK()) {
        throw new IllegalStateException(
            "FISCO business-event transaction failed: %s"
                .formatted(receipt == null ? "no receipt" : receipt.getMessage())
        );
      }

      return new ChainBusinessEventReceipt(
          normalize(chainProperties.getProvider()),
          normalize(chainProperties.getGroup()).isBlank() ? "group0" : chainProperties.getGroup(),
          businessContract.contractName(),
          businessContract.address(),
          eventHash,
          receipt.getTransactionHash(),
          Instant.now()
      );
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to anchor business event on FISCO BCOS.", exception);
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

  private ContractBinding resolveDataContract() throws Exception {
    String contractName = normalize(chainProperties.getContractName()).isBlank() ? "DataNotary" : chainProperties.getContractName();
    String abiPath = "classpath:fisco/contracts/DataNotary.abi";
    String binPath = "classpath:fisco/contracts/DataNotary.bin";
    String address = resolveContractAddress(
        contractName,
        chainProperties.isAutoDeploy(),
        abiPath,
        binPath,
        true
    );
    return new ContractBinding(contractName, address, abiPath, binPath);
  }

  private ContractBinding resolveBusinessContract() throws Exception {
    String contractName = normalize(chainProperties.getBusinessContractName());
    if (contractName.isBlank()) {
      contractName = "BusinessEventAnchor";
    }
    String abiPath = normalize(chainProperties.getBusinessContractAbiPath()).isBlank()
        ? "classpath:fisco/contracts/DataNotary.abi"
        : chainProperties.getBusinessContractAbiPath();
    String binPath = normalize(chainProperties.getBusinessContractBinPath()).isBlank()
        ? "classpath:fisco/contracts/DataNotary.bin"
        : chainProperties.getBusinessContractBinPath();
    String explicitAddress = normalize(chainProperties.getBusinessContractAddress());
    if (explicitAddress.isBlank() && !chainProperties.isBusinessContractAutoDeploy()) {
      ContractBinding dataContract = resolveDataContract();
      return new ContractBinding(contractName, dataContract.address(), abiPath, binPath);
    }

    String address = resolveContractAddress(
        contractName,
        chainProperties.isBusinessContractAutoDeploy(),
        abiPath,
        binPath,
        false
    );
    return new ContractBinding(contractName, address, abiPath, binPath);
  }

  private String resolveContractAddress(
      String contractName,
      boolean autoDeploy,
      String abiPath,
      String binPath,
      boolean primaryContract
  ) throws Exception {
    String currentAddress = primaryContract ? dataContractAddress : businessContractAddress;
    if (!normalize(currentAddress).isBlank()) {
      return currentAddress;
    }

    synchronized (monitor) {
      currentAddress = primaryContract ? dataContractAddress : businessContractAddress;
      if (!normalize(currentAddress).isBlank()) {
        return currentAddress;
      }
      if (!autoDeploy) {
        throw new IllegalStateException(
            "%s address is required when auto deployment is disabled.".formatted(contractName)
        );
      }

      TransactionResponse deployResponse = transactionProcessor.deployAndGetResponse(
          loadResource(abiPath),
          loadResource(binPath),
          List.of()
      );
      TransactionReceipt receipt = deployResponse.getTransactionReceipt();
      if (receipt == null || !receipt.isStatusOK()) {
        throw new IllegalStateException(
            "Failed to deploy %s: %s".formatted(contractName, receipt == null ? "no receipt" : receipt.getMessage())
        );
      }

      currentAddress = normalize(deployResponse.getContractAddress());
      if (primaryContract) {
        dataContractAddress = currentAddress;
      } else {
        businessContractAddress = currentAddress;
      }
      log.info("Auto deployed {} to {}", contractName, currentAddress);
      return currentAddress;
    }
  }

  private String buildChainHash(String fingerprint) {
    String hashValue = client.getCryptoSuite().hash(fingerprint);
    if (client.getCryptoType() != null && client.getCryptoType() == 1) {
      return "SM3:%s".formatted(hashValue);
    }
    return "HASH:%s".formatted(hashValue);
  }

  private String buildBusinessEventSeed(ChainBusinessEventCommand command) {
    return String.join(
        "|",
        normalize(command.datasetId()),
        normalize(command.eventType()),
        normalize(command.referenceId()),
        normalize(command.status()),
        normalize(command.actorId()),
        normalize(command.actorOrg()),
        normalize(command.detail())
    );
  }

  private String buildBusinessAnchorId(ChainBusinessEventCommand command, String eventHash) {
    String normalizedEventType = slugify(command.eventType());
    String digest = eventHash.contains(":") ? eventHash.substring(eventHash.indexOf(':') + 1) : eventHash;
    String shortDigest = digest.length() > 16 ? digest.substring(0, 16) : digest;
    return "evt-%s-%s".formatted(normalizedEventType, shortDigest);
  }

  private String buildBusinessAnchorReference(ChainBusinessEventCommand command) {
    return "brainweb3://chain-event/%s/%s/%s?status=%s"
        .formatted(
            normalize(command.datasetId()),
            normalize(command.eventType()),
            normalize(command.referenceId()),
            normalize(command.status())
        );
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
        normalize(dataContractAddress),
        resolveRpcPeers(),
        resolveTransportSecurity()
    );
  }

  private String loadResource(String location) throws IOException {
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

  private record ContractBinding(
      String contractName,
      String address,
      String abiPath,
      String binPath
  ) {
  }

  @Override
  public void close() {
    if (sdk != null) {
      sdk.stopAll();
    }
  }
}
