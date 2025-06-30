/*
 * Copyright 2025 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dynatrace;

import static jdk.incubator.vector.VectorOperators.*;
import static jdk.incubator.vector.VectorOperators.XOR;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.stream.IntStream;
import jdk.incubator.vector.*;

/** Utility class with alternative implementations of {@link java.util.Arrays#hashCode(byte[])}. */
public final class ArrayHashCode {

  private ArrayHashCode() {}

  static final VarHandle VAR_HANDLE =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

  static long getLong(byte[] b, int k) {
    return (long) VAR_HANDLE.get(b, k);
  }

  /**
   * Computes the hash code of a given byte array.
   *
   * <p>This implementation corresponds to the implementation of {@link
   * java.util.Arrays#hashCode(byte[])} up to OpenJDK 17.
   *
   * @param b the byte array
   * @return the hash value
   */
  static int hashCodeReference(byte[] b) {
    if (b == null) return 0;
    int h = 1;
    for (byte q : b) {
      h = 31 * h + q;
    }
    return h;
  }

  /**
   * Computes the hash code of a given byte array by unrolling 8 iterations of the loop in {@link
   * #hashCodeReference(byte[])}.
   *
   * <p>This method is equivalent to {@link java.util.Arrays#hashCode(byte[])}.
   *
   * @see <a
   *     href="https://github.com/lemire/microbenchmarks/blob/575062f825ed294c44592a4880f4ab68e27a7505/src/main/java/me/lemire/hashing/InterleavedHash.java#L122">this
   *     code</a> and <a
   *     href="https://lemire.me/blog/2015/10/22/faster-hashing-without-effort/">Lemire's blog
   *     post</a>
   * @param b the byte array
   * @return the hash value
   */
  static int hashCodeUnroll8(byte[] b) {
    if (b == null) return 0;
    int len = b.length;
    int h = 1;
    int k = 0;
    for (; k + 8 <= len; k += 8) {
      h =
          31 * 31 * 31 * 31 * 31 * 31 * 31 * 31 * h
              + 31 * 31 * 31 * 31 * 31 * 31 * 31 * b[k]
              + 31 * 31 * 31 * 31 * 31 * 31 * b[k + 1]
              + 31 * 31 * 31 * 31 * 31 * b[k + 2]
              + 31 * 31 * 31 * 31 * b[k + 3]
              + 31 * 31 * 31 * b[k + 4]
              + 31 * 31 * b[k + 5]
              + 31 * b[k + 6]
              + b[k + 7];
    }
    return finalize(h, b, k);
  }

  /** 31^2 */
  static final int P2 = 31 * 31;

  /** 31^4 */
  static final int P4 = P2 * P2;

  /** 31^8 */
  static final int P8 = P4 * P4;

  /** -128 * (31^7 + 31^6 + 31^5 + 31^4 + 31^3 + 31^2 + 31 + 1) */
  static final int U = -128 * (1 + 31) * (1 + P2) * (1 + P4);

  /**
   * Computes the hash code of a given byte array based on SWAR (SIMD within a register).
   *
   * <p>This method is equivalent to {@link java.util.Arrays#hashCode(byte[])}.
   *
   * <p>This implementation is optimized for little-endian byte order, because {@link
   * #getLong(byte[], int)} maps 8 subsequent bytes to a {@code long} using little-endian order.
   * However, it would be possible to implement a big-endian version using the same SWAR techniques
   * without the need of byte order reversal.
   *
   * @param b the byte array
   * @return the hash value
   */
  static int hashCodeSWAR(byte[] b) {
    if (b == null) return 0;
    if (b.length == 0) return 1;
    if (b.length == 1) return 31 + b[0];
    int h = 1;
    int k = 0;
    for (; k <= b.length - 8; k += 8) {
      long x = getLong(b, k) ^ 0x8080808080808080L;
      x = 31 * (x & 0x00FF00FF00FF00FFL) + ((x >>> 8) & 0x00FF00FF00FF00FFL);
      x = P2 * (x & 0x0000FFFF0000FFFFL) + ((x >>> 16) & 0x0000FFFF0000FFFFL);
      h = P8 * h + P4 * (int) x + (int) (x >>> 32) + U;
    }
    return finalize(h, b, k);
  }

  static int finalize(int h, byte[] b, int k) {
    if (k < b.length) {
      h = 31 * h + b[k++];
      if (k < b.length) {
        h = 31 * h + b[k++];
        if (k < b.length) {
          h = 31 * h + b[k++];
          if (k < b.length) {
            h = 31 * h + b[k++];
            if (k < b.length) {
              h = 31 * h + b[k++];
              if (k < b.length) {
                h = 31 * h + b[k++];
                if (k < b.length) h = 31 * h + b[k];
              }
            }
          }
        }
      }
    }
    return h;
  }

  /** SIMD width in bytes */
  static final int L = ByteVector.SPECIES_PREFERRED.length();

  /**
   * Creates an int[] array of length LEN+1 with values {31^0, 31^1, 31^2, 31^3, ..., 31^L}.
   *
   * @return the int[] array
   */
  static int[] calculatePowers() {
    int[] result = new int[L + 1];
    result[0] = 1;
    for (int i = 1; i <= L; ++i) {
      result[i] = result[i - 1] * 31;
    }
    return result;
  }

  /** {31^0, 31^1, 31^2, 31^3, ..., 31^L} */
  static final int[] P = calculatePowers();

  /** -98 * (31^3 + 31^2 + 31 + 1) */
  static final int V = -98 * (1 + 31) * (1 + P2);

  /** Multiplicative inverse of 31. */
  static final int I = 0xbdef7bdf;

  /**
   * Creates an int[] array of length LEN+1 with values {I^L, I^(L-1), ..., I^2, I^1, I^0}.
   *
   * @return the int[] array
   */
  static int[] calculateFactors() {
    int[] factors = new int[L + 1];
    factors[L] = 1;
    for (int i = L; i > 0; --i) {
      factors[i - 1] = factors[i] * I;
    }
    return factors;
  }

  /** {I^L, I^(L-1), ..., I^2, I^1, I^0} where {@link #I} is the multiplicative inverse of 31. */
  static final int[] F = calculateFactors();

  /** {31^(LEN-4), 31^(LEN-8), ..., 31^8, 31^4, 31^0} */
  static final IntVector W =
      IntVector.fromArray(
          IntVector.SPECIES_PREFERRED,
          IntStream.range(0, IntVector.SPECIES_PREFERRED.length())
              .map(i -> P[L - (1 + i) * 4])
              .toArray(),
          0);

  /** 31^LEN */
  static final int PL = P[L];

  /**
   * Computes the hash code of a given byte array based on SIMD using the Vector API.
   *
   * <p>This method is equivalent to {@link java.util.Arrays#hashCode(byte[])}.
   *
   * @param b the byte array
   * @return the hash value
   */
  static int hashCodeSIMD(byte[] b) {
    if (b == null) return 0;
    if (b.length == 0) return 1;
    if (b.length == 1) return 31 + b[0];
    var a = IntVector.zero(IntVector.SPECIES_PREFERRED);
    int remaining = b.length;
    int k = 0;
    while (remaining > L) {
      var s =
          ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, b, k)
              .lanewise(XOR, (byte) 0x80)
              .reinterpretAsShorts();
      var i = s.and((short) 0xFF).mul((short) 31).add(s.lanewise(LSHR, 8)).reinterpretAsInts();
      a = a.add(i.and(0xFFFF).mul(P2).add(i.lanewise(LSHR, 16)));
      a = a.add(V);
      a = a.mul(PL);
      k += L;
      remaining -= L;
    }
    return finalizeSIMD(a, b, k, remaining);
  }

  static int finalizeSIMD(IntVector a, byte[] b, int k, int remaining) {
    var s =
        ByteVector.fromArray(
                ByteVector.SPECIES_PREFERRED,
                b,
                k,
                ByteVector.SPECIES_PREFERRED.indexInRange(0, remaining))
            .lanewise(XOR, (byte) 0x80)
            .reinterpretAsShorts();
    var i = s.and((short) 0xFF).mul((short) 31).add(s.lanewise(LSHR, 8)).reinterpretAsInts();
    a = a.add(i.and(0xFFFF).mul(P2).add(i.lanewise(LSHR, 16)));
    a = a.add(V);
    return (1 + a.mul(W).reduceLanes(ADD)) * F[remaining];
  }
}
