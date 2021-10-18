package org.tarasca.contracts;

import com.jelurida.ardor.contracts.DistributedRandomNumberGenerator;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.callers.*;
import com.jelurida.ardor.contracts.AbstractContractTest;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.tarasca.contracts.TarascaTester.*;
import static java.lang.Math.abs;
import static nxt.blockchain.ChildChain.IGNIS;
import com.jelurida.ardor.contracts.ContractTestHelper;


public class JackpotTest extends AbstractContractTest {

    @Test
    public void JackpotApi(){
        Logger.logDebugMessage("TEST: JackpotApi(): Start");
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
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

        Logger.logDebugMessage("TEST: JackpotApi(): Prepare accounts");

        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player   (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());

        generateBlock();
        JO contractResponse = TriggerContractByRequestCall.create().contractName("Jackpot").call();
        Logger.logInfoMessage(contractResponse.toString());
        Assert.assertTrue( contractResponse.getJo("winners").size() == 0);

        Logger.logDebugMessage("TEST: JackpotApi(): Start playing");

        JO responseFull = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceFull = Long.parseLong((String) responseFull.get("balanceNQT"))/IGNIS.ONE_COIN;
        long expectedJackpot = balanceFull/2;

        JO responseBobBefore = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobBefore = Long.parseLong((String) responseBobBefore.get("balanceNQT"))/IGNIS.ONE_COIN;

        //send not all assets to contract, expectation is jackpot will reject BOB!
        sendAssets(collectionAssets,1,BOB.getSecretPhrase(),ALICE.getRsAccount(),"to Contract ALICE");

        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: JackpotApi(): Evaluate results");
        contractResponse = TriggerContractByRequestCall.create().contractName("Jackpot").call();
        Logger.logInfoMessage(contractResponse.toString());
        Assert.assertTrue( contractResponse.getJo("winners").size() == 1);
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
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

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
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

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

        // two winners, both won once. maximum 1 card per winner (one participation)
        checkTarascaCardMessage(1,1,1);

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
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

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

        // two winners, both won once. maximum 1 card per winner (one participation)
        checkTarascaCardMessage(2,2,1);

        //Assert.assertTrue( abs(diffBob + diffAlice + 300)<10); // 10 ignis tolerance for fees.., 300 Ignis fees with Ardor V2.3.3
        Logger.logDebugMessage("TEST: dividePriceByTwo(): Done");
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
        jackParams.put("maxnumtarascas",3);
        initCollection(collectionSize);

        generateBlock();
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

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

        checkTarascaCardMessage(2,3,2);
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
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

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


    @Test
    public void testTarascaCardDistribution(){
        Logger.logDebugMessage("TEST: testTarascaCardDistribution(): Start");
        int contractFrequency = 9;
        int confirmationTime = 1;
        int collectionSize = 3;
        JO jackParams = new JO();
        jackParams.put("frequency",contractFrequency);
        jackParams.put("collectionRs",ALICE.getRsAccount());
        jackParams.put("confirmationTime",confirmationTime);
        jackParams.put("maxnumtarascas",3);
        JO assetJo = initSpecialCardAsset(CHUCK);
        TransferAssetCall.create(2).secretPhrase(CHUCK.getSecretPhrase()).quantityQNT((long)500).asset(assetJo.getString("asset")).recipient(ALICE.getRsAccount()).feeNQT(IGNIS.ONE_COIN).call();
        initCollection(collectionSize);


        JO distributorParams = new JO();
        distributorParams.put("asset",assetJo.getString("asset"));
        distributorParams.put("validSender",ALICE.getRsAccount());


        //generateBlock();
        String distributor = ContractTestHelper.deployContract(AssetDistributor.class,distributorParams,false);
        String jackName = ContractTestHelper.deployContract(Jackpot.class,jackParams,false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);

        Logger.logDebugMessage("TEST: testTarascaCardDistribution(): Prepare accounts");

        JO response = GetAssetsByIssuerCall.create().account(ALICE.getRsAccount()).call();
        JA collectionAssets = response.getArray("assets").getArray(0);
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),BOB.getRsAccount(),"to Bob");
        sendAssets(collectionAssets,3,ALICE.getSecretPhrase(),DAVE.getRsAccount(),"to Bob");

        Logger.logDebugMessage("TEST: Accounts");
        Logger.logDebugMessage("TEST: Contract (Alice): "+ALICE.getRsAccount()+", numeric: "+ALICE.getAccount());
        Logger.logDebugMessage("TEST: Player1  (Bob  ): "+BOB.getRsAccount()+", numeric: "+BOB.getAccount());
        Logger.logDebugMessage("TEST: Player2  (Dave ): "+DAVE.getRsAccount()+", numeric: "+DAVE.getAccount());
        Logger.logDebugMessage("TEST: Asset " + assetJo.getString("asset"));

        generateBlock();
        Logger.logDebugMessage("TEST: testTarascaCardDistribution(): Start playing");

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
        generateBlock();
        Logger.logDebugMessage("TEST: testTarascaCardDistribution(): Evaluate results");


        /*JO responseEmpty = GetBalanceCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();
        long balanceEmpty = Long.parseLong((String) responseEmpty.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseBobAfter = GetBalanceCall.create(IGNIS.getId()).account(BOB.getRsAccount()).call();
        long balanceBobAfter = Long.parseLong((String) responseBobAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

        JO responseDaveAfter = GetBalanceCall.create(IGNIS.getId()).account(DAVE.getRsAccount()).call();
        long balanceDaveAfter = Long.parseLong((String) responseDaveAfter.get("balanceNQT"))/IGNIS.ONE_COIN;

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
        Assert.assertTrue(abs(balanceBobAfter - expectedBalanceBob)<10);
        // Assert dave won one third
        Assert.assertTrue(abs(balanceDaveAfter - expectedBalanceDave)<10);*/


        checkTarascaCardMessage(2,3,2);

        response = GetAccountAssetsCall.create().account(BOB.getRsAccount()).asset(assetJo.getString("asset")).call();
        Assert.assertTrue("assetId matches", response.getString("asset").equals(assetJo.getString("asset")));
        Assert.assertEquals("quantity as expected",response.getLong("quantityQNT"),(long)2);

        // Assert Dave has one card
        response = GetAccountAssetsCall.create().account(DAVE.getRsAccount()).asset(assetJo.getString("asset")).call();
        Assert.assertTrue("assetId matches", response.getString("asset").equals(assetJo.getString("asset")));
        Assert.assertEquals("quantity as expected",response.getLong("quantityQNT"),(long)1);


        Logger.logDebugMessage("TEST: testTarascaCardDistribution(): Done");
    }


    public static long getBalance(String accountRs) {
        JO response = GetBalanceCall.create(IGNIS.getId()).account(accountRs).call();
        return Long.parseLong((String) response.get("balanceNQT"))/IGNIS.ONE_COIN;
    }

    /*
    *  function scans all messages, filters those send together with the price and analysis the tarasca card winner attribute "numTarascasWon".
    *  It checks the amount of cards won, how many winners won at least one, and maximum number of cards of a single account.
    * */
    public static void checkTarascaCardMessage(int numWinners, int numCardsTotal, int numCardsPerWinnerMax){
        JO messages = GetPrunableMessagesCall.create(IGNIS.getId()).account(ALICE.getRsAccount()).call();

        List<JO> sendPrizeMessages = messages.getArray("prunableMessages").objects().stream().filter(msg -> {
                JO messageBody = JO.parse(msg.getString("message"));
                return (msg.getString("senderRS").equals(ALICE.getRsAccount()) &&
                        messageBody.getString("reason") != null &&
                        messageBody.getString("reason").equals("sendPrize"));
        }
        ).collect(Collectors.toList());

        // check amount of all cards won
        AtomicInteger numCardsTotalObserved = new AtomicInteger();
        sendPrizeMessages.forEach((msg) -> {
            JO messageBody = JO.parse(msg.getString("message"));
            Assert.assertTrue(messageBody.getString("reason").equals("sendPrize"));

            JO params = messageBody.getJo("params");
            int currentValue = params.getInt("numTarascasWon");

            numCardsTotalObserved.addAndGet(currentValue);
        });

        // check number of winners
        AtomicInteger numWinnersObserved = new AtomicInteger();
        numWinnersObserved.set(sendPrizeMessages.size());

        // check numCardsPerWinnerMax
        AtomicInteger numCardsPerWinnerMaxObserved = new AtomicInteger();
        sendPrizeMessages.forEach((msg) -> {
            JO messageBody = JO.parse(msg.getString("message"));
            Assert.assertTrue(messageBody.getString("reason").equals("sendPrize"));

            JO params = messageBody.getJo("params");
            int currentValue = params.getInt("numTarascasWon");
            if (currentValue > numCardsPerWinnerMaxObserved.get()) {
                numCardsPerWinnerMaxObserved.set(currentValue);
            }
        });

        Assert.assertTrue(numWinners == numWinnersObserved.get());
        Assert.assertTrue(numCardsTotal == numCardsTotalObserved.get());
        Assert.assertTrue(numCardsPerWinnerMax == numCardsPerWinnerMaxObserved.get());
    }
}