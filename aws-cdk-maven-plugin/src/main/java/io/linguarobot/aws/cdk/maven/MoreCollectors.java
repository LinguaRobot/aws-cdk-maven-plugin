package io.linguarobot.aws.cdk.maven;

import java.util.Comparator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Contains additional implementations of {@link Collector}.
 */
public final class MoreCollectors {

    private MoreCollectors() {
    }

    /**
     * Returns a {@link Collector} that will sort elements of the stream before passing them to the downstream collector.
     *
     * @param downstreamCollector a collector that will accept the sorted elements
     * @param <T> the types of the input elements
     * @param <R> the result type of the collector
     *
     * @return a collector sorting the elements of the stream before passing them to the next, downstream collector
     */
    public static <T extends Comparable<? super T>, R> Collector<T, ?, R> sorting(Collector<T, ?, R> downstreamCollector) {
        return sorting(Comparator.naturalOrder(), downstreamCollector);
    }

    /**
     * Returns a {@link Collector} that will sort elements of the stream using the given comparator before passing
     * them to the downstream collector.
     *
     * @param comparator the comparator used to compare the elements of the stream
     * @param downstreamCollector a collector that will accept the sorted elements
     * @param <T> the types of the input elements
     * @param <R> the result type of the collector
     *
     * @return a collector sorting the elements of the stream before passing them to the next, downstream collector
     */
    public static <T, R> Collector<T, ?, R> sorting(Comparator<? super T> comparator, Collector<T, ?, R> downstreamCollector) {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                values -> values.stream().sorted(comparator).collect(downstreamCollector)
        );
    }
}
