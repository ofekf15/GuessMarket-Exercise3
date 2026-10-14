package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;
import il.ac.mta.gm.engine.model.domain.User;
import il.ac.mta.gm.engine.xml.generated.ex2.GMOrderBook;
import il.ac.mta.gm.engine.xml.generated.ex2.GuessMarket;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Exercise2XmlLoader {

    public LoadedSystemData load(String filePath) {
        Exercise2StructureValidator.validate(filePath);
        GuessMarket root = unmarshal(filePath);
        if (root == null || root.getGMEvents() == null || root.getGMUsers() == null) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null, null,
                "Exercise 2 XML must contain GM-events and GM-users.");
        }

        List<GMEvent> events = mapEvents(root, filePath);
        Set<Integer> eventIds = new HashSet<>();
        for (GMEvent event : events) {
            eventIds.add(event.getId());
        }

        Map<Integer, Integer> marketMakerCounts = new HashMap<>();
        List<User> users = mapUsers(root, filePath, eventIds, marketMakerCounts);
        validateExactlyOneMarketMaker(events, marketMakerCounts, filePath);

        return new LoadedSystemData(events, users);
    }

    private GuessMarket unmarshal(String filePath) {
        try {
            JAXBContext context = JAXBContext.newInstance(GuessMarket.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (GuessMarket) unmarshaller.unmarshal(new File(filePath));
        } catch (JAXBException e) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null,
                e.getMessage(), "Failed to read or parse Exercise 2 XML file.", e);
        }
    }

    private List<GMEvent> mapEvents(GuessMarket root, String filePath) {
        List<GMEvent> events = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        for (il.ac.mta.gm.engine.xml.generated.ex2.GMEvent xmlEvent
                : root.getGMEvents().getGMEvent()) {
            int id = xmlEvent.getId();
            if (!seenIds.add(id)) {
                throw new XmlLoadException(ErrorCode.DUPLICATE_EVENT_ID, filePath, id,
                    "id=" + id, "Duplicate event ID found.");
            }

            String name = xmlEvent.getName() == null ? "" : xmlEvent.getName().trim();
            String description = xmlEvent.getDescription() == null
                ? "" : xmlEvent.getDescription().trim();
            Commission commission = mapCommission(xmlEvent, filePath, id);
            GMOption[] options = mapOptions(xmlEvent, filePath, id);

            if (xmlEvent.getGMMethod() == null) {
                throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, id,
                    null, "Event method is missing.");
            }

            boolean hasLmsr = xmlEvent.getGMMethod().getGMLMSR() != null;
            boolean hasOrderBook = xmlEvent.getGMMethod().getGMOrderBook() != null;
            if (hasLmsr == hasOrderBook) {
                throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, id,
                    null, "Event must define exactly one market method.");
            }

            GMEvent event;
            if (hasLmsr) {
                int b = xmlEvent.getGMMethod().getGMLMSR().getB();
                try {
                    event = GMEvent.createExercise2LmsrEvent(
                        id, name, description, commission, options, b);
                } catch (IllegalArgumentException e) {
                    throw new XmlLoadException(ErrorCode.INVALID_LMSR_PARAMETER, filePath, id,
                        "b=" + b, e.getMessage(), e);
                }
            } else {
                GMOrderBook xmlOrderBook = xmlEvent.getGMMethod().getGMOrderBook();
                String allowMintText = xmlOrderBook.getAllowMint();
                if (!"true".equals(allowMintText) && !"false".equals(allowMintText)) {
                    throw new XmlLoadException(ErrorCode.INVALID_ORDER_BOOK_PARAMETER, filePath, id,
                        "allow-mint=" + allowMintText, "Invalid Order Book allow-mint value.");
                }
                try {
                    OrderBookConfig config = new OrderBookConfig(
                        Boolean.parseBoolean(allowMintText), xmlOrderBook.getInitial(), xmlOrderBook.getD());
                    event = GMEvent.createOrderBookEvent(
                        id, name, description, commission, options, config);
                } catch (IllegalArgumentException e) {
                    throw new XmlLoadException(ErrorCode.INVALID_ORDER_BOOK_PARAMETER, filePath, id,
                        "initial=" + xmlOrderBook.getInitial() + ", d=" + xmlOrderBook.getD(),
                        e.getMessage(), e);
                }
            }

            events.add(event);
        }
        return events;
    }

    private Commission mapCommission(il.ac.mta.gm.engine.xml.generated.ex2.GMEvent xmlEvent,
                                     String filePath, int eventId) {
        il.ac.mta.gm.engine.xml.generated.ex2.Commission xmlCommission = xmlEvent.getCommission();
        if (xmlCommission == null) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, eventId,
                null, "Commission is missing.");
        }

        CommissionType type;
        if ("on-close".equals(xmlCommission.getType())) {
            type = CommissionType.ON_CLOSE;
        } else if ("on-purchase".equals(xmlCommission.getType())) {
            type = CommissionType.ON_PURCHASE;
        } else {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, eventId,
                "type=" + xmlCommission.getType(), "Unknown commission type.");
        }

        try {
            return new Commission(xmlCommission.getValue(), type);
        } catch (IllegalArgumentException e) {
            throw new XmlLoadException(ErrorCode.INVALID_COMMISSION, filePath, eventId,
                "commission=" + xmlCommission.getValue(), e.getMessage(), e);
        }
    }

    private GMOption[] mapOptions(il.ac.mta.gm.engine.xml.generated.ex2.GMEvent xmlEvent,
                                  String filePath, int eventId) {
        if (xmlEvent.getGMOptions() == null) {
            throw new XmlLoadException(ErrorCode.INVALID_OPTIONS_COUNT, filePath, eventId,
                "count=0", "Exercise 2 requires exactly 2 options.");
        }
        List<String> xmlOptions = xmlEvent.getGMOptions().getGMOption();
        if (xmlOptions.size() != 2) {
            throw new XmlLoadException(ErrorCode.INVALID_OPTIONS_COUNT, filePath, eventId,
                "count=" + xmlOptions.size(), "Exercise 2 requires exactly 2 options.");
        }
        try {
            return new GMOption[] {
                new GMOption(xmlOptions.get(0).trim()),
                new GMOption(xmlOptions.get(1).trim())
            };
        } catch (IllegalArgumentException e) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, eventId,
                null, e.getMessage(), e);
        }
    }

    private List<User> mapUsers(GuessMarket root, String filePath, Set<Integer> eventIds,
                                Map<Integer, Integer> marketMakerCounts) {
        List<User> users = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();

        for (il.ac.mta.gm.engine.xml.generated.ex2.GMUser xmlUser
                : root.getGMUsers().getGMUser()) {
            String name = xmlUser.getName() == null ? "" : xmlUser.getName().trim();
            if (name.isEmpty()) {
                throw new XmlLoadException(ErrorCode.INVALID_USER_NAME, filePath, null,
                    "name=" + name, "User name cannot be null or blank.");
            }
            if (!seenNames.add(name)) {
                throw new XmlLoadException(ErrorCode.DUPLICATE_USER_NAME, filePath, null,
                    "name=" + name, "Duplicate user name found: " + name);
            }

            User user;
            try {
                user = new User(name, xmlUser.getInitialCash());
            } catch (IllegalArgumentException e) {
                throw new XmlLoadException(ErrorCode.INVALID_INITIAL_CASH, filePath, null,
                    "user=" + name + ", initial-cash=" + xmlUser.getInitialCash(),
                    e.getMessage(), e);
            }

            if (xmlUser.getGMMarketMaker() != null) {
                for (il.ac.mta.gm.engine.xml.generated.ex2.Event reference
                        : xmlUser.getGMMarketMaker().getEvent()) {
                    int eventId = reference.getId();
                    if (!eventIds.contains(eventId)) {
                        throw new XmlLoadException(ErrorCode.MM_EVENT_NOT_FOUND, filePath, eventId,
                            "user=" + name, "Market Maker event reference does not exist.");
                    }
                    user.addMarketMakerEventId(eventId);
                    marketMakerCounts.merge(eventId, 1, Integer::sum);
                }
            }
            users.add(user);
        }
        return users;
    }

    private void validateExactlyOneMarketMaker(List<GMEvent> events,
                                               Map<Integer, Integer> marketMakerCounts,
                                               String filePath) {
        for (GMEvent event : events) {
            int count = marketMakerCounts.getOrDefault(event.getId(), 0);
            if (count == 0) {
                throw new XmlLoadException(ErrorCode.EVENT_WITHOUT_MARKET_MAKER, filePath,
                    event.getId(), "count=0", "Event has no Market Maker.");
            }
            if (count > 1) {
                throw new XmlLoadException(ErrorCode.EVENT_WITH_MULTIPLE_MARKET_MAKERS, filePath,
                    event.getId(), "count=" + count, "Event has multiple Market Makers.");
            }
        }
    }
}
