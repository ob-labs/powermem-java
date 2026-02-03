package com.oceanbase.powermem.sdk.prompts.graph;

/**
 * Prompts/tool specs used for graph tools integration.
 *
 * <p>Python reference: {@code src/powermem/prompts/graph/graph_tools_prompts.py}</p>
 */
public final class GraphToolsPrompts {
    private GraphToolsPrompts() {}

    public static java.util.Map<String, Object> extractEntitiesTool(boolean structured) {
        java.util.Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("type", "function");
        java.util.Map<String, Object> fn = new java.util.HashMap<>();
        fn.put("name", "extract_entities");
        fn.put("description", "Extract entities and their types from the text.");
        if (structured) {
            fn.put("strict", Boolean.TRUE);
        }
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("type", "object");
        java.util.Map<String, Object> props = new java.util.HashMap<>();
        java.util.Map<String, Object> entities = new java.util.HashMap<>();
        entities.put("type", "array");
        entities.put("description", "An array of entities with their types.");
        java.util.Map<String, Object> items = new java.util.HashMap<>();
        items.put("type", "object");
        java.util.Map<String, Object> itemProps = new java.util.HashMap<>();
        itemProps.put("entity", java.util.Map.of("type", "string", "description", "The name or identifier of the entity."));
        itemProps.put("entity_type", java.util.Map.of("type", "string", "description", "The type or category of the entity."));
        items.put("properties", itemProps);
        items.put("required", java.util.List.of("entity", "entity_type"));
        items.put("additionalProperties", Boolean.FALSE);
        entities.put("items", items);
        props.put("entities", entities);
        params.put("properties", props);
        params.put("required", java.util.List.of("entities"));
        params.put("additionalProperties", Boolean.FALSE);
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    public static java.util.Map<String, Object> establishRelationsTool(boolean structured) {
        java.util.Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("type", "function");
        java.util.Map<String, Object> fn = new java.util.HashMap<>();
        // Python uses "establish_relationships" for non-structured and "establish_relations" for structured.
        fn.put("name", structured ? "establish_relations" : "establish_relationships");
        fn.put("description", "Establish relationships among the entities based on the provided text.");
        if (structured) {
            fn.put("strict", Boolean.TRUE);
        }
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("type", "object");
        java.util.Map<String, Object> props = new java.util.HashMap<>();

        java.util.Map<String, Object> entities = new java.util.HashMap<>();
        entities.put("type", "array");
        java.util.Map<String, Object> items = new java.util.HashMap<>();
        items.put("type", "object");
        java.util.Map<String, Object> itemProps = new java.util.HashMap<>();
        itemProps.put("source", java.util.Map.of("type", "string", "description", "The source entity of the relationship."));
        itemProps.put("relationship", java.util.Map.of("type", "string", "description", "The relationship between the source and destination entities."));
        itemProps.put("destination", java.util.Map.of("type", "string", "description", "The destination entity of the relationship."));
        items.put("properties", itemProps);
        items.put("required", java.util.List.of("source", "relationship", "destination"));
        items.put("additionalProperties", Boolean.FALSE);
        entities.put("items", items);

        props.put("entities", entities);
        params.put("properties", props);
        params.put("required", java.util.List.of("entities"));
        params.put("additionalProperties", Boolean.FALSE);
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    public static java.util.Map<String, Object> deleteGraphMemoryTool(boolean structured) {
        java.util.Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("type", "function");
        java.util.Map<String, Object> fn = new java.util.HashMap<>();
        fn.put("name", "delete_graph_memory");
        fn.put("description", "Delete the relationship between two nodes. This function deletes the existing relationship.");
        if (structured) {
            fn.put("strict", Boolean.TRUE);
        }
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("type", "object");
        java.util.Map<String, Object> props = new java.util.HashMap<>();
        props.put("source", java.util.Map.of("type", "string", "description", "The identifier of the source node in the relationship."));
        props.put("relationship", java.util.Map.of("type", "string", "description", "The existing relationship between the source and destination nodes that needs to be deleted."));
        props.put("destination", java.util.Map.of("type", "string", "description", "The identifier of the destination node in the relationship."));
        params.put("properties", props);
        params.put("required", java.util.List.of("source", "relationship", "destination"));
        params.put("additionalProperties", Boolean.FALSE);
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    public static java.util.List<java.util.Map<String, Object>> tools(java.util.List<java.util.Map<String, Object>> toolList) {
        return toolList == null ? java.util.Collections.emptyList() : toolList;
    }

    /**
     * OpenAI-style tool_choice object: {"type":"function","function":{"name":"..."}}.
     */
    public static java.util.Map<String, Object> toolChoiceFunction(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("type", "function");
        m.put("function", java.util.Map.of("name", name));
        return m;
    }
}

