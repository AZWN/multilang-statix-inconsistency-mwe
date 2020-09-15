package mb.multilang.inconsistency.demo;

import com.google.common.collect.ImmutableMap;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.build.TermVar;
import mb.nabl2.terms.unification.ud.IUniDisunifier;
import mb.statix.constraints.*;
import mb.statix.constraints.messages.IMessage;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IState;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.log.LoggerDebugContext;
import mb.statix.solver.persistent.Solver;
import mb.statix.solver.persistent.SolverResult;
import mb.statix.solver.persistent.State;
import mb.statix.spec.Spec;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.Level;
import org.metaborg.util.log.MetaborgLogger;
import org.metaborg.util.task.NullCancel;
import org.metaborg.util.task.NullProgress;
import org.slf4j.impl.SimpleLoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class Demo {
    /**
     * Indicator which demo set to use
     */
    public enum DemoSet {
        // Demo set with rules with overlapping patterns
        // Will throw runtime error
        OVERLAPPING,

        // Demo set with interference
        // Will have inconsistent behaviour
        INCONSISTENT
    }

    // Parameters
    private final DemoSet demoSet;
    private boolean usePartialSpecs;

    // ** Calculated fields **

    // Isolated modules
    private final Spec interfaceModule;
    private final Spec langAModule;
    private final Spec langBModule;

    /**
     * Modules: interface + langA
     */
    private final Spec langASpec;

    /**
     * Modules: interface + langB
     */
    private final Spec langBSpec;

    /**
     * Modules: interface + langA + langB
     */
    private final Spec combinedSpec;

    // Fields used for constraint instantiation
    private final ITermVar globalScopeVar;
    private final Set<ITermVar> fileOkArgs;

    // Before solving language constraints, we instantiate a global scope once
    // By solving constraint: {s}, new s.
    private final ITerm globalScope;
    private final IState.Immutable globalState;

    private final IConstraint langAfileOk;
    private final IConstraint langBfileOk;

    // Util
    private static final ILogger logger = new MetaborgLogger(new SimpleLoggerFactory().getLogger("Demo"));
    private final IDebugContext debug;

    public Demo(DemoSet demoSet, boolean usePartialSpecs, Level logLevel) throws Exception {
        this.demoSet = demoSet;
        this.usePartialSpecs = usePartialSpecs;
        this.debug = new LoggerDebugContext(logger, logLevel);

        interfaceModule = SpecUtil.load(demoSet, "interface");
        langAModule = SpecUtil.load(demoSet, "langa");
        langBModule = SpecUtil.load(demoSet, "langb");

        if(usePartialSpecs) {
            langASpec = SpecUtil.merge(interfaceModule, langAModule);
            langBSpec = SpecUtil.merge(interfaceModule, langBModule);
            combinedSpec = SpecUtil.merge(langASpec, langBModule);
        } else {
            Spec intermediate = SpecUtil.merge(interfaceModule, langAModule);

            combinedSpec = SpecUtil.merge(intermediate, langBModule);
            langASpec = combinedSpec;
            langBSpec = combinedSpec;
        }

        SolverResult globalResult = instantiateGlobalScope();
        globalScopeVar = globalResult.state().vars().iterator().next();
        globalScope = globalScopeFromResult(globalResult);
        globalState = globalResult.state();

        fileOkArgs = Collections.singleton(globalScopeVar);

        langAfileOk = new CUser("langa!fileOk", fileOkArgs);
        langBfileOk = new CUser("langb!fileOk", fileOkArgs);
    }

    public void run() throws Exception {
        System.out.printf("%n** Running solver **%n  Variant: %s%n  Partial specs: %b%n",
                demoSet.toString().toLowerCase(), usePartialSpecs);

        // Stage 1.a: Run constraints for lang a.
        SolverResult langAResult = runPartial(langASpec, langAfileOk);
        report("lang A", langAResult);

        // Stage 1.b: Run constraints for lang b.
        SolverResult langBResult = runPartial(langBSpec, langBfileOk);
        report("lang B", langBResult);

        // Stage 2: Solve residual constraints with combined spec/state
        SolverResult finalResult = runFinal(combinedSpec, langAResult, langBResult);
        report("final step", finalResult);
    }

    private void report(String name, SolverResult result) {
        // Print delayed constraints for this solver run
        System.out.println("Finished solving constraints for " + name);
        /* if(!result.delays().isEmpty()) {
            System.out.println("  Residual constraints:");
            result.delays().forEach((c, d) -> System.out.printf("  - %s%n", c));
        } */

        if(result.hasErrors()) {
            System.out.println("  Errors:");
            result.messages().forEach((c, m) -> System.out.printf("  - %s%n", MsgUtil.formatMessage(m, c, result.state().unifier())));
        }
    }

    private SolverResult runPartial(Spec spec, IConstraint initialConstraint) throws InterruptedException {
        IState.Immutable initialState  = State.of(spec).add(globalState);
        SolverResult result = Solver.solve(spec, initialState, initialConstraint, (s, l, st) -> !initialState.scopes().contains(s),
                debug, new NullProgress(), new NullCancel());

        System.err.flush();
        System.out.flush();

        return result;
    }

    private SolverResult runFinal(Spec spec, SolverResult... partialResults) throws Exception {
        IState.Immutable combinedState = Arrays.stream(partialResults)
                .map(SolverResult::state)
                .reduce(IState.Immutable::add)
                .orElseThrow(() -> new Exception("No partial results"));

        IConstraint delays = Constraints.conjoin(Arrays.stream(partialResults)
                .map(SolverResult::delays)
                .map(ImmutableMap::keySet)
                .flatMap(Set::stream)
                .collect(Collectors.toSet()));

        SolverResult combinedResult = Solver.solve(combinedSpec, combinedState, delays, debug, new NullProgress(), new NullCancel());

        // Build messages for result
        final ImmutableMap.Builder<IConstraint, IMessage> messages = ImmutableMap.builder();
        messages.putAll(combinedResult.messages());
        combinedResult.delays().keySet().forEach(c -> messages.put(c, null));
        return combinedResult.withMessages(messages.build()).withDelays(ImmutableMap.of());
    }

    /**
     * Solves <c>{s}, new s.</c>
     */
    private SolverResult instantiateGlobalScope() throws InterruptedException {
        ITermVar s = TermVar.of("", "s");
        Set<ITermVar> cExistsArgs = Collections.singleton(s);
        Set<ITerm> cNewArgs = Collections.singleton(s); // No covariant assign in Java  :-( , so reinstantiate.
        IConstraint constraint = new CExists(cExistsArgs, new CNew(cNewArgs));

        Spec emptySpec = Spec.of();
        return Solver.solve(emptySpec, State.of(emptySpec), constraint, debug, new NullProgress(), new NullCancel());
    }

    private ITerm globalScopeFromResult(SolverResult result) {
        return result.state().unifier().findRecursive(globalScopeVar);
    }
}
