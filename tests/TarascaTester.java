package com.jelurida.ardor.contracts;

import nxt.BlockchainTest;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.util.Logger;
import org.json.simple.JSONObject;

import java.util.ListIterator;

import static nxt.BlockchainTest.generateBlock;
import static nxt.blockchain.ChildChain.IGNIS;

public class TarascaTester {

    public static long priceIgnis() {return 26*IGNIS.ONE_COIN;}
    public static long priceGiftz() {return 1;}
    public static int cardsPerPack() {return 3;}
    public static int collectionSize() {return 8;}

    public static JO getCollectionCurrency(String account){
        APICall getCoin = new APICall.Builder("getCurrenciesByIssuer").
                param("account", account).
                build();
        JO response = new JO(getCoin.invoke());
        JA schachtel = new JA(response.get("currencies")); // Need to unbox another array
        JA currency = new JA(schachtel.getObject(0));
        return new JO(currency.getObject(0));
    }

    public static JA getCollectionAssets(String account){
        APICall getCollectionAssets = new APICall.Builder("getAssetsByIssuer").
                param("account", account).
                build();
        JO response = new JO(getCollectionAssets.invoke());
        JA schachtel = new JA(response.get("assets")); // Need to unbox another array
        return new JA(schachtel.getObject(0));
    }

    public static void initCurrency(String secretPhrase, String code, String name, String description, int supply) {
        Logger.logMessage("create Currency.");
        APICall createCoin = new APICall.Builder("issueCurrency").
                secretPhrase(secretPhrase).
                param("chain", IGNIS.getId()).
                param("name",name).
                param("code",code).
                param("description",description).
                param("type",1).
                param("decimals",2).
                param("initialSupplyQNT",supply).
                param("maxSupplyQNT",supply).
                feeNQT(4*IGNIS.ONE_COIN).
                build();
        createCoin.invoke();
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
            APICall createAsset = new APICall.Builder("issueAsset").
                    secretPhrase(secretPhrase).
                    param("chain", IGNIS.getId()).
                    param("name",name).
                    param("description",description).
                    param("quantityQNT",q).
                    param("decimals",0).
                    feeNQT(11*IGNIS.ONE_COIN).
                    build();
            createAsset.invoke();
            generateBlock();
        }
    }

    public static void sendCoin(String currencyId, int amount, String recipient, String secretPhrase){
        APICall sender = new APICall.Builder("transferCurrency").
                param("chain", 2).
                param("currency", currencyId).
                param("recipient", recipient).
                param("unitsQNT", amount).
                param("secretPhrase", secretPhrase).
                feeNQT(1 * IGNIS.ONE_COIN).
                build();
        JO response = new JO(sender.invoke());
    }

    public static void sendAssets(JA assets, int quantityQNT, String secretPhrase, String receiverRs, String message) {
        ListIterator itr = assets.listIterator();
        while (itr.hasNext()) {
            JSONObject assetObj = (JSONObject) itr.next();
            JO asset = new JO(assetObj);
            String assetId = (String) asset.getString("asset");
            String msg = "send Asset: " + assetId;
            Logger.logMessage(msg);
            APICall sender = new APICall.Builder("transferAsset").
                    param("chain", 2).
                    param("asset", assetId).
                    param("recipient", receiverRs).
                    param("quantityQNT", quantityQNT).
                    param("secretPhrase", secretPhrase).
                    param("message", message).
                    feeNQT(1 * IGNIS.ONE_COIN).
                    build();
            JO response = new JO(sender.invoke());
        }
    }

    public static JO payContractIgnis(long amount, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        APICall.Builder builder = new APICall.Builder("sendMoney").
                secretPhrase(secretPhrase).
                param("chain", 2).
                param("recipient", BlockchainTest.ALICE.getRsAccount()).
                param("amountNQT", amount).
                param("messageIsPrunable", "true").
                param("message",message).
                feeNQT(IGNIS.ONE_COIN);
        APICall apiCall = builder.build();
        JO response = new JO(apiCall.invoke());
        //Logger.logDebugMessage("sendMoney: " + response);
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO buyPacksIgnis(int numPacks, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        APICall.Builder builder = new APICall.Builder("sendMoney").
                secretPhrase(secretPhrase).
                param("chain", 2).
                param("recipient", BlockchainTest.ALICE.getRsAccount()).
                param("amountNQT", numPacks * TarascaTester.priceIgnis()).
                param("messageIsPrunable", "true").
                param("message",message).
                feeNQT(IGNIS.ONE_COIN);
        APICall apiCall = builder.build();
        JO response = new JO(apiCall.invoke());
        //Logger.logDebugMessage("sendMoney: " + response);
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO buyPacksGiftz(int numPacks, String contractName, String secretPhrase) {
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();

        JO coin = TarascaTester.getCollectionCurrency(BlockchainTest.CHUCK.getRsAccount());

        APICall sender = new APICall.Builder("transferCurrency").
                param("chain", 2).
                param("currency", coin.getString("currency")).
                param("recipient", BlockchainTest.ALICE.getRsAccount()).
                param("unitsQNT", numPacks).
                param("secretPhrase", secretPhrase).
                param("messageIsPrunable", "true").
                param("message",message).
                feeNQT(1 * IGNIS.ONE_COIN).
                build();
        JO response = new JO(sender.invoke());
        BlockchainTest.generateBlock();
        return response;
    }

    public static JO getAccountAssets(String account){
        APICall sender = new APICall.Builder("getAccountAssets").
                param("account", account).
                build();
        JO response = new JO(sender.invoke());
        BlockchainTest.generateBlock();
        return response;
    }
}