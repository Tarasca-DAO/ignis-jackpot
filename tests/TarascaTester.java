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

import static nxt.BlockchainTest.ALICE;
import static nxt.BlockchainTest.generateBlock;
import static nxt.blockchain.ChildChain.IGNIS;

public class TarascaTester {

    public static long priceIgnis() {return 99;}
    public static long priceGiftz() {return 1;}
    public static int cardsPerPack() {return 3;}
    public static int collectionSize() {return 8;}

    public static JO getCollectionCurrency(){
        JO response = GetCurrenciesByIssuerCall.create().param("account",ALICE.getRsAccount()).call();
        JA schachtel = response.getArray("currencies"); // Need to unbox another array
        JA currency = schachtel.getArray(0);
        return new JO(currency.getObject(0));
    }

    public static JA getCollectionAssets(){
        JO response = GetAssetsByIssuerCall.create().param("account",ALICE.getRsAccount()).call();
        JA outerArray = response.getArray("assets");
        JA assets = outerArray.getArray(0);
        return assets;
    }


    public static void initCollectionCurrency() {
        Logger.logMessage("create Currency.");
        JO response = IssueCurrencyCall.create(2).
                secretPhrase(ALICE.getSecretPhrase()).
                code("TOLLA").
                name("Tarascolla").
                description("exchangeable,useful").
                initialSupplyQNT(100000).
                maxSupplyQNT(100000).
                type(1).decimals(2).
                chain(IGNIS.getId()).
                feeNQT(IGNIS.ONE_COIN*12).
                call();
        generateBlock();
    }

    public static void initCollection(int numAssets){
        Logger.logMessage("TarascaTester.initCollection(): from account: "+ ALICE.getRsAccount());
        for (int i=0; i<numAssets; i++){
            int q = 10000;
            String rarity = "common";
            if (i==0 | i==1){
                q = 2500;
                rarity = "rare";
            }
            Logger.logMessage("TarascaTester.initCollection(): create Asset: Asset"+i);
            String name = String.format("Asset%s", i);
            JO description = new JO();
            description.put("description",String.format("this is Asset %s description",i));
            description.put("rarity",rarity);
            JO response = IssueAssetCall.create(IGNIS.getId()).
                    secretPhrase(ALICE.getSecretPhrase()).
                    description(description.toJSONString()).
                    name(name).
                    quantityQNT(q).
                    decimals(0).
                    feeNQT(IGNIS.ONE_COIN*100).
                    call();
            generateBlock();
            //int j = i; // this is useless
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
            //Logger.logMessage(msg);
            JO response = TransferAssetCall.create(IGNIS.getId()).
                    asset(assetId).
                    recipient(receiverRs).
                    quantityQNT(quantityQNT).
                    secretPhrase(secretPhrase).
                    message(msg).
                    feeNQT(IGNIS.ONE_COIN*100).
                    call();
        }
    }

    public static JO payContractIgnis(long amount, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(secretPhrase).
                recipient(ALICE.getRsAccount()).
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
                recipient(ALICE.getRsAccount()).
                amountNQT(numPacks*TarascaTester.priceIgnis()*IGNIS.ONE_COIN).
                messageIsPrunable(true).
                message(message).feeNQT(IGNIS.ONE_COIN).call();
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO buyPacksGiftz(int numPacks, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();

        JO coin = TarascaTester.getCollectionCurrency();
        JO response = TransferCurrencyCall.create(IGNIS.getId()).
                currency(coin.getString("currency")).
                recipient(ALICE.getRsAccount()).
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
        //BlockchainTest.generateBlock();
        return response;
    }
}