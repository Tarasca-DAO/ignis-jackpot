package com.jelurida.ardor.contracts;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.GetAssetsByIssuerCall;
import nxt.http.callers.TriggerContractByRequestCall;
import nxt.http.callers.GetBalanceCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static com.jelurida.ardor.contracts.TarascaTester.initCollection;
import static com.jelurida.ardor.contracts.TarascaTester.sendAssets;
import static java.lang.Math.abs;
import static nxt.blockchain.ChildChain.IGNIS;


public class JackpotTest extends AbstractContractTest {

    @Test
    public void JackpotApi(){
        Logger.logDebugMessage("Test JackpotApi()");
        JO jackParams = new JO();
        int contractFrequency = 3;
        int collectionSize = 4;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",CHUCK.getRsAccount());

        initCollection(CHUCK.getSecretPhrase(), collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        // call contract to request information
        JO contractResponse = TriggerContractByRequestCall.create().contractName("Jackpot").call();

        Assert.assertEquals(contractResponse.getInt("collectionSize"),collectionSize);
        Assert.assertEquals(contractResponse.getString("jackpotAccountRs"),ALICE.getRsAccount());
        Assert.assertEquals(contractResponse.getString("collectionAccountRs"),CHUCK.getRsAccount());
        Assert.assertEquals(contractResponse.getInt("jackpotRunFrequency"),contractFrequency);
    }

    @Test
    public void rejectWinnerIncompleteTx(){
        Logger.logDebugMessage("Test rejectWinnerIncompleteTx()");
        JO jackParams = new JO();
        int contractFrequency = 4;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",CHUCK.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(CHUCK.getSecretPhrase(), collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;


        //JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        //sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        JO response = GetAssetsByIssuerCall.create().account(CHUCK.getRsAccount()).call();
        JA outerArray = response.getArray("assets");
        JA collectionAssets = outerArray.getArray(0);
        sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        generateBlock();
        generateBlock();


        JA notAllAssets = new JA();
        for (int i=0;i<collectionAssets.size()-1;i++){
            notAllAssets.add(collectionAssets.get(i));
        }

        //send not all assets to contract, expectation is jackpot will reject BOB!
        sendAssets(notAllAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"to Contract ALICE");

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        JO responseEmpty = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobAfter = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffAlice = balanceEmpty-balanceFull;

        Logger.logDebugMessage("TEST:CHECK RESULTS - reject winner incomplete TX ");
        // nothing shouldve happened
        Assert.assertTrue( balanceEmpty==balanceFull);
        Assert.assertTrue( balanceBobAfter==balanceBobBefore-notAllAssets.size()); //this assumes 1 Ignis fee!

        Logger.logDebugMessage("TEST:STOP - reject winner incomplete TX ");
    }

    @Test
    public void acceptSingleWinner(){
        Logger.logDebugMessage("TEST:START - accept single winner ");
        JO jackParams = new JO();
        int contractFrequency = 4;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",CHUCK.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(CHUCK.getSecretPhrase(), collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;


        JO response = GetAssetsByIssuerCall.create().account(CHUCK.getRsAccount()).call();
        JA outerArray = response.getArray("assets");
        JA collectionAssets = outerArray.getArray(0);
        sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        generateBlock();
        generateBlock();

        //send all assets to contract, expectation is JACKPOT!
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"to Contract ALICE");

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        JO responseEmpty = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobAfter = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffAlice = balanceEmpty-balanceFull;

        Logger.logDebugMessage("TEST acceptSingleWinner: RESULTS diffBob: %d, diffAlice: %d, jackpotBalance: %d",diffBob,diffAlice,balanceEmpty);

        Assert.assertTrue( balanceEmpty<10); // 10 ignis tolerance for fees..
        Assert.assertTrue( abs(diffBob + diffAlice)<10); // 10 ignis tolerance for fees..
    }

    @Test
    public void dividePriceByTwo(){
        Logger.logDebugMessage("Test jackPotDividePrice()");
        JO jackParams = new JO();
        int contractFrequency = 4;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",CHUCK.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(CHUCK.getSecretPhrase(), collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseDaveBefore = GetBalanceCall.create(IGNIS.getId()).account(DAVE.getRsAccount()).call();
        long balanceDaveBefore = Long.parseLong((String) responseDaveBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO response = GetAssetsByIssuerCall.create().account(CHUCK.getRsAccount()).call();
        JA outerArray = response.getArray("assets");
        JA collectionAssets = outerArray.getArray(0);
        sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");
        sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),DAVE.getRsAccount(),"to Dave");

        generateBlock();
        generateBlock();

        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"from Bob to Contract ALICE");
        sendAssets(collectionAssets,1,DAVE.getSecretPhrase(),ALICE.getRsAccount(),"from Dave to Contract ALICE");

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        JO responseEmpty = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobAfter = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseDaveAfter = GetBalanceCall.create(IGNIS.getId()).account(DAVE.getRsAccount()).call();
        long balanceDaveAfter = Long.parseLong((String) responseDaveAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffDave = balanceDaveAfter-balanceDaveBefore;
        long diffAlice = balanceEmpty-balanceFull;

        Assert.assertTrue( balanceEmpty<10); // 10 ignis tolerance for fees..
        Assert.assertTrue( abs(diffBob + diffAlice/2)<10); // 10 ignis tolerance for fees..
        Assert.assertTrue( abs(diffDave+ diffAlice/2)<10);
    }


    /*    @Test
     *   public void emptyPotRejectWinner(){
     *       Assert.assertTrue(false);
     *   }
     */
}