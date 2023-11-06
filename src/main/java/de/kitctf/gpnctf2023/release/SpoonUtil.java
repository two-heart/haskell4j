package de.kitctf.gpnctf2023.release;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtParameterReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

public class SpoonUtil {

  public static void inlineAllLocalVariables(CtMethod<?> method) {
    boolean foundVars = true;
    while (foundVars) {
      List<CtLocalVariable> vars = method
          .getElements(new TypeFilter<>(CtLocalVariable.class))
          .stream().filter(it -> !(it.getParent() instanceof CtForEach))
          .toList();
      method.accept(new CtScanner() {
        @Override
        public <T> void visitCtLocalVariableReference(CtLocalVariableReference<T> reference) {
          if (reference.getParent() instanceof CtVariableRead<?>) {
            if (reference.getDeclaration().getAssignment() != null) {
              reference.getParent().replace(reference.getDeclaration().getAssignment().clone());
            }
          }
        }
      });
      vars.forEach(CtElement::delete);

      foundVars = !vars.isEmpty();
    }
  }

  /**
   * A very minimalistic function inliner. Requires a single return with a value at the end of the
   * method. Can not handle (in-)direct recursive calls.
   *
   * @param toInline the method to inline
   * @param call a call to the method
   */
  public static void inline(CtMethod<?> toInline, CtInvocation<?> call) {
    Factory factory = toInline.getFactory();
    long returnCount = toInline.getBody()
        .getStatements()
        .stream()
        .filter(it -> it instanceof CtReturn<?>)
        .count();

    if (!(toInline.getBody().getLastStatement() instanceof CtReturn<?>)) {
      throw new IllegalArgumentException("Method did not end with return " + toInline);
    }
    if (returnCount != 1) {
      throw new IllegalArgumentException("Not exactly one return statement in " + toInline);
    }
    if (toInline.getType().equals(factory.Type().voidPrimitiveType())) {
      throw new IllegalArgumentException("Void return type in " + toInline);
    }

    if (call.getParent(CtExecutable.class).equals(toInline)) {
      throw new IllegalArgumentException("Can not inline recursively!");
    }

    // Deduplicate variable names
    Set<String> takenVariableNames = new HashSet<>();
    call.getParent(CtExecutable.class).accept(new CtScanner() {
      @Override
      public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
        super.visitCtLocalVariable(localVariable);
        takenVariableNames.add(localVariable.getSimpleName());
      }

      @Override
      public <T> void visitCtParameter(CtParameter<T> parameter) {
        super.visitCtParameter(parameter);
        takenVariableNames.add(parameter.getSimpleName());
      }
    });

    Set<String> ourVariableNames = new HashSet<>();
    toInline.accept(new CtScanner() {
      @Override
      public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
        super.visitCtLocalVariable(localVariable);
        ourVariableNames.add(localVariable.getSimpleName());
      }
    });

    if (!Sets.intersection(takenVariableNames, ourVariableNames).isEmpty()) {
      // we need to rename our variables
      Map<String, String> renames = new HashMap<>();
      for (String name : ourVariableNames) {
        String current = name;
        for (int counter = 0; true; counter++) {
          boolean hasConflict = takenVariableNames.contains(current);
          hasConflict |= (ourVariableNames.contains(current) && counter > 0);
          if (!hasConflict) {
            break;
          }
          current = current + counter;
        }
        renames.put(name, current);
      }

      toInline.accept(new CtScanner() {
        @Override
        public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
          super.visitCtLocalVariable(localVariable);
          if (renames.containsKey(localVariable.getSimpleName())) {
            localVariable.setSimpleName(renames.get(localVariable.getSimpleName()));
          }
        }

        @Override
        public <T> void visitCtLocalVariableReference(CtLocalVariableReference<T> reference) {
          super.visitCtLocalVariableReference(reference);
          if (renames.containsKey(reference.getSimpleName())) {
            reference.setSimpleName(renames.get(reference.getSimpleName()));
          }
        }
      });
    }

    // Copy method statements over
    List<CtStatement> methodStatements = toInline.getBody().clone().getStatements();
    CtStatement callStatement = call.getParent(CtStatement.class);
    for (int i = 0; i < methodStatements.size() - 1; i++) {
      callStatement.insertBefore(rewireParameters(toInline, call, methodStatements.get(i)));
    }

    // Replace return value
    CtExpression<?> ourReturn = toInline.getBody()
        .<CtReturn<?>>getLastStatement()
        .getReturnedExpression()
        .clone();
    call.replace(rewireParameters(toInline, call, ourReturn));
  }

  private static <R extends CtElement> R rewireParameters(
      CtMethod<?> toInline, CtInvocation<?> call, R element
  ) {
    element.accept(new CtScanner() {
      @Override
      public <T> void visitCtVariableRead(CtVariableRead<T> variableRead) {
        if (!(variableRead.getVariable() instanceof CtParameterReference<T> paramRef)) {
          return;
        }
        CtParameter<?> param = toInline.getParameters()
            .stream()
            .filter(it -> it.getSimpleName().equals(paramRef.getSimpleName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Could not find my parameter?"));
        int index = toInline.getParameters().indexOf(param);
        if (index < 0) {
          throw new IllegalArgumentException("Could not find my parameter in call?");
        }
        variableRead.replace(call.getArguments().get(index).clone());
      }
    });

    return element;
  }

}
