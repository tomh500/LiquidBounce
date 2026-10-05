package net.ccbluex.liquidbounce.config.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.config.types.ValueType;
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue;

import java.util.Arrays;

/** Normalizes older persisted shapes before upstream's ordinary value deserializer runs. */
public final class LegacyValueMigration {
    private LegacyValueMigration() { }

    public static JsonElement normalize(Value<?> value, JsonElement element) {
        if (!(element instanceof JsonPrimitive primitive)) {
            return element;
        }

        if (primitive.isBoolean() && value.getName().equals("AcceleratedPaint")
            && value instanceof ChoiceListValue<?> choice) {
            var tags = Arrays.asList(choice.getChoicesStrings());
            if (tags.contains("On") && tags.contains("Off")) {
                return new JsonPrimitive(primitive.getAsBoolean() ? "On" : "Off");
            }
        }

        // A fixed scalar from an old version is a fixed range, not a new random interval.
        if (primitive.isNumber() && (value.getValueType() == ValueType.FLOAT_RANGE
            || value.getValueType() == ValueType.INT_RANGE)) {
            var range = new JsonObject();
            range.add("from", primitive);
            range.add("to", primitive);
            return range;
        }
        return element;
    }
}
