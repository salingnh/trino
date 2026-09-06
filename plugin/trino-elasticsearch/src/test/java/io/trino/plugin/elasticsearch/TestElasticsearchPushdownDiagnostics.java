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
package io.trino.plugin.elasticsearch;

import com.google.common.collect.ImmutableList;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Decision;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.COUNT;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.NEXT_PAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.SEARCH;
import static io.trino.plugin.elasticsearch.ElasticsearchQueryBuilder.buildSearchQuery;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchPushdownDiagnostics
{
    @Test
    public void testJmxExportReadsLiveDiagnosticState()
            throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        MBeanExporter exporter = new MBeanExporter(server);
        ObjectName name = new ObjectName("test:type=ElasticsearchPushdownDiagnostics");
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        try {
            exporter.export(name, diagnostics);
            diagnostics.recordRemoteRequest(SEARCH);
            diagnostics.recordTranslation(new Decision(Reason.EXACT_DOMAIN, Optional.of(EXACT), true, false, false, List.of()));
            assertThat(server.getAttribute(name, "SearchRequests")).isEqualTo(1L);
            assertThat(server.getAttribute(name, "ExactTranslations")).isEqualTo(diagnostics.snapshot().exactTranslations());
            assertThat(server.getAttribute(name, "TranslationReasonCounts")).isEqualTo(diagnostics.getTranslationReasonCounts());
            assertThat(server.getAttribute(name, "DynamicFilterOutcomes")).isEqualTo(diagnostics.getDynamicFilterOutcomes());
            assertThat(server.getAttribute(name, "NormalizationCounts")).isEqualTo(diagnostics.getNormalizationCounts());
        }
        finally {
            exporter.unexport(name);
        }
        assertThat(server.isRegistered(name)).isFalse();
    }

    @Test
    public void testDebugLoggingDoesNotChangeRenderingOrAccounting()
    {
        Logger logger = Logger.getLogger(ElasticsearchPushdownDiagnostics.class.getName());
        Level originalLevel = logger.getLevel();
        List<String> messages = new CopyOnWriteArrayList<>();
        Handler handler = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        ElasticsearchTableHandle table = withRemotePredicate(
                new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty()),
                Optional.of(new ElasticsearchRemotePredicate.Term("id", "private-value")));
        ElasticsearchPushdownDiagnostics disabled = new ElasticsearchPushdownDiagnostics();
        ElasticsearchPushdownDiagnostics enabled = new ElasticsearchPushdownDiagnostics();
        try {
            logger.addHandler(handler);
            logger.setLevel(Level.OFF);
            var query = buildSearchQuery(table, disabled);
            logger.setLevel(Level.FINE);
            assertThat(buildSearchQuery(table, enabled)).isEqualTo(query);
            assertThat(enabled.snapshot()).isEqualTo(disabled.snapshot());
            assertThat(messages).anyMatch(message -> message.startsWith("Rendered query:"));
            assertThat(messages).noneMatch(message -> message.contains("private-value"));
        }
        finally {
            logger.setLevel(originalLevel);
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testTranslationDecisionAccounting()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        Decision decision = new Decision(
                Reason.BOOLEAN_AND,
                Optional.of(PREFILTER),
                true,
                false,
                true,
                List.of(
                        new Decision(
                                Reason.EXACT_DOMAIN,
                                Optional.of(EXACT),
                                true,
                                false,
                                false,
                                List.of()),
                        new Decision(
                                Reason.EXACT_ARRAY,
                                Optional.of(EXACT),
                                true,
                                false,
                                false,
                                List.of()),
                        new Decision(
                                Reason.EXACT_ANY_MATCH,
                                Optional.of(EXACT),
                                true,
                                false,
                                false,
                                List.of()),
                        new Decision(
                                Reason.FULL_TEXT_SAFE_PREFILTER,
                                Optional.of(PREFILTER),
                                true,
                                false,
                                true,
                                List.of())));

        diagnostics.recordTranslation(decision);

        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(snapshot.translationNodes()).isEqualTo(5);
        assertThat(snapshot.exactTranslations()).isEqualTo(3);
        assertThat(snapshot.prefilterTranslations()).isEqualTo(2);
        assertThat(snapshot.approximateTranslations()).isZero();
        assertThat(snapshot.residualTranslations()).isEqualTo(2);
        assertThat(snapshot.remainingTranslations()).isZero();
        assertThat(snapshot.arrayMembershipTranslations()).isEqualTo(1);
        assertThat(snapshot.anyMatchTranslations()).isEqualTo(1);
        assertThat(snapshot.booleanAndTranslations()).isEqualTo(1);
        assertThat(snapshot.translationReasonCounts())
                .containsEntry(Reason.BOOLEAN_AND.name(), 1L)
                .containsEntry(Reason.EXACT_DOMAIN.name(), 1L)
                .containsEntry(Reason.EXACT_ARRAY.name(), 1L)
                .containsEntry(Reason.EXACT_ANY_MATCH.name(), 1L)
                .containsEntry(Reason.FULL_TEXT_SAFE_PREFILTER.name(), 1L);
    }

    @Test
    public void testRemotePredicateAccounting()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.And(ImmutableList.of(
                        new ElasticsearchRemotePredicate.Term("status", "active"),
                        new ElasticsearchRemotePredicate.Terms("id", ImmutableList.of(1L, 2L, 3L)),
                        new ElasticsearchRemotePredicate.Range(
                                "score",
                                Optional.of(new ElasticsearchRemotePredicate.Bound(10L, true)),
                                Optional.empty()),
                        new ElasticsearchRemotePredicate.Or(ImmutableList.of(
                                new ElasticsearchRemotePredicate.Prefix("name", "A"),
                                new ElasticsearchRemotePredicate.Regexp("name", "B.*"))))),
                PREFILTER);

        diagnostics.recordRemotePredicate(predicate);

        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(snapshot.remotePredicateNodes()).isEqualTo(8);
        assertThat(snapshot.enforcementPredicates()).isEqualTo(1);
        assertThat(snapshot.andPredicates()).isEqualTo(1);
        assertThat(snapshot.orPredicates()).isEqualTo(1);
        assertThat(snapshot.termPredicates()).isEqualTo(1);
        assertThat(snapshot.termsPredicates()).isEqualTo(1);
        assertThat(snapshot.termsValues()).isEqualTo(3);
        assertThat(snapshot.rangePredicates()).isEqualTo(1);
        assertThat(snapshot.prefixPredicates()).isEqualTo(1);
        assertThat(snapshot.regexpPredicates()).isEqualTo(1);
    }

    @Test
    public void testExecutionAccountingContract()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();

        diagnostics.recordRemoteRequest(SEARCH);
        diagnostics.recordRemoteRequest(NEXT_PAGE);
        diagnostics.recordRemoteRequest(COUNT);
        diagnostics.recordDecodedRows(3, 120);
        diagnostics.recordPageReturned();
        diagnostics.recordRetryAttempt();
        diagnostics.recordCancellation();
        diagnostics.recordFailure();
        diagnostics.recordClearScroll();

        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(snapshot.searchRequests()).isEqualTo(1);
        assertThat(snapshot.nextPageRequests()).isEqualTo(1);
        assertThat(snapshot.countRequests()).isEqualTo(1);
        assertThat(snapshot.rowsDecoded()).isEqualTo(3);
        assertThat(snapshot.sourceBytesDecoded()).isEqualTo(120);
        assertThat(snapshot.pagesReturned()).isEqualTo(1);
        assertThat(snapshot.retryAttempts()).isEqualTo(1);
        assertThat(snapshot.cancellations()).isEqualTo(1);
        assertThat(snapshot.failures()).isEqualTo(1);
        assertThat(snapshot.clearScrollCalls()).isEqualTo(1);
    }

    @Test
    public void testJmxCountersExposeSameStateAsSnapshot()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        diagnostics.recordTranslation(new Decision(
                Reason.EXACT_DOMAIN,
                Optional.of(EXACT),
                true,
                false,
                false,
                List.of()));
        diagnostics.recordRemotePredicate(new ElasticsearchRemotePredicate.Terms("id", ImmutableList.of(1L, 2L)));

        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(diagnostics.getTranslationNodes()).isEqualTo(snapshot.translationNodes());
        assertThat(diagnostics.getExactTranslations()).isEqualTo(snapshot.exactTranslations());
        assertThat(diagnostics.getRemotePredicateNodes()).isEqualTo(snapshot.remotePredicateNodes());
        assertThat(diagnostics.getTermsPredicates()).isEqualTo(snapshot.termsPredicates());
        assertThat(diagnostics.getTermsValues()).isEqualTo(snapshot.termsValues());
    }
}
