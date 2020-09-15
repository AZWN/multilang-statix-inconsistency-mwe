package mb.multilang.inconsistency.demo;

import org.metaborg.util.log.Level;

public class Main {

    public static void main(String[] args) throws Exception {
        // One failing constraint
        new Demo(Demo.DemoSet.INCONSISTENT, true, Level.Info).run();
        // Current implementation, 2 failing constraints
        new Demo(Demo.DemoSet.INCONSISTENT, false, Level.Warn).run();

        // Exception when loading
        // constraint 'myConstraint(C(), _)' is duplicated
        // new Demo(Demo.DemoSet.OVERLAPPING, false, Level.Warn).run();
    }
}
