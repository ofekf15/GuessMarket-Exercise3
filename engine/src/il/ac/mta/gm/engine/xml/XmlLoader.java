package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.EventAccount;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.lmsr.LMSRMarket;
import il.ac.mta.gm.engine.model.domain.EventStatus;
import il.ac.mta.gm.engine.xml.generated.Comision;
import il.ac.mta.gm.engine.xml.generated.GuessMarket;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XmlLoader {

    public List<il.ac.mta.gm.engine.model.domain.GMEvent> load(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new XmlLoadException(ErrorCode.INVALID_PATH, filePath, null, null, "File path cannot be empty.");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            throw new XmlLoadException(ErrorCode.FILE_NOT_FOUND, filePath, null, null, "File does not exist.");
        }
        if (!file.isFile()) {
            throw new XmlLoadException(ErrorCode.NOT_A_FILE, filePath, null, null, "Path is not a regular file.");
        }
        if (!filePath.toLowerCase().endsWith(".xml")) {
            throw new XmlLoadException(ErrorCode.INVALID_EXTENSION, filePath, null, null, "File must have a .xml extension.");
        }

        GuessMarket root;
        try {
            JAXBContext context = JAXBContext.newInstance(GuessMarket.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            root = (GuessMarket) unmarshaller.unmarshal(file);
        } catch (JAXBException e) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null, e.getMessage(), "Failed to read or parse XML file.", e);
        }

        if (root == null || root.getGMEvents() == null) {
            return new ArrayList<>(); // Empty valid guess market
        }

        List<il.ac.mta.gm.engine.model.domain.GMEvent> candidateEvents = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        for (il.ac.mta.gm.engine.xml.generated.GMEvent xmlEvent : root.getGMEvents().getGMEvent()) {
            int id = xmlEvent.getId();
            
            if (!seenIds.add(id)) {
                throw new XmlLoadException(ErrorCode.DUPLICATE_EVENT_ID, filePath, id, "id=" + id, "Duplicate event ID found.");
            }

            // Normalizing Name
            String name = String.join(" ", xmlEvent.getName()).trim();
            
            // Normalizing Description
            String description = "";
            if (xmlEvent.getDescription() != null) {
                description = xmlEvent.getDescription().trim();
            }

            // Commission mapping
            Comision xmlCommission = xmlEvent.getComision();
            Commission commission;
            try {
                CommissionType type = null;
                if ("on-close".equals(xmlCommission.getType())) {
                    type = CommissionType.ON_CLOSE;
                } else if ("on-purchase".equals(xmlCommission.getType())) {
                    type = CommissionType.ON_PURCHASE;
                } else {
                    throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, id, "type=" + xmlCommission.getType(), "Unknown commission type.");
                }
                commission = new Commission(xmlCommission.getValue(), type);
            } catch (IllegalArgumentException e) {
                throw new XmlLoadException(ErrorCode.INVALID_COMMISSION, filePath, id, "commission=" + xmlCommission.getValue(), e.getMessage(), e);
            }

            // Options mapping
            List<String> xmlOptions = xmlEvent.getGMOptions().getGMOption();
            if (xmlOptions.size() != 2) {
                throw new XmlLoadException(ErrorCode.INVALID_OPTIONS_COUNT, filePath, id, "count=" + xmlOptions.size(), "Exercise 1 requires exactly 2 options.");
            }
            GMOption[] domainOptions = new GMOption[2];
            domainOptions[0] = new GMOption(xmlOptions.get(0).trim());
            domainOptions[1] = new GMOption(xmlOptions.get(1).trim());

            // LMSR b mapping
            int b = xmlEvent.getGMMethod().getGMLMSR().getB();
            try {
                new LMSRMarket(b);
            } catch (IllegalArgumentException e) {
                throw new XmlLoadException(ErrorCode.INVALID_LMSR_PARAMETER, filePath, id, "b=" + b, e.getMessage(), e);
            }

            // Construct domain GMEvent (no try-catch, unexpected errors remain visible)
            il.ac.mta.gm.engine.model.domain.GMEvent domainEvent = new il.ac.mta.gm.engine.model.domain.GMEvent(
                id,
                name,
                description,
                commission,
                domainOptions,
                b
            );

            // Temporary Exercise 1 compatibility: Ex1 XML events are immediately ACTIVE.
            // Exercise 2 XML loading will leave new events NOT_STARTED until an MM starts them.
            domainEvent.startEvent();

            candidateEvents.add(domainEvent);
        }

        return candidateEvents;
    }
}
