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

import java.util.List;
import java.util.stream.Collectors;

import static com.jelurida.ardor.contracts.TarascaTester.initCollection;
import static com.jelurida.ardor.contracts.TarascaTester.sendAssets;
import com.jelurida.ardor.contracts.TarascaPlayers;
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

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

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

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        Logger.logDebugMessage("TEST: rejectWinnerIncompleteTx(): Prepare accounts");

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
        Assert.assertTrue( balanceBobAfter==balanceBobBefore- (notAllAssets.size())); //this assumes 1 Ignis fee!

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
        jackParams.put("jackpotIsHalfBalance",true);
        initCollection(collectionSize);

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        Logger.logDebugMessage("TEST: acceptSingleWinner(): Prepare accounts");

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
        long expectedJackpot = balanceFull/2;

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
        Assert.assertTrue( (balanceFull - expectedJackpot - balanceEmpty)<10); // 10 ignis tolerance for fees..
        Assert.assertTrue((balanceEmpty - balanceFull/2) < 10);
        Logger.logDebugMessage("TEST: acceptSingleWinner(): Asserting that Bobs balance change like Alices.. ");
        Assert.assertTrue( abs(diffBob + diffAlice )<10); // 10 ignis tolerance for fees.., 300 Ignis fees with Ardor V2.3.3

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

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        Logger.logDebugMessage("TEST: dividePriceByTwo(): Prepare accounts");

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
        Assert.assertTrue( abs(diffBob + (diffAlice)/2)<10); // 10 ignis tolerance for fees..
        Assert.assertTrue( abs(diffDave+ (diffAlice)/2)<10);

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
        int contractFrequency = 12;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

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

        // Contract sends a few money calls with messages attached, provoking the branch wins < number of messages of the contract (line 99)
        JO messageJson = new JO();
        messageJson.put("contact", "This message shall be ignored");
        SendMoneyCall.create(IGNIS.getId()).secretPhrase(ALICE.getSecretPhrase()).
                recipient(DAVE.getRsAccount()).
                amountNQT(IGNIS.ONE_COIN).
                messageIsPrunable(true).
                message(messageJson.toJSONString()).
                feeNQT(IGNIS.ONE_COIN).
                call();
        SendMoneyCall.create(IGNIS.getId()).secretPhrase(ALICE.getSecretPhrase()).
                recipient(DAVE.getRsAccount()).
                amountNQT(IGNIS.ONE_COIN).
                messageIsPrunable(true).
                message(messageJson.toJSONString()).
                feeNQT(IGNIS.ONE_COIN).
                call();
        SendMoneyCall.create(IGNIS.getId()).secretPhrase(ALICE.getSecretPhrase()).
                recipient(DAVE.getRsAccount()).
                amountNQT(IGNIS.ONE_COIN).
                messageIsPrunable(true).
                message(messageJson.toJSONString()).
                feeNQT(IGNIS.ONE_COIN).
                call();
        generateBlock();

        JA notAllAssets = new JA();
        for (int i=0;i<collectionAssets.size()-1;i++){
            notAllAssets.add(collectionAssets.get(i));
        }

        //send not all assets to contract, expectation is jackpot will reject BOB!
        JO participationResponse = GetBlockCall.create().call();
        long participationBlockTimestamp = participationResponse.getInt("timestamp");

        sendAssets(notAllAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"Bob to Contract ALICE");
        sendAssets(collectionAssets,2,DAVE.getSecretPhrase(),ALICE.getRsAccount(),"Dave to Contract ALICE");


        //expectedMessage.put("participationConfirmed", true);

        generateBlock(); // height = 8
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock(); // height 12
        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: sendMessageForWinner(): Evaluate results");


        // assert: correct recipient, message content
        Logger.logDebugMessage("TEST: sendMessageForWinner(): Asserting message content");
        JO messagesToDave = GetPrunableMessagesCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).otherAccount(DAVE.getRsAccount()).call();
        List<JO> jackpotMessagesToDave = messagesToDave.getArray("prunableMessages").objects().stream().filter(
                (msg) -> {
                    JO messageBody = JO.parse(msg.getString("message"));
                    String senderRS = msg.getString("senderRS");
                    String submittedBy = messageBody.getString("submittedBy");
                    String reason = messageBody.getString("reason");
                    if ( submittedBy != null && submittedBy.equals(jackName) && senderRS != null && senderRS.equals(ALICE.getRsAccount()) && reason != null && reason.equals("confirmParticipation"))
                        return true;
                    else
                        return false;
                }).collect(Collectors.toList());
        Assert.assertTrue(jackpotMessagesToDave.size()==2);


        Logger.logDebugMessage("TEST: sendMessageForWinner(): Asserting that an incomplete participation results in no message to recipient");
        JO messagesToBob = GetPrunableMessagesCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).otherAccount(BOB.getRsAccount()).call();
        List<JO> jackpotMessagesToBob = messagesToBob.getArray("prunableMessages").objects().stream().filter(msg ->
                msg.getString("senderRs").equals(ALICE.getRsAccount()) &
                        msg.getString("submittedBy").equals(jackName) &
                        msg.getBoolean("participationConfirmed")
        ).collect(Collectors.toList());
        Assert.assertTrue(jackpotMessagesToBob.size()==0);

        Logger.logDebugMessage("TEST: sendMessageForWinner(): Done");
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

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Prepare accounts");

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

        //long diffBob = balanceBobAfter-balanceBobBefore;
        //long diffDave = balanceDaveAfter-balanceDaveBefore;
        long diffAlice = balanceEmpty-balanceFull;

        // TODO: add halfing of the jackpot
        long jackTotal = abs(diffAlice);
        long twoWins = jackTotal*2/3;
        long oneWin = jackTotal*1/3;

        long expectedBalanceBob = balanceBobBefore + twoWins  -6; // 3 ignis for the fees (2 participation)
        long expectedBalanceDave = balanceDaveBefore + oneWin -3; // 3 ignis for the fees

        // Make sure Jackpot account is nearly empty

        Assert.assertTrue( balanceEmpty<10); // 10 ignis tolerance for fees..

        // Assert bob won the two thirds
        //Assert.assertTrue( abs(diffBob -300 + (diffAlice+600)*2/3)<10); // 10 ignis tolerance for fees..
        Assert.assertTrue(abs(balanceBobAfter - expectedBalanceBob)<10);
        // Assert dave won one third
        //Assert.assertTrue( abs(diffDave-300 + (diffAlice+600)*1/3)<10);
        Assert.assertTrue(abs(balanceDaveAfter - expectedBalanceDave)<10);

        //Assert.assertTrue( abs(diffBob + diffAlice + 300)<10); // 10 ignis tolerance for fees.., 300 Ignis fees with Ardor V2.3.3
        Logger.logDebugMessage("TEST: multipleParticipationSplitPot(): Done");
    }

    @Test
    public void morePlayers(){
        Logger.logDebugMessage("TEST: morePlayers(): Start");
        JO jackParams = new JO();
        int contractFrequency = 16;
        int confirmationTime = 1;
        int collectionSize = 3;
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        initCollection(collectionSize);
        generateBlock();
        TarascaPlayers.initMorePlayers();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);

        Logger.logDebugMessage("TEST: morePlayers(): Prepare accounts");

        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),DAVE.getRsAccount(),"to Dave");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),CHUCK.getRsAccount(),"to Chuck");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),RIKER.getRsAccount(),"to riker");

        Logger.logDebugMessage("TEST: morePlayers(): Sending to the new players");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.PALHEIRO.getRsAccount(),"to palheiro");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.SHUGO.getRsAccount(),"to shugo");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.MALNEMARK.getRsAccount(),"to malnemark");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.WIRE.getRsAccount(),"to wire");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.SAZAN.getRsAccount(),"to sazan");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.TRE.getRsAccount(),"to tre");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),TarascaPlayers.SWIFT.getRsAccount(),"to swift");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player1  (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());
        Logger.logDebugMessage("TEST: Player2  (Dave ): "+DAVE.getRsAccount()+", numeric: "+DAVE.getAccount());

        generateBlock();
        Logger.logDebugMessage("TEST: morePlayers(): Start playing");


        long balanceFull = getBalance(ALICE.getRsAccount());
        long balanceBobBefore = getBalance(BOB.getRsAccount());
        long balanceDaveBefore = getBalance(DAVE.getRsAccount());
        long balanceChuckBefore = getBalance(CHUCK.getRsAccount());
        long balanceRikerBefore = getBalance(RIKER.getRsAccount());
        long balancePalheiroBefore = getBalance(TarascaPlayers.PALHEIRO.getRsAccount());
        long balanceShugoBefore = getBalance(TarascaPlayers.SHUGO.getRsAccount());
        long balanceMalnemarkBefore = getBalance(TarascaPlayers.MALNEMARK.getRsAccount());
        long balanceWireBefore = getBalance(TarascaPlayers.WIRE.getRsAccount());
        long balanceSazanBefore = getBalance(TarascaPlayers.SAZAN.getRsAccount());
        long balanceTreBefore = getBalance(TarascaPlayers.TRE.getRsAccount());
        long balanceSwiftBefore = getBalance(TarascaPlayers.SWIFT.getRsAccount());


        //send all assets to contract
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"from Bob to Contract ALICE");
        sendAssets(collectionAssets,1,DAVE.getSecretPhrase(),ALICE.getRsAccount(),"from Dave to Contract ALICE");
        sendAssets(collectionAssets,1,CHUCK.getSecretPhrase(),ALICE.getRsAccount(),"from Bob to Contract ALICE");
        sendAssets(collectionAssets,1,RIKER.getSecretPhrase(),ALICE.getRsAccount(),"from Dave to Contract ALICE");
        generateBlock();
        Logger.logDebugMessage("TEST: morePlayers(): New players sending to contract");
        sendAssets(collectionAssets,1,TarascaPlayers.MALNEMARK.getSecretPhrase(),ALICE.getRsAccount(),"from malnemark to Contract ALICE");
        sendAssets(collectionAssets,1,TarascaPlayers.PALHEIRO.getSecretPhrase(),ALICE.getRsAccount(),"from palheiro to Contract ALICE");
        sendAssets(collectionAssets,1,TarascaPlayers.SHUGO.getSecretPhrase(),ALICE.getRsAccount(),"from shugo to Contract ALICE");
        generateBlock();
        sendAssets(collectionAssets,1,TarascaPlayers.SAZAN.getSecretPhrase(),ALICE.getRsAccount(),"from sazan to Contract ALICE");
        sendAssets(collectionAssets,1,TarascaPlayers.WIRE.getSecretPhrase(),ALICE.getRsAccount(),"from wire to Contract ALICE");
        sendAssets(collectionAssets,1,TarascaPlayers.TRE.getSecretPhrase(),ALICE.getRsAccount(),"from tre to Contract ALICE");
        sendAssets(collectionAssets,1,TarascaPlayers.SWIFT.getSecretPhrase(),ALICE.getRsAccount(),"from swift to Contract ALICE");
        Logger.logDebugMessage("TEST: morePlayers(): New players sending to contract complete");
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();// somehwere here is block 16
        generateBlock();
        Logger.logDebugMessage("TEST: morePlayers(): Evaluate results");

        long balanceEmpty = getBalance(ALICE.getRsAccount());
        long balanceBobAfter = getBalance(BOB.getRsAccount());
        long balanceDaveAfter = getBalance(DAVE.getRsAccount());
        long balanceChuckAfter = getBalance(CHUCK.getRsAccount());
        long balanceRikerAfter = getBalance(RIKER.getRsAccount());
        long balancePalheiroAfter = getBalance(TarascaPlayers.PALHEIRO.getRsAccount());
        long balanceShugoAfter = getBalance(TarascaPlayers.SHUGO.getRsAccount());
        long balanceMalnemarkAfter = getBalance(TarascaPlayers.MALNEMARK.getRsAccount());
        long balanceWireAfter = getBalance(TarascaPlayers.WIRE.getRsAccount());
        long balanceSazanAfter = getBalance(TarascaPlayers.SAZAN.getRsAccount());
        long balanceTreAfter = getBalance(TarascaPlayers.TRE.getRsAccount());
        long balanceSwiftAfter = getBalance(TarascaPlayers.SWIFT.getRsAccount());


        long diffAlice = balanceEmpty-balanceFull;

        long jackTotal = abs(diffAlice);
        long oneWin = jackTotal*1/11;

        // Make sure Jackpot account is nearly empty
        Assert.assertTrue( balanceEmpty<15); // 10 ignis tolerance for fees..

        Assert.assertTrue(abs(balanceBobAfter - balanceBobBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceChuckAfter - balanceChuckBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceDaveAfter - balanceDaveBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceRikerAfter - balanceRikerBefore - oneWin) < 10);

        Assert.assertTrue(abs(balanceMalnemarkAfter - balanceMalnemarkBefore - oneWin) < 10);
        Assert.assertTrue(abs(balancePalheiroAfter - balancePalheiroBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceShugoAfter - balanceShugoBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceSazanAfter - balanceSazanBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceWireAfter - balanceWireBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceTreAfter - balanceTreBefore - oneWin) < 10);
        Assert.assertTrue(abs(balanceSwiftAfter - balanceSwiftBefore - oneWin) < 10);

        Logger.logDebugMessage("TEST: morePlayers(): Done");
    }

    public static long getBalance(String accountRs) {
        JO response = GetBalanceCall.create(IGNIS.getId()).account(accountRs).call();
        return Long.parseLong((String) response.get("balanceNQT"))/IGNIS.ONE_COIN;
    }
}