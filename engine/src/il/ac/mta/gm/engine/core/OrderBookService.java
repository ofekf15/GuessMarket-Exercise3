package il.ac.mta.gm.engine.core;

import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import il.ac.mta.gm.dto.OrderBookOptionDTO;
import il.ac.mta.gm.dto.OrderBookPositionDTO;
import il.ac.mta.gm.dto.OrderBookStatsDTO;
import il.ac.mta.gm.dto.OrderExecutionType;
import il.ac.mta.gm.dto.OrderFillDTO;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.dto.RestingOrderDTO;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.ExecutionKind;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.Order;
import il.ac.mta.gm.engine.model.domain.OrderBookExecution;
import il.ac.mta.gm.engine.model.domain.OrderBookPosition;
import il.ac.mta.gm.engine.model.domain.OrderBookState;
import il.ac.mta.gm.engine.model.domain.User;
import il.ac.mta.gm.engine.model.domain.BalanceComponent;
import il.ac.mta.gm.engine.model.domain.AccountTransactionType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OrderBookService {
    private static final double EPS = 1e-9;
    private final SystemState state;

    OrderBookService(SystemState state) {
        this.state = state;
    }

    void start(GMEvent event, User marketMaker) {
        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw error(TradingErrorCode.EVENT_ALREADY_ACTIVE, event.getId(),
                marketMaker.getName(), null, "Event is already active.");
        }
        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.CLOSED) {
            throw error(TradingErrorCode.EVENT_ALREADY_CLOSED, event.getId(),
                marketMaker.getName(), null, "Closed event cannot be started.");
        }
        int initial = event.getOrderBookConfig().getInitial();
        int d = event.getOrderBookConfig().getD();
        if (initial % d != 0) {
            throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                marketMaker.getName(), null, "initial must be divisible by d");
        }
        int pairs = initial / d;
        if (pairs <= 0) {
            throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                marketMaker.getName(), null, "Initial pair quantity must be positive.");
        }
        OrderBookPosition existing = marketMaker.getOrderBookPosition(event.getId());
        OrderBookPosition position = existing == null
            ? new OrderBookPosition(event.getId()) : existing;
        try {
            marketMaker.getAccount().validateDebit(initial);
            event.getAccount().validateCredit(initial);
            position.validateHoldingDelta(0, pairs);
            position.validateHoldingDelta(1, pairs);
        } catch (IllegalStateException e) {
            throw error(TradingErrorCode.INSUFFICIENT_FUNDS, event.getId(),
                marketMaker.getName(), null, "Market Maker has insufficient funds.");
        } catch (RuntimeException e) {
            throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                marketMaker.getName(), null, e.getMessage());
        }

        marketMaker.getAccount().debitWithLedger(initial, List.of(new BalanceComponent(
            AccountTransactionType.MM_INITIAL_FUNDING, -initial, event.getName(),
            "Order Book initial funding")));
        event.getAccount().credit(initial);
        if (existing == null) position = marketMaker.getOrCreateOrderBookPosition(event.getId());
        position.applyHoldingDelta(0, pairs);
        position.applyHoldingDelta(1, pairs);
        position.recordFunding(initial);
        event.startEvent();
    }

    OrderResultDTO placeOrder(GMEvent event, User incomingUser, int optionIndex,
                              il.ac.mta.gm.dto.OrderSide dtoSide, int quantity,
                              double limitPrice) {
        if (dtoSide == null) {
            throw error(TradingErrorCode.INVALID_ORDER_SIDE, event.getId(),
                incomingUser.getName(), optionIndex, "Order side is required.");
        }
        il.ac.mta.gm.engine.model.domain.OrderSide side =
            il.ac.mta.gm.engine.model.domain.OrderSide.valueOf(dtoSide.name());
        if (event.getStatus() != il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw error(TradingErrorCode.EVENT_NOT_ACTIVE, event.getId(), incomingUser.getName(),
                optionIndex, "Order Book event is not ACTIVE.");
        }
        if (optionIndex < 0 || optionIndex > 1) {
            throw error(TradingErrorCode.INVALID_OPTION, event.getId(), incomingUser.getName(),
                optionIndex, "Invalid option index.");
        }
        if (quantity <= 0) {
            throw error(TradingErrorCode.INVALID_QUANTITY, event.getId(), incomingUser.getName(),
                optionIndex, "Quantity must be positive.");
        }
        int d = event.getOrderBookConfig().getD();
        if (!Double.isFinite(limitPrice) || limitPrice < 0.01 || limitPrice > d - 0.01) {
            throw error(TradingErrorCode.INVALID_ORDER_PRICE, event.getId(), incomingUser.getName(),
                optionIndex, "Price must be in [0.01, d - 0.01].");
        }

        double rate = event.getCommission().getType() == CommissionType.ON_PURCHASE
            ? event.getCommission().getPercentage() / 100.0 : 0.0;
        double reservedPerShare = checked(limitPrice * (1.0 + rate), event, incomingUser,
            optionIndex, "reservation per share");
        double incomingReservation = side == il.ac.mta.gm.engine.model.domain.OrderSide.BUY
            ? checked(reservedPerShare * quantity, event, incomingUser, optionIndex,
                "order reservation") : 0.0;

        OrderBookPosition incomingPosition = incomingUser.getOrderBookPosition(event.getId());
        if (side == il.ac.mta.gm.engine.model.domain.OrderSide.BUY) {
            double available = incomingUser.getAccount().getBalance()
                - incomingUser.getTotalReservedCash();
            if (incomingReservation > available + EPS) {
                throw error(TradingErrorCode.INSUFFICIENT_AVAILABLE_FUNDS, event.getId(),
                    incomingUser.getName(), optionIndex, "Insufficient unreserved cash.");
            }
        } else {
            int available = incomingPosition == null ? 0
                : incomingPosition.getAvailableShares(optionIndex);
            if (quantity > available) {
                throw error(TradingErrorCode.INSUFFICIENT_AVAILABLE_SHARES, event.getId(),
                    incomingUser.getName(), optionIndex, "Insufficient unreserved shares.");
            }
        }

        OrderBookState book = event.getOrderBookState();
        Order incoming = book.createOrder(incomingUser, optionIndex, side, quantity, limitPrice,
            side == il.ac.mta.gm.engine.model.domain.OrderSide.BUY ? reservedPerShare : 0.0);
        List<FillPlan> fills = planFills(event, incoming, rate);
        int executed = fills.stream().mapToInt(fill -> fill.quantity).sum();
        int remaining = quantity - executed;

        MutationPlan mutations = buildMutationPlan(event, incoming, fills,
            incomingReservation, remaining);
        validateMutationPlan(event, incoming, mutations, incomingReservation, remaining);
        commitOrder(event, incoming, fills, incomingReservation, remaining, mutations);

        List<OrderFillDTO> incomingFills = new ArrayList<>();
        double principalPaid = 0.0;
        double principalReceived = 0.0;
        double commissionPaid = 0.0;
        for (FillPlan fill : fills) {
            FillAmounts amounts = fill.amountsFor(incomingUser);
            principalPaid += amounts.principalPaid;
            principalReceived += amounts.principalReceived;
            commissionPaid += amounts.commissionPaid;
            incomingFills.add(fill.toDtoFor(incomingUser));
        }
        return new OrderResultDTO(incoming.getId(), event.getId(), optionIndex, dtoSide,
            quantity, executed, remaining, principalPaid, principalReceived, commissionPaid,
            incomingFills);
    }

    il.ac.mta.gm.dto.CloseEventResultDTO close(GMEvent event, User marketMaker,
                                               int winningOptionIndex) {
        if (event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.CLOSED) {
            throw error(TradingErrorCode.EVENT_ALREADY_CLOSED, event.getId(),
                marketMaker.getName(), winningOptionIndex, "Event is already closed.");
        }
        if (event.getStatus() != il.ac.mta.gm.engine.model.domain.EventStatus.ACTIVE) {
            throw error(TradingErrorCode.EVENT_NOT_ACTIVE, event.getId(), marketMaker.getName(),
                winningOptionIndex, "Event is not ACTIVE.");
        }
        if (winningOptionIndex < 0 || winningOptionIndex > 1) {
            throw error(TradingErrorCode.INVALID_OPTION, event.getId(), marketMaker.getName(),
                winningOptionIndex, "Invalid winning option.");
        }

        int d = event.getOrderBookConfig().getD();
        double rate = event.getCommission().getType() == CommissionType.ON_CLOSE
            ? event.getCommission().getPercentage() / 100.0 : 0.0;
        Map<User, Double> credits = new LinkedHashMap<>();
        Map<User, List<BalanceComponent>> ledgerComponents = new IdentityHashMap<>();
        Map<User, Double> winnerNet = new IdentityHashMap<>();
        Map<User, Double> winnerCommission = new IdentityHashMap<>();
        int winningShares = 0;
        double totalGross = 0.0;
        double totalCommission = 0.0;
        for (User user : state.getUsers()) {
            OrderBookPosition position = user.getOrderBookPosition(event.getId());
            if (position == null) continue;
            int shares = position.getHoldings()[winningOptionIndex];
            winningShares = Math.addExact(winningShares, shares);
            double gross = checked((double) shares * d, event, user, winningOptionIndex, "gross");
            double commission = checked(gross * rate, event, user, winningOptionIndex,
                "closing commission");
            double net = checked(gross - commission, event, user, winningOptionIndex, "net payout");
            totalGross = sum(totalGross, gross, event, marketMaker, winningOptionIndex);
            totalCommission = sum(totalCommission, commission, event, marketMaker,
                winningOptionIndex);
            if (net > 0.0) {
                credits.merge(user, net, (a, b) -> sum(a, b, event, marketMaker,
                    winningOptionIndex));
                winnerNet.put(user, net);
                ledgerComponents.computeIfAbsent(user, ignored -> new ArrayList<>())
                    .add(new BalanceComponent(AccountTransactionType.SETTLEMENT_PAYOUT,
                        gross, event.getName(), "Order Book winning payout"));
                if (commission > 0.0) ledgerComponents.get(user).add(new BalanceComponent(
                    AccountTransactionType.COMMISSION_PAID, -commission, event.getName(),
                    "Order Book closing commission"));
            }
            if (commission > 0.0) winnerCommission.put(user, commission);
        }
        double eventBalance = event.getAccount().getBalance();
        if (!Double.isFinite(eventBalance) || totalGross > eventBalance) {
            throw error(TradingErrorCode.INSUFFICIENT_EVENT_FUNDS, event.getId(),
                marketMaker.getName(), winningOptionIndex, "Event cannot cover payouts.");
        }
        double residual = eventBalance - totalGross;
        double mmCredit = sum(totalCommission, residual, event, marketMaker, winningOptionIndex);
        if (mmCredit > 0.0) {
            credits.merge(marketMaker, mmCredit, (a, b) -> sum(a, b, event, marketMaker,
                winningOptionIndex));
            List<BalanceComponent> makerComponents = ledgerComponents.computeIfAbsent(
                marketMaker, ignored -> new ArrayList<>());
            if (totalCommission > 0.0) makerComponents.add(new BalanceComponent(
                AccountTransactionType.COMMISSION_RECEIVED, totalCommission,
                event.getName(), "Order Book closing commission income"));
            if (residual > 0.0) makerComponents.add(new BalanceComponent(
                AccountTransactionType.RESIDUAL_RETURN, residual,
                event.getName(), "Order Book residual return"));
        }
        try {
            for (Map.Entry<User, Double> credit : credits.entrySet()) {
                credit.getKey().getAccount().validateDelta(credit.getValue());
                credit.getKey().getAccount().validateLedgerComponents(credit.getValue(),
                    ledgerComponents.get(credit.getKey()));
            }
            if (eventBalance > 0.0) event.getAccount().validateDebit(eventBalance);
            validateReservations(event);
        } catch (RuntimeException e) {
            throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                marketMaker.getName(), winningOptionIndex, e.getMessage());
        }

        if (eventBalance > 0.0) event.getAccount().debit(eventBalance);
        for (Map.Entry<User, Double> credit : credits.entrySet()) {
            credit.getKey().getAccount().applyDeltaWithLedger(credit.getValue(),
                ledgerComponents.get(credit.getKey()));
        }
        for (Map.Entry<User, Double> entry : winnerNet.entrySet()) {
            entry.getKey().getOrderBookPosition(event.getId()).recordSettlement(entry.getValue(),
                winnerCommission.getOrDefault(entry.getKey(), 0.0));
        }
        if (mmCredit > 0.0) {
            marketMaker.getOrderBookPosition(event.getId()).recordSettlement(mmCredit);
        }
        releaseAllReservations(event);
        event.getOrderBookState().clearOrders();
        event.closeWithWinner(winningOptionIndex);
        return new il.ac.mta.gm.dto.CloseEventResultDTO(event.getId(),
            event.getOptions()[winningOptionIndex].getName(), winningShares, totalGross,
            totalCommission, totalGross - totalCommission, event.getAccount().getBalance());
    }

    OrderBookDetailsDTO details(GMEvent event) {
        OrderBookState book = event.getOrderBookState();
        GMOption[] names = event.getOptions();
        List<OrderBookOptionDTO> options = new ArrayList<>();
        for (int option = 0; option < 2; option++) {
            List<RestingOrderDTO> orders = new ArrayList<>();
            for (Order order : book.priorityOrders(option,
                    il.ac.mta.gm.engine.model.domain.OrderSide.BUY)) {
                orders.add(toRestingDto(order));
            }
            for (Order order : book.priorityOrders(option,
                    il.ac.mta.gm.engine.model.domain.OrderSide.SELL)) {
                orders.add(toRestingDto(order));
            }
            Double bid = best(book, option, il.ac.mta.gm.engine.model.domain.OrderSide.BUY);
            Double ask = best(book, option, il.ac.mta.gm.engine.model.domain.OrderSide.SELL);
            Double mid = bid == null || ask == null ? null : (bid + ask) / 2.0;
            Double spread = bid == null || ask == null ? null : ask - bid;
            options.add(new OrderBookOptionDTO(option, names[option].getName(), orders,
                new OrderBookStatsDTO(book.getLastPrice(option), bid, ask, mid, spread)));
        }
        List<OrderBookPositionDTO> positions = new ArrayList<>();
        for (User user : state.getUsers()) {
            OrderBookPosition position = user.getOrderBookPosition(event.getId());
            if (position != null) positions.add(toPositionDto(event, user, position));
        }
        return new OrderBookDetailsDTO(event.getId(), event.getName(),
            EventStatus.valueOf(event.getStatus().name()), event.getOrderBookConfig().getD(),
            options, positions);
    }

    OrderBookPositionDTO toPositionDto(GMEvent event, User user, OrderBookPosition position) {
        int[] holdings = position.getHoldings();
        int[] reserved = position.getReservedShares();
        List<OrderFillDTO> executions = new ArrayList<>();
        List<OrderBookExecution> history = position.getExecutions();
        for (int i = history.size() - 1; i >= 0; i--) {
            OrderBookExecution execution = history.get(i);
            executions.add(toFillDto(execution));
        }
        Double profitLoss = event.getStatus() == il.ac.mta.gm.engine.model.domain.EventStatus.CLOSED
            ? position.getProfitLoss() : null;
        return new OrderBookPositionDTO(event.getId(), event.getName(), user.getName(),
            position.isParticipant(), List.of(holdings[0], holdings[1]),
            List.of(reserved[0], reserved[1]), position.getReservedCash(),
            position.getBuyPrincipal(), position.getCommissionPaid(),
            position.getSaleProceeds(), position.getSettlementReceived(), profitLoss, executions);
    }

    private List<FillPlan> planFills(GMEvent event, Order incoming, double rate) {
        OrderBookState book = event.getOrderBookState();
        List<FillPlan> fills = new ArrayList<>();
        Map<Order, Integer> consumed = new IdentityHashMap<>();
        int remaining = incoming.getQuantity();
        il.ac.mta.gm.engine.model.domain.OrderSide opposite =
            incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY
                ? il.ac.mta.gm.engine.model.domain.OrderSide.SELL
                : il.ac.mta.gm.engine.model.domain.OrderSide.BUY;
        for (Order resting : book.priorityOrders(incoming.getOptionIndex(), opposite)) {
            boolean crosses = incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY
                ? incoming.getLimitPrice() >= resting.getLimitPrice()
                : resting.getLimitPrice() >= incoming.getLimitPrice();
            if (!crosses || remaining == 0) break;
            int available = resting.getRemainingQuantity() - consumed.getOrDefault(resting, 0);
            int quantity = Math.min(remaining, available);
            fills.add(FillPlan.direct(event, incoming, resting, quantity,
                resting.getLimitPrice(), rate));
            consumed.merge(resting, quantity, Integer::sum);
            remaining -= quantity;
        }
        if (remaining > 0
            && incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY
            && event.getOrderBookConfig().isAllowMint()) {
            int oppositeOption = 1 - incoming.getOptionIndex();
            for (Order resting : book.priorityOrders(oppositeOption,
                    il.ac.mta.gm.engine.model.domain.OrderSide.BUY)) {
                if (incoming.getLimitPrice() + resting.getLimitPrice()
                        < event.getOrderBookConfig().getD() || remaining == 0) {
                    break;
                }
                int available = resting.getRemainingQuantity()
                    - consumed.getOrDefault(resting, 0);
                int quantity = Math.min(remaining, available);
                double incomingPrice = event.getOrderBookConfig().getD()
                    - resting.getLimitPrice();
                fills.add(FillPlan.mint(event, incoming, resting, quantity, incomingPrice,
                    resting.getLimitPrice(), rate));
                consumed.merge(resting, quantity, Integer::sum);
                remaining -= quantity;
            }
        }
        return fills;
    }

    private MutationPlan buildMutationPlan(GMEvent event, Order incoming, List<FillPlan> fills,
                                           double incomingReservation, int remaining) {
        MutationPlan plan = new MutationPlan();
        User marketMaker = state.getMarketMakerForEvent(event.getId());
        if (marketMaker == null) {
            throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                incoming.getUser().getName(), incoming.getOptionIndex(),
                "Event has no Market Maker.");
        }
        plan.reservationDelta.merge(incoming.getUser(), incomingReservation, Double::sum);
        if (incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.SELL) {
            plan.shareReservationDelta(incoming.getUser(), incoming.getOptionIndex(),
                incoming.getQuantity());
        }
        for (FillPlan fill : fills) fill.addTo(plan, event, marketMaker);
        return plan;
    }

    private void validateMutationPlan(GMEvent event, Order incoming, MutationPlan plan,
                                      double incomingReservation, int remaining) {
        for (Map.Entry<User, Double> entry : plan.cashDelta.entrySet()) {
            try {
                entry.getKey().getAccount().validateDelta(entry.getValue());
                entry.getKey().getAccount().validateLedgerComponents(entry.getValue(),
                    plan.ledgerComponents.get(entry.getKey()));
            } catch (RuntimeException e) {
                throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                    incoming.getUser().getName(), incoming.getOptionIndex(), e.getMessage());
            }
        }
        if (plan.eventCashDelta > 0.0) {
            try {
                event.getAccount().validateCredit(plan.eventCashDelta);
            } catch (RuntimeException e) {
                throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                    incoming.getUser().getName(), incoming.getOptionIndex(), e.getMessage());
            }
        }
        for (Map.Entry<User, int[]> entry : plan.holdingDelta.entrySet()) {
            OrderBookPosition position = entry.getKey().getOrderBookPosition(event.getId());
            int[] current = position == null ? new int[2] : position.getHoldings();
            int[] reserved = position == null ? new int[2] : position.getReservedShares();
            int[] reservationDelta = plan.shareReservationDelta.getOrDefault(entry.getKey(),
                new int[2]);
            for (int option = 0; option < 2; option++) {
                int finalHolding = Math.addExact(current[option], entry.getValue()[option]);
                int finalReserved = Math.addExact(reserved[option], reservationDelta[option]);
                if (finalHolding < 0 || finalReserved < 0 || finalReserved > finalHolding) {
                    throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                        incoming.getUser().getName(), option, "Invalid holding/reservation result.");
                }
            }
        }
        for (User user : state.getUsers()) {
            double cashAfter = user.getAccount().getBalance()
                + plan.cashDelta.getOrDefault(user, 0.0);
            double reservedAfter = user.getTotalReservedCash()
                + plan.reservationDelta.getOrDefault(user, 0.0);
            if (!Double.isFinite(cashAfter) || !Double.isFinite(reservedAfter)
                    || reservedAfter < -EPS || reservedAfter > cashAfter + EPS) {
                throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(),
                    incoming.getUser().getName(), incoming.getOptionIndex(),
                    "Cash reservation invariant would fail.");
            }
        }
    }

    private void commitOrder(GMEvent event, Order incoming, List<FillPlan> fills,
                             double incomingReservation, int remaining, MutationPlan plan) {
        OrderBookPosition incomingPosition = incoming.getUser()
            .getOrCreateOrderBookPosition(event.getId());
        incomingPosition.markParticipant();
        if (incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY) {
            incomingPosition.reserveCash(incomingReservation);
        } else {
            incomingPosition.reserveShares(incoming.getOptionIndex(), incoming.getQuantity());
        }
        event.getOrderBookState().commitOrder(incoming);

        for (Map.Entry<User, Double> entry : plan.cashDelta.entrySet()) {
            entry.getKey().getAccount().applyDeltaWithLedger(entry.getValue(),
                plan.ledgerComponents.get(entry.getKey()));
        }
        if (plan.eventCashDelta > 0.0) event.getAccount().credit(plan.eventCashDelta);
        User marketMaker = state.getMarketMakerForEvent(event.getId());
        for (FillPlan fill : fills) fill.commit(event, marketMaker);
        event.getOrderBookState().removeFilled();
        if (remaining > 0) event.getOrderBookState().addResting(incoming);
    }

    private void validateReservations(GMEvent event) {
        Map<OrderBookPosition, Double> cashByPosition = new IdentityHashMap<>();
        Map<OrderBookPosition, int[]> sharesByPosition = new IdentityHashMap<>();
        for (Order order : event.getOrderBookState().allOrders()) {
            OrderBookPosition position = order.getUser().getOrderBookPosition(event.getId());
            if (position == null) throw new IllegalStateException("Order has no position.");
            if (order.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY) {
                cashByPosition.merge(position, order.getReservedCash(), Double::sum);
            } else {
                sharesByPosition.computeIfAbsent(position, ignored -> new int[2])
                    [order.getOptionIndex()] = Math.addExact(
                        sharesByPosition.get(position)[order.getOptionIndex()],
                        order.getRemainingQuantity());
            }
        }
        for (User user : state.getUsers()) {
            OrderBookPosition position = user.getOrderBookPosition(event.getId());
            if (position == null) continue;
            if (Math.abs(position.getReservedCash()
                    - cashByPosition.getOrDefault(position, 0.0)) > EPS) {
                throw new IllegalStateException("Invalid aggregate cash reservation.");
            }
            int[] expected = sharesByPosition.getOrDefault(position, new int[2]);
            int[] actual = position.getReservedShares();
            int[] holdings = position.getHoldings();
            for (int option = 0; option < 2; option++) {
                if (actual[option] != expected[option] || actual[option] > holdings[option]) {
                    throw new IllegalStateException("Invalid aggregate share reservation.");
                }
            }
        }
    }

    private void releaseAllReservations(GMEvent event) {
        for (Order order : event.getOrderBookState().allOrders()) {
            OrderBookPosition position = order.getUser().getOrderBookPosition(event.getId());
            if (order.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY) {
                if (order.getReservedCash() > 0.0) position.releaseCash(order.getReservedCash());
            } else {
                position.releaseShares(order.getOptionIndex(), order.getRemainingQuantity());
            }
        }
    }

    private static RestingOrderDTO toRestingDto(Order order) {
        return new RestingOrderDTO(order.getId(), order.getUser().getName(),
            il.ac.mta.gm.dto.OrderSide.valueOf(order.getSide().name()),
            order.getRemainingQuantity(), order.getLimitPrice());
    }

    private static OrderFillDTO toFillDto(OrderBookExecution execution) {
        return new OrderFillDTO(execution.getOptionIndex(),
            il.ac.mta.gm.dto.OrderSide.valueOf(execution.getSide().name()),
            OrderExecutionType.valueOf(execution.getKind().name()), execution.getQuantity(),
            execution.getPrice(), execution.getPrincipal(), execution.getCommission(),
            execution.getCounterparty());
    }

    private static Double best(OrderBookState book, int option,
                               il.ac.mta.gm.engine.model.domain.OrderSide side) {
        List<Order> orders = book.priorityOrders(option, side);
        return orders.isEmpty() ? null : orders.get(0).getLimitPrice();
    }

    private static double checked(double value, GMEvent event, User user, Integer option,
                                  String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw error(TradingErrorCode.INVALID_ORDER_BOOK_STATE, event.getId(), user.getName(),
                option, "Invalid " + field + ".");
        }
        return value;
    }

    private static double sum(double left, double right, GMEvent event, User user,
                              Integer option) {
        return checked(left + right, event, user, option, "financial sum");
    }

    private static TradingException error(TradingErrorCode code, int eventId, String user,
                                          Integer option, String message) {
        return new TradingException(code, eventId, user, option, null, message);
    }

    private static final class MutationPlan {
        final Map<User, Double> cashDelta = new IdentityHashMap<>();
        final Map<User, Double> reservationDelta = new IdentityHashMap<>();
        final Map<User, int[]> holdingDelta = new IdentityHashMap<>();
        final Map<User, int[]> shareReservationDelta = new IdentityHashMap<>();
        final Map<User, List<BalanceComponent>> ledgerComponents = new IdentityHashMap<>();
        double eventCashDelta;

        void cash(User user, double delta) { cashDelta.merge(user, delta, Double::sum); }
        void record(User user, AccountTransactionType type, double amount, String eventName,
                    String description) {
            if (amount != 0.0) ledgerComponents.computeIfAbsent(user, ignored -> new ArrayList<>())
                .add(new BalanceComponent(type, amount, eventName, description));
        }
        void reserveCash(User user, double delta) {
            reservationDelta.merge(user, delta, Double::sum);
        }
        void holding(User user, int option, int delta) {
            holdingDelta.computeIfAbsent(user, ignored -> new int[2])[option]
                = Math.addExact(holdingDelta.get(user)[option], delta);
        }
        void shareReservationDelta(User user, int option, int delta) {
            shareReservationDelta.computeIfAbsent(user, ignored -> new int[2])[option]
                = Math.addExact(shareReservationDelta.get(user)[option], delta);
        }
    }

    private static final class FillAmounts {
        double principalPaid;
        double principalReceived;
        double commissionPaid;
    }

    private static final class FillPlan {
        final GMEvent event;
        final Order incoming;
        final Order resting;
        final int quantity;
        final ExecutionKind kind;
        final User buyerA;
        final User buyerB;
        final User seller;
        final double priceA;
        final double priceB;
        final double principalA;
        final double principalB;
        final double commissionA;
        final double commissionB;

        private FillPlan(GMEvent event, Order incoming, Order resting, int quantity,
                         ExecutionKind kind, User buyerA, User buyerB, User seller,
                         double priceA, double priceB, double principalA, double principalB,
                         double commissionA, double commissionB) {
            this.event = event; this.incoming = incoming; this.resting = resting;
            this.quantity = quantity; this.kind = kind; this.buyerA = buyerA;
            this.buyerB = buyerB; this.seller = seller; this.priceA = priceA;
            this.priceB = priceB; this.principalA = principalA; this.principalB = principalB;
            this.commissionA = commissionA; this.commissionB = commissionB;
        }

        static FillPlan direct(GMEvent event, Order incoming, Order resting, int quantity,
                               double price, double rate) {
            User buyer = incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY
                ? incoming.getUser() : resting.getUser();
            User seller = incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.SELL
                ? incoming.getUser() : resting.getUser();
            double principal = price * quantity;
            double commission = principal * rate;
            return new FillPlan(event, incoming, resting, quantity, ExecutionKind.DIRECT,
                buyer, null, seller, price, 0.0, principal, 0.0, commission, 0.0);
        }

        static FillPlan mint(GMEvent event, Order incoming, Order resting, int quantity,
                             double incomingPrice, double restingPrice, double rate) {
            double incomingPrincipal = incomingPrice * quantity;
            double restingPrincipal = restingPrice * quantity;
            return new FillPlan(event, incoming, resting, quantity, ExecutionKind.MINT,
                incoming.getUser(), resting.getUser(), null, incomingPrice, restingPrice,
                incomingPrincipal, restingPrincipal, incomingPrincipal * rate,
                restingPrincipal * rate);
        }

        void addTo(MutationPlan plan, GMEvent event, User mm) {
            plan.cash(buyerA, -(principalA + commissionA));
            plan.record(buyerA, AccountTransactionType.BUY, -principalA, event.getName(),
                "Order Book purchase principal");
            plan.record(buyerA, AccountTransactionType.COMMISSION_PAID, -commissionA,
                event.getName(), "Order Book purchase commission");
            plan.holding(buyerA, incoming.getOptionIndex(), quantity);
            plan.reserveCash(buyerA, -reservationReleaseFor(buyerA));
            if (kind == ExecutionKind.DIRECT) {
                plan.cash(seller, principalA);
                plan.record(seller, AccountTransactionType.SELL, principalA, event.getName(),
                    "Order Book sale proceeds");
                plan.holding(seller, incoming.getOptionIndex(), -quantity);
                plan.shareReservationDelta(seller, incoming.getOptionIndex(), -quantity);
            } else {
                plan.cash(buyerB, -(principalB + commissionB));
                plan.record(buyerB, AccountTransactionType.BUY, -principalB, event.getName(),
                    "Order Book purchase principal");
                plan.record(buyerB, AccountTransactionType.COMMISSION_PAID, -commissionB,
                    event.getName(), "Order Book purchase commission");
                plan.holding(buyerB, resting.getOptionIndex(), quantity);
                plan.reserveCash(buyerB, -reservationReleaseFor(buyerB));
                plan.eventCashDelta += event.getOrderBookConfig().getD() * quantity;
            }
            double commission = commissionA + commissionB;
            if (commission > 0.0) plan.cash(mm, commission);
            plan.record(mm, AccountTransactionType.COMMISSION_RECEIVED, commission,
                event.getName(), "Order Book commission income");
        }

        void commit(GMEvent event, User marketMaker) {
            OrderBookPosition incomingPosition = incoming.getUser()
                .getOrCreateOrderBookPosition(event.getId());
            OrderBookPosition restingPosition = resting.getUser()
                .getOrCreateOrderBookPosition(event.getId());
            double incomingReservedBefore = incoming.getReservedCash();
            double restingReservedBefore = resting.getReservedCash();
            incoming.fill(quantity);
            resting.fill(quantity);
            if (incoming.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY) {
                incomingPosition.releaseCash(incomingReservedBefore - incoming.getReservedCash());
            } else {
                incomingPosition.releaseShares(incoming.getOptionIndex(), quantity);
            }
            if (resting.getSide() == il.ac.mta.gm.engine.model.domain.OrderSide.BUY) {
                restingPosition.releaseCash(restingReservedBefore - resting.getReservedCash());
            } else {
                restingPosition.releaseShares(resting.getOptionIndex(), quantity);
            }

            if (kind == ExecutionKind.DIRECT) {
                OrderBookPosition buyerPosition = buyerA.getOrCreateOrderBookPosition(event.getId());
                OrderBookPosition sellerPosition = seller.getOrCreateOrderBookPosition(event.getId());
                buyerPosition.applyHoldingDelta(incoming.getOptionIndex(), quantity);
                sellerPosition.applyHoldingDelta(incoming.getOptionIndex(), -quantity);
                buyerPosition.recordBuy(principalA, commissionA,
                    execution(buyerA, incoming.getOptionIndex(),
                        il.ac.mta.gm.engine.model.domain.OrderSide.BUY, priceA, principalA,
                        commissionA, seller.getName()));
                sellerPosition.recordSale(principalA,
                    execution(seller, incoming.getOptionIndex(),
                        il.ac.mta.gm.engine.model.domain.OrderSide.SELL, priceA, principalA,
                        0.0, buyerA.getName()));
                event.getOrderBookState().setLastPrice(incoming.getOptionIndex(), priceA);
            } else {
                OrderBookPosition first = buyerA.getOrCreateOrderBookPosition(event.getId());
                OrderBookPosition second = buyerB.getOrCreateOrderBookPosition(event.getId());
                first.applyHoldingDelta(incoming.getOptionIndex(), quantity);
                second.applyHoldingDelta(resting.getOptionIndex(), quantity);
                first.recordBuy(principalA, commissionA,
                    execution(buyerA, incoming.getOptionIndex(),
                        il.ac.mta.gm.engine.model.domain.OrderSide.BUY, priceA, principalA,
                        commissionA, buyerB.getName()));
                second.recordBuy(principalB, commissionB,
                    execution(buyerB, resting.getOptionIndex(),
                        il.ac.mta.gm.engine.model.domain.OrderSide.BUY, priceB, principalB,
                        commissionB, buyerA.getName()));
                event.getOrderBookState().setLastPrice(incoming.getOptionIndex(), priceA);
                event.getOrderBookState().setLastPrice(resting.getOptionIndex(), priceB);
            }
            double commissions = commissionA + commissionB;
            if (commissions > 0.0) {
                marketMaker.getOrCreateOrderBookPosition(event.getId())
                    .recordCommissionIncome(commissions);
            }
        }

        FillAmounts amountsFor(User user) {
            FillAmounts result = new FillAmounts();
            if (user == buyerA) {
                result.principalPaid += principalA;
                result.commissionPaid += commissionA;
            }
            if (user == buyerB) {
                result.principalPaid += principalB;
                result.commissionPaid += commissionB;
            }
            if (user == seller) result.principalReceived += principalA;
            return result;
        }

        OrderFillDTO toDtoFor(User user) {
            OrderBookExecution execution;
            if (user == buyerA) {
                execution = execution(user, incoming.getOptionIndex(),
                    il.ac.mta.gm.engine.model.domain.OrderSide.BUY, priceA, principalA,
                    commissionA, kind == ExecutionKind.DIRECT ? seller.getName() : buyerB.getName());
            } else if (user == buyerB) {
                execution = execution(user, resting.getOptionIndex(),
                    il.ac.mta.gm.engine.model.domain.OrderSide.BUY, priceB, principalB,
                    commissionB, buyerA.getName());
            } else {
                execution = execution(user, incoming.getOptionIndex(),
                    il.ac.mta.gm.engine.model.domain.OrderSide.SELL, priceA, principalA,
                    0.0, buyerA.getName());
            }
            return toFillDto(execution);
        }

        private double reservationReleaseFor(User buyer) {
            Order order = incoming.getUser() == buyer ? incoming : resting;
            return order.getReservedCash() / order.getRemainingQuantity() * quantity;
        }

        private OrderBookExecution execution(User user, int option,
                                             il.ac.mta.gm.engine.model.domain.OrderSide side,
                                             double price, double principal, double commission,
                                             String counterparty) {
            return new OrderBookExecution(event.getId(), option, side, kind, quantity, price,
                principal, commission, counterparty);
        }

    }
}
