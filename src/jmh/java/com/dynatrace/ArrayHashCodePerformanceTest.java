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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.openjdk.jmh.annotations.*;

public class ArrayHashCodePerformanceTest {

  private static final int MAX_LEN = 100000;
  private static final double FACTOR = 0.95;

  @State(Scope.Thread)
  public static class TestState {

    private static String format(String[] s) {
      return Arrays.stream(s).collect(Collectors.joining("\", \"", "[\"", "\"]"));
    }

    private static void verifyLengthParams() {

      List<Integer> lengths = new ArrayList<>();
      for (int length = MAX_LEN;
          length > 0;
          length = Math.min(length - 1, (int) (length * FACTOR))) {
        lengths.add(length);
      }
      String[] expected =
          lengths.stream().sorted().map(i -> i.toString()).toArray(i -> new String[i]);
      try {
        String[] actual =
            TestState.class.getDeclaredField("maxLength").getAnnotation(Param.class).value();
        if (!Arrays.equals(expected, actual, String::compareTo)) {
          throw new RuntimeException("expected " + format(expected) + " but was " + format(actual));
        }

      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }

    @Param({
      "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
      "18", "19", "20", "22", "24", "26", "28", "30", "32", "34", "36", "38", "40", "43", "46",
      "49", "52", "55", "58", "62", "66", "70", "74", "78", "83", "88", "93", "98", "104", "110",
      "116", "123", "130", "137", "145", "153", "162", "171", "181", "191", "202", "213", "225",
      "237", "250", "264", "278", "293", "309", "326", "344", "363", "383", "404", "426", "449",
      "473", "498", "525", "553", "583", "614", "647", "682", "718", "756", "796", "838", "883",
      "930", "979", "1031", "1086", "1144", "1205", "1269", "1336", "1407", "1482", "1560", "1643",
      "1730", "1822", "1918", "2019", "2126", "2238", "2356", "2480", "2611", "2749", "2894",
      "3047", "3208", "3377", "3555", "3743", "3941", "4149", "4368", "4598", "4841", "5096",
      "5365", "5648", "5946", "6259", "6589", "6936", "7302", "7687", "8092", "8518", "8967",
      "9439", "9936", "10459", "11010", "11590", "12200", "12843", "13519", "14231", "14981",
      "15770", "16600", "17474", "18394", "19363", "20383", "21456", "22586", "23775", "25027",
      "26345", "27732", "29192", "30729", "32347", "34050", "35843", "37730", "39716", "41807",
      "44008", "46325", "48764", "51331", "54033", "56877", "59871", "63023", "66340", "69832",
      "73508", "77377", "81450", "85737", "90250", "95000", "100000"
    })
    int maxLength;

    @Param({"10000"})
    int numExamples;

    byte[][] data;

    @Setup
    public void init() {
      verifyLengthParams();
      SplittableRandom random = new SplittableRandom(0xcc0c8504d74321f5L);
      data = new byte[numExamples][];
      for (int i = 0; i < numExamples; ++i) {
        byte[] b = new byte[random.nextInt(maxLength + 1)];
        random.nextBytes(b);
        data[i] = b;
      }
    }
  }

  public int testHashCode(ToIntFunction<byte[]> hashCodeFunction, TestState testState) {
    int sum = 0;
    byte[][] data = testState.data;
    for (int i = 0; i < data.length; ++i) {
      sum += hashCodeFunction.applyAsInt(data[i]);
    }
    return sum;
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public int testHashCodeUnroll8(TestState testState) {
    return testHashCode(ArrayHashCode::hashCodeUnroll8, testState);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public int testHashCodeSIMD(TestState testState) {
    return testHashCode(ArrayHashCode::hashCodeSIMD, testState);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public int testHashCodeJava(TestState testState) {
    return testHashCode(Arrays::hashCode, testState);
  }

  @Benchmark
  @Fork(
      jvmArgsAppend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:DisableIntrinsic=_vectorizedHashCode"
      })
  @BenchmarkMode(Mode.AverageTime)
  public int testHashCodeJavaWithoutIntrinsic(TestState testState) {
    return testHashCode(Arrays::hashCode, testState);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public int testHashCodeSWAR(TestState testState) {
    return testHashCode(ArrayHashCode::hashCodeSWAR, testState);
  }
}
