package stest.tron.wallet.dailybuild.trctoken;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.AccountResourceMessage;
import org.tron.api.WalletGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractTrcToken037 {


  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private static ByteString assetAccountId = null;
  private static final long now = System.currentTimeMillis();
  private static String tokenName = "testAssetIssue_" + Long.toString(now);
  private static final long TotalSupply = 10000000L;

  String description = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetDescription");
  String url = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetUrl");

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] dev001Address = ecKey1.getAddress();
  String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] user001Address = ecKey2.getAddress();
  String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {

    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

  }


  @Test(enabled = true, description = "Multi-level call transferToken tokenBalance")
  public void deploy01TransferTokenContract() {

    Assert
        .assertTrue(PublicMethed.sendcoin(dev001Address, 4048000000L, fromAddress,
            testKey002, blockingStubFull));
    logger.info(
        "dev001Address:" + Base58.encode58Check(dev001Address));
    Assert
        .assertTrue(PublicMethed.sendcoin(user001Address, 4048000000L, fromAddress,
            testKey002, blockingStubFull));
    logger.info(
        "user001Address:" + Base58.encode58Check(user001Address));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // freeze balance
    Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(dev001Address, 204800000,
        0, 1, dev001Key, blockingStubFull));

    Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(user001Address, 2048000000,
        0, 1, user001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    //Create a new AssetIssue success.
    Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
        100, start, end, 1, description, url, 10000L,
        10000L, 1L, 1L, dev001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();

    // deploy transferTokenContract
    int originEnergyLimit = 50000;

    String filePath = "src/test/resources/soliditycode/contractTrcToken037.sol";
    String contractName = "receiveTrc10";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    byte[] btestAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit,
            0L, 0, originEnergyLimit, "0",
            0, null, dev001Key, dev001Address,
            blockingStubFull);

    String contractName1 = "transferTrc10";
    HashMap retMap1 = PublicMethed.getBycodeAbi(filePath, contractName1);
    String code1 = retMap1.get("byteCode").toString();
    String abi1 = retMap1.get("abI").toString();
    byte[] transferTokenContractAddress = PublicMethed
        .deployContract(contractName1, abi1, code1, "", maxFeeLimit,
            0L, 0, originEnergyLimit, "0",
            0, null, dev001Key, dev001Address,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    Long beforeAssetIssueDevAddress = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);
    Long beforeAssetIssueUserAddress = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId,
            blockingStubFull);

    Long beforeAssetIssueContractAddress = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId,
            blockingStubFull);
    Long beforeAssetIssueBAddress = PublicMethed
        .getAssetIssueValue(btestAddress, assetAccountId,
            blockingStubFull);

    Long beforeBalanceContractAddress = PublicMethed.queryAccount(transferTokenContractAddress,
        blockingStubFull).getBalance();
    Long beforeUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("beforeAssetIssueContractAddress:" + beforeAssetIssueContractAddress);
    logger.info("beforeAssetIssueBAddress:" + beforeAssetIssueBAddress);

    logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress);
    logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress);
    logger.info("beforeBalanceContractAddress:" + beforeBalanceContractAddress);
    logger.info("beforeUserBalance:" + beforeUserBalance);

    String param =
        "\"" + Base58.encode58Check(btestAddress) + "\"";

    final String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
        "receive(address)",
        param, false, 0, 1000000000L, assetAccountId
            .toStringUtf8(),
        10, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account infoafter = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterAssetIssueDevAddress = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    Long afterAssetIssueContractAddress = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId,
            blockingStubFull);
    Long afterAssetIssueBAddress = PublicMethed
        .getAssetIssueValue(btestAddress, assetAccountId,
            blockingStubFull);

    Long afterAssetIssueUserAddress = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId, blockingStubFull);
    Long afterBalanceContractAddress = PublicMethed.queryAccount(transferTokenContractAddress,
        blockingStubFull).getBalance();
    Long afterUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("afterAssetIssueCount:" + afterAssetIssueDevAddress);
    logger.info("afterAssetIssueDevAddress:" + afterAssetIssueContractAddress);
    logger.info("afterAssetIssueBAddress:" + afterAssetIssueBAddress);
    logger.info("afterAssetIssueUserAddress:" + afterAssetIssueUserAddress);
    logger.info("afterBalanceContractAddress:" + afterBalanceContractAddress);
    logger.info("afterUserBalance:" + afterUserBalance);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterAssetIssueUserAddress == beforeAssetIssueUserAddress);
    Assert.assertEquals(afterBalanceContractAddress, beforeBalanceContractAddress);
    Assert.assertTrue(afterAssetIssueDevAddress == beforeAssetIssueDevAddress - 10);
    Assert.assertTrue(afterAssetIssueBAddress == beforeAssetIssueBAddress + 10);
    PublicMethed.unFreezeBalance(dev001Address, dev001Key, 1,
        dev001Address, blockingStubFull);
    PublicMethed.unFreezeBalance(user001Address, user001Key, 1,
        user001Address, blockingStubFull);
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(user001Address, user001Key, fromAddress, blockingStubFull);
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 0, dev001Address, blockingStubFull);
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 0, user001Address, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}


