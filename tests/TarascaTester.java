package com.jelurida.ardor.contracts;

import nxt.BlockchainTest;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.TransferAsset;
import nxt.http.callers.*;
import nxt.util.Logger;
import org.json.simple.JSONObject;

import java.util.ListIterator;
import java.lang.Object;

import static nxt.BlockchainTest.generateBlock;
import static nxt.blockchain.ChildChain.IGNIS;

public class TarascaTester {

    public static long priceIgnis() {return 26*IGNIS.ONE_COIN;}
    public static long priceGiftz() {return 1;}
    public static int cardsPerPack() {return 3;}
    public static int collectionSize() {return 8;}

    public static JO getCollectionCurrency(String account){
        JO response = GetCurrenciesByIssuerCall.create().param("account",account).call();
        JA schachtel = new JA(response.get("currencies")); // Need to unbox another array
        JA currency = new JA(schachtel.getObject(0));
        return new JO(currency.getObject(0));
    }

    public static JA getCollectionAssets(String account){
        JO response = GetAssetsByIssuerCall.create().param("account",account).call();
        JA outerArray = response.getArray("assets");
        return outerArray.getArray(0);
    }

    public static void initCurrency(String secretPhrase, String code, String name, String description, int supply) {
        Logger.logMessage("create Currency.");
        JO response = IssueCurrencyCall.create(2).
                secretPhrase(secretPhrase).
                code(code).
                name(name).
                description(description).
                initialSupplyQNT(supply).
                maxSupplyQNT(supply).
                type(1).decimals(2).
                chain(IGNIS.getId()).
                call();
        generateBlock();
    }

    public static void initCollection(String secretPhrase, int numAssets){
        for (int i=0; i<numAssets; i++){
            int q = 10000;
            if (i==0 | i==1){
                q = 2500;
            }
            Logger.logMessage("create Asset: "+i);
            String name = String.format("Asset%s", i);
            String description = String.format("Asset Description %s", i);
            IssueAssetCall.create(IGNIS.getId()).
                    secretPhrase(secretPhrase).
                    description(description).
                    name(name).
                    quantityQNT(q).
                    decimals(0).
                    call();
            generateBlock();
        }
    }

    public static void sendCoin(String currencyId, int amount, String recipient, String secretPhrase){
        JO response = TransferCurrencyCall.create(IGNIS.getId()).
                currency(currencyId).
                recipient(recipient).
                unitsQNT(amount).
                secretPhrase(secretPhrase).
                feeNQT(IGNIS.ONE_COIN).
                call();
    }

    public static void sendAssets(JA assets, int quantityQNT, String secretPhrase, String receiverRs, String message) {
        ListIterator itr = assets.listIterator();
        while (itr.hasNext()) {
            JSONObject assetObj = (JSONObject) itr.next();
            JO asset = new JO(assetObj);
            //JO asset = itr.next();
            //JO asset = new JO(assetObj);
            String assetId = (String) asset.getString("asset");
            String msg = "send Asset: " + assetId;
            Logger.logMessage(msg);
            JO response = TransferAssetCall.create(IGNIS.getId()).
                    asset(assetId).
                    recipient(receiverRs).
                    quantityQNT(quantityQNT).
                    secretPhrase(secretPhrase).
                    message(msg).
                    feeNQT(IGNIS.ONE_COIN).
                    call();
        }
    }

    public static JO payContractIgnis(long amount, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(secretPhrase).
                recipient(BlockchainTest.ALICE.getRsAccount()).
                amountNQT(amount).
                messageIsPrunable(true).
                message(message).
                feeNQT(IGNIS.ONE_COIN).
                call();
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO buyPacksIgnis(int numPacks, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        JO response = SendMoneyCall.create(IGNIS.getId()).secretPhrase(secretPhrase).
                recipient(BlockchainTest.ALICE.getRsAccount()).
                amountNQT(numPacks*TarascaTester.priceIgnis()).
                messageIsPrunable(true).
                message(message).feeNQT(IGNIS.ONE_COIN).call();
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO buyPacksGiftz(int numPacks, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();

        JO coin = TarascaTester.getCollectionCurrency(BlockchainTest.CHUCK.getRsAccount());
        JO response = TransferCurrencyCall.create(IGNIS.getId()).
                currency(coin.getString("currency")).
                recipient(BlockchainTest.ALICE.getRsAccount()).
                unitsQNT(numPacks).
                secretPhrase(secretPhrase).
                messageIsPrunable(true).
                message(message).
                feeNQT(IGNIS.ONE_COIN).
                call();
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO getAccountAssets(String account){
        JO response = GetAccountAssetsCall.create().account(account).call();
        BlockchainTest.generateBlock();
        return response;
    }
}