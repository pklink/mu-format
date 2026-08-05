package net.einself.mu.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputFormatterTest {

    private OutputFormatter underTest;

    private ByteArrayOutputStream buffer;

    private PrintStream out;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        out = new PrintStream(buffer);
    }

    @Test
    void validate_acceptsText() {
        OutputFormatter.validate("text");
    }

    @Test
    void validate_acceptsJson() {
        OutputFormatter.validate("json");
    }

    @Test
    void validate_acceptsFormatsCaseInsensitively() {
        OutputFormatter.validate("TEXT");
        OutputFormatter.validate("Json");
        OutputFormatter.validate("JSON");
    }

    @Test
    void validate_rejectsNull() {
        assertThatThrownBy(() -> OutputFormatter.validate(null))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Invalid --format")
                                        .extracting(e -> ((MuException) e).exitCode())
                                        .isEqualTo(ExitCode.USAGE);
    }

    @Test
    void validate_rejectsUnknownFormat() {
        assertThatThrownBy(() -> OutputFormatter.validate("yaml"))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Invalid --format")
                                        .hasMessageContaining("yaml")
                                        .extracting(e -> ((MuException) e).exitCode())
                                        .isEqualTo(ExitCode.USAGE);
    }

    @Test
    void write_jsonEmitsEnvelopeWithCommandAndData() {
        underTest = new OutputFormatter(out);
        underTest.write("json", "search", new SampleData("good-lies", 1),
                                        printer -> printer.println("should not appear"));

        JsonObject json = JsonParser.parseString(buffer.toString()).getAsJsonObject();
        assertThat(json.get("command").getAsString()).isEqualTo("search");
        assertThat(json.getAsJsonObject("data").get("id").getAsString()).isEqualTo("good-lies");
        assertThat(json.getAsJsonObject("data").get("count").getAsInt()).isEqualTo(1);
    }

    @Test
    void write_jsonEmitsTrailingNewline() {
        underTest = new OutputFormatter(out);
        underTest.write("json", "search", new SampleData("x", 0), printer -> {
        });

        assertThat(buffer.toString()).endsWith(System.lineSeparator());
    }

    @Test
    void write_jsonIsCaseInsensitive() {
        underTest = new OutputFormatter(out);
        underTest.write("JSON", "search", new SampleData("x", 0), printer -> {
        });

        JsonObject json = JsonParser.parseString(buffer.toString()).getAsJsonObject();
        assertThat(json.get("command").getAsString()).isEqualTo("search");
    }

    @Test
    void write_jsonDoesNotInvokeTextWriter() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        underTest = new OutputFormatter(out);
        underTest.write("json", "search", new SampleData("x", 0),
                                        printer -> invoked.set(true));

        assertThat(invoked).isFalse();
    }

    @Test
    void write_textDelegatesToTextWriter() {
        underTest = new OutputFormatter(out);
        underTest.write("text", "search", new SampleData("good-lies", 1),
                                        printer -> printer.println("Releases (1 found):"));

        assertThat(buffer.toString()).contains("Releases (1 found):");
        assertThat(buffer.toString()).doesNotContain("\"command\"");
    }

    private record SampleData(String id, int count) {
    }

}