package com.jelurida.ardor.contracts;

import nxt.addons.*;
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

import java.util.*;
import java.util.stream.Collectors;

public class Jackpot extends AbstractContract {
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

    //Removed most of the redundant Object array declarations
    //Changed some variable names according to JAVA naming convention

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
            int nextRun = height / frequency * frequency + frequency;
            returned.put("message", "Jackpot: ignore block at height " + height + ", next run at height " + nextRun);
            return returned;
        } else {
            context.logInfoMessage("CONTRACT: Jackpot: run contract at height %d with params chainId %d, frequency: %d, collection: %s", height, chainId, frequency, collectionRs);
            JA collectionAssets = this.getCollectionAssets(collectionRs);
            List<TransactionResponse> payments = this.getPaymentTransactions(context, chainId, Math.max(height - frequency + 1, 2), context.getAccount());
            if (payments.size() == 0) {
                context.logInfoMessage("CONTRACT: Jackpot: No incoming payments between block %d and %d", Math.max(0, height - frequency + 1), height);
                return new JO();
            } else {
                context.logInfoMessage("CONTRACT: Jackpot: Incoming payments between block %d and %d, now checking against collection reqs", Math.max(0, height - frequency + 1), height);

                // !!!Repeat Participants Bug!!! HashSet is used in method call which removes repeat participants
                Set<String> Senders = this.getUniqueSenders(payments);

                context.logInfoMessage("CONTRACT: Jackpot: getUniqueSenders(): " + Senders.toString());
                List<String> Winners = (List)Senders.stream().filter((Sender) -> {
                    return this.confirmJackpotForSender(context, Sender, context.getAccount(), collectionAssets, payments);
                }).collect(Collectors.toList());
                context.logInfoMessage("CONTRACT: Jackpot: confirmJackpotForSender() Winners: " + Winners.toString());
                long balance = 0L;
                if (Winners.size() > 0) {
                    JO response = GetBalanceCall.create(2).account(context.getAccountRs()).call();
                    balance = Long.parseLong((String)response.get("balanceNQT"));
                    long fee = ChildChain.IGNIS.ONE_COIN;
                    long prize = (balance - fee * (long)Winners.size()) / (long)Winners.size();
                    // Changed Lambda to For loop
                    for (String Winner : Winners) {
                        context.logInfoMessage("CONTRACT: Jackpot: Incoming assets between block %d and %d. Account %s won the jackpot", Math.max(0, height - frequency + 1), height, Winner);
                        SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(Winner).amountNQT(prize).feeNQT(fee);
                        context.logInfoMessage("CONTRACT: Jackpot: Send Prize: %d Ignis to %s", prize, Winner);
                        context.createTransaction(sendMoneyCall);
                        context.logInfoMessage("It is still running");
                    }
                    context.logInfoMessage("CONTRACT: Jackpot: finished, exiting.");
                    return context.getResponse();
                } else {
                    context.logInfoMessage("CONTRACT: Jackpot: No set of incoming assets between block %d and %d won the jackpot", Math.max(0, height - frequency + 1), height);
                    return new JO();
                }
            }
        }
    }

    private List<TransactionResponse> getPaymentTransactions(AbstractContractContext context, int chainId, int height, String contractAccount) {
        BlockResponse block = GetBlockCall.create().height(height).getBlock();
        GetBlockchainTransactionsCall getBlockchainTransactionsResponse = GetBlockchainTransactionsCall.create(chainId).
                timestamp(block.getTimestamp()).
                account(contractAccount).
                executedOnly(true).
                type(2).
                subtype(1);
        List<TransactionResponse> transactionList = getBlockchainTransactionsResponse.getTransactions();

        return transactionList.stream().filter(t -> {
            if (!t.getRecipient().equals(contractAccount)) {
                return false;
            }
            //Removed redundant else statement with return !recipient.equals(contractAccount) || !t.getSender().equals(contractAccount);
            return true;
        }).collect(Collectors.toList());
    }

    /* !!!Repeat Participant Bug!!!
     HashSet used here - Limits participants from joining more than once
     This bug can be fixed by changing this function to getSenders and
     adding a new function to find getAssetQuantity for each Sender and adding the sender
     to the list multiple times according to quantity of assets. All other code will integrate easily
     just by changing the modified function names.
     */
    private Set<String> getUniqueSenders(List<TransactionResponse> transactionList) {
        List<String> Senders = (List)transactionList.stream().map(TransactionResponse::getSender).
                collect(Collectors.toList());
        return new HashSet(Senders);
    }

    private JA getCollectionAssets(String account) {
        APICall getCollectionAssets = (new Builder("getAssetsByIssuer")).
                param("account", account).
                build();
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
            context.logInfoMessage("CONTRACT: confirmJackpotForSender(): Checking for collection(Asset)Id: %s", assetId);
            Object[] matching = transactionList.stream().filter((payment) -> payment.getAttachmentJson().getString("asset").equals(assetId) && payment.getSender().equals(Sender) && payment.getRecipient().equals(Recipient)).toArray();
            if (matching.length == 0) {
                context.logInfoMessage("CONTRACT: confirmJackpotForSender(): No matching asset: %s for collectionId: %s, exit", Arrays.toString(matching), assetId);
                return false;
            }
            if (matching.length > 1) {
                context.logInfoMessage("CONTRACT: confirmJackpotForSender(): %d matching assets: %s for collectionId: %s, exit", matching.length, Arrays.toString(matching), assetId);
            }
        }

        return true;
    }

    public JO processRequest(RequestContext context) {
        context.logInfoMessage("CONTRACT: Jackpot: received API request.");
        Jackpot.Params params = (Jackpot.Params)context.getParams(Jackpot.Params.class);
        int chainId = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).chainId();
        int frequency = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).frequency();
        String collectionRs = ((Jackpot.Params)context.getParams(Jackpot.Params.class)).collectionRs();
        int confirmationTime = 2;
        String jackAccount = context.getAccountRs();
        JO response = GetBalanceCall.create(2).account(jackAccount).call();
        long balance = Long.parseLong((String)response.get("balanceNQT"));
        JA assets = this.getCollectionAssets(collectionRs);
        int numAssets = assets.size();
        JO retResponse = new JO();
        retResponse.put("jackpotBalanceNQT", balance);
        retResponse.put("jackpotAccountRs", jackAccount);
        retResponse.put("collectionAccountRs", collectionRs);
        retResponse.put("collectionSize", numAssets);
        retResponse.put("jackpotRunFrequency", frequency);
        retResponse.put("jackpotConfirmationTime", confirmationTime);
        return retResponse;
    }

}
