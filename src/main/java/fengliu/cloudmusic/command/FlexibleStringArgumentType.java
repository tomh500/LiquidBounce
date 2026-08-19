package fengliu.cloudmusic.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.List;

/** String arguments matching CloudMusic's user-facing input behavior. */
public final class FlexibleStringArgumentType implements ArgumentType<String> {
    private final boolean greedy;

    private FlexibleStringArgumentType(boolean greedy) {
        this.greedy = greedy;
    }

    public static FlexibleStringArgumentType word() {
        return new FlexibleStringArgumentType(false);
    }

    public static FlexibleStringArgumentType greedy() {
        return new FlexibleStringArgumentType(true);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (!greedy) {
            if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) {
                return reader.readString();
            }
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
                reader.skip();
            }
            return reader.getString().substring(start, reader.getCursor());
        }

        String remaining = reader.getRemaining();
        reader.setCursor(reader.getTotalLength());
        if (remaining.length() >= 2 && StringReader.isQuotedStringStart(remaining.charAt(0))
                && remaining.charAt(remaining.length() - 1) == remaining.charAt(0)) {
            StringReader quoted = new StringReader(remaining);
            String parsed = quoted.readString();
            if (!quoted.canRead()) {
                return parsed;
            }
        }
        return remaining;
    }

    @Override
    public Collection<String> getExamples() {
        return greedy ? List.of("测试", "测试 音乐", "quoted text") : List.of("user@example.com", "测试");
    }
}
