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

package net.sf.ntru.encrypt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.sf.ntru.encrypt.EncryptionParameters.TernaryPolynomialType;
import net.sf.ntru.exception.NtruException;
import net.sf.ntru.polynomial.DenseTernaryPolynomial;
import net.sf.ntru.polynomial.IntegerPolynomial;
import net.sf.ntru.polynomial.Polynomial;
import net.sf.ntru.polynomial.ProductFormPolynomial;
import net.sf.ntru.polynomial.SparseTernaryPolynomial;

/**
 * A NtruEncrypt private key is essentially a polynomial named <code>f</code>
 * which takes different forms depending on whether product-form polynomials are used,
 * and on <code>fastP</code><br/>
 * The inverse of <code>f</code> modulo <code>p</code> is precomputed on initialization.
 */
public class EncryptionPrivateKey {
    EncryptionParameters params;
    Polynomial t;
    IntegerPolynomial fp;

    /**
     * Constructs a new private key from a polynomial
     * @param t the polynomial which determines the key: if <code>fastFp=true</code>, <code>f=1+3t</code>; otherwise, <code>f=t</code>
     * @param fp the inverse of <code>f</code>
     * @param params the NtruEncrypt parameters to use
     */
    EncryptionPrivateKey(Polynomial t, IntegerPolynomial fp, EncryptionParameters params) {
        this.t = t;
        this.fp = fp;
        this.params = params;
    }
    
    /**
     * Converts a byte array to a polynomial <code>f</code> and constructs a new private key
     * @param b an encoded polynomial
     * @param params the NtruEncrypt parameters to use
     * @see #getEncoded()
     */
    public EncryptionPrivateKey(byte[] b, EncryptionParameters params) {
        this(new ByteArrayInputStream(b), params);
    }
    
    /**
     * Reads a polynomial <code>f</code> from an input stream and constructs a new private key
     * @param is an input stream
     * @param params the NtruEncrypt parameters to use
     * @throws NtruException if an {@link IOException} occurs
     * @see #writeTo(OutputStream)
     */
    public EncryptionPrivateKey(InputStream is, EncryptionParameters params) {
        this.params = params;
        try {
            if (params.polyType == TernaryPolynomialType.PRODUCT) {
                int N = params.N;
                int df1 = params.df1;
                int df2 = params.df2;
                int df3Ones = params.df3;
                int df3NegOnes = params.fastFp ? params.df3 : params.df3-1;
                t = ProductFormPolynomial.fromBinary(is, N, df1, df2, df3Ones, df3NegOnes);
            }
            else {
                IntegerPolynomial fInt = IntegerPolynomial.fromBinary3Tight(is, params.N);
                t = params.sparse ? new SparseTernaryPolynomial(fInt) : new DenseTernaryPolynomial(fInt);
            }
        }
        catch (IOException e) {
            throw new NtruException(e);
        }
        init();
    }
    
    /**
     * Initializes <code>fp</code> from t.
     */
    private void init() {
        if (params.fastFp) {
            fp = new IntegerPolynomial(params.N);
            fp.coeffs[0] = 1;
        }
        else
            fp = t.toIntegerPolynomial().invertF3();
    }
    
    /**
     * Converts the key to a byte array
     * @return the encoded key
     * @see #EncryptionPrivateKey(byte[], EncryptionParameters)
     */
    public byte[] getEncoded() {
        if (t instanceof ProductFormPolynomial)
            return ((ProductFormPolynomial)t).toBinary();
        else
            return t.toIntegerPolynomial().toBinary3Tight();
    }
    
    /**
     * Writes the key to an output stream
     * @param os an output stream
     * @throws IOException
     * @see #EncryptionPrivateKey(InputStream, EncryptionParameters)
     */
    public void writeTo(OutputStream os) throws IOException {
        os.write(getEncoded());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((params == null) ? 0 : params.hashCode());
        result = prime * result + ((t == null) ? 0 : t.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof EncryptionPrivateKey))
            return false;
        EncryptionPrivateKey other = (EncryptionPrivateKey) obj;
        if (params == null) {
            if (other.params != null)
                return false;
        } else if (!params.equals(other.params))
            return false;
        if (t == null) {
            if (other.t != null)
                return false;
        } else if (!t.equals(other.t))
            return false;
        return true;
    }
}