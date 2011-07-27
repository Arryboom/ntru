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
 * Encrypts, decrypts data and generates key pairs.<br/>
 * The parameter p is hardcoded to 3.
 */
public class NtruEncrypt {
    private EncryptionParameters params;
    
    /**
     * Constructs a new instance with a set of encryption parameters.
     * @param params encryption parameters
     */
    public NtruEncrypt(EncryptionParameters params) {
        this.params = params;
    }
    
    /**
     * Generates a new encryption key pair.
     * @return a key pair
     */
    public EncryptionKeyPair generateKeyPair() {
        int N = params.N;
        int q = params.q;
        int df = params.df;
        int dg = params.dg;
        boolean sparse = params.sparse;
        boolean fastFp = params.fastFp;
        
        DenseTernaryPolynomial t = null;
        IntegerPolynomial fq = null;
        IntegerPolynomial fp = null;
        
        // choose a random f that is invertible mod 3 and q
        while (true) {
            IntegerPolynomial f;
            
            // choose random t, calculate f and fp
            if (fastFp) {
                // if fastFp=true, f is always invertible mod 3
                t = DenseTernaryPolynomial.generateRandom(N, df, df);
                f = t.clone();
                f.mult(3);
                f.coeffs[0] += 1;
            }
            else {
                f = t = DenseTernaryPolynomial.generateRandom(N, df, df-1);
                fp = f.invertF3();
                if (fp == null)
                    continue;
            }
            
            fq = f.invertFq(q);
            if (fq == null)
                continue;
            break;
        }
        
        // if fastFp=true, fp=1
        if (fastFp) {
            fp = new IntegerPolynomial(N);
            fp.coeffs[0] = 1;
        }
        
        TernaryPolynomial g = Util.generateRandomTernary(N, dg, dg, sparse);
        IntegerPolynomial h = g.mult(fq, q);
        h.mult3(q);
        h.ensurePositive(q);
        g.clear();
        fq.clear();
        
        EncryptionPrivateKey priv = new EncryptionPrivateKey(t, fp, params);
        EncryptionPublicKey pub = new EncryptionPublicKey(h, params);
        return new EncryptionKeyPair(priv, pub);
    }
    
    /**
     * Encrypts a message.
     * @param m The message to encrypt
     * @param pubKey the public key to encrypt the message with
     * @return
     * @throws NtruException if SHA-512 is not available, the message is longer than <code>maxLenBytes</code>, or <code>maxLenBytes</code> is greater than 255
     */
    public byte[] encrypt(byte[] m, EncryptionPublicKey pubKey) {
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
            throw new NtruException("llen values bigger than 1 are not supported");
        if (l > maxLenBytes)
            throw new NtruException("Message too long: " + l + ">" + maxLenBytes);
        
        SecureRandom rng = new SecureRandom();
        while (true) {
            // M = b|octL|m|p0
            byte[] b = new byte[db/8];
            rng.nextBytes(b);
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
            
            TernaryPolynomial r = generateBlindingPoly(sData, M);
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
    
    IntegerPolynomial encrypt(IntegerPolynomial m, TernaryPolynomial r, IntegerPolynomial pubKey) {
        IntegerPolynomial e = r.mult(pubKey, params.q);
        e.add(m, params.q);
        e.ensurePositive(params.q);
        return e;
    }
    
    private TernaryPolynomial generateBlindingPoly(byte[] seed, byte[] M) {
        int N = params.N;
        int dr = params.dr;
        boolean sparse = params.sparse;
        
        int[] r = new int[N];
        IndexGenerator ig = new IndexGenerator(seed, params);
        for (int coeff=-1; coeff<=1; coeff+=2) {
            int t = 0;
            while (t < dr) {
                int i = ig.nextIndex();
                if (r[i] == 0) {
                    r[i] = coeff;
                    t++;
                }
            }
        }
        
        if (sparse)
            return new SparseTernaryPolynomial(r);
        else
            return new DenseTernaryPolynomial(r);
    }
    
    /**
     * An implementation of MGF-TP-1 from P1363.1 section 8.4.1.1.
     * @param input
     * @param N
     * @param minCallsMask
     * @return
     * @throws NtruException if SHA-512 is not available
     */
    private IntegerPolynomial MGF1(byte[] input, int N, int minCallsMask) {
        int numBytes = (N*3+2)/2;
        int numCalls = (numBytes+63) / 64;   // calls to SHA-512
        ByteBuffer buf = ByteBuffer.allocate(numCalls*64);
        MessageDigest hashAlg;
        try {
            hashAlg = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new NtruException(e);
        }
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

    /**
     * Decrypts a message.
     * @param m The message to decrypt
     * @param kp a key pair that contains the public key the message was encrypted with, and the corresponding private key
     * @return
     * @throws NtruException if SHA-512 is not available, the encrypted data is invalid, or <code>maxLenBytes</code> is greater than 255
     */
    public byte[] decrypt(byte[] data, EncryptionKeyPair kp) {
        TernaryPolynomial priv_t = kp.priv.t;
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
            throw new NtruException("maxMsgLenBytes values bigger than 255 are not supported");
        
        int bLen = db / 8;
        
        IntegerPolynomial e = IntegerPolynomial.fromBinary(data, N, q);
        IntegerPolynomial ci = decrypt(e, priv_t, priv_fp);
        
        if (ci.count(-1) < dm0)
            throw new NtruException("More than dm0 coefficients equal -1");
        if (ci.count(0) < dm0)
            throw new NtruException("More than dm0 coefficients equal 0");
        if (ci.count(1) < dm0)
            throw new NtruException("More than dm0 coefficients equal 1");
        
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
            throw new NtruException("Message too long: " + cl + ">" + maxMsgLenBytes);
        byte[] cm = new byte[cl];
        buf.get(cm);
        byte[] p0 = new byte[buf.remaining()];
        buf.get(p0);
        if (!Arrays.equals(p0, new byte[p0.length]))
            throw new NtruException("The message is not followed by zeroes");
        
        // sData = OID|m|b|hTrunc
        byte[] bh = pub.toBinary(q);
        byte[] hTrunc = Arrays.copyOf(bh, pkLen/8);
        ByteBuffer sDataBuffer = ByteBuffer.allocate(oid.length + cl + cb.length + hTrunc.length);
        sDataBuffer.put(oid);
        sDataBuffer.put(cm);
        sDataBuffer.put(cb);
        sDataBuffer.put(hTrunc);
        byte[] sData = sDataBuffer.array();
        
        TernaryPolynomial cr = generateBlindingPoly(sData, cm);
        IntegerPolynomial cRPrime = cr.mult(pub, q);
        if (cRPrime.equals(cr))
            throw new NtruException("Invalid message encoding");
       
        return cm;
    }
    
    /**
     * 
     * @param e
     * @param priv_t a polynomial such that if <code>fastFp=true</code>, <code>f=1+3*priv_t</code>; otherwise, <code>f=priv_t</code>
     * @param priv_fp
     * @return
     */
    IntegerPolynomial decrypt(IntegerPolynomial e, TernaryPolynomial priv_t, IntegerPolynomial priv_fp) {
        IntegerPolynomial a;
        if (params.fastFp) {
            a = priv_t.mult(e, params.q);
            a.mult(3);
            a.add(e);
        }
        else
            a = priv_t.mult(e, params.q);
        a.center0(params.q);
        a.mod3();
        
        IntegerPolynomial c = params.fastFp ? a : new DenseTernaryPolynomial(a).mult(priv_fp, 3);
        c.center0(3);
        return c;
    }
}