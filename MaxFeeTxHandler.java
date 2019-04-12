import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;


public class MaxFeeTxHandler {

    private UTXOPool utxoPool;
    private Set<Transaction> pendingTxs;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is {@code utxoPool}. This should make a copy of
     * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
     *
     * @param utxoPool
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
        this.pendingTxs = new HashSet<Transaction>();
    }

    /**
     * @param tx
     * @return true if: (1) all outputs claimed by {@code tx} are tx_in the
     * current UTXO pool, (2) the signatures on each input of {@code tx} are
     * valid, (3) no UTXO is claimed multiple times by {@code tx}, (4) all of
     * {@code tx}s prev_tx_out values are non-negative, and (5) the sum of
     * {@code tx}s input values is greater than or equal to the sum of its
     * prev_tx_out values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	UTXOPool utxPool = new UTXOPool();
        double prevTxSum = 0;
        double curTxSum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input txIn = tx.getInput(i);
            UTXO utxo = new UTXO(txIn.prevTxHash, txIn.outputIndex);
            Transaction.Output txOut;

            if (!utxoPool.contains(utxo)) {
                return false; 
            } else {
                txOut = utxoPool.getTxOutput(utxo); // @return the transaction output corresponding to this UTXO in hash map
            }
            if (!Crypto.verifySignature(txOut.address, tx.getRawDataToSign(i), txIn.signature)) {
                return false; 
            }
            if (utxPool.contains(utxo)) {
                return false; 
            } else {
                utxPool.addUTXO(utxo, txOut);
                prevTxSum += txOut.value;
            }
        }
        for (Transaction.Output curTxOut : tx.getOutputs()) {
            if (curTxOut.value < 0) {
                return false; 
            } else {
                curTxSum += curTxOut.value;
            }
        }
        return prevTxSum >= curTxSum;
   }

    private double calcTxFees(Transaction tx) {
        double sumInputs = 0;
        double sumOutputs = 0;

        for (Transaction.Input in : tx.getInputs()) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if (!utxoPool.contains(utxo) || !isValidTx(tx)) {
                continue;
            }
            Transaction.Output txOutput = utxoPool.getTxOutput(utxo);
            sumInputs += txOutput.value;
        }
        for (Transaction.Output out : tx.getOutputs()) {
            sumOutputs += out.value;
        }
        return sumInputs - sumOutputs;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed
     * transactions, checking each transaction for correctness, returning a
     * mutually valid array of accepted transactions, and updating the current
     * UTXO pool as appropriate.
     *
     * @param possibleTxs
     * @return
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Set<Transaction> txnSortedByFees = new TreeSet<>((tx1, tx2) -> {
            double tx1Fees = calcTxFees(tx1);
            double tx2Fees = calcTxFees(tx2);
            return Double.valueOf(tx2Fees).compareTo(tx1Fees);
        });
        Collections.addAll(txnSortedByFees, possibleTxs);
        Set<Transaction> acceptedTxs = new HashSet<>();

        for (Transaction tx : txnSortedByFees) {
            if (isValidTx(tx)) {
                try {
                    acceptedTxs.add(tx);
                    for (Transaction.Input txIn : tx.getInputs()) { 
                        UTXO utxo = new UTXO(txIn.prevTxHash, txIn.outputIndex);
                        utxoPool.removeUTXO(utxo);
                    }
                    for (int i = 0; i < tx.numOutputs(); i++) { 
                        Transaction.Output txOut = tx.getOutput(i);
                        UTXO utxo = new UTXO(tx.getHash(), i);
                        utxoPool.addUTXO(utxo, txOut);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    
        while (!pendingTxs.isEmpty()) {
            int pendingTxSize = pendingTxs.size();
            for (Transaction tx : pendingTxs) {
                if (isValidTx(tx)) {
                    try {
                        acceptedTxs.add(tx);
                        for (Transaction.Input txIn : tx.getInputs()) { 
                            UTXO utxo = new UTXO(txIn.prevTxHash, txIn.outputIndex);
                            utxoPool.removeUTXO(utxo);
                        }
                        for (int i = 0; i < tx.numOutputs(); i++) { 
                            Transaction.Output txOut = tx.getOutput(i);
                            UTXO utxo = new UTXO(tx.getHash(), i);
                            utxoPool.addUTXO(utxo, txOut);
                        }
                        pendingTxs.remove(tx);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (pendingTxSize == pendingTxs.size()) {
                break; 
            }
        }
        Transaction[] validTxArray = new Transaction[acceptedTxs.size()];
        return acceptedTxs.toArray(validTxArray);
    }

}
