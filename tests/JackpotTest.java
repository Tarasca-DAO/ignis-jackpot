package com.jelurida.ardor.contracts;

import nxt.addons.ContractRunner;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.*;
import nxt.http.responses.BlockResponse;
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
        int contractFrequency = 13;
        int collectionSize = 4;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",BOB.getRsAccount());

        initCollection(collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        // call contract to request information
        JO contractResponse = TriggerContractByRequestCall.create().contractName("Jackpot").call();

        Assert.assertEquals(contractResponse.getInt("collectionSize"),collectionSize);
        Assert.assertEquals(contractResponse.getString("jackpotAccountRs"),ALICE.getRsAccount());
        Assert.assertEquals(contractResponse.getString("collectionAccountRs"),BOB.getRsAccount());
        Assert.assertEquals(contractResponse.getInt("jackpotRunFrequency"),contractFrequency);
    }

    @Test
    public void rejectWinnerIncompleteTx(){
        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Start");
        JO jackParams = new JO();
        int contractFrequency = 9;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        //JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        //sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Prepare accounts");
        //JA collectionAssets = TarascaTester.getCollectionAssets();
        //collectionAssets.stream().forEach(a -> Logger.logDebugMessage("TEST: asset from collection: ",a.toString()));
        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player   (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());

        generateBlock();
        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Start playing");

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

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

        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Evaluate results");
        JO responseEmpty = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobAfter = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffAlice = balanceEmpty-balanceFull;

        // nothing shouldve happened
        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Asserting that ALICE's balance didn't change");
        Assert.assertTrue( balanceEmpty==balanceFull);
        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Asserting that Bobs balance didnt change except for the fees");
        Assert.assertTrue( balanceBobAfter==balanceBobBefore- (notAllAssets.size()*100)); //this assumes 100 Ignis fee!

        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Done");
    }

    @Test
    public void acceptSingleWinner(){
        Logger.logDebugMessage("TEST: acceptSingleWinner(): Start");
        JO jackParams = new JO();
        int contractFrequency = 9;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        //JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        //sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: acceptSingleWinner(): Prepare accounts");
        //JA collectionAssets = TarascaTester.getCollectionAssets();
        //collectionAssets.stream().forEach(a -> Logger.logDebugMessage("TEST: asset from collection: ",a.toString()));
        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player   (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());

        generateBlock();
        Logger.logDebugMessage("TEST: acceptSingleWinner(): Start playing");

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        //send not all assets to contract, expectation is jackpot will reject BOB!
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"to Contract ALICE");

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: acceptSingleWinner(): Evaluate results");
        JO responseEmpty = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobAfter = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        long diffBob = balanceBobAfter-balanceBobBefore;
        long diffAlice = balanceEmpty-balanceFull;

        // nothing shouldve happened
        Logger.logDebugMessage("TEST: acceptSingleWinner(): Asserting that ALICE's balance changed");
        //Assert.assertTrue( balanceEmpty==balanceFull);
        Assert.assertTrue( balanceEmpty<10); // 10 ignis tolerance for fees..
        Logger.logDebugMessage("TEST: acceptSingleWinner(): Asserting that Bobs balance change like Alices.. ");
        Assert.assertTrue( abs(diffBob + diffAlice + 300)<10); // 10 ignis tolerance for fees.., 300 Ignis fees with Ardor V2.3.3

        Logger.logDebugMessage("TEST: acceptSingleWinner(): Done");
    }

    @Test
    public void dividePriceByTwo(){
        Logger.logDebugMessage("TEST: dividePriceByTwo(): Start");
        JO jackParams = new JO();
        int contractFrequency = 9;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        //JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        //sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: dividePriceByTwo(): Prepare accounts");
        //JA collectionAssets = TarascaTester.getCollectionAssets();
        //collectionAssets.stream().forEach(a -> Logger.logDebugMessage("TEST: asset from collection: ",a.toString()));
        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),DAVE.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player1  (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());
        Logger.logDebugMessage("TEST: Player2  (Dave ): "+DAVE.getRsAccount()+", numeric: "+DAVE.getAccount());

        generateBlock();
        Logger.logDebugMessage("TEST: dividePriceByTwo(): Start playing");

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseDaveBefore = GetBalanceCall.create(IGNIS.getId()).account(DAVE.getRsAccount()).call();
        long balanceDaveBefore = Long.parseLong((String) responseDaveBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        //send not all assets to contract, expectation is jackpot will reject BOB!
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"from Bob to Contract ALICE");
        sendAssets(collectionAssets,1,DAVE.getSecretPhrase(),ALICE.getRsAccount(),"from Dave to Contract ALICE");

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: dividePriceByTwo(): Evaluate results");


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
        Assert.assertTrue( abs(diffBob + (diffAlice+600)/2)<10); // 10 ignis tolerance for fees..
        Assert.assertTrue( abs(diffDave+ (diffAlice+600)/2)<10);

        //Assert.assertTrue( abs(diffBob + diffAlice + 300)<10); // 10 ignis tolerance for fees.., 300 Ignis fees with Ardor V2.3.3
        Logger.logDebugMessage("TEST: dividePriceByTwo(): Done");
    }


    // this test checks that a message is sent for a winner, and only once. -> the jackpot block should be included in the message
    // assert that in the next cycle after a player has a complete participation, a message is sent
    // assert that no message is sent for incomplete participation
    // assert: correct recipient, message content
    @Test
    public void sendMessageForWinner(){
        Logger.logDebugMessage("TEST: sendMessageForWinner(): Start");
        JO jackParams = new JO();
        int contractFrequency = 9;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        Logger.logDebugMessage("TEST: sendMessageForWinner(): Prepare accounts");
        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),DAVE.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player   (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());
        Logger.logDebugMessage("TEST: Player   (Dave ): "+DAVE.getRsAccount()+", numeric: "+DAVE.getAccount());

        generateBlock();
        Logger.logDebugMessage("TEST: sendMessageForWinner(): Start playing");

        /*JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;*/

        JA notAllAssets = new JA();
        for (int i=0;i<collectionAssets.size()-1;i++){
            notAllAssets.add(collectionAssets.get(i));
        }

        //send not all assets to contract, expectation is jackpot will reject BOB!
        JO participationResponse = GetBlockCall.create().call();
        int participationHeight = participationResponse.getInt("height");

        sendAssets(notAllAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"Bob to Contract ALICE");
        sendAssets(collectionAssets,1,DAVE.getSecretPhrase(),ALICE.getRsAccount(),"Dave to Contract ALICE");

        //mock up:
        // Compose the message
        JO current = GetBlockCall.create().call();
        int currentHeight = current.getInt("height");
        JO message = new JO();
        message.put("contract", "Jackpot");
        message.put("currentHeight",currentHeight);
        message.put("jackpotHeight",contractFrequency);//simplified, only works for the first round.
        message.put("participationConfirmed", true);
        JO msgresponse = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                message(message.toJSONString()).
                messageIsPrunable(true).
                recipient(DAVE.getRsAccount()).
                feeNQT(IGNIS.ONE_COIN*(long)0.5).
                call();

        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: sendMessageForWinner(): Evaluate results");

        BlockResponse block = GetBlockCall.create().height(3).getBlock(); //TODO this is sketchy
        GetBlockchainTransactionsCall.
                create(IGNIS.getId()).
                timestamp(block.getTimestamp()).
                account(ALICE.getRsAccount()).
                executedOnly(true).
                type(2).
                subtype(1).
                call();

        // assert that in the next cycle after a player has a complete participation, a message is sent
        Logger.logDebugMessage("TEST: sendMessageForWinner(): Asserting that a complete participation results in a message to recipient");
        current = GetBlockCall.create().call();
        currentHeight = current.getInt("height");
        int lastJackpotHeight = currentHeight - (currentHeight % contractFrequency);
        JO messages = GetPrunableMessagesCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        JA PrunableMessages = messages.getArray("prunableMessages");

        //  Assert message is send after participation
        JO Msg = PrunableMessages.get(0);
        int messageHeight = Msg.getInt("currentHeight");
        Assert.assertTrue(participationHeight<messageHeight);
        // TODO Assert 1
        // TODO Assert 2
        // assert: correct recipient, message content
        Logger.logDebugMessage("TEST: sendMessageForWinner(): Asserting message content");
        // TODO Assert 4
        // assert that no message is sent for incomplete participation
        Logger.logDebugMessage("TEST: sendMessageForWinner(): Asserting that an incomplete participation results in no message to recipient");
        // TODO Assert 3

        Logger.logDebugMessage("TEST: sendMessageForWinner(): Done");
    }

    @Test
    public void dontSendForPreviousRound(){
        Assert.assertTrue(false);
    }

    @Test
    public void multipleParticipationSplitPot(){
        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Start");
        JO jackParams = new JO();
        int contractFrequency = 9;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);

        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        generateBlock();

        //JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        //sendAssets(collectionAssets,3,CHUCK.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Prepare accounts");
        //JA collectionAssets = TarascaTester.getCollectionAssets();
        //collectionAssets.stream().forEach(a -> Logger.logDebugMessage("TEST: asset from collection: ",a.toString()));
        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),DAVE.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player1  (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());
        Logger.logDebugMessage("TEST: Player2  (Dave ): "+DAVE.getRsAccount()+", numeric: "+DAVE.getAccount());

        generateBlock();
        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Start playing");

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseDaveBefore = GetBalanceCall.create(IGNIS.getId()).account(DAVE.getRsAccount()).call();
        long balanceDaveBefore = Long.parseLong((String) responseDaveBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        //send not all assets to contract, expectation is jackpot will reject BOB!
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"from Bob to Contract ALICE");
        sendAssets(collectionAssets,1,DAVE.getSecretPhrase(),ALICE.getRsAccount(),"from Dave to Contract ALICE");

        generateBlock();
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"from Bob to Contract ALICE");
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Evaluate results");


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
        Assert.assertTrue( abs(diffBob + (diffAlice+600)*2/3)<10); // 10 ignis tolerance for fees..
        Assert.assertTrue( abs(diffDave+ (diffAlice+600)*1/3)<10);

        //Assert.assertTrue( abs(diffBob + diffAlice + 300)<10); // 10 ignis tolerance for fees.., 300 Ignis fees with Ardor V2.3.3
        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Done");
    }
}