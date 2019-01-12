package com.jelurida.ardor.contracts;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
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
        APICall apiCall = new APICall.Builder("triggerContractByRequest").
                param("contractName", "Jackpot").
                build();
        JO contractResponse = new JO(apiCall.invoke());

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

        APICall apiCallFull = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",ALICE.getRsAccount()).
                build();
        JO responseFull = new JO(apiCallFull.invoke());
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallBobBefore = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",BOB.getRsAccount()).
                build();
        JO responseBobBefore = new JO(apiCallBobBefore.invoke());
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;


        JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        generateBlock();
        generateBlock();


        JA notAllAssets = new JA();
        for (int i=0;i<collectionAssets.size()-1;i++){
            notAllAssets.add(collectionAssets.get(i));
        }

        //send all assets to contract, expectation is JACKPOT!
        sendAssets(notAllAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"to Contract ALICE");

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        APICall apiCallEmpty = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",ALICE.getRsAccount()).
                build();
        JO responseEmpty = new JO(apiCallEmpty.invoke());
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallBobAfter = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",BOB.getRsAccount()).
                build();
        JO responseBobAfter = new JO(apiCallBobAfter.invoke());
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;


        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffAlice = balanceEmpty-balanceFull;

        // nothing shouldve happened
        Assert.assertTrue( balanceEmpty==balanceFull);
        Assert.assertTrue( balanceBobAfter==balanceBobBefore-notAllAssets.size()); //this assumes 1 Ignis fee!
    }

    @Test
    public void acceptSingleWinner(){
        Logger.logDebugMessage("Test jackPotAccept()");
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

        APICall apiCallFull = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",ALICE.getRsAccount()).
                build();
        JO responseFull = new JO(apiCallFull.invoke());
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallBobBefore = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",BOB.getRsAccount()).
                build();
        JO responseBobBefore = new JO(apiCallBobBefore.invoke());
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;


        JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
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

        APICall apiCallEmpty = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",ALICE.getRsAccount()).
                build();
        JO responseEmpty = new JO(apiCallEmpty.invoke());
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallBobAfter = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",BOB.getRsAccount()).
                build();
        JO responseBobAfter = new JO(apiCallBobAfter.invoke());
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;


        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffAlice = balanceEmpty-balanceFull;

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

        APICall apiCallFull = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",ALICE.getRsAccount()).
                build();
        JO responseFull = new JO(apiCallFull.invoke());
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallBobBefore = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",BOB.getRsAccount()).
                build();
        APICall apiCallDaveBefore = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",DAVE.getRsAccount()).
                build();
        JO responseBobBefore = new JO(apiCallBobBefore.invoke());
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseDaveBefore = new JO(apiCallDaveBefore.invoke());
        long balanceDaveBefore = Long.parseLong((String) responseDaveBefore.get("balanceNQT"))/IGNIS.ONE_COIN;


        JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
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

        APICall apiCallEmpty = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",ALICE.getRsAccount()).
                build();
        JO responseEmpty = new JO(apiCallEmpty.invoke());
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallBobAfter = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",BOB.getRsAccount()).
                build();
        JO responseBobAfter = new JO(apiCallBobAfter.invoke());
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        APICall apiCallDaveAfter = new APICall.Builder("getBalance").
                param("chain", 2).
                param("account",DAVE.getRsAccount()).
                build();
        JO responseDaveAfter = new JO(apiCallDaveAfter.invoke());
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
