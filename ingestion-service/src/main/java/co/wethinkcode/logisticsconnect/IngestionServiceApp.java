package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class IngestionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceApp.class);
    public static final int PORT = 7050;

    public static void main(String[] args) {
        IngestionReport report = new HubCsvCleaner().clean(loadCsv());
        log.info("Cleaned {} rows -> {} hub records ({} duplicates merged, {} placeholders, {} invalid values)",
                report.stats().rowsRead(), report.stats().recordsExposed(),
                report.stats().duplicatesMerged(), report.stats().placeholdersReplaced(),
                report.stats().invalidValues());



        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));



        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.


        app.get("/hubs", ctx -> ctx.json(report.hubs()));

        app.get("/hubs/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();
            report.hubs().stream()
                    .filter(hub -> hub.hubId().equals(hubId))
                    .findFirst()
                    .ifPresentOrElse(
                            ctx::json,
                            () -> ctx.status(404).json(Map.of(
                                    "error", "unknown hub",
                                    "hubId", hubId)));
        });

        app.get("/stats", ctx -> ctx.json(report.stats()));

        app.get("/report", ctx -> ctx.json(report));
    }

    static String loadCsv() {
        try (InputStream in = IngestionServiceApp.class.getResourceAsStream("/hubs-global.csv")) {
            if (in == null) {
                throw new IllegalStateException("hubs-global.csv not found on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read hubs-global.csv", e);
        }
    }
}