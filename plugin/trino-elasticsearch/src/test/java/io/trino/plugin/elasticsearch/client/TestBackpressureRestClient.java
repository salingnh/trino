/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.elasticsearch.client;

import com.sun.net.httpserver.HttpServer;
import io.airlift.stats.TimeStat;
import io.airlift.units.Duration;
import io.trino.plugin.elasticsearch.ElasticsearchConfig;
import io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics;
import org.apache.http.HttpHost;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestBackpressureRestClient
{
    @Test
    public void testRetryAccountingUsesActualAttempts()
            throws Exception
    {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/retry", exchange -> {
            try (exchange) {
                int attempt = requests.incrementAndGet();
                exchange.sendResponseHeaders(attempt <= 2 ? 429 : 200, -1);
            }
        });
        server.createContext("/failure", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                exchange.sendResponseHeaders(400, -1);
            }
        });
        server.start();
        try (RestClient delegate = RestClient.builder(new HttpHost(server.getAddress().getAddress().getHostAddress(), server.getAddress().getPort())).build()) {
            ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
            ElasticsearchConfig config = new ElasticsearchConfig()
                    .setBackoffInitDelay(new Duration(1, MILLISECONDS))
                    .setBackoffMaxDelay(new Duration(2, MILLISECONDS))
                    .setMaxRetryTime(new Duration(10, SECONDS));
            BackpressureRestClient client = new BackpressureRestClient(delegate, config, new TimeStat(MILLISECONDS), diagnostics);

            assertThat(client.performRequest("GET", "/retry").getStatusLine().getStatusCode()).isEqualTo(200);
            assertThat(requests.get()).isEqualTo(3);
            assertThat(diagnostics.getRetryAttempts()).isEqualTo(2);
            assertThat(diagnostics.snapshot().retryAttempts()).isEqualTo(2);

            assertThatThrownBy(() -> client.performRequest("GET", "/failure")).isInstanceOf(ResponseException.class);
            assertThat(requests.get()).isEqualTo(4);
            assertThat(diagnostics.getRetryAttempts()).isEqualTo(2);
        }
        finally {
            server.stop(0);
        }
    }
}
