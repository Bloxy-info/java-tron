package org.tron.core.services;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.tron.common.storage.Deposit;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.db.AccountStore;
import org.tron.core.db.DelegationStore;
import org.tron.core.db.DynamicPropertiesStore;
import org.tron.core.db.Manager;
import org.tron.core.exception.BalanceInsufficientException;
import org.tron.protos.Protocol.Vote;

@Slf4j(topic = "delegation")
@Component
public class DelegationService {

  @Setter
  private Manager manager;

  public void payStandbyWitness() {
    List<ByteString> witnessAddressList = new ArrayList<>();
    for (WitnessCapsule witnessCapsule : manager.getWitnessStore().getAllWitnesses()) {
      witnessAddressList.add(witnessCapsule.getAddress());
    }
    sortWitness(witnessAddressList);
    if (witnessAddressList.size() > ChainConstant.MAX_ACTIVE_WITNESS_NUM) {
      witnessAddressList = witnessAddressList.subList(0, ChainConstant.MAX_ACTIVE_WITNESS_NUM);
    }

    long voteSum = 0;
    long totalPay = 115_200_000_000L / 7200;
    for (ByteString b : witnessAddressList) {
      voteSum += getWitnesseByAddress(b).getVoteCount();
    }
    long cycle = manager.getDynamicPropertiesStore().getCurrentCycleNumber();
    if (voteSum > 0) {
      for (ByteString b : witnessAddressList) {
        double eachVotePay = (double) totalPay / voteSum;
        long pay = (long) (getWitnesseByAddress(b).getVoteCount() * eachVotePay);
        logger.debug("pay {} stand reward {}", Hex.toHexString(b.toByteArray()), pay);
        manager.getDelegationStore().addReward(cycle, b.toByteArray(), pay);
      }
    }

  }

  public void payBlockReward(byte[] witnessAddress, long value) {
    logger.debug("pay {} block reward {}", Hex.toHexString(witnessAddress), value);
    long cycle = manager.getDynamicPropertiesStore().getCurrentCycleNumber();
    manager.getDelegationStore().addReward(cycle, witnessAddress, value);
  }

  public void withdrawReward(byte[] address, Deposit deposit) {
    if (!manager.getDynamicPropertiesStore().allowChangeDelegation()) {
      return;
    }
    AccountStore accountStore = manager.getAccountStore();
    DelegationStore delegationStore = manager.getDelegationStore();
    DynamicPropertiesStore dynamicPropertiesStore = manager.getDynamicPropertiesStore();
    AccountCapsule accountCapsule;
    if (deposit == null) {
      accountCapsule = accountStore.get(address);
    } else {
      accountCapsule = deposit.getAccount(address);
    }
    long beginCycle = delegationStore.getBeginCycle(address);
    long endCycle = delegationStore.getEndCycle(address);
    long currentCycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long reward = 0;
    int brokerage = 0;
    if (beginCycle == currentCycle) {
      return;
    }
    //withdraw the latest cycle reward
    if (beginCycle + 1 == endCycle && beginCycle < currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      brokerage = delegationStore.getBrokerage(beginCycle, address);
      logger.info("latest cycle reward {},{}", beginCycle, account.getVotesList());
      if (account != null) {
        reward = computeReward(beginCycle, account, brokerage);
        adjustAllowance(address, reward);
        reward = 0;
      }
      beginCycle += 1;
    }
    //
    endCycle = currentCycle;
    if (accountCapsule == null || CollectionUtils.isEmpty(accountCapsule.getVotesList())) {
      manager.getDelegationStore().setBeginCycle(address,
          dynamicPropertiesStore.getCurrentCycleNumber());
      return;
    }
    if (beginCycle < endCycle) {
      brokerage = delegationStore.getBrokerage(address);
      for (long cycle = beginCycle; cycle < endCycle; cycle++) {
        reward += computeReward(cycle, accountCapsule, brokerage);
      }
      adjustAllowance(address, reward);
    }
    delegationStore.setBeginCycle(address, endCycle);
    delegationStore.setEndCycle(address, endCycle + 1);
    delegationStore.setAccountVote(endCycle, address, accountCapsule);
    delegationStore.setBrokerage(endCycle, address, brokerage);
    logger.info("adjust {} allowance {}, now currentCycle {}, beginCycle {}, endCycle {}, "
            + "brokerage {}, " + "account vote {},", Hex.toHexString(address), reward, currentCycle,
        beginCycle, endCycle, brokerage, accountCapsule.getVotesList());
  }

  public void queryReward(byte[] address) {

  }

  private long computeReward(long cycle, AccountCapsule accountCapsule, int brokerage) {
    long reward = 0;
    for (Vote vote : accountCapsule.getVotesList()) {
      long totalReward = manager.getDelegationStore()
          .getReward(cycle, vote.getVoteAddress().toByteArray());
      long totalVote = manager.getDelegationStore()
          .getWitnessVote(cycle, vote.getVoteAddress().toByteArray());
      if (totalVote == DelegationStore.REMARK) {
        totalVote = manager.getWitnessStore().get(vote.getVoteAddress().toByteArray())
            .getVoteCount();
      }
      long userVote = vote.getVoteCount();
      double voteRate = (double) userVote / totalVote;
      reward += voteRate * totalReward;
      logger.debug("computeReward {} {},{},{},{}",
          Hex.toHexString(vote.getVoteAddress().toByteArray()),
          userVote, totalVote, totalReward, reward);
      if (!Arrays.equals(vote.getVoteAddress().toByteArray(),
          accountCapsule.getAddress().toByteArray()) && brokerage > 0) {
        double brokerageRate = (double) brokerage / 100;
        long brokerageAmount = (long) (brokerageRate * reward);
        reward -= brokerageAmount;
        adjustAllowance(vote.getVoteAddress().toByteArray(), brokerageAmount);
      }
    }
    return reward;
  }

  public WitnessCapsule getWitnesseByAddress(ByteString address) {
    return this.manager.getWitnessStore().get(address.toByteArray());
  }

  private void adjustAllowance(byte[] address, long amount) {
    try {
      if (amount <= 0) {
        return;
      }
      logger.info("before {}", manager.getAccountStore().get(address));
      manager.adjustAllowance(address, amount);
      logger.info("end {}", manager.getAccountStore().get(address));
    } catch (BalanceInsufficientException e) {
      logger.error("withdrawReward error: {},{}", Hex.toHexString(address), address, e);
    }
  }

  private long getEndCycle(byte[] address) {
    long endCycle = manager.getDelegationStore().getEndCycle(address);
    if (endCycle == DelegationStore.REMARK) {
      endCycle = manager.getDynamicPropertiesStore().getCurrentCycleNumber();
    }
    return endCycle;
  }

  private void sortWitness(List<ByteString> list) {
    list.sort(Comparator.comparingLong((ByteString b) -> getWitnesseByAddress(b).getVoteCount())
        .reversed()
        .thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
  }

}
