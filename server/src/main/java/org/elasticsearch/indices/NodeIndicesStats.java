/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.indices;

import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.action.admin.indices.stats.IndexShardStats;
import org.elasticsearch.action.admin.indices.stats.ShardStats;
import org.elasticsearch.index.bulk.stats.BulkStats;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.cache.query.QueryCacheStats;
import org.elasticsearch.index.cache.request.RequestCacheStats;
import org.elasticsearch.index.engine.SegmentsStats;
import org.elasticsearch.index.fielddata.FieldDataStats;
import org.elasticsearch.index.flush.FlushStats;
import org.elasticsearch.index.get.GetStats;
import org.elasticsearch.index.merge.MergeStats;
import org.elasticsearch.index.recovery.RecoveryStats;
import org.elasticsearch.index.refresh.RefreshStats;
import org.elasticsearch.index.search.stats.SearchStats;
import org.elasticsearch.index.shard.DocsStats;
import org.elasticsearch.index.shard.IndexingStats;
import org.elasticsearch.index.store.StoreStats;
import org.elasticsearch.index.translog.TranslogStats;
import org.elasticsearch.index.warmer.WarmerStats;
import org.elasticsearch.search.suggest.completion.CompletionStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Global information on indices stats running on a specific node.
 */
public class NodeIndicesStats implements Writeable, ToXContentFragment {

    private final CommonStats stats;
    private final Map<Index, List<IndexShardStats>> statsByShard;

    public NodeIndicesStats(StreamInput in) throws IOException {
        stats = new CommonStats(in);

        final boolean hasStatsByShard;
        if (in.getVersion().onOrAfter(Version.V_8_0_0)) {
            hasStatsByShard = true;
        } else {
            hasStatsByShard = in.readBoolean();
        }

        statsByShard = new HashMap<>();
        if (hasStatsByShard) {
            int entries = in.readVInt();
            for (int i = 0; i < entries; i++) {
                Index index = new Index(in);
                int indexShardListSize = in.readVInt();
                List<IndexShardStats> indexShardStats = new ArrayList<>(indexShardListSize);
                for (int j = 0; j < indexShardListSize; j++) {
                    indexShardStats.add(new IndexShardStats(in));
                }
                statsByShard.put(index, indexShardStats);
            }
        }
    }

    public NodeIndicesStats(CommonStats oldStats, Map<Index, List<IndexShardStats>> statsByShard) {
        this.statsByShard = Objects.requireNonNull(statsByShard);

        // make a total common stats from old ones and current ones
        this.stats = oldStats;
        for (List<IndexShardStats> shardStatsList : statsByShard.values()) {
            for (IndexShardStats indexShardStats : shardStatsList) {
                for (ShardStats shardStats : indexShardStats.getShards()) {
                    stats.add(shardStats.getStats());
                }
            }
        }
    }

    @Nullable
    public StoreStats getStore() {
        return stats.getStore();
    }

    @Nullable
    public DocsStats getDocs() {
        return stats.getDocs();
    }

    @Nullable
    public IndexingStats getIndexing() {
        return stats.getIndexing();
    }

    @Nullable
    public GetStats getGet() {
        return stats.getGet();
    }

    @Nullable
    public SearchStats getSearch() {
        return stats.getSearch();
    }

    @Nullable
    public MergeStats getMerge() {
        return stats.getMerge();
    }

    @Nullable
    public RefreshStats getRefresh() {
        return stats.getRefresh();
    }

    @Nullable
    public FlushStats getFlush() {
        return stats.getFlush();
    }

    @Nullable
    public WarmerStats getWarmer() {
        return stats.getWarmer();
    }

    @Nullable
    public FieldDataStats getFieldData() {
        return stats.getFieldData();
    }

    @Nullable
    public QueryCacheStats getQueryCache() {
        return stats.getQueryCache();
    }

    @Nullable
    public RequestCacheStats getRequestCache() {
        return stats.getRequestCache();
    }

    @Nullable
    public CompletionStats getCompletion() {
        return stats.getCompletion();
    }

    @Nullable
    public SegmentsStats getSegments() {
        return stats.getSegments();
    }

    @Nullable
    public TranslogStats getTranslog() {
        return stats.getTranslog();
    }

    @Nullable
    public RecoveryStats getRecoveryStats() {
        return stats.getRecoveryStats();
    }

    @Nullable
    public BulkStats getBulk() {
        return stats.getBulk();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        stats.writeTo(out);
        if (out.getVersion().before(Version.V_8_0_0)) {
            out.writeBoolean(true);
        }
        out.writeVInt(statsByShard.size());
        for (Map.Entry<Index, List<IndexShardStats>> entry : statsByShard.entrySet()) {
            entry.getKey().writeTo(out);
            out.writeVInt(entry.getValue().size());
            for (IndexShardStats indexShardStats : entry.getValue()) {
                indexShardStats.writeTo(out);
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        final String level = params.param("level", "node");
        final boolean isLevelValid =
            "indices".equalsIgnoreCase(level) || "node".equalsIgnoreCase(level) || "shards".equalsIgnoreCase(level);
        if (isLevelValid == false) {
            throw new IllegalArgumentException("level parameter must be one of [indices] or [node] or [shards] but was [" + level + "]");
        }

        // "node" level
        builder.startObject(Fields.INDICES);
        stats.toXContent(builder, params);

        if ("indices".equals(level)) {
            Map<Index, CommonStats> indexStats = createStatsByIndex();
            builder.startObject(Fields.INDICES);
            for (Map.Entry<Index, CommonStats> entry : indexStats.entrySet()) {
                builder.startObject(entry.getKey().getName());
                entry.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        } else if ("shards".equals(level)) {
            builder.startObject("shards");
            for (Map.Entry<Index, List<IndexShardStats>> entry : statsByShard.entrySet()) {
                builder.startArray(entry.getKey().getName());
                for (IndexShardStats indexShardStats : entry.getValue()) {
                    builder.startObject().startObject(String.valueOf(indexShardStats.getShardId().getId()));
                    for (ShardStats shardStats : indexShardStats.getShards()) {
                        shardStats.toXContent(builder, params);
                    }
                    builder.endObject().endObject();
                }
                builder.endArray();
            }
            builder.endObject();
        }

        builder.endObject();
        return builder;
    }

    private Map<Index, CommonStats> createStatsByIndex() {
        Map<Index, CommonStats> statsMap = new HashMap<>();
        for (Map.Entry<Index, List<IndexShardStats>> entry : statsByShard.entrySet()) {
            if (statsMap.containsKey(entry.getKey()) == false) {
                statsMap.put(entry.getKey(), new CommonStats());
            }

            for (IndexShardStats indexShardStats : entry.getValue()) {
                for (ShardStats shardStats : indexShardStats.getShards()) {
                    statsMap.get(entry.getKey()).add(shardStats.getStats());
                }
            }
        }

        return statsMap;
    }

    public List<IndexShardStats> getShardStats(Index index) {
        return statsByShard.get(index);
    }

    static final class Fields {
        static final String INDICES = "indices";
    }
}
