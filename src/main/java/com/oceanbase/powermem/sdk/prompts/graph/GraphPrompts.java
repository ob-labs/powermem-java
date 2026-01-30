package com.oceanbase.powermem.sdk.prompts.graph;

/**
 * Graph-related prompts used for entity/relation extraction and graph operations.
 *
 * <p>Python reference: {@code src/powermem/prompts/graph/graph_prompts.py}</p>
 */
public final class GraphPrompts {
    private GraphPrompts() {}

    // Default prompts (mirrors Python).
    public static final String EXTRACT_RELATIONS_PROMPT =
            "You are an advanced algorithm designed to extract structured information from text to construct knowledge graphs. "
                    + "Your goal is to capture comprehensive and accurate information. Follow these key principles:\n\n"
                    + "1. Extract only explicitly stated information from the text.\n"
                    + "2. Establish relationships among the entities provided.\n"
                    + "3. Use \"USER_ID\" as the source entity for any self-references (e.g., \"I,\" \"me,\" \"my,\" etc.) in user messages.\n"
                    + "CUSTOM_PROMPT\n\n"
                    + "Relationships:\n"
                    + "    - Use consistent, general, and timeless relationship types.\n"
                    + "    - Example: Prefer \"professor\" over \"became_professor.\"\n"
                    + "    - Relationships should only be established among the entities explicitly mentioned in the user message.\n\n"
                    + "Entity Consistency:\n"
                    + "    - Ensure that relationships are coherent and logically align with the context of the message.\n"
                    + "    - Maintain consistent naming for entities across the extracted data.\n\n"
                    + "Strive to construct a coherent and easily understandable knowledge graph by eshtablishing all the relationships among the entities "
                    + "and adherence to the user's context.\n\n"
                    + "Adhere strictly to these guidelines to ensure high-quality knowledge graph extraction.";

    public static final String DELETE_RELATIONS_SYSTEM_PROMPT =
            "You are a graph memory manager specializing in identifying, managing, and optimizing relationships within graph-based memories. "
                    + "Your primary task is to analyze a list of existing relationships and determine which ones should be deleted based on the new information provided.\n"
                    + "Input:\n"
                    + "1. Existing Graph Memories: A list of current graph memories, each containing source, relationship, and destination information.\n"
                    + "2. New Text: The new information to be integrated into the existing graph structure.\n"
                    + "3. Use \"USER_ID\" as node for any self-references (e.g., \"I,\" \"me,\" \"my,\" etc.) in user messages.\n\n"
                    + "Guidelines:\n"
                    + "1. Identification: Use the new information to evaluate existing relationships in the memory graph.\n"
                    + "2. Deletion Criteria: Delete a relationship only if it meets at least one of these conditions:\n"
                    + "   - Outdated or Inaccurate: The new information is more recent or accurate.\n"
                    + "   - Contradictory: The new information conflicts with or negates the existing information.\n"
                    + "3. DO NOT DELETE if their is a possibility of same type of relationship but different destination nodes.\n"
                    + "4. Comprehensive Analysis:\n"
                    + "   - Thoroughly examine each existing relationship against the new information and delete as necessary.\n"
                    + "   - Multiple deletions may be required based on the new information.\n"
                    + "5. Semantic Integrity:\n"
                    + "   - Ensure that deletions maintain or improve the overall semantic structure of the graph.\n"
                    + "   - Avoid deleting relationships that are NOT contradictory/outdated to the new information.\n"
                    + "6. Temporal Awareness: Prioritize recency when timestamps are available.\n"
                    + "7. Necessity Principle: Only DELETE relationships that must be deleted and are contradictory/outdated to the new information to maintain an accurate and coherent memory graph.\n\n"
                    + "Note: DO NOT DELETE if their is a possibility of same type of relationship but different destination nodes.\n\n"
                    + "For example:\n"
                    + "Existing Memory: alice -- loves_to_eat -- pizza\n"
                    + "New Information: Alice also loves to eat burger.\n\n"
                    + "Do not delete in the above example because there is a possibility that Alice loves to eat both pizza and burger.\n\n"
                    + "Memory Format:\n"
                    + "source -- relationship -- destination\n\n"
                    + "Provide a list of deletion instructions, each specifying the relationship to be deleted.";

    public static String extractRelationsSystemPrompt(String userIdentity, String customPrompt) {
        String p = EXTRACT_RELATIONS_PROMPT;
        if (userIdentity != null && !userIdentity.isBlank()) {
            p = p.replace("USER_ID", userIdentity);
        }
        if (customPrompt != null && !customPrompt.isBlank()) {
            p = p.replace("CUSTOM_PROMPT", "4. " + customPrompt);
        } else {
            p = p.replace("CUSTOM_PROMPT", "");
        }
        return p;
    }

    public static String deleteRelationsSystemPrompt(String userIdForSelfRef, String customPrompt) {
        String p = DELETE_RELATIONS_SYSTEM_PROMPT;
        String uid = (userIdForSelfRef == null || userIdForSelfRef.isBlank()) ? "USER_ID" : userIdForSelfRef;
        p = p.replace("USER_ID", uid);
        // Currently we don't have a separate custom delete prompt; allow appending.
        if (customPrompt != null && !customPrompt.isBlank()) {
            p = p + "\n\nCUSTOM:\n" + customPrompt;
        }
        return p;
    }

    public static String deleteRelationsUserPrompt(String existingMemories, String newText) {
        String em = existingMemories == null ? "" : existingMemories;
        String nt = newText == null ? "" : newText;
        return "Here are the existing memories: " + em + " \n\n New Information: " + nt;
    }
}

