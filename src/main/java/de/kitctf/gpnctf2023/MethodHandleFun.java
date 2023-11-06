package de.kitctf.gpnctf2023;

import static java.lang.invoke.MethodHandles.arrayConstructor;
import static java.lang.invoke.MethodHandles.arrayElementGetter;
import static java.lang.invoke.MethodHandles.arrayElementSetter;
import static java.lang.invoke.MethodHandles.arrayElementVarHandle;
import static java.lang.invoke.MethodHandles.arrayLength;
import static java.lang.invoke.MethodHandles.catchException;
import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.countedLoop;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.explicitCastArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.loop;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MethodHandleFun {
    // TODO: Replace constants with seeded Random
    //       Some random dropArguments without a type (no-op)

    /**
     * Implements this:
     * {@snippet lang = java:
     *       public static int rotateRight(int a) {
     *         int l = a & 1;
     *         a = a >>> 1;
     *         if (l != 0) {
     *             a = a | -2147483648;
     *         }
     *         return a;
     *     }
     *}
     */
    public static MethodHandle rotateRight() throws Throwable {
      return permuteArguments( // add `l = a & 1` as parameter
          filterArguments(
              // a, a&1
              guardWithTest( // do the if check
                  dropArguments(
                      isNotZero(),
                      0,
                      int.class
                  ),
                  dropArguments(
                      compose(
                          insertArguments(
                              or(),
                              0,
                              -2147483648
                          ),
                          shiftR1()
                      ),
                      1,
                      int.class
                  ),
                  dropArguments(
                      shiftR1(),
                      1,
                      int.class
                  )
              ),
              1,
              insertArguments(and(), 0, 1)
          ),
          methodType(int.class, int.class),
          0, 0
      );
    }

  /**
   * Rotates right by the given amount
   */
    public static MethodHandle rotateRightN(int amount) throws Throwable {
      return countedLoop(
          dropArguments(constant(int.class, amount), 0, int.class),
          identity(int.class),
          dropArguments(rotateRight(), 1, int.class)
      );
    }

    /**
     * Implements this:
     * {@snippet lang = java:
     *       public static int shiftL1_impl(int a) {
     *         for (int i = 0; i < 31; i++) {
     *             int l = a & 1;
     *             // print a as binary with 32 digits padded
     *             System.out.println(String.format("%32s", Integer.toBinaryString(a)).replace(' ', '0'));
     *             a = a >>> 1;
     *             if (l != 0) {
     *                 a = a | -2147483648;
     *             }
     *             System.out.println(String.format("%32s", Integer.toBinaryString(a)).replace(' ', '0') + "\n");
     *         }
     *         return a & ~1;
     *     }
     *}
     */
    public static MethodHandle shiftL1() throws Throwable {
        // gets (body(v, i, a...)
        MethodHandle body = dropArguments( // we don't need `i` and don't supply any `a...`
                permuteArguments( // add `l = a & 1` as parameter
                        filterArguments(
                                guardWithTest( // do the if check
                                        dropArguments(
                                                isNotZero(),
                                                0,
                                                int.class
                                        ),
                                        dropArguments(
                                                compose(
                                                        insertArguments(
                                                                or(),
                                                                0,
                                                                -2147483648
                                                        ),
                                                        shiftR1()
                                                ),
                                                1,
                                                int.class
                                        ), // assume compose(f, g) is g(f(x))
                                        dropArguments(
                                                shiftR1(),
                                                1,
                                                int.class
                                        )
                                ),
                                1,
                                insertArguments(and(), 0, 1)
                        ),
                        methodType(int.class, int.class, int.class),
                        0, 0
                ),
                1
        );
        MethodHandle loopiboy = countedLoop(
                constant(int.class, 31), // iterations
                identity(int.class), // Loop variable (a)
                dropArguments(
                        body,
                        2,
                        int.class
                )
        );
        // same assumption
        return compose(insertArguments(and(), 0, ~1), loopiboy);
    }


    /**
     * Idea:
     * <pre>{@code
     *   def isNotZero(a):
     *     for _ in range(32):
     *       if a:          # bit was set (or a set to 1)
     *         a = 1
     *       else:
     *         a = a >>> 1  # try next bit
     *     return a
     * }</pre>
     */
    public static MethodHandle isNotZero() throws Throwable {
        MethodHandle loopiboy = countedLoop(
                // iterations
                dropArguments(constant(int.class, 32), 0, int.class),
                // init
                identity(int.class),
                // body. v, i, a
                dropArguments(
                        // v, a
                        dropArguments(
                                // v
                                guardWithTest(
                                        // Convert to boolean for test. If we have a 1, will be true
                                        explicitCastArguments(
                                                identity(int.class),
                                                methodType(boolean.class, int.class)
                                        ),
                                        // keep our one value
                                        dropArguments(constant(int.class, 1), 0, int.class),
                                        // test next bit
                                        shiftR1()
                                ),
                                1,
                                int.class
                        ),
                        1,
                        int.class
                )
        );
        return compose(
                explicitCastArguments(identity(int.class), methodType(boolean.class, int.class)),
                loopiboy
        );
    }

  public static MethodHandle isZero() throws Throwable {
    return explicitCastArguments(
        compose(
            insertArguments(xor3(), 1, 1),
            explicitCastArguments(isNotZero(), methodType(int.class, int.class))
        ),
        methodType(boolean.class, int.class)
    );
  }


   /**
     * Implements
     * {@snippet :
     *   int add(int a, int b) {
     *     while (b != 0) {
     *         a = a ^ b;
     *         b = ((~a) & b) << 1;
     *     }
     *     return a;
     *   }
     *}
     */
    public static MethodHandle add() throws Throwable {
        return loop(
                new MethodHandle[]{
                        // ini
                        dropArguments(MethodHandles.identity(int.class), 1, int.class),
                        // step
                        xor3(),
                        // pred. We do not exit here.
                        // Drop args here are NO-OP without a type, but the loop will drop for you
                        dropArguments(dropArguments(constant(boolean.class, true), 0), 0),
                        // fini
                        dropArguments(identity(int.class), 1, int.class)
                },
                new MethodHandle[]{
                        // ini
                        dropArguments(MethodHandles.identity(int.class), 0, int.class),
                        // step
                        compose(shiftL1(), compose(and(), not())),
                        // pred
                        dropArguments(isNotZero(), 0, int.class),
                        // fini
                        dropArguments(identity(int.class), 1, int.class)
                }
        );
    }

    /**
     * Implements two's complement negation and then uses add.
     */
    public static MethodHandle subtractInt() throws Throwable {
        return collectArguments(
                add(),
                1,
                compose(insertArguments(add(), 0, 1), not())
        );
    }

    public static MethodHandle compose(MethodHandle f, MethodHandle g) {
        return MethodHandles.foldArguments(
                MethodHandles.dropArguments(f, 1, g.type().parameterList()),
                g
        );
    }

    /**
     * Pops the first element from an array.
     * {@snippet :
     *   char[] newArr = new char[old.length - 1];
     *   for (int i = 0; i < old.length - 1; i++) {
     *     newArr[i] = old[i + 1];
     *   }
     *}
     */
    public static MethodHandle popFirst() throws Throwable {
        MethodHandle iterations = MethodHandles.filterArguments(
                MethodHandles.insertArguments(add(), 0, -1),
                0,
                arrayLength(char[].class)
        );
        MethodHandle init = compose(arrayConstructor(char[].class), iterations);
        // newArray, i, oldArray
        MethodHandle body = MethodHandles.permuteArguments(
                // newArray, newArray, i, oldArray, i
                MethodHandles.filterArguments(
                        // newArray, newArray, i, oldArray, i+1
                        MethodHandles.collectArguments(
                                // newArray, newArray, i, oldValue
                                MethodHandles.collectArguments(
                                        // newArray
                                        identity(char[].class),
                                        1,
                                        arrayElementSetter(char[].class)
                                ),
                                3,
                                arrayElementGetter(char[].class)
                        ),
                        4,
                        MethodHandles.collectArguments(add(), 0, constant(int.class, 1))
                ),
                methodType(char[].class, char[].class, int.class, char[].class),
                0, 0, 1, 2, 1
        );

        return countedLoop(
                iterations,
                init,
                body
        );
    }

    public static MethodHandle interceptArraySetIfIndexIsNice(
        MethodHandle normalSet
    ) throws Throwable {
      return guardWithTest(
          // arrayref, index, value
          dropArguments(
              dropArguments(
                  compose(
                      isZero(),
                      insertArguments(xor3(), 0, 11)
                  ),
                  1,
                  char.class
              ),
              0,
              char[].class
          ),
          // arrayref, index, value
          filterArguments(
              normalSet,
              2,
              explicitCastArguments(
                  insertArguments(
                      xor3(),
                      0,
                      11
                  ),
                  methodType(char.class, char.class)
              )
          ),
          normalSet
      );
    }

    public static MethodHandle reverse() throws Throwable {
        VarHandle arrayElementVarHandle = arrayElementVarHandle(char.class.arrayType());
        MethodHandle arrayGetAndSet = arrayElementVarHandle.toMethodHandle(VarHandle.AccessMode.GET_AND_SET);
        MethodHandle arrayGetChar = arrayElementGetter(char.class.arrayType());
        MethodHandle arraySetChar = arrayElementSetter(char.class.arrayType());
        MethodHandle arrayLength = arrayLength(char.class.arrayType());

        // v, i, (length - i - 1)
        // (char[], int, int)
        MethodHandle combinedSwitchP1 = permuteArguments(
                // v, i, v, (length - i - 1)
                collectArguments(
                    // v, i, v[length - i - 1]
                    // tmp = v[i] ; v[i] = v[length - i - 1] ; return tmp
                    interceptArraySetIfIndexIsNice(arrayGetAndSet),
                    2,
                    arrayGetChar // read v[length - i - 1]
                ),
                methodType(char.class, char.class.arrayType(), int.class, int.class),
                0, 1, 0, 2
        );

        // v, i, (length - i - 1)
        // (char[], int, int)
        MethodHandle combinedSwap = permuteArguments(
                // v, (length - i - 1), v, i, (length - i - 1)
                collectArguments(
                    // v, (length - i - 1), v[i]
                    interceptArraySetIfIndexIsNice(arraySetChar),
                    2,
                    combinedSwitchP1
                ),
                methodType(void.class, char.class.arrayType(), int.class, int.class),
                0, 2, 0, 1, 2
        );

        MethodHandle loopyboy = countedLoop(
                // iterations(char[])
                collectArguments(
                        // x / 2
                        shiftR1(),
                        0,
                        // char.length
                        arrayLength
                ),
                null,
                permuteArguments(
                        // v, i, v, i
                        collectArguments(
                                // v, i, (length - i - 1)
                                combinedSwap,
                                2,
                                collectArguments(
                                        filterReturnValue(
                                                subtractInt(),
                                                insertArguments(subtractInt(), 1, 1)
                                        ),
                                        0,
                                        arrayLength
                                )
                        ),
                        methodType(void.class, int.class, char.class.arrayType()),
                        1, 0, 1, 0
                )
        );

        return permuteArguments(
                // s, s
                collectArguments(
                        identity(char[].class),
                        0,
                        loopyboy
                ),
                methodType(char[].class, char[].class),
                0, 0
        );
    }

  /**
   * In-place xors the input by the value of its first element.
   */
  public static MethodHandle xorInput() throws Throwable {
      MethodHandle modifyArray = countedLoop(
          arrayLength(char[].class),
          insertArguments(arrayElementGetter(char[].class), 1, 0),
          permuteArguments(
              // arr[0], arr, i, arr, i
              collectArguments(
                  // arr[0], arr, i, val
                  permuteArguments(
                      // arr, i, val, arr[0], arr[0]
                      collectArguments(
                          // arr, i, val ^ arr[0], arr[0]
                          collectArguments(
                              // arr[0]
                              identity(char.class),
                              0,
                              arrayElementSetter(char[].class)
                          ),
                          2,
                          explicitCastArguments(
                              xor3(),
                              methodType(char.class, char.class, char.class)
                          )
                      ),
                      methodType(char.class, char.class, char[].class, int.class, char.class),
                      1, 2, 3, 0, 0
                  ),
                  3,
                  arrayElementGetter(char[].class)
              ),
              methodType(char.class, char.class, int.class, char[].class),
              0, 2, 1, 2, 1
          )
      );
      return permuteArguments(
          // arr, arr
          filterArguments(
              // arr[0], arr
              dropArguments(identity(char[].class), 0, char.class),
              0,
              modifyArray
          ),
          methodType(char[].class, char[].class),
          0, 0
      );
    }

    /**
     * Implements
     * <pre>{@code
     * def reverse'(message):
     *   msg = message[::-1]
     *   if len(msg) != (11 * 2 + 1):
     *     msg[11] = msg[11] ^ 11
     *   return msg
     *
     * def encrypt(message: str) -> List[int]:
     *     for i in range(message):
     *       message[i] = rotr(val=message[0], by=ord(message[0]))
     *     res = []
     *     while message:
     *         res = append(res, (char) message[0] ^ (char) message[-1] ^ 3)
     *         message = popFirst(reverse'(message))
     *     return res
     * }</pre>
     */
    public static MethodHandle encrypt() throws Throwable {
        // Vs: res, message
      MethodHandle encryptLoop = loop(
          // res
          new MethodHandle[]{
              // init. We create a new empty char array
              insertArguments(arrayConstructor(char[].class), 0, 0),
              // step
              permuteArguments(
                  // res, message, message
                  filterArguments(
                      // res, message[0], message[message.length - 1]
                      explicitCastArguments(
                          // res, message[0], message[message.length - 1]
                          collectArguments(
                              append(),
                              1,
                              explicitCastArguments(
                                  filterArguments(
                                      xor3(),
                                      0,
                                      insertArguments( // xor first argument with constant
                                          xor3(),
                                          1,
                                          3
                                      )
                                  ),
                                  methodType(char.class, int.class, int.class)
                              )
                          ),
                          methodType(char[].class, char[].class, char.class, char.class)
                      ),
                      1,
                      // message -> message[0]
                      insertArguments(arrayElementGetter(char[].class), 1, 0),
                      // message -> message[message.length - 1]
                      permuteArguments(
                          // message, message
                          filterArguments(
                              // message, length
                              filterArguments(
                                  // message, length - 1
                                  arrayElementGetter(char[].class),
                                  1,
                                  insertArguments(add(), 0, -1)
                              ),
                              1,
                              arrayLength(char[].class)
                          ),
                          methodType(char.class, char[].class),
                          0, 0
                      )
                  ),
                  methodType(char[].class, char[].class, char[].class, char[].class),
                  0, 1, 1
              ),
              // pred. We never cancel the loop from here
              constant(boolean.class, true),
              // fini. Ignored, never returns.
              dropArguments(identity(char[].class), 1, char[].class)
          },
          // message
          new MethodHandle[]{
              // init. We just keep the message as-is to start with
              identity(char[].class),
              // step. popFirst(reverse(message))
              dropArguments(compose(popFirst(), reverse()), 0, char[].class),
              // pred. if(message.length != 0)
              dropArguments(compose(isNotZero(), arrayLength(char[].class)), 0, char[].class),
              // fini. return res
              dropArguments(identity(char[].class), 1, char[].class)
          }
      );
      return filterArguments(
          encryptLoop,
          0,
          xorInput()
      );
    }

    /**
     * Creates a method handle taking an array and a char and appends the char to the array,
     * allocating a new one with one more entry.
     * <p>
     * {@snippet :
     *   private char[] append(char[] src, char c) {
     *     int end = iterations(src);
     *     char[] v = new char[src.length + 1];
     *     for (int i = 0; i < end; i++) {
     *         v = body(i, src, v, c);
     *     }
     *     return v;
     *   }
     *   private char[] body(int index, char[] src, char[] dest, char c) {
     *     tryFilter(index, src, dest, c);
     *     return dest;
     *   }
     *   private static void tryFilter(int index, char[] src, char[] dest, char c) {
     *     try {
     *         tryBody(index, src, dest);
     *     } catch (Exception e) {
     *         catchBody(index, dest, c);
     *     }
     *   }
     *   private static void catchBody(int index, char[] dest, char c) {
     *     dest[index] = c;
     *   }
     *   private static void tryBody(int index, char[] src, char[] dest) {
     *     dest[index] = src[index];
     *   }
     *   private int iterations(char[] src) {
     *     return src.length + 1;
     *   }
     *}
     *
     * @param add performing integer addition
     */
    public static MethodHandle createAppend(MethodHandle add) {
        MethodHandle charArrayCtor = arrayConstructor(char[].class);
        MethodHandle charArrayLength = arrayLength(char[].class);
        MethodHandle get = arrayElementGetter(char[].class);
        MethodHandle set = arrayElementSetter(char[].class);
        MethodHandle addOne = insertArguments(add, 0, 1);
        MethodHandle newSize = filterReturnValue(charArrayLength, addOne);
        MethodHandle init = filterReturnValue(newSize, charArrayCtor);
        MethodHandle copyOne = permuteArguments(
                collectArguments(set, 2, get),
                methodType(void.class, char[].class, int.class, char[].class),
                0, 1, 2, 1
        );
        MethodHandle tryBody = dropArguments(copyOne, 3, char.class);
        MethodHandle catchBody = dropArguments(set, 2, char[].class);
        MethodHandle dupForReturn = foldArguments(
                dropArguments(identity(char[].class), 1, int.class, char[].class, char.class),
                catchException(tryBody, Exception.class, dropArguments(catchBody, 0, Exception.class))
        );
        return countedLoop(newSize, init, dupForReturn);
    }

    /**
     * char[] in, char a
     */
    public static MethodHandle append() throws Throwable {
        return createAppend(add());
    }

    /**
     * Implements bitwise xor using not and and.
     * <pre>{@code
     *   a ^ b = ¬(a ∧ b) ∧ ¬(¬a ∧ ¬b)
     * }</pre>
     */
    public static MethodHandle xor3() throws Throwable {
        MethodHandle nand = compose(not(), and());

        return MethodHandles.permuteArguments(
                // x, y, x, y -> res
                MethodHandles.filterArguments(
                        // x, y, !x, !y -> res
                        collectArguments(
                                // nand(x, y), !x, !y -> res
                                collectArguments(
                                        // nand(x, y), nand(!x, !y) -> res
                                        and(),
                                        1,
                                        nand
                                ),
                                0,
                                nand
                        ),
                        2,
                        not(), not()
                ),
                methodType(int.class, int.class, int.class),
                0, 1, 0, 1
        );
    }

    /**
     * Bitwise or.
     * <pre>{@code
     *   a ∨ b = ¬(¬a ∧ ¬b)  (anything but ¬a and ¬b)
     * }</pre>
     */
    public static MethodHandle or() throws Throwable {
        return compose(not(), filterArguments(and(), 0, not(), not()));
    }

    public static MethodHandle and() throws Throwable {
        return MethodHandles.lookup()
                .findStatic(MethodHandleFun.class, "and", methodType(int.class, int.class, int.class));
    }

    public static MethodHandle not() throws Throwable {
        return MethodHandles.lookup()
                .findStatic(MethodHandleFun.class, "not", methodType(int.class, int.class));
    }

    public static MethodHandle shiftR1() throws Throwable {
        return insertArguments(
            MethodHandles.lookup()
                .findStatic(Integer.class, "compress", methodType(int.class, int.class, int.class)),
            1,
            -2
        );
    }

    public static int and(int a, int b) {
        return a & b;
    }

    public static int not(int a) {
        return ~a;
    }

    public static void main(String[] args) throws Throwable {
        char[] flag = new String(Files.readAllBytes(
            Paths.get(
                MethodHandleFun.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).resolveSibling("flag.txt")
        )).toCharArray();
        char[] res = (char[]) encrypt().invoke(flag);
        for (char re : res) {
            System.out.print((int) re + " ");
        }
        System.out.println();
    }

}
