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

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import net.sf.ntru.exception.NtruException;

/**
 * A polynomial with {@link BigDecimal} coefficients.
 * Some methods (like <code>add</code>) change the polynomial, others (like <code>mult</code>) do
 * not but return the result as a new polynomial.
 */
public class BigDecimalPolynomial {
    private static final BigDecimal ONE_HALF = new BigDecimal("0.5");
    
    BigDecimal[] coeffs;
    
    /**
     * Constructs a new polynomial with <code>N</code> coefficients initialized to 0.
     * @param N the number of coefficients
     */
    BigDecimalPolynomial(int N) {
        coeffs = new BigDecimal[N];
        for (int i=0; i<N; i++)
            coeffs[i] = ZERO;
    }
    
    /**
     * Constructs a new polynomial with a given set of coefficients.
     * @param coeffs the coefficients
     */
    BigDecimalPolynomial(BigDecimal[] coeffs) {
        this.coeffs = coeffs;
    }
    
    /**
     * Constructs a <code>BigDecimalPolynomial</code> from a <code>BigIntPolynomial</code>. The two polynomials are independent of each other.
     * @param p the original polynomial
     */
    BigDecimalPolynomial(BigIntPolynomial p) {
        int N = p.coeffs.length;
        coeffs = new BigDecimal[N];
        for (int i=0; i<N; i++)
            coeffs[i] = new BigDecimal(p.coeffs[i]);
    }
    
    /**
     * Divides all coefficients by 2.
     */
    public void halve() {
        for (int i=0; i<coeffs.length; i++)
            coeffs[i] = coeffs[i].multiply(ONE_HALF);
    }
    
    /**
     * Multiplies the polynomial by another. Does not change this polynomial
     * but returns the result as a new polynomial.
     * @param poly2 the polynomial to multiply by
     * @return a new polynomial
     */
    public BigDecimalPolynomial mult(BigIntPolynomial poly2) {
        return mult(new BigDecimalPolynomial(poly2));
    }
    
    /**
     * Multiplies the polynomial by another, taking the indices mod N. Does not
     * change this polynomial but returns the result as a new polynomial.
     * @param poly2 the polynomial to multiply by
     * @return a new polynomial
     */
    BigDecimalPolynomial mult(BigDecimalPolynomial poly2) {
        int N = coeffs.length;
        if (poly2.coeffs.length != N)
            throw new NtruException("Number of coefficients must be the same");
        
        BigDecimalPolynomial c = multRecursive(poly2);
        
        if (c.coeffs.length > N) {
            for (int k=N; k<c.coeffs.length; k++)
                c.coeffs[k-N] = c.coeffs[k-N].add(c.coeffs[k]);
            c.coeffs = Arrays.copyOf(c.coeffs, N);
        }
        return c;
    }
    
    /** Karazuba multiplication */
    private BigDecimalPolynomial multRecursive(BigDecimalPolynomial poly2) {
        BigDecimal[] a = coeffs;
        BigDecimal[] b = poly2.coeffs;
        
        int n = poly2.coeffs.length;
        if (n <= 1) {
            BigDecimal[] c = coeffs.clone();
            for (int i=0; i<coeffs.length; i++)
                c[i] = c[i].multiply(poly2.coeffs[0]);
            return new BigDecimalPolynomial(c);
        }
        else {
            int n1 = n / 2;
            
            BigDecimalPolynomial a1 = new BigDecimalPolynomial(Arrays.copyOf(a, n1));
            BigDecimalPolynomial a2 = new BigDecimalPolynomial(Arrays.copyOfRange(a, n1, n));
            BigDecimalPolynomial b1 = new BigDecimalPolynomial(Arrays.copyOf(b, n1));
            BigDecimalPolynomial b2 = new BigDecimalPolynomial(Arrays.copyOfRange(b, n1, n));
            
            BigDecimalPolynomial A = a1.clone();
            A.add(a2);
            BigDecimalPolynomial B = b1.clone();
            B.add(b2);
            
            BigDecimalPolynomial c1 = a1.multRecursive(b1);
            BigDecimalPolynomial c2 = a2.multRecursive(b2);
            BigDecimalPolynomial c3 = A.multRecursive(B);
            c3.sub(c1);
            c3.sub(c2);
            
            BigDecimalPolynomial c = new BigDecimalPolynomial(2*n-1);
            for (int i=0; i<c1.coeffs.length; i++)
                c.coeffs[i] = c1.coeffs[i];
            for (int i=0; i<c3.coeffs.length; i++)
                c.coeffs[n1+i] = c.coeffs[n1+i].add(c3.coeffs[i]);
            for (int i=0; i<c2.coeffs.length; i++)
                c.coeffs[2*n1+i] = c.coeffs[2*n1+i].add(c2.coeffs[i]);
            return c;
        }
    }
    
    /**
     * Adds another polynomial which can have a different number of coefficients.
     * @param b another polynomial
     */
    public void add(BigDecimalPolynomial b) {
      if (b.coeffs.length > coeffs.length) {
          int N = coeffs.length;
          coeffs = Arrays.copyOf(coeffs, b.coeffs.length);
          for (int i=N; i<coeffs.length; i++)
              coeffs[i] = ZERO;
      }
      for (int i=0; i<b.coeffs.length; i++)
          coeffs[i] = coeffs[i].add(b.coeffs[i]);
    }

    /**
     * Subtracts another polynomial which can have a different number of coefficients.
     * @param b
     */
    void sub(BigDecimalPolynomial b) {
        if (b.coeffs.length > coeffs.length) {
            int N = coeffs.length;
            coeffs = Arrays.copyOf(coeffs, b.coeffs.length);
            for (int i=N; i<coeffs.length; i++)
                coeffs[i] = ZERO;
        }
        for (int i=0; i<b.coeffs.length; i++)
            coeffs[i] = coeffs[i].subtract(b.coeffs[i]);
    }
    
    /**
     * Rounds all coefficients to the nearest integer.
     * @return a new polynomial with <code>BigInteger</code> coefficients
     */
    public BigIntPolynomial round() {
        int N = coeffs.length;
        BigIntPolynomial p = new BigIntPolynomial(N);
        for (int i=0; i<N; i++)
            p.coeffs[i] = coeffs[i].setScale(0, RoundingMode.HALF_EVEN).toBigInteger();
        return p;
    }
    
    /**
     * Makes a copy of the polynomial that is independent of the original.
     */
    @Override
    public BigDecimalPolynomial clone() {
        return new BigDecimalPolynomial(coeffs.clone());
    }
}