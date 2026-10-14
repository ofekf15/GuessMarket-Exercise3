package il.ac.mta.gm.engine.model.domain;

public class GMOption {
    private final String name;

    public GMOption(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Option name cannot be empty.");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
