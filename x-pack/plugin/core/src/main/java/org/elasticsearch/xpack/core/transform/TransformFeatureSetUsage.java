/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.transform;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.xpack.core.XPackFeatureSet.Usage;
import org.elasticsearch.xpack.core.transform.transforms.TransformIndexerStats;
import org.elasticsearch.xpack.core.XPackField;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class TransformFeatureSetUsage extends Usage {

    private static final String FEATURE_COUNTS = "feature_counts";

    private final Map<String, Long> transformCountByState;
    private final Map<String, Long> transformCountByFeature;
    private final TransformIndexerStats accumulatedStats;

    public TransformFeatureSetUsage(StreamInput in) throws IOException {
        super(in);
        this.transformCountByState = in.readMap(StreamInput::readString, StreamInput::readLong);
        if (in.getVersion().onOrAfter(Version.V_8_0_0)) {  // TODO: V_7_13_0
            this.transformCountByFeature = in.readMap(StreamInput::readString, StreamInput::readLong);
        } else {
            this.transformCountByFeature = Collections.emptyMap();
        }
        this.accumulatedStats = new TransformIndexerStats(in);
    }

    public TransformFeatureSetUsage(Map<String, Long> transformCountByState,
                                    Map<String, Long> transformCountByFeature,
                                    TransformIndexerStats accumulatedStats) {
        super(XPackField.TRANSFORM, true, true);
        this.transformCountByState = Objects.requireNonNull(transformCountByState);
        this.transformCountByFeature = Objects.requireNonNull(transformCountByFeature);
        this.accumulatedStats = Objects.requireNonNull(accumulatedStats);
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return Version.V_7_5_0;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeMap(transformCountByState, StreamOutput::writeString, StreamOutput::writeLong);
        if (out.getVersion().onOrAfter(Version.V_8_0_0)) {  // TODO: V_7_13_0
            out.writeMap(transformCountByFeature, StreamOutput::writeString, StreamOutput::writeLong);
        }
        accumulatedStats.writeTo(out);
    }

    @Override
    protected void innerXContent(XContentBuilder builder, Params params) throws IOException {
        super.innerXContent(builder, params);
        if (transformCountByState.isEmpty() == false) {
            // Transforms by state
            builder.startObject(TransformField.TRANSFORMS.getPreferredName());
            long all = 0L;
            for (Entry<String, Long> entry : transformCountByState.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
                all += entry.getValue();
            }
            builder.field(Metadata.ALL, all);
            builder.endObject();

            // Transform count for each feature
            builder.field(FEATURE_COUNTS, transformCountByFeature);

            // if there are no transforms, do not show any stats
            builder.field(TransformField.STATS_FIELD.getPreferredName(), accumulatedStats);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, available, transformCountByState, transformCountByFeature, accumulatedStats);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TransformFeatureSetUsage other = (TransformFeatureSetUsage) obj;
        return Objects.equals(name, other.name) && available == other.available && enabled == other.enabled
                && Objects.equals(transformCountByState, other.transformCountByState)
                && Objects.equals(transformCountByFeature, other.transformCountByFeature)
                && Objects.equals(accumulatedStats, other.accumulatedStats);
    }
}
