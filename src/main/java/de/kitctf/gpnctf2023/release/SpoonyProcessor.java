package de.kitctf.gpnctf2023.release;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import de.kitctf.gpnctf2023.release.compilation.JavacFacade;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

public class SpoonyProcessor {

  public static void main(String[] args) throws IOException {
    Launcher launcher = new Launcher();
    launcher.addInputResource("src/main/java/de/kitctf/gpnctf2023/MethodHandleFun.java");
    launcher.getEnvironment().setComplianceLevel(19);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.getEnvironment().setAutoImports(true);

    CtType<?> methodHandleFunClass = launcher.buildModel().getAllTypes().iterator().next();

    methodHandleFunClass.getMethodsByName("rotateRight").forEach(CtElement::delete);
    methodHandleFunClass.getMethodsByName("rotateRightN").forEach(CtElement::delete);

    for (CtMethod<?> method : methodHandleFunClass.getMethods()) {
      SpoonUtil.inlineAllLocalVariables(method);
    }

    methodHandleFunClass.accept(new ReplaceLiteralsScanner(launcher.getFactory()));

    System.out.println("\n\nInlining...\n");
    boolean changed = true;
    while (changed) {
      changed = doInline(methodHandleFunClass);
    }

    System.out.println("\n\nOutlining...\n");
    for (int i = 0; i < 10; i++) {
      outlineSomeParts(methodHandleFunClass.getMethodsByName("main").get(0), "outlined" + i);
    }

    String asString = launcher.getEnvironment().createPrettyPrinter()
        .printCompilationUnit(methodHandleFunClass.getPosition().getCompilationUnit())
        .replace(methodHandleFunClass.getSimpleName(), "ToRelease");

    Map<String, byte[]> compiledFiles = JavacFacade.compileFiles(
        Map.of("de/kitctf/gpnctf2023/ToRelease.java", asString),
        List.of()
    );
    Path outFile = Path.of("out.jar");
    Files.deleteIfExists(outFile);
    try (var fs = FileSystems.newFileSystem(outFile, Map.of("create", "true"))) {
      Files.createDirectories(fs.getPath("/META-INF/"));
      Files.writeString(
          fs.getPath("/META-INF/MANIFEST.MF"),
          "Main-Class: de.kitctf.gpnctf2023.ToRelease\n"
      );
      for (Entry<String, byte[]> entry : compiledFiles.entrySet()) {
        String nameAsPath = entry.getKey().replace(".", "/") + ".class";
        Files.createDirectories(fs.getPath(nameAsPath).getParent());
        Files.write(fs.getPath(nameAsPath), entry.getValue());
      }
    }
  }

  private static boolean doInline(CtType<?> methodHandleFunClass) {
    Callgraph callgraph = Callgraph.forType(
        methodHandleFunClass,
        it -> !it.getParameters().isEmpty() && Set.of("and", "not").contains(it.getSimpleName())
    );

    for (CtMethod<?> leaf : callgraph.findLeaves()) {
      for (CtMethod<?> caller : callgraph.getDirectCallers(leaf)) {
        inlineInvocations(leaf, caller);
      }

      if (!callgraph.getDirectCallers(leaf).isEmpty()) {
        System.out.println("Inlined " + leaf.getSimpleName());
        leaf.delete();
        return true;
      }
    }

    return false;
  }

  @SuppressWarnings("rawtypes")
  private static void inlineInvocations(CtMethod<?> leaf, CtMethod<?> caller) {
    while (true) {
      List<CtInvocation> invocations = caller.getElements(new TypeFilter<>(CtInvocation.class))
          .stream()
          .filter(it -> leaf.equals(it.getExecutable().getExecutableDeclaration()))
          .collect(Collectors.toCollection(ArrayList::new));

      if (invocations.isEmpty()) {
        return;
      }

      // inline innermost first
      SpoonUtil.inline(leaf, invocations.get(invocations.size() - 1));
    }
  }

  @SuppressWarnings({"UnstableApiUsage", "unchecked", "rawtypes"})
  private static void outlineSomeParts(CtMethod<?> root, String name) {
    System.out.println("Outlining due to bytecode size constraints...");
    System.out.println("  Building call stats");
    MutableGraph<CtInvocation<?>> invocations = GraphBuilder.directed()
        .allowsSelfLoops(false)
        .build();

    root.accept(new CtScanner() {
      @Override
      public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        super.visitCtInvocation(invocation);
        if (invocation.getParent(CtInvocation.class) instanceof CtInvocation<?> parentInvoc) {
          invocations.putEdge(parentInvoc, invocation);
        }
      }
    });

    System.out.println("  Building call weights");
    List<CtInvocation<?>> roots = invocations.nodes()
        .stream()
        .filter(it -> invocations.inDegree(it) == 0)
        .toList();
    if (roots.size() != 1) {
      throw new IllegalArgumentException("More than one root!");
    }
    Map<CtInvocation<?>, Integer> weights = new HashMap<>();
    buildWeightsPerNode(invocations, weights, roots.get(0));
    List<Integer> sortedWeights = weights.values().stream().sorted().toList();
    int selectedWeight = sortedWeights.get((int) (sortedWeights.size() * 0.9));

    System.out.println("  Evicting victim");

    CtInvocation<?> victim = weights.entrySet()
        .stream()
        .filter(it -> it.getValue() == selectedWeight)
        .findFirst()
        .map(Entry::getKey)
        .orElseThrow();

    Factory factory = root.getFactory();
    CtMethod<?> newMethod = factory.createMethod();
    newMethod.setModifiers(Set.of(ModifierKind.STATIC, ModifierKind.PUBLIC));
    newMethod.setSimpleName(name);
    newMethod.setBody(((CtReturn) factory.createReturn()).setReturnedExpression(victim.clone()));
    newMethod.setType(victim.getType());
    newMethod.setThrownTypes(Set.of(factory.createCtTypeReference(Throwable.class)));

    root.getDeclaringType().addMethod(newMethod);
    victim.replace(factory.createCodeSnippetExpression(newMethod.getSimpleName() + "()"));
  }

  @SuppressWarnings("UnstableApiUsage")
  private static int buildWeightsPerNode(
      Graph<CtInvocation<?>> graph,
      Map<CtInvocation<?>, Integer> weights,
      CtInvocation<?> current
  ) {
    int myWeight = (int) (
        current.getArguments().stream().filter(it -> !(it instanceof CtInvocation<?>)).count() + 1
    );
    for (CtInvocation<?> successor : graph.successors(current)) {
      myWeight += buildWeightsPerNode(graph, weights, successor);
    }

    weights.put(current, myWeight);

    return myWeight;
  }

}
