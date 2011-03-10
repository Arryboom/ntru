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

public class SignatureParameters {
    // from http://grouper.ieee.org/groups/1363/WorkingGroup/presentations/NTRUSignParams-1363-0411.ps
    public static final SignatureParameters T157 = new SignatureParameters(157, 256, 29, 1, BasisType.TRANSPOSE, 0.38407, 150.02);   // gives less than 80 bits of security
    public static final SignatureParameters T349 = new SignatureParameters(349, 512, 75, 1, BasisType.TRANSPOSE, 0.18543, 368.62);   // gives less than 256 bits of security
    
    public enum BasisType {STANDARD, TRANSPOSE};
    
    int N, q, d, B;
    double betaSq, normBoundSq;
    BasisType basisType;
    boolean primeCheck = false;   // set to true if N and 2N+1 are prime
    int bitsF = 6;   // max #bits needed to encode one coefficient of the polynomial F
    
    public SignatureParameters(int N, int q, int d, int B, BasisType basisType, double beta, double normBound) {
        this.N = N;
        this.q = q;
        this.d = d;
        this.B = B;
        this.basisType = basisType;
        this.betaSq = beta * beta;
        this.normBoundSq = normBound * normBound;
    }
}