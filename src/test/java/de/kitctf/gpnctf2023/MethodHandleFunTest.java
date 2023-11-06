package de.kitctf.gpnctf2023;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MethodHandleFunTest {

  @Property
  boolean shiftL1(@ForAll int input) throws Throwable {
    return input << 1 == (int) MethodHandleFun.shiftL1().invoke(input);
  }

  @Property
  boolean isNotZero(@ForAll int input) throws Throwable {
    return input != 0 == (boolean) MethodHandleFun.isNotZero().invoke(input);
  }

  @Property
  boolean isZero(@ForAll int input) throws Throwable {
    return input == 0 == (boolean) MethodHandleFun.isZero().invoke(input);
  }

  @Property
  boolean add(@ForAll int a, @ForAll int b) throws Throwable {
    return a + b == (int) MethodHandleFun.add().invoke(a, b);
  }

  @Property
  boolean subtractInt(@ForAll int a, @ForAll int b) throws Throwable {
    return a - b == (int) MethodHandleFun.subtractInt().invoke(a, b);
  }

  @Property(tries = 250)
  boolean popFirst(@ForAll char[] a) throws Throwable {
    if (a.length == 0) {
      return true;
    }

    char[] manualResult = Arrays.copyOfRange(a, 1, a.length);
    char[] popped = (char[]) MethodHandleFun.popFirst().invoke(a);
    return Arrays.equals(manualResult, popped);
  }

  @Property
  void reverse(@ForAll char[] input) throws Throwable {
    char[] expected = reverseArray(input);
    char[] actual = (char[]) MethodHandleFun.reverse().invoke(input);

    for (int i = 0; i < input.length; i++) {
      if (i == 11 && input.length != (11 * 2 + 1)) {
        assertThat(actual[i]).isEqualTo((char) (expected[i] ^ 11));
      } else {
        assertThat(actual[i]).isEqualTo(expected[i]);
      }
    }
  }

  @Property
  boolean append(@ForAll char[] arr, @ForAll char next) throws Throwable {
    char[] reference = Arrays.copyOfRange(arr, 0, arr.length + 1);
    reference[arr.length] = next;

    return Arrays.equals(reference, (char[]) MethodHandleFun.append().invoke(arr, next));
  }

  @Property
  boolean xor3(@ForAll int a, @ForAll int b) throws Throwable {
    return (a ^ b) == (int) MethodHandleFun.xor3().invoke(a, b);
  }

  @Property
  boolean or(@ForAll int a, @ForAll int b) throws Throwable {
    return (a | b) == (int) MethodHandleFun.or().invoke(a, b);
  }

  @Property
  boolean and(@ForAll int a, @ForAll int b) throws Throwable {
    return (a & b) == (int) MethodHandleFun.and().invoke(a, b);
  }

  @Property
  boolean not(@ForAll int input) throws Throwable {
    return ~input == (int) MethodHandleFun.not().invoke(input);
  }

  @Property
  boolean shiftR1(@ForAll int input) throws Throwable {
    return input >>> 1 == (int) MethodHandleFun.shiftR1().invoke(input);
  }

  @Property
  boolean rotateR1(@ForAll int input) throws Throwable {
    return rotateRightOnce(input) == (int) MethodHandleFun.rotateRight().invoke(input);
  }

  private static int rotateRightOnce(int input) {
    int a = input;
    int l = a & 1;
    a = a >>> 1;
    if (l != 0) {
      a = a | -2147483648;
    }
    return a;
  }

  @Property
  boolean rotateRn(@ForAll int input, @ForAll("bitIndex") int amount) throws Throwable {
    int a = input;
    for (int i = 0; i < amount; i++) {
      a = rotateRightOnce(a);
    }
    return a == (int) MethodHandleFun.rotateRightN(amount).invoke(input);
  }

  @Provide
  Arbitrary<Integer> bitIndex() {
    return Arbitraries.integers().between(0, 32);
  }

  @Property
  void xorInput(@ForAll char[] input) throws Throwable {
    // We can not rotate by the first element if there is none
    if (input.length == 0) {
      return;
    }

    char[] expected = doRotateInput(input);
    char[] actual = (char[]) MethodHandleFun.xorInput().invoke(input);

    assertThat(actual).isEqualTo(expected);
  }

  private static char[] doRotateInput(char[] input) {
    char[] expected = new char[input.length];
    for (int i = 0; i < input.length; i++) {
      expected[i] = (char) (input[0] ^ input[i]);
    }
    return expected;
  }

  @Property(tries = 250)
  void encrypt(@ForAll("encryptingStrings") String input) throws Throwable {
    char[] byHand = encryptHand(input);
    char[] result = (char[]) MethodHandleFun.encrypt().invoke(input.toCharArray());

    assertThat(result).isEqualTo(byHand);
  }

  @Provide
  Arbitrary<String> encryptingStrings() {
    return Arbitraries.strings().ofMinLength(1).ofMaxLength(25);
  }

  private static char[] encryptHand(String message) {
    char[] msgArr = message.toCharArray();
    msgArr = doRotateInput(msgArr);
    List<Character> result = new ArrayList<>();
    for (int i = 0, end = msgArr.length; i < end; i++) {
      result.add((char) (msgArr[0] ^ msgArr[msgArr.length - 1] ^ 3));
      char[] reversed = reverseArray(msgArr);
      if (msgArr.length > 11 && msgArr.length != (11 * 2 + 1)) {
        reversed[11] ^= 11;
      }
      msgArr = Arrays.copyOfRange(reversed, 1, reversed.length);
    }
    return listToCharArray(result);
  }

  private static char[] reverseArray(char[] input) {
    char[] reversed = new char[input.length];
    for (int i = 0; i < reversed.length; i++) {
      reversed[i] = input[input.length - 1 - i];
    }
    return reversed;
  }

  private static char[] listToCharArray(List<Character> a) {
    char[] res = new char[a.size()];
    for (int i = 0; i < a.size(); i++) {
      res[i] = a.get(i);
    }
    return res;
  }

}
