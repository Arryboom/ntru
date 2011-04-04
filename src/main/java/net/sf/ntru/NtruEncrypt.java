/**
 * This software is dual-licensed. You may choose either the
 * Non-Profit Open Software License version 3.0, or any license
 * agreement into which you enter with Security Innovation, Inc.
 * 
 * Use of this code, or certain portions thereof, implements
 * inventions covered by claims of one or more of the following
 * U.S. Patents and/or foreign counterpart patents, owned by
 * Security Innovation, Inc.:
 * 7,308,097, 7,031,468, 6,959,085, 6,298,137, and 6,081,597.
 * Practice or sale of the inventions embodied in the code hereof
 * requires a license from Security Innovation Inc. at:
 * 
 * 187 Ballardvale St, Suite A195
 * Wilmington, MA 01887
 * USA
 */

package net.sf.ntru;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * The parameter p is hardcoded to 3.
 */
public class NtruEncrypt {
    
    public static EncryptionKeyPair generateKeyPair(EncryptionParameters params) {
        int N = params.N;
        int q = params.q;
        int df = params.df;
        int dg = params.dg;
        
        IntegerPolynomial f = null;
        IntegerPolynomial fp = null;
        IntegerPolynomial fq = null;
        do {
            f = IntegerPolynomial.generateRandomSmall(N, df, df-1);
            fp = f.invertF3();
            fq = f.invertFq(q);
        } while (fp==null || fq==null);   // repeat until f is invertible
        SparseTernaryPolynomial g = SparseTernaryPolynomial.generateRandom(N, dg, dg);
        IntegerPolynomial h = g.mult(fq, q);
        h.mult3(q);
        h.ensurePositive(q);
        g.clear();
        fq.clear();
        
        EncryptionPrivateKey priv = new EncryptionPrivateKey(new SparseTernaryPolynomial(f), params);
        EncryptionPublicKey pub = new EncryptionPublicKey(h, params);
        return new EncryptionKeyPair(priv, pub);
    }
    
    /**
     * 
     * @param m The message to encrypt
     * @param pubKey
     * @param params
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public static byte[] encrypt(byte[] m, EncryptionPublicKey pubKey, EncryptionParameters params) throws NoSuchAlgorithmException {
        IntegerPolynomial pub = pubKey.h;
        int N = params.N;
        int q = params.q;
        int maxLenBytes = params.maxMsgLenBytes;
        int db = params.db;
        int bufferLenBits = params.bufferLenBits;
        int dm0 = params.dm0;
        int pkLen = params.pkLen;
        int minCallsMask = params.minCallsMask;
        byte[] oid = params.oid;
        
        int l = m.length;
        if (maxLenBytes > 255)
            throw new RuntimeException("llen values bigger than 1 are not supported");
        if (l > maxLenBytes)
            throw new RuntimeException("Message too long: " + l + ">" + maxLenBytes);
        
        while (true) {
            // M = b|octL|m|p0
            byte[] b = new byte[db/8];
            new SecureRandom().nextBytes(b);
            byte[] p0 = new byte[maxLenBytes-l];
            ByteBuffer mBuf = ByteBuffer.allocate(bufferLenBits/8);
            mBuf.put(b);
            mBuf.put((byte)l);
            mBuf.put(m);
            mBuf.put(p0);
            byte[] M = mBuf.array();
            
            IntegerPolynomial mTrin = IntegerPolynomial.fromBinary3(M, N);
            
            // sData = OID|m|b|hTrunc
            byte[] bh = pub.toBinary(q);
            byte[] hTrunc = Arrays.copyOf(bh, pkLen/8);
            ByteBuffer sDataBuffer = ByteBuffer.allocate(oid.length + l + b.length + hTrunc.length);
            sDataBuffer.put(oid);
            sDataBuffer.put(m);
            sDataBuffer.put(b);
            sDataBuffer.put(hTrunc);
            byte[] sData = sDataBuffer.array();
            
            SparseTernaryPolynomial r = generateBlindingPoly(sData, M, params);
            IntegerPolynomial R = r.mult(pub, q);
            IntegerPolynomial R4 = R.clone();
            R4.modPositive(4);
            byte[] oR4 = R4.toBinary(4);
            IntegerPolynomial mask = MGF1(oR4, N, minCallsMask);
            mTrin.add(mask, 3);
            mTrin.mod3();
            
            if (mTrin.count(-1) < dm0)
                continue;
            if (mTrin.count(0) < dm0)
                continue;
            if (mTrin.count(1) < dm0)
                continue;
            
            R.add(mTrin, q);
            R.ensurePositive(q);
            return R.toBinary(q);
        }
    }
    
    static IntegerPolynomial encrypt(IntegerPolynomial m, SparseTernaryPolynomial r, IntegerPolynomial pubKey, EncryptionParameters params) {
        IntegerPolynomial e = r.mult(pubKey, params.q);
        e.add(m, params.q);
        e.ensurePositive(params.q);
        return e;
    }
    
    private static SparseTernaryPolynomial generateBlindingPoly(byte[] seed, byte[] M, EncryptionParameters params) throws NoSuchAlgorithmException {
        int N = params.N;
        int dr = params.dr;
        
        int[] r = new int[N];
        IndexGenerator ig = new IndexGenerator(seed, params);
        for (int coeff=-1; coeff<=1; coeff+=2) {
            int t = 0;
            while (t < dr) {
                int i = ((ig.nextIndex()%N)+N) % N;
                if (r[i] == 0) {
                    r[i] = coeff;
                    t++;
                }
            }
        }
        return new SparseTernaryPolynomial(new IntegerPolynomial(r));
    }
    
    // XXX verify this correctly implements MGF-TP-1
    private static IntegerPolynomial MGF1(byte[] input, int N, int minCallsMask) throws NoSuchAlgorithmException {
        int numBytes = (N*3+2)/2;
        int numCalls = (numBytes+63) / 64;   // calls to SHA-512
        ByteBuffer buf = ByteBuffer.allocate(numCalls*64);
        MessageDigest hashAlg = MessageDigest.getInstance("SHA-512");
        for (int counter=0; counter<numCalls; counter++) {
            ByteBuffer hashInput = ByteBuffer.allocate(input.length + 4);
            hashInput.put(input);
            hashInput.putInt(counter);
            byte[] hash = hashAlg.digest(hashInput.array());
            buf.put(hash);
        }
        byte [] output = buf.array();
        output = Arrays.copyOf(output, numBytes);
        return IntegerPolynomial.fromBinary3(buf.array(), N);
    }

    public static byte[] decrypt(byte[] data, EncryptionKeyPair kp, EncryptionParameters params) throws NoSuchAlgorithmException {
        SparseTernaryPolynomial priv_f = kp.priv.f;
        IntegerPolynomial priv_fp = kp.priv.fp;
        IntegerPolynomial pub = kp.pub.h;
        int N = params.N;
        int q = params.q;
        int db = params.db;
        int maxMsgLenBytes = params.maxMsgLenBytes;
        int dm0 = params.dm0;
        int pkLen = params.pkLen;
        int minCallsMask = params.minCallsMask;
        byte[] oid = params.oid;
        
        if (maxMsgLenBytes > 255)
            throw new RuntimeException("maxMsgLenBytes values bigger than 255 are not supported");
        
        int bLen = db / 8;
        
        IntegerPolynomial e = IntegerPolynomial.fromBinary(data, N, q);
        IntegerPolynomial ci = decrypt(e, priv_f, priv_fp, params);
        
        if (ci.count(-1) < dm0)
            throw new RuntimeException("More than dm0 coefficients equal -1");
        if (ci.count(0) < dm0)
            throw new RuntimeException("More than dm0 coefficients equal 0");
        if (ci.count(1) < dm0)
            throw new RuntimeException("More than dm0 coefficients equal 1");
        
        IntegerPolynomial cR4 = e.clone();
        cR4.sub(ci, q);
        cR4.modPositive(4);
        byte[] coR4 = cR4.toBinary(4);
        IntegerPolynomial mask = MGF1(coR4, N, minCallsMask);
        IntegerPolynomial cMTrin = ci;
        cMTrin.sub(mask, 3);
        cMTrin.mod3();
        byte[] cM = cMTrin.toBinary3();
        
        ByteBuffer buf = ByteBuffer.wrap(cM);
        byte[] cb = new byte[bLen];
        buf.get(cb);
        int cl = buf.get() & 0xFF;   // llen=1, so read one byte
        if (cl > maxMsgLenBytes)
            throw new RuntimeException("Message too long: " + cl + ">" + maxMsgLenBytes);
        byte[] cm = new byte[cl];
        buf.get(cm);
        byte[] p0 = new byte[buf.remaining()];
        buf.get(p0);
        if (!Arrays.equals(p0, new byte[p0.length]))
            throw new RuntimeException("The message is not followed by zeroes");
        
        // sData = OID|m|b|hTrunc
        byte[] bh = pub.toBinary(q);
        byte[] hTrunc = Arrays.copyOf(bh, pkLen/8);
        ByteBuffer sDataBuffer = ByteBuffer.allocate(oid.length + cl + cb.length + hTrunc.length);
        sDataBuffer.put(oid);
        sDataBuffer.put(cm);
        sDataBuffer.put(cb);
        sDataBuffer.put(hTrunc);
        byte[] sData = sDataBuffer.array();
        
        SparseTernaryPolynomial cr = generateBlindingPoly(sData, cm, params);
        IntegerPolynomial cRPrime = cr.mult(pub, q);
        if (cRPrime.equals(cr))
            throw new RuntimeException("Invalid message encoding");
       
        return cm;
    }
    
    static IntegerPolynomial decrypt(IntegerPolynomial e, SparseTernaryPolynomial priv_f, IntegerPolynomial priv_fp, EncryptionParameters params) {
        IntegerPolynomial a = priv_f.mult(e, params.q);
        a.center0(params.q);
        a.mod3();
        IntegerPolynomial c = priv_fp.mult(a, 3);
        c.center0(3);
        return c;
    }
}