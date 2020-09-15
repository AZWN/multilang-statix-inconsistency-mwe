### Statix interference demo

This repository contains a demo that shows that constraints from separately compiled Statix modules can interfere.
This shows that it is needed to supply the full specification to the solver, when multiple languages are analyzed together.

#### Repository Content

* `src/main/java`: Command line application that executes 2 solver runs, demonstrating the inconsistency
* `src/main/resources`: Two sets of modules: one exhibiting inconsistent behavior, the other showing how the runtime can refuse to load a combined spec.
* `statix-sources`: Three statix modules that were used to generate the spec files for the demo. These were compiled in a otherwise empty Spoofax project.

#### Interference pattern

So now the question: How does the interference work?

First, regarding the module hierarchy:
The type system of (fictional) language A consists of modules `interface` and `langa`.
Likewise, the type system of language B consists of modules `interface` and `langb`.
When both languages are analyzed together, the modules `interface`, `langa` and `langb` are used.

##### Definition

What happens is the following:

The interface defines:
* a sort `S`
* a constructor `C : S`
* a constraint `myConstraint : C * string`

Language A defines two instances for `myConstraint`:
* `myConstraint(C(), "unused").`
* `myConstraint(C(), _).`

Language B also defines a variant for `myConstraint`:
* `myConstraint(C(), "") :- false.`

Futhermore, language A's entry point (`fileOk`) tries to solve constraint `myConstraint(C(), "")`.

##### Runtime

So what happens at runtime is the following. In the current solver, solving is done in 2 steps:
1. The constraints for all files are solved in isolation. This may yield constraints that cannot be decided without results from the other files. These constraints are therefore _delayed_.
2. All intermediate results are combined, and all delayed constraints are solved.

When the solver encounters a constraint, 3 things can happen:
1. When one instance of a constraint applies, it is instantiated
2. When multiple instances apply, and all arguments are ground, the instance with the highest precedence is chosen.
3. When multiple instances apply, and not all arguments are ground, it is delayed until some variables are instantiated further

When (in phase 1) the entry point of language A is solved with modules `interface` and `langa`, the solver has 2 variants to choose from, namely the ones provided my module `langa`.
Because the variant `myConstraint(C(), "unused")` does not apply (`""` cannot be unified with `"unused"`), it chooses `myConstraint(C(), _).`, which succeeds.

However, when (still in phase 1) module `langb` is added to the spec as well, the solver has all three variants available. It will now choose `myConstraint(C(), "") :- false.`, because the term `""` takes precedence over `_`. Now the constraint fails.

That this can result in really counterintuitive behaviour, is demonstrated by the other 2 constraints in the entry point of `langa`. Together they constitute the exact same constraint (`myConstraint(C(), "")`) as the inital one. 
However, the arguments are actually query results, which can only be resolved in phase 2 (since in phase 1, the uninstantiated variable could be unified with `""` as well as `"unused"`. So 2 variants might apply, and not all arguments are ground.). When the constraint `myConstraint(C(), "")` is then instantiated in phase 2, suddenly the variant of `langb` (which was not available in phase 1) is available, and chosen. 
Now in the same solver run, the same constraint once succeeds, and once fails.

In the solver log, this is visible as well:
```
461 [main] INFO Demo - Solving interface!myConstraint(C(), "")
461 [main] INFO Demo - Rule accepted
461 [main] INFO Demo - | Implied equalities: {}
461 [main] INFO Demo - | Simplified to:
461 [main] INFO Demo - |  * true
```
and somewhile later:
```
490 [main] INFO Demo Solving interface!myConstraint(C(), "")
490 [main] INFO Demo - Rule accepted
490 [main] INFO Demo - | Implied equalities: {}
490 [main] INFO Demo - | Simplified to:
490 [main] INFO Demo - |  * false
```

Therefore, when languages are analyzed together, they should use the combined spec even in phase 1.