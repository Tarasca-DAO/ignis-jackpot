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
import nxt.http.callers.GetAssetsByIssuerCall;
import nxt.http.callers.GetBalanceCall;
import nxt.http.callers.GetBlockCall;
import nxt.http.callers.GetBlockchainTransactionsCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.responses.BlockResponse;
import nxt.http.responses.TransactionResponse;
import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Jackpot extends AbstractContract {

    public JO processBlock(BlockContext context) {
        int chainId = context.getParams(Params.class).chainId();
        int frequency = context.getParams(Params.class).frequency();
        String collectionRs = context.getParams(Params.class).collectionRs();
        int height = context.getHeight();
        if (height % frequency != 0) {
            JO returned = new JO();
            int nextrun = height / frequency * frequency + frequency;
            returned.put("message", "Jackpot: ignore block at height " + height + ", next run at height " + nextrun);
            return returned;
        } else {
            context.logInfoMessage("CONTRACT: Jackpot: run contract at height %d with params chainId %d, frequency: %d, collection: %s", height, chainId, frequency, collectionRs);
            List<JO> collectionAssets = getCollectionAssets(collectionRs);
            List<TransactionResponse> payments = getPaymentTransactions(chainId, Math.max(height - frequency + 1, 2), context.getAccount());
            if (payments.size() == 0) {
                context.logInfoMessage("CONTRACT: Jackpot: No incoming payments between block %d and %d", Math.max(0, height - frequency + 1), height);
                return new JO();
            } else {
                context.logInfoMessage("CONTRACT: Jackpot: Incoming payments between block %d and %d, now checking against collection reqs", Math.max(0, height - frequency + 1), height);
                Set<String> senders = getUniqueSenders(payments);
                context.logInfoMessage("CONTRACT: Jackpot: getUniqueSenders(): " + senders.toString());
                List<String> winners = senders.stream().filter(sender -> confirmJackpotForSender(context, sender, context.getAccount(), collectionAssets, payments)).collect(Collectors.toList());
                context.logInfoMessage("CONTRACT: Jackpot: confirmJackpotForSender() Winners: " + winners.toString());
                long balance;
                if (winners.size() > 0) {
                    JO response = GetBalanceCall.create(2).account(context.getAccountRs()).call();
                    balance = response.getLong("balanceNQT");
                    long fee = ChildChain.IGNIS.ONE_COIN;
                    long price = (balance - fee * winners.size()) / winners.size();
                    winners.forEach(winner -> {
                        context.logInfoMessage("CONTRACT: Jackpot: Incoming assets between block %d and %d. Account %s won the jackpot", Math.max(0, height - frequency + 1), height, winner);
                        SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(winner).amountNQT(price).feeNQT(fee);
                        context.logInfoMessage("CONTRACT: Jackpot: Send Prize: %d Ignis to %s", price, winner);
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