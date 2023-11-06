package de.kitctf.gpnctf2023.release;

import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

class ReplaceLiteralsScanner extends CtScanner {

  private static final int MAX_RANDOM_SEQUENCE_LENGTH = 2;
  private static final List<String> CLASS_LIT_OPERATIONS = List.of(
      ".arrayType()",
      ".getComponentType()"
  );

  private final Factory factory;

  ReplaceLiteralsScanner(Factory factory) {
    this.factory = factory;
  }

  @Override
  public <T> void visitCtLiteral(CtLiteral<T> literal) {
    if (literal.getValue() instanceof Integer i) {
      var cracked = crackSeed(i);
      if (cracked.isEmpty()) {
        return;
      }
      int seed = cracked.getAsInt();
      int actual = new Random(seed).nextInt(255);
      if (actual != i) {
        throw new RuntimeException("OH NO");
      }
      literal.replace(factory.createCodeSnippetExpression(
          "new java.util.Random({seed}).nextInt(255)"
              .replace("{seed}", Integer.toString(seed))
      ));
    }
    super.visitCtLiteral(literal);
  }

  private OptionalInt crackSeed(int target) {
    if (target > 255 || target < 0) {
      return OptionalInt.empty();
    }
    while (true) {
      int seed = ThreadLocalRandom.current().nextInt();
      Random random = new Random(seed);
      int ourTry = random.nextInt(255);
      if (ourTry == target) {
        return OptionalInt.of(seed);
      }
    }
  }

  @Override
  public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
    super.visitCtFieldRead(fieldRead);
    if (!fieldRead.getVariable().getSimpleName().equals("class")) {
      return;
    }
    if (!(fieldRead.getTarget() instanceof CtTypeAccess<?> typeAccess)) {
      return;
    }

    int dimension = 0;
    CtTypeReference<?> arrayType = typeAccess.getAccessedType();
    if ((typeAccess.getAccessedType() instanceof CtArrayTypeReference<?> arrayRef)) {
      arrayType = arrayRef.getArrayType();
      dimension = arrayRef.getDimensionCount();
    }

    if (Set.of("Exception", "void").contains(arrayType.toString())) {
      return;
    }

    var snippet = classLitForDimension(arrayType.toString(), dimension);
    fieldRead.replace(factory.createCodeSnippetExpression(snippet));
  }

  private static String classLitForDimension(String arrayType, int dimension) {
    int openingArrayBrackets = ThreadLocalRandom.current().nextInt(dimension + 1);
    int currentDepth = openingArrayBrackets;

    StringBuilder result = new StringBuilder(arrayType)
        .append("[]".repeat(openingArrayBrackets))
        .append(".class");

    for (int i = 0; i < MAX_RANDOM_SEQUENCE_LENGTH; i++) {
      double ascentPercentage = currentDepth > dimension
          ? 0.1
          : currentDepth < dimension ? 0.8 : 0.5;

      if (ThreadLocalRandom.current().nextDouble() >= ascentPercentage || currentDepth == 0) {
        result.append(CLASS_LIT_OPERATIONS.get(0));
        currentDepth++;
      } else {
        result.append(CLASS_LIT_OPERATIONS.get(1));
        currentDepth--;
      }
    }

    while (currentDepth > dimension) {
      result.append(CLASS_LIT_OPERATIONS.get(1));
      currentDepth--;
    }

    while (currentDepth < dimension) {
      result.append(CLASS_LIT_OPERATIONS.get(0));
      currentDepth++;
    }

    return result.toString();
  }

}
