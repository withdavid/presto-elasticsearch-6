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
package com.facebook.presto.elasticsearch.client;

import com.facebook.presto.spi.PrestoException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.facebook.presto.elasticsearch.ElasticsearchErrorCode.ELASTICSEARCH_CONNECTION_ERROR;
import static com.facebook.presto.elasticsearch.client.ElasticsearchClient.isDataNode;
import static com.facebook.presto.elasticsearch.client.ElasticsearchClient.selectShard;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestElasticsearchClient
{
    private static final ElasticsearchNode NODE_A = new ElasticsearchNode("a", Optional.of("node-a:9200"));
    private static final ElasticsearchNode NODE_B = new ElasticsearchNode("b", Optional.of("node-b:9200"));

    @Test
    public void testIsDataNode()
    {
        assertTrue(isDataNode(ImmutableSet.of("data")));
        assertTrue(isDataNode(ImmutableSet.of("master", "data", "ingest")));
        assertTrue(isDataNode(ImmutableSet.of("data_content", "data_hot")));
        assertTrue(isDataNode(ImmutableSet.of("data_warm")));
        assertTrue(isDataNode(ImmutableSet.of("data_cold")));
        assertTrue(isDataNode(ImmutableSet.of("data_frozen")));

        assertFalse(isDataNode(ImmutableSet.of("master")));
        assertFalse(isDataNode(ImmutableSet.of("ingest", "ml", "transform")));
        assertFalse(isDataNode(ImmutableSet.of()));
    }

    @Test
    public void testSelectShardPrefersReplicaOnKnownNode()
    {
        List<SearchShardsResponse.Shard> shardGroup = ImmutableList.of(
                new SearchShardsResponse.Shard("idx", 0, true, "a"),
                new SearchShardsResponse.Shard("idx", 0, false, "b"));

        Shard shard = selectShard("idx", shardGroup, nodesById(NODE_A, NODE_B), ImmutableList.of(NODE_A, NODE_B));

        assertEquals(shard.getIndex(), "idx");
        assertEquals(shard.getId(), 0);
        assertEquals(shard.getAddress(), Optional.of("node-b:9200"));
    }

    @Test
    public void testSelectShardFallsBackToKnownNodeWhenCopiesAreOnUnknownNodes()
    {
        List<SearchShardsResponse.Shard> shardGroup = ImmutableList.of(
                new SearchShardsResponse.Shard("idx", 3, true, "x"),
                new SearchShardsResponse.Shard("idx", 3, false, "y"));

        Shard shard = selectShard("idx", shardGroup, nodesById(NODE_A, NODE_B), ImmutableList.of(NODE_A, NODE_B));

        assertEquals(shard.getIndex(), "idx");
        assertEquals(shard.getId(), 3);
        // shard 3 % 2 nodes = the second node
        assertEquals(shard.getAddress(), Optional.of("node-b:9200"));
    }

    @Test
    public void testSelectShardFailsWhenThereAreNoDataNodes()
    {
        List<SearchShardsResponse.Shard> shardGroup = ImmutableList.of(new SearchShardsResponse.Shard("idx", 0, true, "x"));

        try {
            selectShard("idx", shardGroup, ImmutableMap.of(), ImmutableList.of());
            fail("expected a PrestoException because there are no data nodes");
        }
        catch (PrestoException e) {
            assertEquals(e.getErrorCode(), ELASTICSEARCH_CONNECTION_ERROR.toErrorCode());
            assertTrue(e.getMessage().contains("idx"));
        }
    }

    private static Map<String, ElasticsearchNode> nodesById(ElasticsearchNode... nodes)
    {
        ImmutableMap.Builder<String, ElasticsearchNode> result = ImmutableMap.builder();
        for (ElasticsearchNode node : nodes) {
            result.put(node.getId(), node);
        }
        return result.build();
    }
}