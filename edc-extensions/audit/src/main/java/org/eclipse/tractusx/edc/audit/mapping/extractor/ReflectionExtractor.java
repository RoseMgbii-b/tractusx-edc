package org.eclipse.tractusx.edc.audit.mapping.extractor;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.*;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.*;
import org.eclipse.edc.spi.monitor.Monitor;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Reflection Extractor
 * just a utility helper to safely extract the string properties from event modules
 */
public class ReflectionExtractor {
    private final Monitor monitor;

    public ReflectionExtractor(Monitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Safely extract a string-based propert from an event object.
     *
     * @param payload       the event payload to extract from
     * @param fieldName     the field name to extract
     * @return              the extracted value
     */
    public String extractStringProperty(Object payload, String fieldName) {
        if (payload == null || fieldName == null) return null;

        // Extract using standard getter
        try {
            var getterMethod = payload.getClass().getMethod("get" + capitalize(fieldName));
            var value = getterMethod.invoke(payload);
            if (value != null) return value.toString();
        } catch (Exception ignored) {}

        // Extract using boolean getter
        try {
            var getterMethod = payload.getClass().getMethod("is" + capitalize(fieldName));
            var value = getterMethod.invoke(payload);
            if (value != null) return value.toString();
        } catch (Exception ignored) {}

        // Extract with direct field access
        try {
            var field = findField(payload.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                var value = field.get(payload);
                if (value != null) return value.toString();
            }
        } catch (Exception ignored) {}

        // Extract with loose matching between field and requested name
        try {
            var field = findFieldLoosely(payload.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                var value = field.get(payload);
                if (value != null) return value.toString();
            }
        } catch (Exception ignored) {}


        // Fallback for events where fields live inside nested objects
        try {
            var eventMethods = payload.getClass().getDeclaredMethods();

            for (var potentialGetter : eventMethods) {

                // Only inspect real getters
                if (!potentialGetter.getName().startsWith("get")) {
                    continue;
                }

                // Invoke getter to obtain nested model object (e.g., ContractNegotiation)
                var nestedObject = potentialGetter.invoke(payload);
                if (nestedObject == null) {
                    continue;
                }

                try {
                    // Now try to find the desired field getter inside the nested object
                    var nestedFieldGetter = nestedObject.getClass()
                            .getDeclaredMethod("get" + capitalize(fieldName));

                    var nestedFieldValue = nestedFieldGetter.invoke(nestedObject);

                    if (nestedFieldValue != null) {
                        return nestedFieldValue.toString();
                    }

                } catch (NoSuchMethodException ignored) {
                    // This nested object doesn't have the field; keep searching
                }
            }
        } catch (Exception ignored) {}

        // Debug information
        monitor.debug("[AuditEventMapperRegistry] Could not extract property '" + fieldName +
                "' from event: " + payload.getClass().getName() +
                ". Event structure: " + describeEvent(payload));

        return null;
    }

    /**
     * Attempts to find a declared field with an exact name match on the target class or any of its superclasses.
     *
     * @param type      the type of class to search
     * @param name      the field name
     * @return          the matching field or null if not found
     */
    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Performs a more loose search for a field whose name is similar to the one we are requestin
     *
     * @param type          the class to search
     * @param name          the name requested
     * @return              the matching field or null if not found
     */
    private Field findFieldLoosely(Class<?> type, String name) {
        String target = normalize(name);

        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (var field : current.getDeclaredFields()) {
                String candidate = normalize(field.getName());

                // loose match: both normalized strings must contain each other
                if (candidate.contains(target) || target.contains(candidate)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Normalize string and remove underscores or dashes
     */
    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replace("_", "")
                .replace("-", "");
    }


    /**
     * Returns a description of an event's structure for dubugging if property extraction still fails
     *
     * @param payload   the event object that could not be extracted
     * @return          a description of the object's fields and getters
     */
    private String describeEvent(Object payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fields=[");
        Arrays.stream(payload.getClass().getDeclaredFields())
                .forEach(f -> sb.append(f.getName()).append(", "));
        sb.append("], Methods=[");

        Arrays.stream(payload.getClass().getMethods())
                .filter(m -> m.getName().startsWith("get") || m.getName().startsWith("is"))
                .forEach(m -> sb.append(m.getName()).append(", "));
        sb.append("]");

        return sb.toString();
    }

    /**
     * Just to capitalize the first character of the field name
     * like changing getid to getId
     */
    private String capitalize(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }


}
