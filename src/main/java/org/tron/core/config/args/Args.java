package org.tron.core.config.args;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.typesafe.config.ConfigObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.tron.common.crypto.ECKey;
import org.tron.common.overlay.discover.Node;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static org.tron.common.crypto.Hash.sha3;

@Slf4j
@NoArgsConstructor
@Component
public class Args {

  private static final Args INSTANCE = new Args();

  @Parameter(names = {"-d", "--output-directory"}, description = "Directory")
  private String outputDirectory = "output-directory";

  @Getter
  @Parameter(names = {"-h", "--help"}, help = true, description = "HELP message")
  private boolean help = false;

  @Getter
  @Parameter(names = {"-w", "--witness"})
  private boolean witness = false;

  @Getter
  @Parameter(description = "--seed-nodes")
  private List<String> seedNodes = new ArrayList<>();

  @Getter
  @Parameter(names = {"-p", "--private-key"}, description = "private-key")
  private String privateKey = "";

  @Parameter(names = {"--storage-directory"}, description = "Storage directory")
  private String storageDirectory = "";

  @Parameter(names = {"--overlay-port"}, description = "Overlay port")
  private int overlayPort = 0;

  @Getter
  private Storage storage;

  @Getter
  private Overlay overlay;

  @Getter
  private SeedNode seedNode;

  @Getter
  private GenesisBlock genesisBlock;

  @Getter
  @Setter
  private String chainId;

  @Getter
  @Setter
  private LocalWitnesses localWitnesses;

  @Getter
  @Setter
  private long blockInterval;

  @Getter
  @Setter
  private boolean needSyncCheck;

  @Getter
  @Setter
  private boolean nodeDiscoveryEnable;

  @Getter
  @Setter
  private boolean nodeDiscoveryPersist;

  @Getter
  @Setter
  private int nodeConnectionTimeout;

  @Getter
  @Setter
  private List<Node> nodeActive;

  @Getter
  @Setter
  private int nodeChannelReadTimeout;

  @Getter
  @Setter
  private int nodeMaxActiveNodes;

  @Getter
  @Setter
  private int nodeListenPort;

  @Getter
  @Setter
  private String nodeDiscoveryBindIp;

  @Getter
  @Setter
  private String nodeExternalIp;

  @Getter
  @Setter
  private boolean nodeDiscoveryPublicHomeNode;

  @Getter
  @Setter
  private long nodeP2pPingInterval;

  @Getter
  @Setter
  private long syncNodeCount;

  public static void clearParam() {
    INSTANCE.outputDirectory = "output-directory";
    INSTANCE.help = false;
    INSTANCE.witness = false;
    INSTANCE.seedNodes = new ArrayList<>();
    INSTANCE.privateKey = "";
    INSTANCE.storageDirectory = "";
    INSTANCE.overlayPort = 0;
    INSTANCE.storage = null;
    INSTANCE.overlay = null;
    INSTANCE.seedNode = null;
    INSTANCE.genesisBlock = null;
    INSTANCE.chainId = null;
    INSTANCE.localWitnesses = null;
    INSTANCE.blockInterval = 0L;
    INSTANCE.needSyncCheck = false;
    INSTANCE.nodeDiscoveryEnable = false;
    INSTANCE.nodeDiscoveryPersist = false;
    INSTANCE.nodeConnectionTimeout = 0;
    INSTANCE.nodeActive = Collections.EMPTY_LIST;
    INSTANCE.nodeChannelReadTimeout = 0;
    INSTANCE.nodeMaxActiveNodes = 0;
    INSTANCE.nodeListenPort = 0;
    INSTANCE.nodeDiscoveryBindIp = "";
    INSTANCE.nodeExternalIp = "";
    INSTANCE.nodeDiscoveryPublicHomeNode = false;
    INSTANCE.nodeP2pPingInterval = 0L;
    INSTANCE.syncNodeCount = 0;
  }

  /**
   * set parameters.
   */
  public static void setParam(final String[] args, final com.typesafe.config.Config config) {

    JCommander.newBuilder().addObject(INSTANCE).build().parse(args);
    if (StringUtils.isBlank(INSTANCE.privateKey)) {
      privateKey(config);
    }
    logger.info("private.key = {}", INSTANCE.privateKey);

    INSTANCE.storage = new Storage();
    INSTANCE.storage.setDirectory(Optional.ofNullable(INSTANCE.storageDirectory)
        .filter(StringUtils::isNotEmpty)
        .orElse(config.getString("storage.directory")));

    INSTANCE.overlay = new Overlay();
    INSTANCE.overlay.setPort(Optional.ofNullable(INSTANCE.overlayPort)
        .filter(i -> 0 != i)
        .orElse(config.getInt("overlay.port")));

    INSTANCE.seedNode = new SeedNode();
    INSTANCE.seedNode.setIpList(Optional.ofNullable(INSTANCE.seedNodes)
        .filter(seedNode -> 0 != seedNode.size())
        .orElse(config.getStringList("seed.node.ip.list")));

    if (config.hasPath("localwitness")) {
      INSTANCE.localWitnesses = new LocalWitnesses();
      List<String> localwitness = config.getStringList("localwitness");
      if (localwitness.size() > 1) {
        logger.warn("localwitness size must be one, get the first one");
        localwitness = localwitness.subList(0, 1);
      }
      INSTANCE.localWitnesses.setPrivateKeys(localwitness);
    }

    if (config.hasPath("genesis.block")) {
      INSTANCE.genesisBlock = new GenesisBlock();

      INSTANCE.genesisBlock.setTimestamp(config.getString("genesis.block.timestamp"));
      INSTANCE.genesisBlock.setParentHash(config.getString("genesis.block.parentHash"));

      if (config.hasPath("genesis.block.assets")) {
        INSTANCE.genesisBlock.setAssets(getAccountsFromConfig(config));
      }
      if (config.hasPath("genesis.block.witnesses")) {
        INSTANCE.genesisBlock.setWitnesses(getWitnessesFromConfig(config));
      }
    } else {
      INSTANCE.genesisBlock = GenesisBlock.getDefault();
    }
    INSTANCE.blockInterval = config.getLong("block.interval");
    INSTANCE.needSyncCheck = config.getBoolean("block.needSyncCheck");

    if (config.hasPath("node.discovery.enable")) {
      INSTANCE.nodeDiscoveryEnable = config.getBoolean("node.discovery.enable");
    }

    if (config.hasPath("node.discovery.persist")) {
      INSTANCE.nodeDiscoveryPersist = config.getBoolean("node.discovery.persist");
    }

    if (config.hasPath("node.connection.timeout")) {
      INSTANCE.nodeConnectionTimeout = config.getInt("node.connection.timeout") * 1000;
    }

    INSTANCE.nodeActive = nodeActive(config);

    if (config.hasPath("node.channel.read.timeout")) {
      INSTANCE.nodeChannelReadTimeout = config.getInt("node.channel.read.timeout");
    }

    if (config.hasPath("node.maxActiveNodes")) {
      INSTANCE.nodeMaxActiveNodes = config.getInt("node.maxActiveNodes");
    }

    if (config.hasPath("node.listen.port")) {
      INSTANCE.nodeListenPort = config.getInt("node.listen.port");
    }

    bindIp(config);
    externalIp(config);

    if (config.hasPath("node.discovery.public.home.node")) {
      INSTANCE.nodeDiscoveryPublicHomeNode = config.getBoolean("node.discovery.public.home.node");
    }

    if (config.hasPath("node.p2p.pingInterval")) {
      INSTANCE.nodeP2pPingInterval = config.getLong("node.p2p.pingInterval");
    }

    if (config.hasPath("syn.node.count")) {
      INSTANCE.syncNodeCount = config.getLong("syn.node.count");
    }
  }


  private static List<Witness> getWitnessesFromConfig(final com.typesafe.config.Config config) {
    return config.getObjectList("genesis.block.witnesses").stream()
        .map(Args::createWitness)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static Witness createWitness(final ConfigObject witnessAccount) {
    final Witness witness = new Witness();
    witness.setAddress(witnessAccount.get("address").unwrapped().toString());
    witness.setUrl(witnessAccount.get("url").unwrapped().toString());
    witness.setVoteCount(witnessAccount.toConfig().getLong("voteCount"));
    return witness;
  }

  private static List<Account> getAccountsFromConfig(final com.typesafe.config.Config config) {
    return config.getObjectList("genesis.block.assets").stream()
        .map(Args::createAccount)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static Account createAccount(final ConfigObject asset) {
    final Account account = new Account();
    account.setAccountName(asset.get("accountName").unwrapped().toString());
    account.setAccountType(asset.get("accountType").unwrapped().toString());
    account.setAddress(asset.get("address").unwrapped().toString());
    account.setBalance(asset.get("balance").unwrapped().toString());
    return account;
  }

  public static Args getInstance() {
    return INSTANCE;
  }

  /**
   * get output directory.
   */
  public String getOutputDirectory() {
    if (!this.outputDirectory.equals("") && !this.outputDirectory.endsWith(File.separator)) {
      return this.outputDirectory + File.separator;
    }
    return this.outputDirectory;
  }

  private static List<Node> nodeActive(final com.typesafe.config.Config config) {
    if (!config.hasPath("node.active")) {
      return Collections.EMPTY_LIST;
    }
    List<Node> ret = new ArrayList<>();
    List<? extends ConfigObject> list = config.getObjectList("node.active");
    for (ConfigObject configObject : list) {
      Node n;
      if (configObject.get("url") != null) {
        String url = configObject.toConfig().getString("url");
        n = new Node(url.startsWith("enode://") ? url : "enode://" + url);
      } else if (configObject.get("ip") != null) {
        String ip = configObject.toConfig().getString("ip");
        int port = configObject.toConfig().getInt("port");
        byte[] nodeId;
        if (configObject.toConfig().hasPath("nodeId")) {
          nodeId = Hex.decode(configObject.toConfig().getString("nodeId").trim());
          if (nodeId.length != 64) {
            throw new RuntimeException("Invalid config nodeId '" + nodeId + "' at " + configObject);
          }
        } else {
          if (configObject.toConfig().hasPath("nodeName")) {
            String nodeName = configObject.toConfig().getString("nodeName").trim();
            // FIXME should be keccak-512 here ?
            nodeId = ECKey.fromPrivate(sha3(nodeName.getBytes())).getNodeId();
          } else {
            throw new RuntimeException(
                "Either nodeId or nodeName should be specified: " + configObject);
          }
        }
        n = new Node(nodeId, ip, port);
      } else {
        throw new RuntimeException(
            "Unexpected element within 'peer.active' config list: " + configObject);
      }
      ret.add(n);
    }
    return ret;
  }

  private static void privateKey(final com.typesafe.config.Config config) {
    if (config.hasPath("private.key")) {
      INSTANCE.privateKey = config.getString("private.key");
      if (INSTANCE.privateKey.length() != 64) {
        throw new RuntimeException(
            "The peer.privateKey needs to be Hex encoded and 32 byte length");
      }
    } else {
      INSTANCE.privateKey = getGeneratedNodePrivateKey();
    }
  }

  private static String getGeneratedNodePrivateKey() {
    String nodeId;
    try {
      File file = new File(INSTANCE.storageDirectory, "nodeId.properties");
      Properties props = new Properties();
      if (file.canRead()) {
        try (Reader r = new FileReader(file)) {
          props.load(r);
        }
      } else {
        ECKey key = new ECKey();
        props.setProperty("nodeIdPrivateKey", Hex.toHexString(key.getPrivKeyBytes()));
        props.setProperty("nodeId", Hex.toHexString(key.getNodeId()));
        file.getParentFile().mkdirs();
        try (Writer w = new FileWriter(file)) {
          props.store(w,
              "Generated NodeID. To use your own nodeId please refer to 'peer.privateKey' config option.");
        }
        logger.info("New nodeID generated: " + props.getProperty("nodeId"));
        logger.info("Generated nodeID and its private key stored in " + file);
      }
      nodeId = props.getProperty("nodeIdPrivateKey");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return nodeId;
  }

  private static void bindIp(final com.typesafe.config.Config config) {
    if (!config.hasPath("node.discovery.bind.ip") || config.getString("node.discovery.bind.ip")
        .trim().isEmpty()) {
      if (INSTANCE.nodeDiscoveryBindIp == null) {
        logger.info("Bind address wasn't set, Punching to identify it...");
        try {
          Socket s = new Socket("www.google.com", 80);
          INSTANCE.nodeDiscoveryBindIp = s.getLocalAddress().getHostAddress();
          logger.info("UDP local bound to: {}", INSTANCE.nodeDiscoveryBindIp);
        } catch (IOException e) {
          logger.warn("Can't get bind IP. Fall back to 0.0.0.0: " + e);
          INSTANCE.nodeDiscoveryBindIp = "0.0.0.0";
        }
      }
    } else {
      INSTANCE.nodeDiscoveryBindIp = config.getString("node.discovery.bind.ip").trim();
    }
  }

  private static void externalIp(final com.typesafe.config.Config config) {
    if (!config.hasPath("node.discovery.external.ip") || config
        .getString("node.discovery.external.ip").trim().isEmpty()) {
      if (INSTANCE.nodeExternalIp == null) {
        logger.info("External IP wasn't set, using checkip.amazonaws.com to identify it...");
        try {
          BufferedReader in = new BufferedReader(new InputStreamReader(
              new URL("http://checkip.amazonaws.com").openStream()));
          INSTANCE.nodeExternalIp = in.readLine();
          if (INSTANCE.nodeExternalIp == null || INSTANCE.nodeExternalIp.trim().isEmpty()) {
            throw new IOException("Invalid address: '" + INSTANCE.nodeExternalIp + "'");
          }
          try {
            InetAddress.getByName(INSTANCE.nodeExternalIp);
          } catch (Exception e) {
            throw new IOException("Invalid address: '" + INSTANCE.nodeExternalIp + "'");
          }
          logger.info("External address identified: {}", INSTANCE.nodeExternalIp);
        } catch (IOException e) {
          INSTANCE.nodeExternalIp = INSTANCE.nodeDiscoveryBindIp;
          logger.warn(
              "Can't get external IP. Fall back to peer.bind.ip: " + INSTANCE.nodeExternalIp + " :"
                  + e);
        }
      }
    } else {
      INSTANCE.nodeExternalIp = config.getString("node.discovery.external.ip").trim();
    }
  }

  public ECKey getMyKey() {
    return ECKey.fromPrivate(Hex.decode(INSTANCE.privateKey));
  }

  /**
   * Home NodeID calculated from 'peer.privateKey' property
   */
  public byte[] nodeId() {
    return getMyKey().getNodeId();
  }
}