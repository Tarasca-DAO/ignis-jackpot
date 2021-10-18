package org.tarasca.contracts;

import nxt.addons.*;
import nxt.blockchain.ChildChain;
import nxt.crypto.Crypto;
import nxt.http.callers.*;
import nxt.http.responses.BlockResponse;
import nxt.http.responses.TransactionResponse;
import org.json.simple.JSONArray;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static nxt.blockchain.ChildChain.IGNIS;


public class Jackpot extends AbstractContract {

    private Map<String, Integer> persistentWinners = new HashMap<String,Integer>();

    public JO processBlock(BlockContext context) {
        boolean jackpotIsHalfBalance = context.getParams(Params.class).jackpotIsHalfBalance();
        int chainId = context.getParams(Params.class).chainId();
        int frequency = context.getParams(Params.class).frequency();
        int maxnumtarascas = context.getParams(Params.class).maxnumtarascas();
        String collectionRs = context.getParams(Params.class).collectionRs();
        String pAsset = context.getParams(Params.class).pAsset();
        String adminPassword = context.getParams(Params.class).adminPassword();

        long GEMPRIZE = 9000;
        int DEADLINE = 180;

        int height = context.getHeight();
        int modulo = height % frequency; // need to subtract 1 to get transition right (when jackpot block is reached)
        int nextJackpotHeight = height - modulo + frequency;

        int lastJackpotHeight = 0;
        if (modulo == 0 & height != 0) {
            lastJackpotHeight =  (height-1) - ((height-1) % frequency); // freezes this value for the jackpot height
        }
        else {
            lastJackpotHeight = height - modulo;
        }

        JO message = new JO();
        //message.put("currentHeight",height);

        context.logInfoMessage("run with params: chainId: %d, frequency: %d, collectionRs: %s (at height: %d, lastJackpot: %d, nextJackpot: %d)", chainId, frequency, collectionRs,height,lastJackpotHeight,nextJackpotHeight);
        List<JO> collectionAssets = getCollectionAssets(collectionRs);
        List<TransactionResponse> payments = getPaymentTransactions(context, chainId, lastJackpotHeight+1, context.getAccount(), adminPassword);

        if (payments.size() == 0) {
            JO returned = new JO();
            returned.put("message", "No incoming payments between block " + (lastJackpotHeight+1) + " and " + height + ", exit.");
            return returned;
        } else {
            context.logInfoMessage("Incoming payments between block %d and %d, now checking against collection reqs", lastJackpotHeight+1, height);

            Set<String> senders = getUniqueSenders(payments);
            context.logInfoMessage("getUniqueSenders(): " + senders.toString());

            Map<String, Integer> winners = new HashMap<String, Integer>();
            for(String sender : senders){
                int winnerJackpots = confirmJackpotsForSender(context, sender, context.getAccount(), collectionAssets, payments);
                winners.put(sender,winnerJackpots);
            }
            context.logInfoMessage("confirmJackpotForSender() Winners: " + winners.toString());
            persistentWinners = winners;
            // check jackpot block or not
            if (modulo != 0) {
                return context.generateInfoResponse("No jackpot block height, exit");
            }
            else {
                int winnersSize = 0;
                for (Map.Entry mapElement : winners.entrySet()) {
                    int value = (int) mapElement.getValue();
                    winnersSize += value;
                }

                if (winnersSize > 0) {
                    //init randomness for TC lottery
                    initRandomness(context);
                    ContractAndSetupParameters contractAndParameters = context.loadContract("DistributedRandomNumberGenerator");
                    Contract<Map<String, Long>, String> distributedRandomNumberGenerator = contractAndParameters.getContract();
                    DelegatedContext delegatedContext = new DelegatedContext(context, distributedRandomNumberGenerator.getClass().getName(), contractAndParameters.getParams());

                    // type casting joy
                    //final int winnersSizeFinal = winnersSize;
                    Map<String,Long> winnersl = new HashMap<>();
                    winners.forEach(
                            (account,participations) -> {
                                //long weight = (long)participations;
                                winnersl.put(account, (long)participations);
                            });

                    // run TC lottery
                    int cardsForDraw = Math.min(winnersSize,maxnumtarascas);
                    Map<String,Integer> TcWinners = new HashMap<>();
                    for(int i=0; i<cardsForDraw; i++) {
                        String TcWinner = distributedRandomNumberGenerator.processInvocation(delegatedContext, winnersl);
                        Integer curValue = TcWinners.putIfAbsent(TcWinner, 1);
                        if (curValue != null) {
                            TcWinners.put(TcWinner, curValue + 1);
                        }
                        //update winnersl to reduce the weight of the current winning account
                        long oldWeight = winnersl.get(TcWinner);
                        long newWeight = oldWeight >= (long)1.0 ? (long) (oldWeight - 1.0) : (long)0.0;
                        winnersl.replace(TcWinner,oldWeight,newWeight);
                    }

                    JO response = GetBalanceCall.create(2).account(context.getAccountRs()).call();
                    long balance=0;
                    balance = jackpotIsHalfBalance ? response.getLong("balanceNQT")/2 : response.getLong("balanceNQT");

                    long fee = (long) (0.5*IGNIS.ONE_COIN);
                    long price = (balance - 2 * fee * winnersSize) / winnersSize;
                    long priceAsset = IGNIS.ONE_COIN*GEMPRIZE/winnersSize;

                    message.put("reason","sendPrize");
                    int finalWinnersSize = winnersSize;
                    winners.forEach((winner, jackpots) -> {
                        if (jackpots != 0) {
                            Integer cards = TcWinners.get(winner) == null ? 0 : TcWinners.get(winner);
                            JO params = new JO();
                            params.put("numTarascasWon",cards);
                            message.put("contract","AssetDistributor");
                            message.put("params",params);
                            params.put("participations",jackpots);
                            params.put("totalParticipations", finalWinnersSize);
                            context.logInfoMessage("Incoming assets between block %d and %d. Account %s won the jackpot", Math.max(0, height - frequency + 1), height, winner);
                            SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(winner).amountNQT(price * jackpots).feeNQT(fee).message(message.toJSONString()).messageIsText(true).messageIsPrunable(true).deadline(DEADLINE);
                            context.logInfoMessage("Send Prize: %d Ignis to %s", price * jackpots, winner);
                            context.createTransaction(sendMoneyCall);

                            // Send GEM Asset
                            TransferAssetCall transferAssetCall = TransferAssetCall.create(chainId).asset(pAsset).recipient(winner).quantityQNT(priceAsset * jackpots).feeNQT(fee).deadline(DEADLINE);
                            context.logInfoMessage("Send Prize: %d Assets to %s", priceAsset * jackpots, winner);
                            context.createTransaction(transferAssetCall);
                        }
                    });
                    context.logInfoMessage("finished, exiting.");
                    return context.getResponse();
                }
                else {
                    JO returned = new JO();
                    returned.put("message", "No set of incoming assets between block " + (lastJackpotHeight+1) + " and " + height + " won the jackpot, exit.");
                    return returned;
                }
            }
        }
    }

    private List<TransactionResponse> getPaymentTransactions(AbstractContractContext context, int chainId, int height, String contractAccount, String adminPw) {
        BlockResponse block = GetBlockCall.create().height(height).getBlock();
        GetBlockchainTransactionsCall getBlockchainTransactionsResponse = GetBlockchainTransactionsCall.create(chainId).timestamp(block.getTimestamp()).account(contractAccount).executedOnly(true).type(2).subtype(1).adminPassword(adminPw);
        List<TransactionResponse> transactionList = getBlockchainTransactionsResponse.getTransactions();
        context.logInfoMessage("number of fetched TX: %d",transactionList.size());
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
            //context.logInfoMessage("confirmJackpotForSender(): Checking for collection(Asset)Id: %s", assetId);
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
                //context.logInfoMessage("multipleParticipation(): Checking for collection(Asset)Id: %s, quantity: %d", assetId, assetQNT);
                if (minAsset > assetQNT || i == 0) { minAsset = assetQNT; }
            }
        }
        return minAsset;
    }

    private void initRandomness(BlockContext context){
        // load randomnumber generator
        JO contractParameters = context.getContractRunnerConfigParams(getClass().getSimpleName());
        String secretForRandomString = "0";
        if(contractParameters.isExist("secretForRandomString")) {
            secretForRandomString = contractParameters.getString("secretForRandomString");
        }
        MessageDigest digest = Crypto.sha256();
        digest.update(secretForRandomString.getBytes(StandardCharsets.UTF_8));
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(context.getBlock().getBlockId()).array());
        context.initRandom(longLsbFromBytes(digest.digest()));
    }

    public static long longLsbFromBytes(byte[] bytes) {
        BigInteger bi = new BigInteger(1, new byte[] {bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]});
        return bi.longValue();
    }

    public JO processRequest(RequestContext context) {
        context.logInfoMessage("received API request.");
        JO message = new JO();
        if (persistentWinners.size() == 0){
            message.put("winners",new JO());
            return message;
        }

        context.logInfoMessage("apiRequest: confirmJackpotForSender() Winners: " + persistentWinners.toString());
        JO winneroutput = new JO();
        for (String i : persistentWinners.keySet()) {
            winneroutput.put(i,persistentWinners.get(i));
        }
        message.put("winners",winneroutput);
        return message;

    }

    @ContractParametersProvider
    public interface Params {
        @ContractSetupParameter
        default int chainId() {
            return 2;
        }

        @ContractSetupParameter
        default int frequency() {
            return 5040;
        }

        @ContractSetupParameter
        default int maxnumtarascas() {
            return 7;
        }

        @ContractSetupParameter
        default String collectionRs() {
            return "ARDOR-4V3B-TVQA-Q6LF-GMH3T";
        }

        @ContractSetupParameter
        default boolean jackpotIsHalfBalance() {return false;}

        @ContractSetupParameter
        default String pAsset() {return "10230963490193589789";}

        @ContractRunnerParameter
        default String adminPassword() {return "";}
    }
}
