package com.jelurida.ardor.contracts;


import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JO;
import nxt.http.callers.*;

import static nxt.BlockchainTest.*;
import static nxt.blockchain.ChildChain.IGNIS;

public class TarascaPlayers {

    public static Tester PALHEIRO;
    public static Tester SHUGO;
    public static Tester MALNEMARK;
    public static Tester WIRE;
    public static Tester SAZAN;
    public static Tester TRE;
    public static Tester SWIFT;

    public TarascaPlayers(){
        initMorePlayers();
    }

    public static void initMorePlayers(){
        PALHEIRO = new Tester("three options difficult handle");
        SHUGO = new Tester("need cards different format english quality");
        MALNEMARK = new Tester("unit tests importance always");
        WIRE = new Tester("voting fungible token magic");
        SAZAN = new Tester("complain twitter many years");
        TRE = new Tester("random provable deterministic important trivial manipulated party");
        SWIFT = new Tester("technology believe following structure interface automatic");

        JO respSendToPalheiro = SendMoneyCall.create(2).amountNQT(10000*IGNIS.ONE_COIN).recipient(PALHEIRO.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        JO respSendToShugo = SendMoneyCall.create(2).amountNQT(10000*IGNIS.ONE_COIN).recipient(SHUGO.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        JO respSendToMalnemark = SendMoneyCall.create(2).amountNQT(10000*IGNIS.ONE_COIN).recipient(MALNEMARK.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        JO respSendToSazan = SendMoneyCall.create(2).amountNQT(1000*IGNIS.ONE_COIN).recipient(SAZAN.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        JO respSendToWire = SendMoneyCall.create(2).amountNQT(1000*IGNIS.ONE_COIN).recipient(WIRE.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        JO respSendToTre = SendMoneyCall.create(2).amountNQT(1000*IGNIS.ONE_COIN).recipient(TRE.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        JO respSendToSwift = SendMoneyCall.create(2).amountNQT(1000*IGNIS.ONE_COIN).recipient(SWIFT.getRsAccount()).feeNQT(3*IGNIS.ONE_COIN).secretPhrase(BOB.getSecretPhrase()).call();
        BlockchainTest.generateBlock();
    }
}