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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Before;
import org.junit.Test;

public class ProductFormPolynomialTest {
    private EncryptionParameters params;
    private int N;
    private int df1;
    private int df2;
    private int df3;
    private int q;
    
    @Before
    public void setUp() {
        params = EncryptionParameters.APR2011_439_FAST;
        N = params.N;
        df1 = params.df1;
        df2 = params.df2;
        df3 = params.df3;
        q = params.q;
    }
    
    @Test
    public void testFromToBinary() {
        ProductFormPolynomial p1 = ProductFormPolynomial.generateRandom(N, df1, df2, df3, df3-1);
        byte[] bin1 = p1.toBinary();
        ProductFormPolynomial p2 = ProductFormPolynomial.fromBinary(bin1, N, df1, df2, df3, df3-1);
        byte[] bin2 = p2.toBinary();
        assertArrayEquals(bin1, bin2);
    }
    
    @Test
    public void testMult() {
        ProductFormPolynomial p1 = ProductFormPolynomial.generateRandom(N, df1, df2, df3, df3-1);
        IntegerPolynomial p2 = PolynomialGenerator.generateRandom(N, q);
        IntegerPolynomial p3 = p1.mult(p2);
        IntegerPolynomial p4 = p1.toIntegerPolynomial().mult(p2);
        assertArrayEquals(p3.coeffs, p4.coeffs);
    }
}