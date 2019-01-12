package com.jelurida.ardor.contracts;

import nxt.addons.*;
import nxt.http.APICall;
import nxt.http.callers.GetBalanceCall;
import nxt.http.callers.GetBlockCall;
import nxt.http.callers.GetBlockchainTransactionsCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.responses.BlockResponse;
import nxt.http.responses.TransactionResponse;
import org.json.simple.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

import static nxt.blockchain.ChildChain.IGNIS;


/**
 * The Jackpot contract is triggered every pre-defined number of blocks, checks if somebody won and
 * sends the jackpot to every winner. If there are more than one winner, the jackpot is divided.
 * This is a block based contract.
 *
 * Contract Parameters
 * chain - chain to monitor
 * frequency - how many blocks to wait between payments
 * collectionRs - the account that issued the assets (assuming that all assets this account has issued belong to the collection.
 */
public class Jackpot extends AbstractContract {

    /**
     * This is triggered every block, executes payout every _frequency_th block.
     * payout is to every account that has met the requirements.
     * @param context contract context
     */
    @Override
    public void processBlock(BlockContext context) {
        // Read contract configuration
        int chainId = getContractParams().getInt("chain", 2);
        int frequency = getContractParams().getInt("frequency", 30);
        //String contractRs = context.getConfig().getAccountRs();
        String collectionRs = getContractParams().getString("collectionRs", "ARDOR-6645-FEKY-BC5T-EPW5D");         //if none specified, this will make the contract crash.

        // Check the height if it shold perform payment distribution on this height
        int height = context.getHeight();
        int nextrun = (height / frequency) * frequency + frequency;
        if (height % frequency != 0) {
            context.logInfoMessage("CONTRACT: Jackpot: ignore block at height %d, next run at height %d", height, nextrun);
            return;
        }
        context.logInfoMessage("CONTRACT: Jackpot: run contract at height %d", height);

        JA collectionAssets = getCollectionAssets(collectionRs);

        List<TransactionResponse> payments = getPaymentTransactions(context, chainId, Math.max(height - frequency, 2), context.getConfig().getAccount());
        if (payments.size() == 0) {
            context.logInfoMessage("CONTRACT: Jackpot: No incoming payments between block %d and %d", Math.max(0, height - frequency + 1), height);
            return;
        }
        context.logInfoMessage("CONTRACT: Jackpot: Incoming payments between block %d and %d, now checking against collection reqs", Math.max(0, height - frequency + 1), height);

        Set<String> Senders = getUniqueSenders(payments);
        context.logInfoMessage("CONTRACT: Jackpot: getUniqueSenders(): "+Senders.toString());

        List<String> Winners = Senders.stream().filter(Sender-> confirmJackpotForSender(context, Sender,collectionAssets,payments)).collect(Collectors.toList());
        context.logInfoMessage("CONTRACT: Jackpot: confirmJackpotForSender() Winners: "+Winners.toString());

        long balance = 0;

        if (Winners.size() > 0){
            JO response = GetBalanceCall.create(2).account(context.getConfig().getAccountRs()).call();
            balance = Long.parseLong((String) response.get("balanceNQT"));


            long fee = IGNIS.ONE_COIN;
            long price = (balance- (fee*Winners.size())) / Winners.size();

            Winners.forEach(Winner -> {
                context.logInfoMessage("CONTRACT: Jackpot: Incoming assets between block %d and %d. Account %s won the jackpot", Math.max(0, height - frequency + 1), height,Winner);
                SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(Winner).amountNQT(price).feeNQT(fee);
                context.logInfoMessage("CONTRACT: Jackpot: Send Prize: %d Ignis to %s", price, Winner);
                context.createTransaction(sendMoneyCall);
            });
            context.logInfoMessage("CONTRACT: Jackpot: finished, exiting.");
            return;
        }
        // no winners found!
        context.logInfoMessage("CONTRACT: Jackpot: No set of incoming assets between block %d and %d won the jackpot", Math.max(0, height - frequency + 1), height);
    }

    /**
     * Load all incoming payments to the contract account between the current height and the previously checked height
     * @param context contract context
     * @param chainId chain to monitor fot payments
     * @param height load transactions from this height until the current height
     * @param contractAccount the contract account
     * @return list of incoming payment transactions
     */
    private List<TransactionResponse> getPaymentTransactions(AbstractContractContext context, int chainId, int height, String contractAccount) {
        // Get the block timestamp from which to load transactions and load the contract account transactions
        BlockResponse block = GetBlockCall.create().height(height).getBlock();
        GetBlockchainTransactionsCall getBlockchainTransactionsResponse = GetBlockchainTransactionsCall.create(chainId).
                timestamp(block.getTimestamp()).
                account(context.getConfig().getAccountRs()).
                executedOnly(true).
                type(2).subtype(1);
        List<TransactionResponse> transactionList = getBlockchainTransactionsResponse.getTransactions();

        // Filter the transactions by recipient, ignore transactions the contract sent to itself
        List<TransactionResponse> transactionsFiltered = transactionList.stream().filter(t -> {
            String recipient = t.getRecipient();
            if (!recipient.equals(contractAccount)) {
                return false;
            }
            //noinspection RedundantIfStatement
            if (recipient.equals(contractAccount) && t.getSender().equals(contractAccount)) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        return transactionsFiltered;
    }

    private Set<String> getUniqueSenders(List<TransactionResponse> transactionList){
        List<String> Senders = transactionList.stream().map(TransactionResponse::getSender).collect(Collectors.toList());
        return new HashSet<String>(Senders);
    }

    private JA getCollectionAssets(String account){
        APICall getCollectionAssets = new APICall.Builder("getAssetsByIssuer").
                param("account", account).
                build();
        JO response = new JO(getCollectionAssets.invoke());
        JA schachtel = new JA(response.get("assets")); // Need to unbox another array
        return new JA(schachtel.getObject(0));
    }

    /*
     *
     */
    private boolean confirmJackpotForSender(AbstractContractContext context, String Sender, JA collectionAssets, List<TransactionResponse> transactionList){
        ListIterator caItr = collectionAssets.listIterator();
        while (caItr.hasNext()) {
            JSONObject assetObj = (JSONObject) caItr.next();
            JO asset = new JO(assetObj);
            String assetId = asset.getString("asset");
            context.logInfoMessage("CONTRACT: confirmJackpotForSender(): Checking for collection(Asset)Id: %s",assetId);
            Object[] matching = transactionList
                    .stream()
                    .filter(payment -> payment.getAttachmentJson().getString("asset").equals(assetId) & payment.getSender().equals(Sender))
                    .toArray();
            if (matching.length == 0){
                // this asset not found, no jackpot for you..
                context.logInfoMessage("CONTRACT: confirmJackpotForSender(): No matching asset: %s for collectionId: %s, exit",matching.toString(),assetId);
                return false;
            }
            if (matching.length >1){
                context.logInfoMessage("CONTRACT: confirmJackpotForSender(): Too many matching assets: %s for collectionId: %s, exit",matching.toString(),assetId);
                // exceptional case, the guy send this one twice.
                // TODO cater for this case
                return false;
            }
        }
        // we came this far, so must be good.
        return true;
    }

    /**
     * Check the contract status
     * Returns jackpot information and current status.
     * @param context contract contract
     */
    @Override
    public void processRequest(RequestContext context) {
        context.logInfoMessage("CONTRACT: Jackpot: received API request.");

        int frequency = getContractParams().getInt("frequency", 30);
        int confirmationTime = getContractParams().getInt("confirmationTime", 2);
        String collectionRs = getContractParams().getString("collectionRs", "ARDOR-6645-FEKY-BC5T-EPW5D");

        String jackount = context.getConfig().getAccountRs();

        // call contract to request information
        JO response = GetBalanceCall.create(2).account(jackount).call();
        long balance = Long.parseLong((String) response.get("balanceNQT"));


        JA assets = getCollectionAssets(collectionRs);
        int numAssets = assets.size();

        JO retresponse = new JO();
        retresponse.put("jackpotBalanceNQT", balance);
        retresponse.put("jackpotAccountRs", jackount);
        retresponse.put("collectionAccountRs", collectionRs);
        retresponse.put("collectionSize", numAssets);
        retresponse.put("jackpotRunFrequency", frequency);
        retresponse.put("jackpotConfirmationTime", confirmationTime);
        context.setResponse(retresponse);
    }
}
