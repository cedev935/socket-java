package io.socket.parser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class Helpers {

    private static Parser.Encoder encoder = new IOParser.Encoder();

    public static void test(final Packet obj) {
        encoder.encode(obj, new Parser.Encoder.Callback() {
            @Override
            public void call(Object[] encodedPackets) {
                Parser.Decoder decoder = new IOParser.Decoder();
                decoder.onDecoded(new Parser.Decoder.Callback() {
                    @Override
                    public void call(Packet packet) {
                        assertPacket(packet, obj);
                    }
                });
                decoder.add((String)encodedPackets[0]);
            }
        });
    }

    public static void testDecodeError(final String errorMessage) {
        Parser.Decoder decoder = new IOParser.Decoder();
        try {
            decoder.add(errorMessage);
            fail();
        } catch (DecodingException e) {}
    }

    @SuppressWarnings("unchecked")
    public static void testBin(final Packet obj) {
        final Object originalData = obj.data;
        encoder.encode(obj, new Parser.Encoder.Callback() {
            @Override
            public void call(Object[] encodedPackets) {
                Parser.Decoder decoder = new IOParser.Decoder();
                decoder.onDecoded(new Parser.Decoder.Callback() {
                    @Override
                    public void call(Packet packet) {
                        obj.data = originalData;
                        obj.attachments = -1;
                        assertPacket(packet, obj);
                    }
                });

                for (Object packet : encodedPackets) {
                    if (packet instanceof String) {
                        decoder.add((String)packet);
                    } else if (packet instanceof byte[]) {
                        decoder.add((byte[])packet);
                    }
                }
            }
        });
    }

    public static void assertPacket(Packet expected, Packet actual) {
        assertThat(actual.type, is(expected.type));
        assertThat(actual.id, is(expected.id));
        assertThat(actual.nsp, is(expected.nsp));
        assertThat(actual.attachments, is(expected.attachments));

        if (expected.data instanceof JSONArray) {
            try {
                JSONAssert.assertEquals((JSONArray)expected.data, (JSONArray)actual.data, true);
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        } else if (expected.data instanceof JSONObject) {
            try {
                JSONAssert.assertEquals((JSONObject)expected.data, (JSONObject)actual.data, true);
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        } else {
            assertThat(actual.data, is(expected.data));
        }
    }
}
