package de.kitctf.gpnctf2023.release;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

@SuppressWarnings("UnstableApiUsage")
public class Callgraph {

  private final MutableGraph<CtMethod<?>> callgraph;

  private Callgraph(MutableGraph<CtMethod<?>> callgraph) {
    this.callgraph = callgraph;
  }

  public Set<CtMethod<?>> getDirectCallers(CtMethod<?> method) {
    return callgraph.predecessors(method);
  }

  public Set<CtMethod<?>> findLeaves() {
    return callgraph.nodes()
        .stream()
        .filter(it -> callgraph.outDegree(it) == 0)
        .collect(Collectors.toSet());
  }

  public static Callgraph forType(CtType<?> type, Predicate<CtMethod<?>> blacklist) {
    MutableGraph<CtMethod<?>> graph = GraphBuilder.directed().allowsSelfLoops(false).build();

    for (CtMethod<?> method : type.getMethods()) {
      if (blacklist.test(method)) {
        continue;
      }
      graph.addNode(method);
      buildEdgesForMethod(type, graph, method);
    }

    return new Callgraph(graph);
  }

  private static void buildEdgesForMethod(
      CtType<?> type, MutableGraph<CtMethod<?>> graph, CtMethod<?> method
  ) {
    for (var invocation : method.getBody().getElements(new TypeFilter<>(CtInvocation.class))) {
      CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
      if (!declaringType.getQualifiedName().equals(type.getQualifiedName())) {
        continue;
      }
      CtExecutable<?> executable = invocation.getExecutable().getExecutableDeclaration();
      if (!(executable instanceof CtMethod<?> called)) {
        continue;
      }
      graph.putEdge(method, called);
    }
  }
}
