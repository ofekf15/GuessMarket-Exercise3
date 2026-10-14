package il.ac.mta.gm.console;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;

public class ConsoleMain {
    public static void main(String[] args) {
        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);
        
        ConsoleFormatter formatter = new ConsoleFormatter();
        ConsoleMenu menu = new ConsoleMenu(engine, formatter);
        
        menu.start();
    }
}
