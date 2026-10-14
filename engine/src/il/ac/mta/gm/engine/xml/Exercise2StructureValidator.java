package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class Exercise2StructureValidator {
    private Exercise2StructureValidator() {
    }

    static void validate(String filePath) {
        Document document = parse(filePath);
        Element root = document.getDocumentElement();
        if (root == null || !"Guess-Market".equals(nameOf(root))) {
            fail(filePath, "Root element must be Guess-Market.");
        }

        Element eventsElement = requiredChild(root, "GM-events", filePath, "Guess-Market");
        Element usersElement = requiredChild(root, "GM-users", filePath, "Guess-Market");

        List<Element> events = children(eventsElement, "GM-event");
        if (events.isEmpty()) {
            fail(filePath, "GM-events must contain at least one GM-event.");
        }
        for (Element event : events) {
            validateEvent(event, filePath);
        }

        List<Element> users = children(usersElement, "GM-user");
        if (users.isEmpty()) {
            fail(filePath, "GM-users must contain at least one GM-user.");
        }
        for (Element user : users) {
            validateUser(user, filePath);
        }
    }

    private static void validateEvent(Element event, String filePath) {
        requiredAttribute(event, "name", filePath, "GM-event");
        requiredIntChild(event, "id", filePath, "GM-event");
        requiredChild(event, "description", filePath, "GM-event");

        Element commission = requiredChild(event, "commission", filePath, "GM-event");
        requiredAttribute(commission, "type", filePath, "commission");
        requireInt(commission.getTextContent(), filePath, "commission value");

        requiredChild(event, "GM-options", filePath, "GM-event");
        Element method = requiredChild(event, "GM-method", filePath, "GM-event");
        List<Element> lmsr = children(method, "GM-LMSR");
        List<Element> orderBooks = children(method, "GM-order-book");
        if (lmsr.size() + orderBooks.size() != 1) {
            fail(filePath, "GM-method must contain exactly one GM-LMSR or GM-order-book.");
        }
        if (!lmsr.isEmpty()) {
            requiredIntChild(lmsr.get(0), "b", filePath, "GM-LMSR");
        } else {
            Element orderBook = orderBooks.get(0);
            requiredAttribute(orderBook, "allow-mint", filePath, "GM-order-book");
            requireInt(requiredAttribute(orderBook, "initial", filePath, "GM-order-book"),
                filePath, "GM-order-book initial");
            requireInt(requiredAttribute(orderBook, "d", filePath, "GM-order-book"),
                filePath, "GM-order-book d");
        }
    }

    private static void validateUser(Element user, String filePath) {
        requiredAttribute(user, "name", filePath, "GM-user");
        requiredIntChild(user, "initial-cash", filePath, "GM-user");

        List<Element> marketMakers = children(user, "GM-market-maker");
        if (marketMakers.size() > 1) {
            fail(filePath, "GM-user may contain at most one GM-market-maker.");
        }
        if (!marketMakers.isEmpty()) {
            List<Element> references = children(marketMakers.get(0), "event");
            if (references.isEmpty()) {
                fail(filePath, "GM-market-maker must contain at least one event reference.");
            }
            for (Element reference : references) {
                requireInt(requiredAttribute(reference, "id", filePath, "event reference"),
                    filePath, "event reference id");
            }
        }
    }

    private static Document parse(String filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(new File(filePath));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null,
                e.getMessage(), "Failed to validate Exercise 2 XML structure.", e);
        }
    }

    private static Element requiredChild(Element parent, String name, String filePath,
                                         String context) {
        List<Element> matches = children(parent, name);
        if (matches.size() != 1) {
            fail(filePath, context + " must contain exactly one " + name + ".");
        }
        return matches.get(0);
    }

    private static int requiredIntChild(Element parent, String name, String filePath,
                                        String context) {
        Element child = requiredChild(parent, name, filePath, context);
        return requireInt(child.getTextContent(), filePath, context + " " + name);
    }

    private static String requiredAttribute(Element element, String name, String filePath,
                                            String context) {
        if (!element.hasAttribute(name)) {
            fail(filePath, context + " is missing required attribute " + name + ".");
        }
        return element.getAttribute(name);
    }

    private static int requireInt(String text, String filePath, String context) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null,
                context + "=" + text, context + " must be a valid xs:int.", e);
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(nameOf(node))) {
                matches.add((Element) node);
            }
        }
        return matches;
    }

    private static String nameOf(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private static void fail(String filePath, String message) {
        throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null, null, message);
    }
}
