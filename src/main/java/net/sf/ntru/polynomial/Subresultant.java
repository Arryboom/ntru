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

import java.math.BigInteger;

import net.sf.ntru.euclid.BigIntEuclidean;

/** A resultant modulo a <code>BigInteger</code> */
public class Subresultant extends Resultant {
    BigInteger modulus;
    
    Subresultant(BigIntPolynomial rho, BigInteger res, BigInteger modulus) {
        super(rho, res);
        this.modulus = modulus;
    }
    
    /**
     * Calculates a resultant modulo <code>m1*m2</code> from
     * two resultants modulo <code>m1</code> and <code>m2</code>.
     * @param subres1
     * @param subres2
     * @return a resultant modulo <code>subres1.modulus * subres2.modulus</code>
     */
    static Subresultant combine(Subresultant subres1, Subresultant subres2) {
        BigInteger mod1 = subres1.modulus;
        BigInteger mod2 = subres2.modulus;
        BigInteger prod = mod1.multiply(mod2);
        BigIntEuclidean er = BigIntEuclidean.calculate(mod2, mod1);
        
        BigInteger res = subres1.res.multiply(er.x.multiply(mod2));
        BigInteger res2 = subres2.res.multiply(er.y.multiply(mod1));
        res = res.add(res2).mod(prod);
        
        BigIntPolynomial rho1 = subres1.rho.clone();
        rho1.mult(er.x.multiply(mod2));
        BigIntPolynomial rho2 = subres2.rho.clone();
        rho2.mult(er.y.multiply(mod1));
        rho1.add(rho2);
        rho1.mod(prod);

        return new Subresultant(rho1, res, prod);
    }
}