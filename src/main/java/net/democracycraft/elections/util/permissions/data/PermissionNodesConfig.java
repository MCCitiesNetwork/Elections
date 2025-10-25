package net.democracycraft.elections.util.permissions.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serializable configuration holder used for YAML persistence of permission nodes.
 * This class exists to allow reflection-based serialization/deserialization with AutoYML
 * and provides adapters to/from the public PermissionNodesDto.
 */
public class PermissionNodesConfig implements Serializable {

    private List<String> nodes = new ArrayList<>();

    public PermissionNodesConfig() {}

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }

    /** Creates an immutable DTO view of this config. */
    public PermissionNodesDto toDto() {
        return new PermissionNodesDto(Collections.unmodifiableList(new ArrayList<>(nodes)));
    }

    /** Creates a config instance from a DTO. */
    public static PermissionNodesConfig fromDto(PermissionNodesDto dto) {
        PermissionNodesConfig cfg = new PermissionNodesConfig();
        if (dto != null && dto.nodes() != null) {
            cfg.setNodes(dto.nodes());
        }
        return cfg;
    }
}

