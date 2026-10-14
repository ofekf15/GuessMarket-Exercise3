package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** In-memory v3 parser: v2 event structure minus users and event IDs. */
public final class Exercise3XmlLoader {
    private static final String INPUT = "uploaded XML";

    public List<Exercise3EventSpec> load(byte[] bytes) {
        if (bytes == null || bytes.length == 0) fail("XML body is empty.");
        return load(new ByteArrayInputStream(bytes));
    }

    public List<Exercise3EventSpec> load(InputStream input) {
        if (input == null) fail("XML input is missing.");
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            document = factory.newDocumentBuilder().parse(input);
        } catch (Exception error) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, INPUT, null,
                error.getMessage(), "Malformed or unsafe XML document.", error);
        }
        Element root = document.getDocumentElement();
        requireName(root, "Guess-Market");
        requireAttributes(root);
        Element events = onlyChild(root, "GM-events");
        requireAttributes(events);
        List<Element> xmlEvents = children(events);
        if (xmlEvents.isEmpty()) fail("GM-events must contain at least one GM-event.");
        List<Exercise3EventSpec> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Element xmlEvent : xmlEvents) {
            requireName(xmlEvent, "GM-event");
            requireAttributes(xmlEvent, "name");
            String name = xmlEvent.getAttribute("name").trim();
            if (name.isEmpty()) fail("GM-event name cannot be blank.");
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw error(ErrorCode.DUPLICATE_EVENT_NAME,
                    "Duplicate event name in upload: " + name);
            }
            List<Element> fields = expectedChildren(xmlEvent,
                "description", "commission", "GM-options", "GM-method");
            String description = fields.get(0).getTextContent().trim();
            requireAttributes(fields.get(0));
            if (description.isEmpty()) fail("Event description cannot be blank.");
            Element xmlCommission = fields.get(1);
            requireAttributes(xmlCommission, "type");
            CommissionType type = switch (xmlCommission.getAttribute("type")) {
                case "on-close" -> CommissionType.ON_CLOSE;
                case "on-purchase" -> CommissionType.ON_PURCHASE;
                default -> throw error(ErrorCode.XML_READ_ERROR, "Invalid commission type.");
            };
            Commission commission;
            try {
                commission = new Commission(integer(xmlCommission.getTextContent(),
                    "commission"), type);
            } catch (IllegalArgumentException error) {
                throw error(ErrorCode.INVALID_COMMISSION, error.getMessage());
            }
            Element xmlOptions = fields.get(2);
            requireAttributes(xmlOptions);
            List<Element> optionElements = children(xmlOptions);
            if (optionElements.size() < 2) {
                throw error(ErrorCode.INVALID_OPTIONS_COUNT,
                    "An event requires at least two options.");
            }
            GMOption[] options = new GMOption[optionElements.size()];
            Set<String> optionNames = new HashSet<>();
            for (int i = 0; i < optionElements.size(); i++) {
                Element option = optionElements.get(i);
                requireName(option, "GM-option");
                requireAttributes(option);
                if (!children(option).isEmpty()) fail("GM-option must contain text only.");
                String optionName = option.getTextContent().trim();
                if (optionName.isEmpty() || !optionNames.add(optionName.toLowerCase(Locale.ROOT))) {
                    throw error(ErrorCode.INVALID_OPTION_NAME,
                        "Option names must be nonblank and unique within an event.");
                }
                options[i] = new GMOption(optionName);
            }
            Element method = fields.get(3);
            requireAttributes(method);
            List<Element> methods = children(method);
            if (methods.size() != 1) fail("GM-method must contain exactly one market method.");
            Element config = methods.get(0);
            if ("GM-LMSR".equals(config.getTagName())) {
                requireAttributes(config);
                Element bElement = onlyChild(config, "b");
                requireAttributes(bElement);
                int b = integer(bElement.getTextContent(), "LMSR b");
                if (b <= 0) throw error(ErrorCode.INVALID_LMSR_PARAMETER,
                    "LMSR b must be positive.");
                result.add(new Exercise3EventSpec(name, description, commission, options,
                    b, null));
            } else if ("GM-order-book".equals(config.getTagName())) {
                requireAttributes(config, "initial", "d", "allow-mint");
                if (!children(config).isEmpty() || !config.getTextContent().isBlank()) {
                    fail("GM-order-book cannot contain nested content.");
                }
                if (options.length != 2) {
                    throw error(ErrorCode.UNSUPPORTED_MULTI_OPTION_ORDER_BOOK,
                        "Multi-option Order Book minting is not defined; only binary Order Book is supported.");
                }
                String mint = config.getAttribute("allow-mint");
                if (!"true".equals(mint) && !"false".equals(mint)) {
                    throw error(ErrorCode.INVALID_ORDER_BOOK_PARAMETER,
                        "allow-mint must be true or false.");
                }
                try {
                    OrderBookConfig orderBook = new OrderBookConfig(Boolean.parseBoolean(mint),
                        integer(config.getAttribute("initial"), "Order Book initial"),
                        integer(config.getAttribute("d"), "Order Book d"));
                    result.add(new Exercise3EventSpec(name, description, commission,
                        options, null, orderBook));
                } catch (IllegalArgumentException error) {
                    throw error(ErrorCode.INVALID_ORDER_BOOK_PARAMETER, error.getMessage());
                }
            } else {
                fail("GM-method must contain GM-LMSR or GM-order-book.");
            }
        }
        return List.copyOf(result);
    }

    private static List<Element> expectedChildren(Element parent, String... expected) {
        List<Element> found = children(parent);
        if (found.size() != expected.length) {
            fail(parent.getTagName() + " must contain exactly " + String.join(", ", expected) + ".");
        }
        for (int i = 0; i < expected.length; i++) requireName(found.get(i), expected[i]);
        return found;
    }

    private static Element onlyChild(Element parent, String name) {
        return expectedChildren(parent, name).get(0);
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) result.add((Element) node);
        }
        return result;
    }

    private static void requireName(Element element, String expected) {
        if (element == null || element.getNamespaceURI() != null ||
                !expected.equals(element.getTagName())) fail("Expected " + expected + ".");
    }

    private static void requireAttributes(Element element, String... required) {
        if (element.getAttributes().getLength() != required.length) {
            fail("Unexpected or missing attributes on " + element.getTagName() + ".");
        }
        for (String attribute : required) {
            if (!element.hasAttribute(attribute)) {
                fail(element.getTagName() + " requires attribute " + attribute + ".");
            }
        }
    }

    private static int integer(String text, String label) {
        try { return Integer.parseInt(text.trim()); }
        catch (RuntimeException error) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, INPUT, null,
                label + "=" + text, label + " must be an integer.", error);
        }
    }

    private static XmlLoadException error(ErrorCode code, String message) {
        return new XmlLoadException(code, INPUT, null, null, message);
    }

    private static void fail(String message) {
        throw error(ErrorCode.XML_READ_ERROR, message);
    }
}
