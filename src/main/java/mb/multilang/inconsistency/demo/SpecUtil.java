package mb.multilang.inconsistency.demo;

import com.google.common.collect.ListMultimap;
import mb.nabl2.regexp.IAlphabet;
import mb.nabl2.regexp.impl.FiniteAlphabet;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.stratego.StrategoTerms;
import mb.statix.spec.Rule;
import mb.statix.spec.RuleSet;
import mb.statix.spec.Spec;
import mb.statix.spoofax.StatixTerms;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.terms.TermFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static mb.nabl2.terms.matching.TermMatch.*;

public class SpecUtil {
    public static Spec load(Demo.DemoSet set, String name) throws Exception {
        TermFactory termFactory = new TermFactory();
        StrategoTerms strategoTerms = new StrategoTerms(termFactory);
        String specPath = String.format("%s/%s.spec.aterm", set.toString().toLowerCase(), name);

        try(BufferedReader specReader = new BufferedReader(new InputStreamReader(SpecUtil.class.getResourceAsStream(specPath)))) {
            String specString = specReader.lines().collect(Collectors.joining("\n"));
            IStrategoTerm stxFileSpec = termFactory.parseFromString(specString);
            return fileSpec().match(strategoTerms.fromStratego(stxFileSpec))
                    .orElseThrow(() -> new Exception("Invalid spec file: " + specPath));
        }
    }

    public static Spec merge(Spec acc, Spec newSpec) throws Exception {
        // Error when EOP is not equal, throw exception
        if(!acc.noRelationLabel().equals(newSpec.noRelationLabel())) {
            throw new Exception("No relation labels dont match");
        }

        Set<Rule> rules = new HashSet<>(acc.rules().getAllRules());
        rules.addAll(newSpec.rules().getAllRules());

        Set<ITerm> labels = new HashSet<>(acc.labels().symbols());
        labels.addAll(newSpec.labels().symbols());

        Spec combinedSpec = Spec.builder()
                .from(acc)
                .rules(RuleSet.of(rules))
                .addAllEdgeLabels(newSpec.edgeLabels())
                .addAllRelationLabels(newSpec.relationLabels())
                .labels(new FiniteAlphabet<>(labels))
                .putAllScopeExtensions(newSpec.scopeExtensions())
                .build();

        final ListMultimap<String, Rule> rulesWithEquivalentPatterns = combinedSpec.rules().getAllEquivalentRules();
        if(!rulesWithEquivalentPatterns.isEmpty()) {
            throw new Exception("Overlapping patterns in: " + rulesWithEquivalentPatterns.keySet());
        }

        return combinedSpec;
    }

    /**
     * Transform ITerm to Spec
     */
    public static IMatcher<Spec> fileSpec() {
        return M.appl6("FileSpec", M.list(/* imports */), M.req(StatixTerms.labels()), M.req(StatixTerms.labels()), M.term(), StatixTerms.rules(), M.req(StatixTerms.scopeExtensions()),
                (t, l, edgeLabels, relationLabels, noRelationLabel, rules, ext) -> {
                    List<ITerm> allTerms = new ArrayList<>(relationLabels);
                    Collections.addAll(allTerms, edgeLabels.toArray(new ITerm[0]));
                    allTerms.add(0, noRelationLabel);
                    final IAlphabet<ITerm> labels = new FiniteAlphabet<>(allTerms);
                    return Spec.of(rules, edgeLabels, relationLabels, noRelationLabel, labels, ext);
                });
    }
}
