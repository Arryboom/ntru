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

package net.sf.ntru.polynomial;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import net.sf.ntru.polynomial.BigIntPolynomial;
import net.sf.ntru.polynomial.IntegerPolynomial;

import org.junit.Test;

public class BigIntPolynomialTest {
    
    @Test
    public void testMult() {
        BigIntPolynomial a = new BigIntPolynomial(new IntegerPolynomial(new int[] {4, -1, 9, 2, 1, -5, 12, -7, 0, -9, 5}));
        BigIntPolynomial b = new BigIntPolynomial(new IntegerPolynomial(new int[] {-6, 0, 0, 13, 3, -2, -4, 10, 11, 2, -1}));
        BigIntPolynomial expected = new BigIntPolynomial(new IntegerPolynomial(new int[] {2, -189, 77, 124, -29, 0, -75, 124, -49, 267, 34}));
        assertArrayEquals(expected.coeffs, a.multSmall(b).coeffs);
        assertArrayEquals(expected.coeffs, a.multBig(b).coeffs);
        
        Random rng = new Random();
        for (int i=0; i<3; i++) {
            int[] aArr = new int[rng.nextInt(100)];
            int[] bArr = new int[aArr.length];
            for (int j=0; j<aArr.length; j++) {
                aArr[j] = rng.nextInt(1000) - 500;
                bArr[j] = rng.nextInt(1000) - 500;
            }
            a = new BigIntPolynomial(new IntegerPolynomial(aArr));
            b = new BigIntPolynomial(new IntegerPolynomial(bArr));
            
            assertArrayEquals(a.multSmall(b).coeffs, a.multBig(b).coeffs);
        }
    }
}