import java.util.HashSet;
import java.util.Set;

public class TxHandler {

	private UTXOPool utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
    	 this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
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

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	 Set<Transaction> validTxs = new HashSet<Transaction>();

         for (Transaction tx : possibleTxs) {
             if (isValidTx(tx)) {
                 validTxs.add(tx);
                 try {
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
         Transaction[] validTxArray = new Transaction[validTxs.size()];
         return validTxs.toArray(validTxArray);
    }

}
