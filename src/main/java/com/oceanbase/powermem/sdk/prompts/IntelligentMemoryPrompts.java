package com.oceanbase.powermem.sdk.prompts;

/**
 * Prompts used by intelligent memory extraction, retrieval, and consolidation.
 *
 * <p>Python reference: {@code src/powermem/prompts/intelligent_memory_prompts.py}</p>
 */
public final class IntelligentMemoryPrompts {
    private IntelligentMemoryPrompts() {}

    public static String factRetrievalPrompt() {
        // Ported from Python FACT_RETRIEVAL_PROMPT (trimmed, but behavior-compatible).
        String today = java.time.LocalDate.now().toString();
        return "You are a Personal Information Organizer. Extract relevant facts, memories, and preferences from conversations into distinct, manageable facts.\n\n"
                + "Information Types: Personal preferences, details (names, relationships, dates), plans, activities, health/wellness, professional, miscellaneous.\n\n"
                + "CRITICAL Rules:\n"
                + "1. TEMPORAL: ALWAYS extract time info (dates, relative refs like \"yesterday\", \"last week\"). Include in facts.\n"
                + "2. COMPLETE: Extract self-contained facts with who/what/when/where when available.\n"
                + "3. SEPARATE: Extract distinct facts separately.\n\n"
                + "Rules:\n"
                + "- Today: " + today + "\n"
                + "- Return JSON: {\"facts\": [\"fact1\", \"fact2\"]}\n"
                + "- Extract from user/assistant messages only\n"
                + "- If no relevant facts, return empty list\n"
                + "- Preserve input language\n\n"
                + "Extract facts from the conversation below:";
    }

    public static String defaultUpdateMemoryPrompt() {
        return "You are a memory manager. Compare new facts with existing memory. Decide: ADD, UPDATE, DELETE, or NONE.\n\n"
                + "Operations:\n"
                + "1. ADD: New info not in memory -> add with new ID\n"
                + "2. UPDATE: Info exists but different/enhanced -> update (keep same ID). Prefer fact with most information.\n"
                + "3. DELETE: Contradictory info -> delete (use sparingly)\n"
                + "4. NONE: Already present or irrelevant -> no change\n\n"
                + "Temporal Rules (CRITICAL):\n"
                + "- New fact has time info, memory doesn't -> UPDATE memory to include time\n"
                + "- Both have time, new is more specific/recent -> UPDATE to new time\n"
                + "- Time conflicts -> UPDATE to more recent\n"
                + "- Preserve relative time refs\n\n"
                + "Important: Use existing IDs only. Keep same ID when updating. Always preserve temporal information.\n";
    }

    public static String getMemoryUpdatePrompt(java.util.List<java.util.Map<String, Object>> retrievedOldMemory,
                                              java.util.List<String> newFacts,
                                              String customPrompt) {
        String prompt = (customPrompt == null || customPrompt.isBlank()) ? defaultUpdateMemoryPrompt() : customPrompt;
        String current;
        if (retrievedOldMemory != null && !retrievedOldMemory.isEmpty()) {
            current = "Current memory:\n```\n" + retrievedOldMemory + "\n```\n";
        } else {
            current = "Current memory is empty.\n";
        }
        StringBuilder facts = new StringBuilder();
        if (newFacts != null) {
            for (String f : newFacts) {
                if (f == null || f.isBlank()) {
                    continue;
                }
                facts.append("- ").append(f).append("\n");
            }
        }
        return prompt + "\n\n" + current
                + "New facts:\n```\n" + facts + "```\n\n"
                + "Return JSON only:\n"
                + "{\n"
                + "  \"memory\": [\n"
                + "    {\n"
                + "      \"id\": \"<existing ID for update/delete, new ID for add>\",\n"
                + "      \"text\": \"<memory content>\",\n"
                + "      \"event\": \"ADD|UPDATE|DELETE|NONE\",\n"
                + "      \"old_memory\": \"<old content, required for UPDATE>\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    public static String parseMessagesForFacts(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (com.oceanbase.powermem.sdk.model.Message m : messages) {
            if (m == null) {
                continue;
            }
            if ("system".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }
}

