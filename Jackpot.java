package com.jelurida.ardor.contracts;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;
import nxt.addons.AbstractContract;
import nxt.addons.AbstractContractContext;
import nxt.addons.BlockContext;
import nxt.addons.ContractParametersProvider;
import nxt.addons.ContractSetupParameter;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.addons.RequestContext;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.http.APICall.Builder;
import nxt.http.callers.GetBalanceCall;
import nxt.http.callers.GetBlockCall;
import nxt.http.callers.GetBlockchainTransactionsCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.responses.BlockResponse;
import nxt.http.responses.TransactionResponse;
import org.json.simple.JSONObject;

public class Jackpot extends AbstractContract {
    public Jackpot() {
    }

    public JO processBlock(BlockContext context) {
        Jackpot.Params params = (Jackpot.Params)context.getParams(Jackpot.Params.class);
        int chainId = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).chainId();
        int frequency = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).frequency();
        String collectionRs = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).collectionRs();
        int height = context.getHeight();
        if (height % frequency != 0) {
            JO returned = new JO();
            int nextrun = height / frequency * frequency + frequency;
            returned.put("message", "Jackpot: ignore block at height " + height + ", next run at height " + nextrun);
            return returned;
        } else {
            context.logInfoMessage("CONTRACT: Jackpot: run contract at height %d with params chainId %d, frequency: %d, collection: %s", new Object[]{height, chainId, frequency, collectionRs});
            JA collectionAssets = this.getCollectionAssets(collectionRs);
            List<TransactionResponse> payments = this.getPaymentTransactions(context, chainId, Math.max(height - frequency + 1, 2), context.getAccount());
            if (payments.size() == 0) {
                context.logInfoMessage("CONTRACT: Jackpot: No incoming payments between block %d and %d", new Object[]{Math.max(0, height - frequency + 1), height});
                return new JO();
            } else {
                context.logInfoMessage("CONTRACT: Jackpot: Incoming payments between block %d and %d, now checking against collection reqs", new Object[]{Math.max(0, height - frequency + 1), height});
                Set<String> Senders = this.getUniqueSenders(payments);
                context.logInfoMessage("CONTRACT: Jackpot: getUniqueSenders(): " + Senders.toString(), new Object[0]);
                List<String> Winners = (List)Senders.stream().filter((Sender) -> {
                    return this.confirmJackpotForSender(context, Sender, context.getAccount(), collectionAssets, payments);
                }).collect(Collectors.toList());
                context.logInfoMessage("CONTRACT: Jackpot: confirmJackpotForSender() Winners: " + Winners.toString(), new Object[0]);
                long balance = 0L;
                if (Winners.size() > 0) {
                    JO response = GetBalanceCall.create(2).account(context.getAccountRs()).call();
                    balance = Long.parseLong((String)response.get("balanceNQT"));
                    long fee = ChildChain.IGNIS.ONE_COIN;
                    long price = (balance - fee * (long)Winners.size()) / (long)Winners.size();
                    Winners.forEach((Winner) -> {
                        context.logInfoMessage("CONTRACT: Jackpot: Incoming assets between block %d and %d. Account %s won the jackpot", new Object[]{Math.max(0, height - frequency + 1), height, Winner});
                        SendMoneyCall sendMoneyCall = (SendMoneyCall)((SendMoneyCall)SendMoneyCall.create(chainId).recipient(Winner)).amountNQT(price).feeNQT(fee);
                        context.logInfoMessage("CONTRACT: Jackpot: Send Prize: %d Ignis to %s", new Object[]{price, Winner});
                        context.createTransaction(sendMoneyCall);
                    });
                    context.logInfoMessage("CONTRACT: Jackpot: finished, exiting.", new Object[0]);
                    return context.getResponse();
                } else {
                    context.logInfoMessage("CONTRACT: Jackpot: No set of incoming assets between block %d and %d won the jackpot", new Object[]{Math.max(0, height - frequency + 1), height});
                    return new JO();
                }
            }
        }
    }

    private List<TransactionResponse> getPaymentTransactions(AbstractContractContext context, int chainId, int height, String contractAccount) {
        BlockResponse block = GetBlockCall.create().height(height).getBlock();
        GetBlockchainTransactionsCall getBlockchainTransactionsResponse = GetBlockchainTransactionsCall.create(chainId).timestamp(block.getTimestamp()).account(contractAccount).executedOnly(true).type(2).subtype(1);
        List<TransactionResponse> transactionList = getBlockchainTransactionsResponse.getTransactions();
        List<TransactionResponse> transactionsFiltered = (List)transactionList.stream().filter((t) -> {
            String recipient = t.getRecipient();
            if (!recipient.equals(contractAccount)) {
                return false;
            } else {
                return !recipient.equals(contractAccount) || !t.getSender().equals(contractAccount);
            }
        }).collect(Collectors.toList());
        return transactionsFiltered;
    }

    private Set<String> getUniqueSenders(List<TransactionResponse> transactionList) {
        List<String> Senders = (List)transactionList.stream().map(TransactionResponse::getSender).collect(Collectors.toList());
        return new HashSet(Senders);
    }

    private JA getCollectionAssets(String account) {
        APICall getCollectionAssets = (new Builder("getAssetsByIssuer")).param("account", account).build();
        JO response = new JO(getCollectionAssets.invoke());
        JA schachtel = new JA(response.get("assets"));
        return new JA(schachtel.getObject(0));
    }

    private boolean confirmJackpotForSender(AbstractContractContext context, String Sender, String Recipient, JA collectionAssets, List<TransactionResponse> transactionList) {
        ListIterator caItr = collectionAssets.listIterator();

        while(caItr.hasNext()) {
            JSONObject assetObj = (JSONObject)caItr.next();
            JO asset = new JO(assetObj);
            String assetId = asset.getString("asset");
            context.logInfoMessage("CONTRACT: confirmJackpotForSender(): Checking for collection(Asset)Id: %s", new Object[]{assetId});
            Object[] matching = transactionList.stream().filter((payment) -> {
                return payment.getAttachmentJson().getString("asset").equals(assetId) && payment.getSender().equals(Sender) && payment.getRecipient().equals(Recipient);
            }).toArray();
            if (matching.length == 0) {
                context.logInfoMessage("CONTRACT: confirmJackpotForSender(): No matching asset: %s for collectionId: %s, exit", new Object[]{matching.toString(), assetId});
                return false;
            }

            if (matching.length > 1) {
                context.logInfoMessage("CONTRACT: confirmJackpotForSender(): %d matching assets: %s for collectionId: %s, exit", new Object[]{matching.length, matching.toString(), assetId});
            }
        }

        return true;
    }

    public JO processRequest(RequestContext context) {
        context.logInfoMessage("CONTRACT: Jackpot: received API request.", new Object[0]);
        Jackpot.Params params = (Jackpot.Params)context.getParams(Jackpot.Params.class);
        int chainId = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).chainId();
        int frequency = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).frequency();
        String collectionRs = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).collectionRs();
        int confirmationTime = 2;
        String jackount = context.getAccountRs();
        JO response = GetBalanceCall.create(2).account(jackount).call();
        long balance = Long.parseLong((String)response.get("balanceNQT"));
        JA assets = this.getCollectionAssets(collectionRs);
        int numAssets = assets.size();
        JO retresponse = new JO();
        retresponse.put("jackpotBalanceNQT", balance);
        retresponse.put("jackpotAccountRs", jackount);
        retresponse.put("collectionAccountRs", collectionRs);
        retresponse.put("collectionSize", numAssets);
        retresponse.put("jackpotRunFrequency", frequency);
        retresponse.put("jackpotConfirmationTime", Integer.valueOf(confirmationTime));
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
