package il.ac.mta.gm.console;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.dto.EventDisplayDTO;

import java.util.List;
import java.util.Scanner;

public class ConsoleMenu {

    private final EngineInterface engine;
    private final ConsoleFormatter formatter;
    private final Scanner scanner;

    public ConsoleMenu(EngineInterface engine, ConsoleFormatter formatter) {
        this.engine = engine;
        this.formatter = formatter;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        boolean running = true;
        while (running) {
            formatter.printMenu();
            String input = scanner.nextLine().trim();
            
            try {
                int choice = Integer.parseInt(input);
                switch (choice) {
                    case 1:
                        loadXmlFile();
                        break;
                    case 2:
                        showEvents();
                        break;
                    case 3:
                        showEventStatus();
                        break;
                    case 4:
                        buyShares();
                        break;
                    case 5:
                        closeEvent();
                        break;
                    case 6:
                        running = false;
                        System.out.println("Exiting...");
                        break;
                    default:
                        System.out.println("Invalid option. Please select a number between 1 and 6.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void loadXmlFile() {
        System.out.print("Enter full path to XML file: ");
        String path = scanner.nextLine().trim();
        if (path.isEmpty()) {
            System.out.println("Error: Path cannot be empty.");
            return;
        }

        try {
            engine.loadSystemData(path);
            System.out.println("System details loaded successfully from XML.");
        } catch (il.ac.mta.gm.engine.exception.GuessMarketException e) {
            System.out.println("Error loading XML: " + e.getMessage());
            if (e instanceof il.ac.mta.gm.engine.exception.XmlLoadException) {
                il.ac.mta.gm.engine.exception.XmlLoadException xmlEx = (il.ac.mta.gm.engine.exception.XmlLoadException) e;
                System.out.println("Failure Code: " + xmlEx.getErrorCode());
                if (xmlEx.getEventId() != null) {
                    System.out.println("Event ID: " + xmlEx.getEventId());
                }
                if (xmlEx.getDetails() != null) {
                    System.out.println("Details: " + xmlEx.getDetails());
                }
            }
        }
    }

    private void showEvents() {
        List<EventDisplayDTO> events = engine.getEvents();
        formatter.printEvents(events);
    }

    private void showEventStatus() {
        List<EventDisplayDTO> events = engine.getEvents();
        if (events.isEmpty()) {
            System.out.println("No events available.");
            return;
        }

        formatter.printEvents(events);
        System.out.print("Select an event by its list number: ");
        String input = scanner.nextLine().trim();

        try {
            int listIndex = Integer.parseInt(input);
            if (listIndex < 1 || listIndex > events.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            int eventId = events.get(listIndex - 1).id;
            showEventStatus(eventId);

        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Must be a number.");
        }
    }

    private void showEventStatus(int eventId) {
        formatter.printEventStatus(engine.getEventStatus(eventId));
    }

    private void buyShares() {
        List<il.ac.mta.gm.dto.EventDisplayDTO> allEvents = engine.getEvents();
        List<il.ac.mta.gm.dto.EventDisplayDTO> activeEvents = new java.util.ArrayList<>();
        
        for (il.ac.mta.gm.dto.EventDisplayDTO e : allEvents) {
            if (e.status == il.ac.mta.gm.dto.EventStatus.ACTIVE) {
                activeEvents.add(e);
            }
        }
        
        if (activeEvents.isEmpty()) {
            System.out.println("No active events available for trading.");
            return;
        }

        System.out.println("\n--- Active Events ---");
        for (int i = 0; i < activeEvents.size(); i++) {
            System.out.printf("%d. [ID: %d] %s\n", (i + 1), activeEvents.get(i).id, activeEvents.get(i).name);
        }

        System.out.print("\nSelect an event by its list number: ");
        int selection;
        try {
            selection = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
            return;
        }

        if (selection < 1 || selection > activeEvents.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        int eventId = activeEvents.get(selection - 1).id;
        
        // Show current trading status
        System.out.println("\n--- Current Event Status ---");
        showEventStatus(eventId);
        
        System.out.println("\nOptions:");
        System.out.println("1. Option 1");
        System.out.println("2. Option 2");
        System.out.print("Select an option (1 or 2): ");
        int optionChoice;
        try {
            optionChoice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter 1 or 2.");
            return;
        }
        
        if (optionChoice != 1 && optionChoice != 2) {
            System.out.println("Invalid option selection.");
            return;
        }
        
        int optionIndex = optionChoice - 1;
        
        System.out.print("Enter quantity of shares to buy: ");
        int quantity;
        try {
            quantity = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a positive integer.");
            return;
        }
        
        try {
            il.ac.mta.gm.dto.TradeResultDTO result = engine.buyShares(eventId, optionIndex, quantity);
            System.out.println("\n--- Purchase Successful ---");
            System.out.printf("Option: %s\n", result.optionName);
            System.out.printf("Quantity: %d\n", result.quantity);
            System.out.printf("Shares cost: %.2f\n", result.sharesCost);
            if (result.commissionPaid > 0) {
                System.out.printf("Commission: %.2f\n", result.commissionPaid);
            }
            System.out.printf("Total paid: %.2f\n", result.totalPaid);
            
            System.out.println("\n--- Updated Event Status ---");
            showEventStatus(eventId);
            
        } catch (il.ac.mta.gm.engine.exception.TradingException e) {
            System.out.println("Trade failed: " + e.getMessage() + " (Code: " + e.getErrorCode() + ")");
        } catch (Exception e) {
            System.out.println("An unexpected error occurred during trading.");
        }
    }

    private void closeEvent() {
        List<il.ac.mta.gm.dto.EventDisplayDTO> allEvents = engine.getEvents();
        List<il.ac.mta.gm.dto.EventDisplayDTO> activeEvents = new java.util.ArrayList<>();
        
        for (il.ac.mta.gm.dto.EventDisplayDTO e : allEvents) {
            if (e.status == il.ac.mta.gm.dto.EventStatus.ACTIVE) {
                activeEvents.add(e);
            }
        }
        
        if (activeEvents.isEmpty()) {
            System.out.println("No active events available to close.");
            return;
        }

        System.out.println("\n--- Active Events ---");
        for (int i = 0; i < activeEvents.size(); i++) {
            System.out.println((i + 1) + ". [ID: " + activeEvents.get(i).id + "] " + activeEvents.get(i).name);
        }

        System.out.print("\nSelect an event to close by its list number: ");
        int selection;
        try {
            selection = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
            return;
        }

        if (selection < 1 || selection > activeEvents.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        int eventId = activeEvents.get(selection - 1).id;
        
        System.out.println("\n--- Event Options ---");
        System.out.println("1. " + activeEvents.get(selection - 1).optionNames.get(0));
        System.out.println("2. " + activeEvents.get(selection - 1).optionNames.get(1));
        System.out.print("Select the winning option (1 or 2): ");
        
        int optionChoice;
        try {
            optionChoice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter 1 or 2.");
            return;
        }
        
        if (optionChoice != 1 && optionChoice != 2) {
            System.out.println("Invalid option selection.");
            return;
        }
        
        int winningOptionIndex = optionChoice - 1;
        
        try {
            il.ac.mta.gm.dto.CloseEventResultDTO result = engine.closeEvent(eventId, winningOptionIndex);
            System.out.println("\n--- Settlement Successful ---");
            System.out.printf("Winner: %s\n", result.winningOptionName);
            System.out.printf("Winning shares: %d\n", result.winningShares);
            System.out.printf("Gross payout: %.2f\n", result.grossPayout);
            if (result.commissionPaid > 0) {
                System.out.printf("Commission collected: %.2f\n", result.commissionPaid);
            }
            System.out.printf("Net payout: %.2f\n", result.netPayout);
            System.out.printf("Final MM Account Balance: %.2f\n", result.finalAccountBalance);
            
            System.out.println("\n--- Updated Event Status ---");
            showEventStatus(eventId);
            
        } catch (il.ac.mta.gm.engine.exception.TradingException e) {
            System.out.println("Close failed: " + e.getMessage() + " (Code: " + e.getErrorCode() + ")");
        } catch (Exception e) {
            System.out.println("An unexpected error occurred during event closing.");
        }
    }
}
