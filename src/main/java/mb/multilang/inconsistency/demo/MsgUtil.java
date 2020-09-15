package mb.multilang.inconsistency.demo;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.build.TermBuild;
import mb.nabl2.terms.unification.ud.IUniDisunifier;
import mb.nabl2.util.TermFormatter;
import mb.statix.constraints.messages.IMessage;
import mb.statix.solver.IConstraint;
import mb.statix.solver.persistent.Solver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

public class MsgUtil {
    private static final int MAX_TRACE = 5;

    public static String formatMessage(final IMessage message, final IConstraint constraint, final IUniDisunifier unifier) {
        final TermFormatter formatter = Solver.shallowTermFormatter(unifier);

        final Deque<String> trace = new ArrayDeque<>();
        IConstraint current = constraint;
        int traceCount = 0;
        while(current != null) {
            if(traceCount++ < MAX_TRACE) {
                trace.addLast(current.toString(formatter));
            }
            current = current.cause().orElse(null);
        }
        if(traceCount >= MAX_TRACE) {
            trace.addLast("... trace truncated ...");
        }

        // add constraint message
        trace.addFirst(message.toString(formatter));
        return trace.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("\n\t"));
    }
}
