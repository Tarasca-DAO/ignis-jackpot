package com.jelurida.ardor.contracts;

import nxt.addons.AbstractContract;
import nxt.addons.AbstractContractContext;
import nxt.addons.BlockContext;
import nxt.addons.ContractParametersProvider;
import nxt.addons.ContractSetupParameter;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.addons.RequestContext;
import nxt.blockchain.ChildChain;
import nxt.http.callers.*;
import nxt.http.responses.BlockResponse;
import nxt.http.responses.TransactionResponse;
import org.json.simple.JSONArray;

import java.util.*;
import java.util.stream.Collectors;

import static nxt.blockchain.ChildChain.IGNIS;

public class Jackpot extends AbstractContract {

    public JO processBlock(BlockContext context) {
        int chainId = context.getParams(Params.class).chainId();
        int frequency = context.getParams(Params.class).frequency();
        String collectionRs = context.getParams(Params.class).collectionRs();

        int height = context.getHeight();
        int modulo = (height-1) % frequency; // need to subtract 1 to get transition right (when jackpot block is reached)
        int lastJackpotHeight = height - modulo;
        int nextJackpotHeight = lastJackpotHeight + frequency;

        context.logInfoMessage("CONTRACT: Jackpot: run at height %d with params chainId %d, frequency: %d, collection: %s (lastJackpot: %d, nextJackpot: %d", height, chainId, frequency, collectionRs,lastJackpotHeight,nextJackpotHeight);
        List<JO> collectionAssets = getCollectionAssets(collectionRs);
        List<TransactionResponse> payments = getPaymentTransactions(chainId, lastJackpotHeight+1, context.getAccount());

        if (payments.size() == 0) {
            JO returned = new JO();
            returned.put("message", "CONTRACT: Jackpot: No incoming payments between block " + lastJackpotHeight + " and " + height + ", exit.");
            return returned;
        } else {
            context.logInfoMessage("CONTRACT: Jackpot: Incoming payments between block %d and %d, now checking against collection reqs", lastJackpotHeight, height);

            Set<String> senders = getUniqueSenders(payments);
            context.logInfoMessage("CONTRACT: Jackpot: getUniqueSenders(): " + senders.toString());

            Map<String, Integer> winners = new HashMap<String, Integer>();
            for(String sender : senders){
                int winnerJackpots = confirmJackpotsForSender(context, sender, context.getAccount(), collectionAssets, payments);
                winners.put(sender,winnerJackpots);
            }
            context.logInfoMessage("CONTRACT: Jackpot: confirmJackpotForSender() Winners: " + winners.toString());

            // check jackpot block or not
            if (height % frequency != 0) {
                context.logInfoMessage("CONTRACT: Jackpot: No jackpot block height, checking if messages need to be sent out");
                BlockResponse block = GetBlockCall.create().height(height).getBlock();
                int timestampLastJackpot = block.getTimestamp();
                for (Map.Entry<String, Integer> entry : winners.entrySet()) {
                    String winner = entry.getKey();
                    Integer numWins = entry.getValue();
                    JO response = GetPrunableMessagesCall.create(2).account(context.getAccountRs()).otherAccount(winner).timestamp(timestampLastJackpot).call();
                    JA msgs = response.getArray("prunableMessages");
                    context.logInfoMessage("Winner " + winner + ": found " + msgs.size() + " msgs for " + numWins+ " confirmed participations.");
                    //TODO think do we need to read the messages? maybe not for the first version
                    if (numWins > msgs.size()) {
                        // TODO: add checking for already sent messages...
                        context.logInfoMessage("sending message");
                        JO message = new JO();
                        message.put("contract", "Jackpot");
                        message.put("currentHeight",height);
                        message.put("jackpotHeight",nextJackpotHeight);//simplified, only works for the first round.
                        message.put("participationConfirmed", true);
                        JO msgresponse = SendMessageCall.create(2).
                                message(message.toJSONString()).
                                messageIsPrunable(true).
                                recipient(winner).
                                call();
                    }
                }
                JO returned = new JO();
                returned.put("message", "Jackpot: finish block at height " + height + ", next run at height " + nextJackpotHeight + ", no pay out, not a jackpot block height, exit.");
                return returned;
            } else {
                long balance;
                int winnersSize = 0;
                for (Map.Entry mapElement : winners.entrySet()) {
                    int value = (int) mapElement.getValue();
                    winnersSize += value;
                }
                if (winnersSize > 0) {
                    JO response = GetBalanceCall.create(2).account(context.getAccountRs()).call();
                    balance = response.getLong("balanceNQT");
                    long fee = ChildChain.IGNIS.ONE_COIN;
                    long price = (balance - fee * winnersSize) / winnersSize;
                    winners.forEach((winner, jackpots) -> {
                        context.logInfoMessage("CONTRACT: Jackpot: Incoming assets between block %d and %d. Account %s won the jackpot", Math.max(0, height - frequency + 1), height, winner);
                        SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(winner).amountNQT(price * jackpots).feeNQT(fee);
                        context.logInfoMessage("CONTRACT: Jackpot: Send Prize: %d Ignis to %s", price * jackpots, winner);
                        context.createTransaction(sendMoneyCall);
                    });
                    context.logInfoMessage("CONTRACT: Jackpot: finished, exiting.");
                    return context.getResponse();
                } else {
                    context.logInfoMessage("CONTRACT: Jackpot: No set of incoming assets between block %d and %d won the jackpot", Math.max(0, height - frequency + 1), height);
                    return new JO();
                }
            }
        }
    }

    private List<TransactionResponse> getPaymentTransactions(int chainId, int height, String contractAccount) {
        BlockResponse block = GetBlockCall.create().height(height).getBlock();
        GetBlockchainTransactionsCall getBlockchainTransactionsResponse = GetBlockchainTransactionsCall.create(chainId).timestamp(block.getTimestamp()).account(contractAccount).executedOnly(true).type(2).subtype(1);
        List<TransactionResponse> transactionList = getBlockchainTransactionsResponse.getTransactions();
        return transactionList.stream().filter((t) -> {
            String recipient = t.getRecipient();
            if (!recipient.equals(contractAccount)) {
                return false;
            } else {
                return !t.getSender().equals(contractAccount);
            }
        }).collect(Collectors.toList());
    }

    private Set<String> getUniqueSenders(List<TransactionResponse> transactionList) {
        return transactionList.stream().map(TransactionResponse::getSender).collect(Collectors.toSet());
    }

    private List<JO> getCollectionAssets(String account) {
        JO response = GetAssetsByIssuerCall.create().account(account).call();
        JSONArray accountAssets = (JSONArray) response.getArray("assets").getObject(0);
        List<JO> assets = new ArrayList<>();
        for (Object asset : accountAssets) {
            assets.add(new JO(asset));
        }
        return assets;
    }

    private boolean confirmJackpotForSender(AbstractContractContext context, String sender, String recipient, List<JO> collectionAssets, List<TransactionResponse> transactionList) {
        return collectionAssets.stream().allMatch(asset -> {
            String assetId = asset.getString("asset");
            context.logInfoMessage("CONTRACT: confirmJackpotForSender(): Checking for collection(Asset)Id: %s", assetId);
            return transactionList.stream().filter((payment) -> payment.getAttachmentJson().getString("asset").equals(assetId) && payment.getSender().equals(sender) && payment.getRecipient().equals(recipient)).toArray().length > 0;
        });
    }

    private int confirmJackpotsForSender(AbstractContractContext context, String sender, String recipient, List<JO> collectionAssets, List<TransactionResponse> transactionList) {
        int minAsset = 0;
        if (confirmJackpotForSender(context, sender, recipient, collectionAssets, transactionList)){
            for (int i=0;i<collectionAssets.size();i++){
                String assetId = collectionAssets.get(i).getString("asset");
                int assetQNT = 0;
                for (int j=0; j < transactionList.size(); j++){
                    if(transactionList.get(j).getAttachmentJson().getString("asset").equals(assetId) && transactionList.get(j).getSender().equals(sender) && transactionList.get(j).getRecipient().equals(recipient)){
                        String assetQuantity = transactionList.get(j).getAttachmentJson().getString("quantityQNT");
                        assetQNT = assetQNT + Integer.valueOf(assetQuantity);
                    }
                }
                context.logInfoMessage("CONTRACT: multipleParticipation(): Checking for collection(Asset)Id: %s, quantity: %d", assetId, assetQNT);
                if (minAsset > assetQNT || i == 0) { minAsset = assetQNT; }
            }
        }
        return minAsset;
    }

    public JO processRequest(RequestContext context) {
        context.logInfoMessage("CONTRACT: Jackpot: received API request.");
        int frequency = context.getParams(Params.class).frequency();
        String collectionRs = context.getParams(Params.class).collectionRs();
        int confirmationTime = 2;
        String jackount = context.getAccountRs();
        JO response = GetBalanceCall.create(2).account(jackount).call();
        long balance = Long.parseLong((String)response.get("balanceNQT"));
        List<JO> assets = this.getCollectionAssets(collectionRs);
        int numAssets = assets.size();
        JO retresponse = new JO();
        retresponse.put("jackpotBalanceNQT", balance);
        retresponse.put("jackpotAccountRs", jackount);
        retresponse.put("collectionAccountRs", collectionRs);
        retresponse.put("collectionSize", numAssets);
        retresponse.put("jackpotRunFrequency", frequency);
        retresponse.put("jackpotConfirmationTime", confirmationTime);
        return retresponse;
    }

    @ContractParametersProvider
    public interface Params {
        @ContractSetupParameter
        default int chainId() {
            return 2;
        }

        @ContractSetupParameter
        default int frequency() {
            return 30;
        }

        @ContractSetupParameter
        default String collectionRs() {
            return "ARDOR-YDK2-LDGG-3QL8-3Z9JD";
        }
    }
}
