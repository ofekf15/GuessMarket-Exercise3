package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class SystemXmlLoader {
    private final XmlLoader exercise1Loader;
    private final Exercise2XmlLoader exercise2Loader;

    public SystemXmlLoader(XmlLoader exercise1Loader) {
        this.exercise1Loader = exercise1Loader;
        this.exercise2Loader = new Exercise2XmlLoader();
    }

    public LoadedSystemData load(String filePath) {
        File file = validateFile(filePath);
        if (containsExercise2Marker(file, filePath)) {
            return exercise2Loader.load(filePath);
        }
        return new LoadedSystemData(exercise1Loader.load(filePath), List.of());
    }

    private File validateFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new XmlLoadException(ErrorCode.INVALID_PATH, filePath, null, null,
                "File path cannot be empty.");
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw new XmlLoadException(ErrorCode.FILE_NOT_FOUND, filePath, null, null,
                "File does not exist.");
        }
        if (!file.isFile()) {
            throw new XmlLoadException(ErrorCode.NOT_A_FILE, filePath, null, null,
                "Path is not a regular file.");
        }
        if (!filePath.toLowerCase().endsWith(".xml")) {
            throw new XmlLoadException(ErrorCode.INVALID_EXTENSION, filePath, null, null,
                "File must have a .xml extension.");
        }
        return file;
    }

    private boolean containsExercise2Marker(File file, String filePath) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

        try (FileInputStream input = new FileInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        String localName = reader.getLocalName();
                        if ("GM-users".equals(localName)
                            || "commission".equals(localName)
                            || "GM-order-book".equals(localName)) {
                            return true;
                        }
                    }
                }
                return false;
            } finally {
                reader.close();
            }
        } catch (IOException | XMLStreamException e) {
            throw new XmlLoadException(ErrorCode.XML_READ_ERROR, filePath, null,
                e.getMessage(), "Failed to inspect XML structure.", e);
        }
    }
}
