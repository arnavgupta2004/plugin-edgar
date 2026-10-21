package io.kestra.plugin.edgar;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Resolves tickers to companies with SEC's ticker file, which lists every company with a ticker, its CIK and its exchange.
 */
public final class CompanyTickers {
    static final String PATH = "/files/company_tickers_exchange.json";

    private CompanyTickers() {
    }

    /**
     * Returns one company per ticker, in the order given.
     *
     * @throws IllegalArgumentException naming every ticker SEC doesn't list
     */
    public static List<Company> resolve(EdgarClient client, URI baseUri, List<String> tickers) throws Exception {
        var file = client.getJson(URI.create(baseUri + PATH), TickerFile.class);
        var byTicker = index(file);

        var companies = new ArrayList<Company>();
        var unknown = new ArrayList<String>();
        for (var ticker : tickers) {
            var normalized = ticker.trim().toUpperCase(Locale.ROOT);
            var company = byTicker.containsKey(normalized) ? byTicker.get(normalized) : byTicker.get(normalized.replace('.', '-'));
            if (company == null) {
                unknown.add(ticker.trim());
            } else {
                companies.add(company);
            }
        }

        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                "SEC lists no company with the ticker %s. Check the symbol; EDGAR writes class shares with a hyphen, for example `BRK-B`."
                    .formatted(String.join(", ", unknown))
            );
        }
        return companies;
    }

    private static Map<String, Company> index(TickerFile file) {
        if (file.fields() == null || file.data() == null) {
            throw new IllegalStateException("SEC's ticker file %s has an unexpected format: `fields` or `data` is missing.".formatted(PATH));
        }
        var cik = column(file, "cik");
        var name = column(file, "name");
        var ticker = column(file, "ticker");
        var exchange = column(file, "exchange");

        var byTicker = new HashMap<String, Company>();
        for (var row : file.data()) {
            var symbol = (String) row.get(ticker);
            if (symbol == null) {
                continue;
            }
            byTicker.putIfAbsent(symbol.toUpperCase(Locale.ROOT), Company.builder()
                .ticker(symbol)
                .cik("%010d".formatted(((Number) row.get(cik)).longValue()))
                .name((String) row.get(name))
                .exchange((String) row.get(exchange))
                .build());
        }
        return byTicker;
    }

    private static int column(TickerFile file, String field) {
        var index = file.fields().indexOf(field);
        if (index < 0) {
            throw new IllegalStateException("SEC's ticker file %s has no `%s` column.".formatted(PATH, field));
        }
        return index;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TickerFile(List<String> fields, List<List<Object>> data) {
    }
}
