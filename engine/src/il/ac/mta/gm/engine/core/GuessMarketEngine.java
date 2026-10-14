package il.ac.mta.gm.engine.core;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.OptionStatusDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.CommissionType;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.OrderBookConfigDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.dto.LmsrParticipationDTO;
import il.ac.mta.gm.dto.UserTradeRecordDTO;
import il.ac.mta.gm.dto.OrderBookPositionDTO;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.TradeRecord;
import il.ac.mta.gm.engine.model.domain.User;
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;
import il.ac.mta.gm.engine.model.domain.LmsrParticipation;
import il.ac.mta.gm.engine.model.domain.UserTradeRecord;
import il.ac.mta.gm.engine.model.domain.OrderBookPosition;
import il.ac.mta.gm.engine.model.domain.BalanceComponent;
import il.ac.mta.gm.engine.model.domain.AccountTransactionType;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import il.ac.mta.gm.engine.xml.XmlLoader;
import il.ac.mta.gm.engine.xml.LoadedSystemData;
import il.ac.mta.gm.engine.xml.SystemXmlLoader;

public class GuessMarketEngine implements EngineInterface {

    private final SystemState state;
    private final SystemXmlLoader systemXmlLoader;
    private final OrderBookService orderBookService;

    public GuessMarketEngine(SystemState state) {
        this.state = state;
        this.systemXmlLoader = new SystemXmlLoader(new XmlLoader());
        this.orderBookService = new OrderBookService(state);
    }

    @Override
    public void loadSystemData(String filePath) {
        LoadedSystemData candidate = systemXmlLoader.load(filePath);
        state.replaceSystem(candidate.getEvents(), candidate.getUsers());
    }

    @Override
    public List<EventDisplayDTO> getEvents() {
        List<EventDisplayDTO> dtos = new ArrayList<>();
        for (GMEvent event : state.getEvents()) {
            List<String> optionNames = new ArrayList<>();
            for (GMOption option : event.getOptions()) {
                optionNames.add(option.getName());
            }
            OrderBookConfig orderBookConfig = event.getOrderBookConfig();
            dtos.add(new EventDisplayDTO(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getCommission().getPercentage(),
                CommissionType.valueOf(event.getCommission().getType().name()), // Mapped to DTO enum
                optionNames,
                EventStatus.valueOf(event.getStatus().name()), // Mapped to DTO enum
                EventMethodType.valueOf(event.getMethodType().name()),
                event.getMethodType() == il.ac.mta.gm.engine.model.domain.EventMethodType.LMSR
                    ? event.getMarket().getB() : null,
                orderBookConfig == null ? null : new OrderBookConfigDTO(
                    orderBookConfig.isAllowMint(), orderBookConfig.getInitial(), orderBookConfig.getD())
            ));
        }
        return dtos;
    }

    @Override
    public EventStatusDTO getEventStatus(int eventId) {
        GMEvent event = state.getEventById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }

        List<OptionStatusDTO> optionDtos = new ArrayList<>();
        GMOption[] options = event.getOptions();
        int[] q = event.getMethodType() == il.ac.mta.gm.engine.model.domain.EventMethodType.LMSR
            ? event.getMarket().getQ() : new int[event.getOptions().length];
        
        for (int i = 0; i < options.length; i++) {
            optionDtos.add(new OptionStatusDTO(
                options[i].getName(),
                event.getMethodType() == il.ac.mta.gm.engine.model.domain.EventMethodType.LMSR
                    ? event.getMarket().computeOptionPrice(i) : 0.0,
                q[i]
            ));
        }

        List<TradeRecordDTO> historyDtos = new ArrayList<>();
        List<TradeRecord> history = event.getTradeHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            TradeRecord r = history.get(i);
            historyDtos.add(new TradeRecordDTO(
                r.getOptionName(), r.getQuantity(), r.getSharesCost(), 
                r.getCommissionPaid(), r.getTotalPaid()
            ));
        }

        String winningOptionName = null;
        if (event.getWinningOptionIndex() != null) {
            winningOptionName = event.getOptions()[event.getWinningOptionIndex()].getName();
        }

        return new EventStatusDTO(
            event.getId(),
            event.getName(),
            EventStatus.valueOf(event.getStatus().name()), // Mapped to DTO enum
            optionDtos,
            event.getAccount().getBalance(),
            event.getAccount().getTotalFeesCollected(),
            historyDtos,
            winningOptionName
        );
    }

    @Override
    public TradeResultDTO buyShares(int eventId, int optionIndex, int quantity) {
        // 1. Validate Event exists
        GMEvent event = state.getEventById(eventId);
        if (event == null) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.EVENT_NOT_FOUND, 
                eventId, optionIndex, null, "Event not found with ID: " + eventId);
        }

        if (!event.isLegacyExercise1()) {
            throw new TradingException(TradingErrorCode.USER_REQUIRED, eventId, optionIndex,
                null, "Exercise 2 purchases require an acting user.");
        }

        // 2. Validate Event is ACTIVE
        if (event.getStatus() != il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.EVENT_NOT_ACTIVE, 
                eventId, optionIndex, "status=" + event.getStatus(), "Event is not ACTIVE.");
        }

        // 3. Validate Option Index
        if (optionIndex < 0 || optionIndex > 1) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.INVALID_OPTION, 
                eventId, optionIndex, "index=" + optionIndex, "Invalid option index.");
        }

        // 4. Validate Quantity
        if (quantity <= 0) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.INVALID_QUANTITY, 
                eventId, optionIndex, "quantity=" + quantity, "Quantity must be strictly positive.");
        }

        // 5. Quote Purchase (Calculates sharesCost without mutating q)
        double sharesCost = event.getMarket().quotePurchase(optionIndex, quantity);
        
        // 6. Calculate Commission and Total Paid
        double commissionPaid = 0.0;
        if (event.getCommission().getType() == il.ac.mta.gm.engine.model.domain.CommissionType.ON_PURCHASE) {
            commissionPaid = sharesCost * (event.getCommission().getPercentage() / 100.0);
        }
        double totalPaid = sharesCost + commissionPaid;
        
        // 7. Prepare TradeRecord Data
        String optionName = event.getOptions()[optionIndex].getName();
        TradeRecord record = new TradeRecord(optionName, quantity, sharesCost, commissionPaid, totalPaid);

        // --- All validation and calculation completed. State mutation begins exactly once here. ---
        
        event.getMarket().commitPurchase(optionIndex, quantity);
        event.getAccount().applyPurchase(sharesCost, commissionPaid, event.getCommission().getType());
        event.addTradeRecord(record);
        
        // --- State mutation complete ---
        
        return new TradeResultDTO(eventId, optionName, quantity, sharesCost, commissionPaid, totalPaid);
    }

    @Override
    public il.ac.mta.gm.dto.CloseEventResultDTO closeEvent(int eventId, int winningOptionIndex) {
        // 1. Validate Event exists
        GMEvent event = state.getEventById(eventId);
        if (event == null) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.EVENT_NOT_FOUND, 
                eventId, winningOptionIndex, null, "Event not found with ID: " + eventId);
        }


        if (!event.isLegacyExercise1()) {
            throw new TradingException(TradingErrorCode.USER_REQUIRED, eventId,
                winningOptionIndex, null, "Exercise 2 close requires an acting user.");
        }

        // 2. Validate Event is ACTIVE
        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.CLOSED) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.EVENT_ALREADY_CLOSED, 
                eventId, winningOptionIndex, "status=" + event.getStatus(), "Event is already CLOSED.");
        }
        
        if (event.getStatus() != il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.EVENT_NOT_ACTIVE, 
                eventId, winningOptionIndex, "status=" + event.getStatus(), "Event is not ACTIVE.");
        }

        // 3. Validate Option Index
        if (winningOptionIndex < 0 || winningOptionIndex > 1) {
            throw new il.ac.mta.gm.engine.exception.TradingException(
                il.ac.mta.gm.engine.exception.TradingErrorCode.INVALID_OPTION, 
                eventId, winningOptionIndex, "index=" + winningOptionIndex, "Invalid option index.");
        }

        // 4. Calculate payouts
        int winningShares = event.getMarket().getQ()[winningOptionIndex];
        double grossPayout = winningShares * 1.0;
        
        double commissionPaid = 0.0;
        if (event.getCommission().getType() == il.ac.mta.gm.engine.model.domain.CommissionType.ON_CLOSE) {
            commissionPaid = grossPayout * (event.getCommission().getPercentage() / 100.0);
        }
        
        double netPayout = grossPayout - commissionPaid;
        String optionName = event.getOptions()[winningOptionIndex].getName();

        // --- All validation and calculation completed. State mutation begins exactly once here. ---
        
        event.closeWithWinner(winningOptionIndex);
        event.getAccount().applySettlement(grossPayout, commissionPaid);
        
        // --- State mutation complete ---
        
        return new il.ac.mta.gm.dto.CloseEventResultDTO(
            eventId, optionName, winningShares, grossPayout, commissionPaid, netPayout, event.getAccount().getBalance()
        );
    }

    @Override
    public void startEvent(int eventId, String userName) {
        GMEvent event = requireEvent(eventId, null);
        User actor = requireUser(userName, eventId, null);
        requireMarketMaker(event, actor, null);
        if (event.getMethodType()
                == il.ac.mta.gm.engine.model.domain.EventMethodType.ORDER_BOOK) {
            orderBookService.start(event, actor);
            return;
        }
        requireLmsr(event, null);

        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw tradeError(TradingErrorCode.EVENT_ALREADY_ACTIVE, eventId, userName, null,
                "status=ACTIVE", "Event is already active.");
        }
        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.CLOSED) {
            throw tradeError(TradingErrorCode.EVENT_ALREADY_CLOSED, eventId, userName, null,
                "status=CLOSED", "Closed event cannot be started.");
        }

        double subsidy = event.getMarket().computeC();
        requirePositiveFinite(subsidy, eventId, userName, null, "subsidy");
        validateUserDebit(actor, subsidy, eventId, null);
        validateEventCredit(event, subsidy, userName, null);

        actor.getAccount().debitWithLedger(subsidy, List.of(new BalanceComponent(
            AccountTransactionType.MM_INITIAL_FUNDING, -subsidy, event.getName(),
            "LMSR initial funding")));
        event.getAccount().credit(subsidy);
        event.startEvent();
    }

    @Override
    public TradeResultDTO buyShares(int eventId, String userName, int optionIndex, int quantity) {
        GMEvent event = requireEvent(eventId, optionIndex);
        User buyer = requireUser(userName, eventId, optionIndex);
        requireLmsr(event, optionIndex);
        if (event.getStatus() != il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw tradeError(TradingErrorCode.EVENT_NOT_ACTIVE, eventId, userName, optionIndex,
                "status=" + event.getStatus(), "Event is not ACTIVE.");
        }
        if (optionIndex < 0 || optionIndex >= event.getOptions().length) {
            throw tradeError(TradingErrorCode.INVALID_OPTION, eventId, userName, optionIndex,
                "index=" + optionIndex, "Invalid option index.");
        }
        if (quantity <= 0) {
            throw tradeError(TradingErrorCode.INVALID_QUANTITY, eventId, userName, optionIndex,
                "quantity=" + quantity, "Quantity must be strictly positive.");
        }

        User marketMaker = state.getMarketMakerForEvent(eventId);
        if (marketMaker == null) {
            throw tradeError(TradingErrorCode.USER_NOT_MARKET_MAKER, eventId, userName,
                optionIndex, null, "Event has no Market Maker.");
        }

        double sharesCost;
        try {
            sharesCost = event.getMarket().quotePurchase(optionIndex, quantity);
        } catch (RuntimeException e) {
            throw numericError(eventId, userName, optionIndex, "Unable to calculate LMSR quote.", e);
        }
        requirePositiveFinite(sharesCost, eventId, userName, optionIndex, "sharesCost");

        double commissionPaid = event.getCommission().getType()
            == il.ac.mta.gm.engine.model.domain.CommissionType.ON_PURCHASE
            ? sharesCost * (event.getCommission().getPercentage() / 100.0) : 0.0;
        double totalPaid = sharesCost + commissionPaid;
        requireNonNegativeFinite(commissionPaid, eventId, userName, optionIndex, "commission");
        requirePositiveFinite(totalPaid, eventId, userName, optionIndex, "totalPaid");

        String optionName = event.getOptions()[optionIndex].getName();
        LmsrParticipation existing = buyer.getLmsrParticipation(eventId);
        LmsrParticipation participation = existing == null
            ? new LmsrParticipation(eventId, event.getOptions().length) : existing;
        List<BalanceComponent> buyerComponents = new ArrayList<>();
        buyerComponents.add(new BalanceComponent(AccountTransactionType.BUY, -sharesCost,
            event.getName(), "LMSR purchase principal"));
        List<BalanceComponent> makerComponents = List.of();
        if (commissionPaid > 0.0) {
            buyerComponents.add(new BalanceComponent(AccountTransactionType.COMMISSION_PAID,
                -commissionPaid, event.getName(), "LMSR purchase commission"));
            makerComponents = List.of(new BalanceComponent(AccountTransactionType.COMMISSION_RECEIVED,
                commissionPaid, event.getName(), "LMSR purchase commission income"));
        }
        try {
            participation.validatePurchase(optionIndex, quantity, totalPaid, commissionPaid);
            validateUserDebit(buyer, totalPaid, eventId, optionIndex);
            buyer.getAccount().validateLedgerComponents(-totalPaid, buyerComponents);
            validateEventCredit(event, sharesCost, userName, optionIndex);
            if (commissionPaid > 0.0) {
                marketMaker.getAccount().validateCredit(commissionPaid);
                marketMaker.getAccount().validateLedgerComponents(commissionPaid, makerComponents);
            }
        } catch (TradingException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw tradeError(TradingErrorCode.INSUFFICIENT_FUNDS, eventId, userName, optionIndex,
                "balance=" + buyer.getAccount().getBalance() + ", required=" + totalPaid,
                "User has insufficient funds.");
        } catch (RuntimeException e) {
            throw numericError(eventId, userName, optionIndex, "Invalid purchase result.", e);
        }

        TradeRecord globalRecord = new TradeRecord(optionName, quantity,
            sharesCost, commissionPaid, totalPaid);
        buyer.getAccount().debitWithLedger(totalPaid, buyerComponents);
        event.getAccount().credit(sharesCost);
        if (commissionPaid > 0.0) {
            marketMaker.getAccount().creditWithLedger(commissionPaid, makerComponents);
        }
        event.getMarket().commitPurchase(optionIndex, quantity);
        if (existing == null) {
            buyer.addLmsrParticipation(participation);
        }
        participation.applyPurchase(optionIndex, quantity, sharesCost, commissionPaid,
            totalPaid, optionName);
        event.addTradeRecord(globalRecord);

        return new TradeResultDTO(eventId, optionName, quantity, sharesCost,
            commissionPaid, totalPaid);
    }

    @Override
    public il.ac.mta.gm.dto.CloseEventResultDTO closeEvent(int eventId, String userName,
                                                           int winningOptionIndex) {
        GMEvent event = requireEvent(eventId, winningOptionIndex);
        User actor = requireUser(userName, eventId, winningOptionIndex);
        requireMarketMaker(event, actor, winningOptionIndex);
        if (event.getMethodType()
                == il.ac.mta.gm.engine.model.domain.EventMethodType.ORDER_BOOK) {
            return orderBookService.close(event, actor, winningOptionIndex);
        }
        requireLmsr(event, winningOptionIndex);
        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.CLOSED) {
            throw tradeError(TradingErrorCode.EVENT_ALREADY_CLOSED, eventId, userName,
                winningOptionIndex, "status=CLOSED", "Event is already closed.");
        }
        if (event.getStatus() != il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw tradeError(TradingErrorCode.EVENT_NOT_ACTIVE, eventId, userName,
                winningOptionIndex, "status=" + event.getStatus(), "Event is not ACTIVE.");
        }
        if (winningOptionIndex < 0 || winningOptionIndex >= event.getOptions().length) {
            throw tradeError(TradingErrorCode.INVALID_OPTION, eventId, userName,
                winningOptionIndex, "index=" + winningOptionIndex, "Invalid winning option.");
        }

        User marketMaker = state.getMarketMakerForEvent(eventId);
        List<SettlementLine> lines = new ArrayList<>();
        double totalGross = 0.0;
        double totalCommission = 0.0;
        double totalNet = 0.0;
        int totalWinningShares = 0;
        try {
            for (User user : state.getUsers()) {
                LmsrParticipation participation = user.getLmsrParticipation(eventId);
                if (participation == null) {
                    continue;
                }
                int winningShares = participation.getHoldings()[winningOptionIndex];
                double gross = winningShares;
                double commission = event.getCommission().getType()
                    == il.ac.mta.gm.engine.model.domain.CommissionType.ON_CLOSE
                    ? gross * (event.getCommission().getPercentage() / 100.0) : 0.0;
                double net = gross - commission;
                requireNonNegativeFinite(gross, eventId, userName, winningOptionIndex, "grossPayout");
                requireNonNegativeFinite(commission, eventId, userName, winningOptionIndex,
                    "closingCommission");
                requireNonNegativeFinite(net, eventId, userName, winningOptionIndex, "netPayout");
                participation.validateSettlement(gross, commission, net);
                totalWinningShares = Math.addExact(totalWinningShares, winningShares);
                totalGross = checkedSum(totalGross, gross);
                totalCommission = checkedSum(totalCommission, commission);
                totalNet = checkedSum(totalNet, net);
                lines.add(new SettlementLine(user, participation, gross, commission, net));
            }
        } catch (TradingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw numericError(eventId, userName, winningOptionIndex,
                "Invalid settlement calculation.", e);
        }

        double eventBalance = event.getAccount().getBalance();
        if (!Double.isFinite(eventBalance) || totalGross > eventBalance) {
            throw tradeError(TradingErrorCode.INSUFFICIENT_EVENT_FUNDS, eventId, userName,
                winningOptionIndex, "balance=" + eventBalance + ", gross=" + totalGross,
                "Event account cannot cover winning shares.");
        }
        double residual = eventBalance - totalGross;
        requireNonNegativeFinite(residual, eventId, userName, winningOptionIndex, "residual");
        double mmReceipt;
        try {
            mmReceipt = checkedSum(totalCommission, residual);
        } catch (RuntimeException e) {
            throw numericError(eventId, userName, winningOptionIndex,
                "Invalid Market Maker settlement amount.", e);
        }

        Map<User, Double> credits = new LinkedHashMap<>();
        Map<User, List<BalanceComponent>> ledgerComponents = new LinkedHashMap<>();
        try {
            for (SettlementLine line : lines) {
                if (line.netPayout > 0.0) {
                    credits.merge(line.user, line.netPayout, GuessMarketEngine::checkedSum);
                    ledgerComponents.computeIfAbsent(line.user, ignored -> new ArrayList<>())
                        .add(new BalanceComponent(AccountTransactionType.SETTLEMENT_PAYOUT,
                            line.grossPayout, event.getName(), "LMSR winning payout"));
                    if (line.commission > 0.0) {
                        ledgerComponents.get(line.user).add(new BalanceComponent(
                            AccountTransactionType.COMMISSION_PAID, -line.commission,
                            event.getName(), "LMSR closing commission"));
                    }
                }
            }
            if (mmReceipt > 0.0) {
                credits.merge(marketMaker, mmReceipt, GuessMarketEngine::checkedSum);
                List<BalanceComponent> makerComponents = ledgerComponents.computeIfAbsent(
                    marketMaker, ignored -> new ArrayList<>());
                if (totalCommission > 0.0) makerComponents.add(new BalanceComponent(
                    AccountTransactionType.COMMISSION_RECEIVED, totalCommission,
                    event.getName(), "LMSR closing commission income"));
                if (residual > 0.0) makerComponents.add(new BalanceComponent(
                    AccountTransactionType.RESIDUAL_RETURN, residual,
                    event.getName(), "LMSR residual return"));
            }
            for (Map.Entry<User, Double> credit : credits.entrySet()) {
                credit.getKey().getAccount().validateCredit(credit.getValue());
                credit.getKey().getAccount().validateLedgerComponents(credit.getValue(),
                    ledgerComponents.get(credit.getKey()));
            }
            if (eventBalance > 0.0) {
                event.getAccount().validateDebit(eventBalance);
            }
        } catch (RuntimeException e) {
            throw numericError(eventId, userName, winningOptionIndex,
                "Settlement would produce an invalid balance.", e);
        }

        if (eventBalance > 0.0) {
            event.getAccount().debit(eventBalance);
        }
        for (Map.Entry<User, Double> credit : credits.entrySet()) {
            credit.getKey().getAccount().creditWithLedger(credit.getValue(),
                ledgerComponents.get(credit.getKey()));
        }
        for (SettlementLine line : lines) {
            line.participation.applySettlement(line.grossPayout, line.commission,
                line.netPayout);
        }
        event.closeWithWinner(winningOptionIndex);

        return new il.ac.mta.gm.dto.CloseEventResultDTO(eventId,
            event.getOptions()[winningOptionIndex].getName(), totalWinningShares, totalGross,
            totalCommission, totalNet, event.getAccount().getBalance());
    }

    @Override
    public List<UserSummaryDTO> getUsers() {
        List<UserSummaryDTO> result = new ArrayList<>();
        for (User user : state.getUsers()) {
            result.add(toUserSummaryDTO(user));
        }
        return List.copyOf(result);
    }

    @Override
    public UserSummaryDTO getUser(String userName) {
        User user = state.getUser(userName);
        if (user == null) {
            throw new IllegalArgumentException("User not found with name: " + userName);
        }
        return toUserSummaryDTO(user);
    }

    private UserSummaryDTO toUserSummaryDTO(User user) {
        List<LmsrParticipationDTO> participations = new ArrayList<>();
        for (LmsrParticipation participation : user.getLmsrParticipations()) {
            GMEvent event = state.getEventById(participation.getEventId());
            if (event == null) {
                continue;
            }
            GMOption[] options = event.getOptions();
            int[] holdings = participation.getHoldings();
            List<UserTradeRecordDTO> history = new ArrayList<>();
            List<UserTradeRecord> domainHistory = participation.getTradeHistory();
            for (int i = domainHistory.size() - 1; i >= 0; i--) {
                UserTradeRecord record = domainHistory.get(i);
                history.add(new UserTradeRecordDTO(record.getEventId(), record.getOptionName(),
                    record.getQuantity(), record.getSharesCost(), record.getCommissionPaid(),
                    record.getTotalPaid()));
            }
            String winner = event.getWinningOptionIndex() == null ? null
                : options[event.getWinningOptionIndex()].getName();
            List<String> optionNames = new ArrayList<>();
            List<Integer> quantities = new ArrayList<>();
            for (int i = 0; i < options.length; i++) {
                optionNames.add(options[i].getName());
                quantities.add(holdings[i]);
            }
            participations.add(new LmsrParticipationDTO(event.getId(), event.getName(),
                optionNames, quantities, participation.getAmountPaid(),
                participation.getTotalCommissionPaid(), history,
                EventStatus.valueOf(event.getStatus().name()), winner,
                participation.isSettled(), participation.getGrossPayout(),
                participation.getClosingCommission(), participation.getNetPayout()));
        }
        List<OrderBookPositionDTO> orderBookPositions = new ArrayList<>();
        for (OrderBookPosition position : user.getOrderBookPositions()) {
            GMEvent event = state.getEventById(position.getEventId());
            if (event != null && event.getMethodType()
                    == il.ac.mta.gm.engine.model.domain.EventMethodType.ORDER_BOOK) {
                orderBookPositions.add(orderBookService.toPositionDto(event, user, position));
            }
        }
        return new UserSummaryDTO(user.getName(), user.getAccount().getBalance(),
            user.getMarketMakerEventIds(), participations, orderBookPositions);
    }

    @Override
    public OrderResultDTO placeOrder(int eventId, String userName, int optionIndex,
                                     il.ac.mta.gm.dto.OrderSide side, int quantity,
                                     double limitPrice) {
        GMEvent event = requireEvent(eventId, optionIndex);
        User user = requireUser(userName, eventId, optionIndex);
        if (event.getMethodType()
                != il.ac.mta.gm.engine.model.domain.EventMethodType.ORDER_BOOK) {
            throw tradeError(TradingErrorCode.WRONG_MARKET_METHOD, eventId, userName,
                optionIndex, "method=" + event.getMethodType(),
                "Operation is supported only for ORDER_BOOK events.");
        }
        return orderBookService.placeOrder(event, user, optionIndex, side, quantity, limitPrice);
    }

    @Override
    public OrderBookDetailsDTO getOrderBook(int eventId) {
        GMEvent event = requireEvent(eventId, null);
        if (event.getMethodType()
                != il.ac.mta.gm.engine.model.domain.EventMethodType.ORDER_BOOK) {
            throw tradeError(TradingErrorCode.WRONG_MARKET_METHOD, eventId, null, null,
                "method=" + event.getMethodType(),
                "Event is not an ORDER_BOOK event.");
        }
        return orderBookService.details(event);
    }

    private GMEvent requireEvent(int eventId, Integer optionIndex) {
        GMEvent event = state.getEventById(eventId);
        if (event == null) {
            throw tradeError(TradingErrorCode.EVENT_NOT_FOUND, eventId, null, optionIndex,
                null, "Event not found with ID: " + eventId);
        }
        return event;
    }

    private User requireUser(String userName, int eventId, Integer optionIndex) {
        User user = userName == null ? null : state.getUser(userName.trim());
        if (user == null) {
            throw tradeError(TradingErrorCode.USER_NOT_FOUND, eventId, userName, optionIndex,
                "user=" + userName, "User not found: " + userName);
        }
        return user;
    }

    private void requireMarketMaker(GMEvent event, User actor, Integer optionIndex) {
        User marketMaker = state.getMarketMakerForEvent(event.getId());
        if (marketMaker == null || marketMaker != actor) {
            throw tradeError(TradingErrorCode.USER_NOT_MARKET_MAKER, event.getId(),
                actor.getName(), optionIndex, "user=" + actor.getName(),
                "Only the event Market Maker may perform this operation.");
        }
    }

    private void requireLmsr(GMEvent event, Integer optionIndex) {
        if (event.getMethodType()
                != il.ac.mta.gm.engine.model.domain.EventMethodType.LMSR) {
            throw tradeError(TradingErrorCode.WRONG_MARKET_METHOD, event.getId(), null,
                optionIndex, "method=" + event.getMethodType(),
                "Operation is supported only for LMSR events.");
        }
    }

    private void validateUserDebit(User user, double amount, int eventId, Integer optionIndex) {
        try {
            user.getAccount().validateDebit(amount);
        } catch (IllegalStateException e) {
            throw tradeError(TradingErrorCode.INSUFFICIENT_FUNDS, eventId, user.getName(),
                optionIndex, "balance=" + user.getAccount().getBalance() + ", required=" + amount,
                "User has insufficient funds.");
        } catch (RuntimeException e) {
            throw numericError(eventId, user.getName(), optionIndex,
                "Invalid debit amount.", e);
        }
    }

    private void validateEventCredit(GMEvent event, double amount, String userName,
                                     Integer optionIndex) {
        try {
            event.getAccount().validateCredit(amount);
        } catch (RuntimeException e) {
            throw numericError(event.getId(), userName, optionIndex,
                "Event credit would produce an invalid balance.", e);
        }
    }

    private static void requirePositiveFinite(double value, int eventId, String userName,
                                              Integer optionIndex, String field) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw numericError(eventId, userName, optionIndex,
                field + " must be finite and positive.", null);
        }
    }

    private static void requireNonNegativeFinite(double value, int eventId, String userName,
                                                 Integer optionIndex, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw numericError(eventId, userName, optionIndex,
                field + " must be finite and non-negative.", null);
        }
    }

    private static double checkedSum(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result) || result < 0.0) {
            throw new IllegalArgumentException("Non-finite financial sum.");
        }
        return result;
    }

    private static TradingException numericError(int eventId, String userName,
                                                 Integer optionIndex, String message,
                                                 Throwable cause) {
        TradingException error = tradeError(TradingErrorCode.INVALID_NUMERIC_RESULT,
            eventId, userName, optionIndex, cause == null ? null : cause.getMessage(), message);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    private static TradingException tradeError(TradingErrorCode code, int eventId,
                                               String userName, Integer optionIndex,
                                               String details, String message) {
        return new TradingException(code, eventId, userName, optionIndex, details, message);
    }

    private static final class SettlementLine {
        private final User user;
        private final LmsrParticipation participation;
        private final double grossPayout;
        private final double commission;
        private final double netPayout;

        private SettlementLine(User user, LmsrParticipation participation,
                               double grossPayout, double commission, double netPayout) {
            this.user = user;
            this.participation = participation;
            this.grossPayout = grossPayout;
            this.commission = commission;
            this.netPayout = netPayout;
        }
    }
}
