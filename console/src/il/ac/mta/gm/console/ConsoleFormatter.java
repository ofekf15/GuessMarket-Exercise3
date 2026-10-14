package il.ac.mta.gm.console;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.OptionStatusDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;

import java.util.List;

public class ConsoleFormatter {

    public void printMenu() {
        System.out.println("\n--- Guess Market ---");
        System.out.println("1. Load XML File");
        System.out.println("2. Show Events");
        System.out.println("3. Event Trading Status");
        System.out.println("4. Buy Shares");
        System.out.println("5. Close Event");
        System.out.println("6. Exit");
        System.out.print("Select an option: ");
    }

    public void printEvents(List<EventDisplayDTO> events) {
        if (events.isEmpty()) {
            System.out.println("No events loaded.");
            return;
        }

        System.out.println("\n--- Loaded Events ---");
        int index = 1;
        for (EventDisplayDTO e : events) {
            System.out.println(index + ". [ID: " + e.id + "] " + e.name);
            System.out.println("   " + e.description);
            System.out.println("   Commission: " + e.commissionPercent + "% (" + e.commissionType + ")");
            System.out.println("   Options: " + e.optionNames.get(0) + " / " + e.optionNames.get(1));
            System.out.println("   Status: " + e.status);
            System.out.println();
            index++;
        }
    }

    public void printEventStatus(EventStatusDTO event) {
        System.out.println("\n--- Event Status: " + event.name + " (ID: " + event.id + ") ---");
        System.out.println("Status: " + event.status);
        
        System.out.println("\nOptions:");
        for (OptionStatusDTO opt : event.options) {
            System.out.printf(" - %s: Price = %.2f, Shares bought = %d%n", 
                opt.name, opt.currentValue, opt.sharesBought);
        }

        System.out.printf("\nEvent Account Balance: %.2f%n", event.accountBalance);
        System.out.printf("Total Fees Collected: %.2f%n", event.totalFeesCollected);

        System.out.println("\nTrade History:");
        if (event.history.isEmpty()) {
            System.out.println(" (No trades yet)");
        } else {
            for (TradeRecordDTO r : event.history) {
                System.out.printf(" - Bought %d shares of %s | Paid: %.2f%n",
                    r.quantity, r.optionName, r.sharesCost);
            }
        }
        
        if (event.winningOption != null) {
            System.out.println("\nWinning Option: " + event.winningOption);
        }
    }
}
