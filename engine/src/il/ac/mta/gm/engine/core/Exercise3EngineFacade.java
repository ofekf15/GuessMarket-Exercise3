package il.ac.mta.gm.engine.core;

import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.AccountTransactionType;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3UserDTO;
import il.ac.mta.gm.dto.LoginResultDTO;
import il.ac.mta.gm.dto.Exercise3UserDetailsDTO;
import il.ac.mta.gm.dto.Exercise3LmsrParticipationDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookPositionDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookDTO;
import il.ac.mta.gm.dto.Exercise3EventDetailsDTO;
import il.ac.mta.gm.dto.Exercise3OptionDTO;
import il.ac.mta.gm.dto.Exercise3TradeResultDTO;
import il.ac.mta.gm.dto.Exercise3OrderResultDTO;
import il.ac.mta.gm.dto.Exercise3CloseResultDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.CloseEventResultDTO;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import il.ac.mta.gm.dto.OrderBookPositionDTO;
import il.ac.mta.gm.dto.LmsrParticipationDTO;
import il.ac.mta.gm.dto.UserTradeRecordDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.dto.OrderBookConfigDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.engine.api.Exercise3EngineInterface;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.model.domain.AccountTransaction;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.User;
import java.util.ArrayList;
import java.util.List;
import il.ac.mta.gm.engine.xml.Exercise3XmlLoader;

/** Shares the SystemState monitor with future server-side operations. */
public final class Exercise3EngineFacade implements Exercise3EngineInterface {
    private final SystemState state;
    private final GuessMarketEngine legacyEngine;
    private final Exercise3XmlLoader exercise3XmlLoader = new Exercise3XmlLoader();

    public Exercise3EngineFacade(SystemState state) {
        if (state == null) throw new IllegalArgumentException("State cannot be null.");
        this.state = state;
        this.legacyEngine = new GuessMarketEngine(state);
    }

    @Override
    public LoginResultDTO registerUser(String name) {
        synchronized (state) {
            return new LoginResultDTO(toUserDTO(state.registerExercise3User(name)));
        }
    }

    @Override
    public Exercise3UserDTO deposit(String userName, double amount) {
        synchronized (state) {
            User user = requireUser(userName);
            user.getAccount().deposit(amount);
            return toUserDTO(user);
        }
    }

    @Override
    public Exercise3UserDTO getUser(String userName) {
        synchronized (state) {
            return toUserDTO(requireUser(userName));
        }
    }

    @Override
    public Exercise3UserDetailsDTO getUserDetails(String userName) {
        synchronized (state) {
            User user = requireUser(userName);
            UserSummaryDTO legacy = legacyEngine.getUser(user.getName());
            List<Exercise3LmsrParticipationDTO> lmsr = new ArrayList<>();
            for (LmsrParticipationDTO participation : legacy.participations) {
                List<TradeRecordDTO> history = new ArrayList<>();
                for (UserTradeRecordDTO trade : participation.tradeHistory) {
                    history.add(new TradeRecordDTO(trade.optionName, trade.quantity,
                        trade.sharesCost, trade.commissionPaid, trade.totalPaid));
                }
                lmsr.add(new Exercise3LmsrParticipationDTO(participation.eventName,
                    participation.optionNames, participation.holdings,
                    participation.amountPaid, participation.totalCommissionPaid, history,
                    participation.eventStatus, participation.winningOption,
                    participation.settled, participation.grossPayout,
                    participation.closingCommission, participation.netPayout));
            }
            List<Exercise3OrderBookPositionDTO> orderBook = new ArrayList<>();
            for (OrderBookPositionDTO position : legacy.orderBookPositions) {
                orderBook.add(toPosition(position));
            }
            return new Exercise3UserDetailsDTO(user.getName(), user.getAccount().getBalance(),
                state.isMarketMaker(user), lmsr, orderBook);
        }
    }

    @Override
    public List<Exercise3UserDTO> getUsers() {
        synchronized (state) {
            List<Exercise3UserDTO> result = new ArrayList<>();
            for (User user : state.getUsers()) result.add(toUserDTO(user));
            return List.copyOf(result);
        }
    }

    @Override
    public List<AccountTransactionDTO> getTransactions(String userName) {
        synchronized (state) {
            List<AccountTransaction> entries = requireUser(userName).getAccount().getTransactions();
            List<AccountTransactionDTO> result = new ArrayList<>();
            for (int i = entries.size() - 1; i >= 0; i--) {
                AccountTransaction entry = entries.get(i);
                result.add(new AccountTransactionDTO(entry.getSequence(),
                    AccountTransactionType.valueOf(entry.getType().name()),
                    entry.getSignedAmount(), entry.getResultingBalance(),
                    entry.getEventName(), entry.getDescription()));
            }
            return List.copyOf(result);
        }
    }

    @Override
    public Exercise3EventDTO getEvent(String eventName) {
        synchronized (state) {
            GMEvent event = state.getEventByName(eventName);
            if (event == null) throw new IllegalArgumentException("Event not found: " + eventName);
            return toEventDTO(event);
        }
    }

    @Override
    public Exercise3EventDetailsDTO getEventDetails(String eventName) {
        synchronized (state) {
            GMEvent event = requireEvent(eventName);
            List<Exercise3OptionDTO> options = new ArrayList<>();
            int[] q = event.getMarket() == null ? null : event.getMarket().getQ();
            GMOption[] names = event.getOptions();
            for (int index = 0; index < names.length; index++) {
                options.add(new Exercise3OptionDTO(names[index].getName(),
                    q == null ? null : event.getMarket().computeOptionPrice(index),
                    q == null ? null : q[index]));
            }
            User maker = state.getMarketMakerForEvent(event.getId());
            var config = event.getOrderBookConfig();
            String winner = event.getWinningOptionIndex() == null ? null
                : names[event.getWinningOptionIndex()].getName();
            return new Exercise3EventDetailsDTO(event.getName().trim(),
                event.getDescription(), EventStatus.valueOf(event.getStatus().name()),
                EventMethodType.valueOf(event.getMethodType().name()), options,
                maker == null ? null : maker.getName(), event.getCommission().getPercentage(),
                il.ac.mta.gm.dto.CommissionType.valueOf(event.getCommission().getType().name()),
                event.getAccount().getBalance(), event.getAccount().getTotalFeesCollected(),
                winner, config == null ? null : new OrderBookConfigDTO(config.isAllowMint(),
                    config.getInitial(), config.getD()));
        }
    }

    @Override
    public Exercise3EventDetailsDTO startEvent(String eventName, String actingUser) {
        synchronized (state) {
            GMEvent event = requireEvent(eventName);
            legacyEngine.startEvent(event.getId(), requireUser(actingUser).getName());
            return getEventDetails(event.getName());
        }
    }

    @Override
    public Exercise3TradeResultDTO buyLmsrShares(String eventName, String actingUser,
                                                   int optionIndex, int quantity) {
        synchronized (state) {
            GMEvent event = requireEvent(eventName);
            TradeResultDTO result = legacyEngine.buyShares(event.getId(),
                requireUser(actingUser).getName(), optionIndex, quantity);
            return new Exercise3TradeResultDTO(event.getName().trim(), result.optionName,
                result.quantity, result.sharesCost, result.commissionPaid, result.totalPaid);
        }
    }

    @Override
    public Exercise3OrderResultDTO submitOrder(String eventName, String actingUser,
                                                int optionIndex, OrderSide side,
                                                int quantity, double limitPrice) {
        synchronized (state) {
            GMEvent event = requireEvent(eventName);
            OrderResultDTO result = legacyEngine.placeOrder(event.getId(),
                requireUser(actingUser).getName(), optionIndex, side, quantity, limitPrice);
            return new Exercise3OrderResultDTO(event.getName().trim(), result.orderId,
                result.optionIndex, result.side, result.submittedQuantity,
                result.executedQuantity, result.restingQuantity, result.principalPaid,
                result.principalReceived, result.commissionPaid, result.fills);
        }
    }

    @Override
    public Exercise3OrderBookDTO getOrderBook(String eventName) {
        synchronized (state) {
            GMEvent event = requireEvent(eventName);
            OrderBookDetailsDTO details = legacyEngine.getOrderBook(event.getId());
            List<Exercise3OrderBookPositionDTO> positions = new ArrayList<>();
            for (OrderBookPositionDTO position : details.positions) {
                positions.add(toPosition(position));
            }
            return new Exercise3OrderBookDTO(event.getName().trim(), details.status,
                details.d, details.options, positions);
        }
    }

    @Override
    public Exercise3CloseResultDTO closeEvent(String eventName, String actingUser,
                                               int winningOptionIndex) {
        synchronized (state) {
            GMEvent event = requireEvent(eventName);
            CloseEventResultDTO result = legacyEngine.closeEvent(event.getId(),
                requireUser(actingUser).getName(), winningOptionIndex);
            return new Exercise3CloseResultDTO(event.getName().trim(),
                result.winningOptionName, result.winningShares, result.grossPayout,
                result.commissionPaid, result.netPayout, result.finalAccountBalance);
        }
    }

    @Override
    public List<Exercise3EventDTO> getEvents() {
        synchronized (state) {
            List<Exercise3EventDTO> result = new ArrayList<>();
            for (GMEvent event : state.getEvents()) result.add(toEventDTO(event));
            return List.copyOf(result);
        }
    }

    @Override
    public List<Exercise3EventDTO> uploadEvents(byte[] xml, String uploaderName) {
        var specifications = exercise3XmlLoader.load(xml);
        synchronized (state) {
            List<Exercise3EventDTO> added = new ArrayList<>();
            for (GMEvent event : state.appendExercise3Events(specifications, uploaderName)) {
                added.add(toEventDTO(event));
            }
            return List.copyOf(added);
        }
    }

    private User requireUser(String name) {
        User user = state.getExercise3User(name);
        if (user == null) throw new IllegalArgumentException("User not found: " + name);
        return user;
    }

    private GMEvent requireEvent(String eventName) {
        GMEvent event = state.getEventByName(eventName);
        if (event == null) {
            throw new TradingException(TradingErrorCode.EVENT_NOT_FOUND, null, null,
                null, "Event not found: " + eventName);
        }
        return event;
    }

    private static Exercise3OrderBookPositionDTO toPosition(OrderBookPositionDTO position) {
        return new Exercise3OrderBookPositionDTO(position.eventName, position.userName,
            position.participant, position.holdings, position.reservedShares,
            position.reservedCash, position.buyPrincipal, position.commissionPaid,
            position.saleProceeds, position.settlementReceived, position.profitLoss,
            position.executions);
    }

    private Exercise3UserDTO toUserDTO(User user) {
        return new Exercise3UserDTO(user.getName(), user.getAccount().getBalance(),
            state.isMarketMaker(user));
    }

    private Exercise3EventDTO toEventDTO(GMEvent event) {
        List<String> options = new ArrayList<>();
        for (GMOption option : event.getOptions()) options.add(option.getName());
        User maker = state.getMarketMakerForEvent(event.getId());
        return new Exercise3EventDTO(event.getName().trim(), event.getDescription(),
            EventStatus.valueOf(event.getStatus().name()),
            EventMethodType.valueOf(event.getMethodType().name()), options,
            event.getCommission().getPercentage(),
            il.ac.mta.gm.dto.CommissionType.valueOf(event.getCommission().getType().name()),
            maker == null ? null : maker.getName(), event.getAccount().getBalance());
    }
}
