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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

public class ArrayHashCodeTest {

  @Test
  void testHashCodeReference() {
    testHashCode(ArrayHashCode::hashCodeReference);
  }

  @Test
  void testHashCodeSWAR() {
    testHashCode(ArrayHashCode::hashCodeSWAR);
  }

  @Test
  void testHashCodeSIMD() {
    testHashCode(ArrayHashCode::hashCodeSIMD);
  }

  @Test
  void testHashCodeUnroll8() {
    testHashCode(ArrayHashCode::hashCodeUnroll8);
  }

  void testHashCode(ToIntFunction<byte[]> hashCodeFunction) {

    // test null
    assertThat(hashCodeFunction.applyAsInt(null)).isEqualTo(Arrays.hashCode((byte[]) null));

    // test byte arrays with all entries equal to zero except one set equal to 1
    for (int len = 0; len < 1000; ++len) {
      byte[] data = new byte[len];
      for (int nonZeroPos = 0; nonZeroPos < len; ++nonZeroPos) {
        data[nonZeroPos] = 1;
        assertThat(hashCodeFunction.applyAsInt(data))
            .describedAs(() -> "len = " + data.length + ", " + Arrays.toString(data))
            .isEqualTo(Arrays.hashCode(data));
        data[nonZeroPos] = 0;
      }
    }

    // test random byte arrays
    SplittableRandom random = new SplittableRandom(0xe29c4a2d0bf3098dL);
    for (int len = 0; len < 1000; ++len) {
      byte[] data = new byte[len];
      for (int i = 0; i < 200; ++i) {
        random.nextBytes(data);
        assertThat(hashCodeFunction.applyAsInt(data))
            .describedAs(() -> "len = " + data.length + ", " + Arrays.toString(data))
            .isEqualTo(Arrays.hashCode(data));
      }
    }
  }

  @Test
  void testInverseMultiplier() {
    int inv31 = ArrayHashCode.I;
    assertThat(inv31 * 31).isOne();
    assertThat((inv31 * inv31) * (31 * 31)).isOne();
    SplittableRandom random = new SplittableRandom(0xf5d3bf1edd131774L);
    for (int l = 0; l < 10000; ++l) {
      int i = random.nextInt();
      assertThat((i * 31) * inv31).isEqualTo(i);
    }
  }
}
