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
package com.facebook.presto.elasticsearch.decoders;

import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.spi.PrestoException;
import org.testng.annotations.Test;

import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.common.type.DoubleType.DOUBLE;
import static com.facebook.presto.common.type.IntegerType.INTEGER;
import static com.facebook.presto.common.type.RealType.REAL;
import static com.facebook.presto.common.type.SmallintType.SMALLINT;
import static com.facebook.presto.common.type.TinyintType.TINYINT;
import static com.facebook.presto.common.type.VarcharType.VARCHAR;
import static com.facebook.presto.elasticsearch.ElasticsearchErrorCode.ELASTICSEARCH_TYPE_MISMATCH;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestScalarDecoders
{
    @Test
    public void testBigintDecoder()
    {
        BigintDecoder decoder = new BigintDecoder("field");

        assertEquals(BIGINT.getLong(decode(decoder, BIGINT, 123L), 0), 123L);
        assertEquals(BIGINT.getLong(decode(decoder, BIGINT, 123), 0), 123L);
        assertEquals(BIGINT.getLong(decode(decoder, BIGINT, "956391001476614667"), 0), 956391001476614667L);
        assertEquals(BIGINT.getLong(decode(decoder, BIGINT, "-42"), 0), -42L);
        assertTrue(decode(decoder, BIGINT, "").isNull(0));
        assertTrue(decode(decoder, BIGINT, null).isNull(0));

        assertTypeMismatch(decoder, BIGINT, "1.5");
        assertTypeMismatch(decoder, BIGINT, "abc");
        assertTypeMismatch(decoder, BIGINT, true);
    }

    @Test
    public void testIntegerDecoder()
    {
        IntegerDecoder decoder = new IntegerDecoder("field");

        assertEquals(INTEGER.getLong(decode(decoder, INTEGER, 123), 0), 123L);
        assertEquals(INTEGER.getLong(decode(decoder, INTEGER, "123"), 0), 123L);
        assertEquals(INTEGER.getLong(decode(decoder, INTEGER, "-2147483648"), 0), -2147483648L);
        assertTrue(decode(decoder, INTEGER, "").isNull(0));

        assertTypeMismatch(decoder, INTEGER, "2147483648");
        assertTypeMismatch(decoder, INTEGER, "1.0");
        assertTypeMismatch(decoder, INTEGER, "abc");
    }

    @Test
    public void testSmallintDecoder()
    {
        SmallintDecoder decoder = new SmallintDecoder("field");

        assertEquals(SMALLINT.getLong(decode(decoder, SMALLINT, 123), 0), 123L);
        assertEquals(SMALLINT.getLong(decode(decoder, SMALLINT, "-123"), 0), -123L);
        assertTrue(decode(decoder, SMALLINT, "").isNull(0));

        assertTypeMismatch(decoder, SMALLINT, "40000");
        assertTypeMismatch(decoder, SMALLINT, "abc");
    }

    @Test
    public void testTinyintDecoder()
    {
        TinyintDecoder decoder = new TinyintDecoder("field");

        assertEquals(TINYINT.getLong(decode(decoder, TINYINT, 12), 0), 12L);
        assertEquals(TINYINT.getLong(decode(decoder, TINYINT, "-12"), 0), -12L);
        assertTrue(decode(decoder, TINYINT, "").isNull(0));

        assertTypeMismatch(decoder, TINYINT, "200");
        assertTypeMismatch(decoder, TINYINT, "abc");
    }

    @Test
    public void testDoubleDecoder()
    {
        DoubleDecoder decoder = new DoubleDecoder("field");

        assertEquals(DOUBLE.getDouble(decode(decoder, DOUBLE, 1.5), 0), 1.5);
        assertEquals(DOUBLE.getDouble(decode(decoder, DOUBLE, "1.5"), 0), 1.5);
        assertEquals(DOUBLE.getDouble(decode(decoder, DOUBLE, "-3"), 0), -3.0);
        assertTrue(decode(decoder, DOUBLE, "").isNull(0));

        assertTypeMismatch(decoder, DOUBLE, "abc");
    }

    @Test
    public void testRealDecoder()
    {
        RealDecoder decoder = new RealDecoder("field");

        assertEquals(Float.intBitsToFloat((int) REAL.getLong(decode(decoder, REAL, 1.5f), 0)), 1.5f);
        assertEquals(Float.intBitsToFloat((int) REAL.getLong(decode(decoder, REAL, "1.5"), 0)), 1.5f);
        assertTrue(decode(decoder, REAL, "").isNull(0));

        assertTypeMismatch(decoder, REAL, "abc");
    }

    @Test
    public void testVarcharDecoder()
    {
        VarcharDecoder decoder = new VarcharDecoder("field");

        assertEquals(VARCHAR.getSlice(decode(decoder, VARCHAR, "text"), 0).toStringUtf8(), "text");
        assertEquals(VARCHAR.getSlice(decode(decoder, VARCHAR, 42), 0).toStringUtf8(), "42");
        assertEquals(VARCHAR.getSlice(decode(decoder, VARCHAR, true), 0).toStringUtf8(), "true");
        assertTrue(decode(decoder, VARCHAR, null).isNull(0));
    }

    private static Block decode(Decoder decoder, Type type, Object value)
    {
        BlockBuilder output = type.createBlockBuilder(null, 1);
        decoder.decode(null, () -> value, output);
        return output.build();
    }

    private static void assertTypeMismatch(Decoder decoder, Type type, Object value)
    {
        try {
            decode(decoder, type, value);
            fail(format("expected a type mismatch for value %s", value));
        }
        catch (PrestoException e) {
            assertEquals(e.getErrorCode(), ELASTICSEARCH_TYPE_MISMATCH.toErrorCode());
        }
    }
}
