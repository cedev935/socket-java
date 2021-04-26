package io.socket.client;

import io.socket.emitter.Emitter;
import io.socket.util.Optional;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class SocketTest extends Connection {

    private Socket socket;

    @Test(timeout = TIMEOUT)
    public void shouldHaveAnAccessibleSocketIdEqualToServerSideSocketId() throws InterruptedException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();
        socket = client();
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                values.offer(Optional.ofNullable(socket.id()));
            }
        });
        socket.connect();

        @SuppressWarnings("unchecked")
        Optional<String> id = values.take();
        assertThat(id.isPresent(), is(true));
        assertThat(id.get(), not(socket.io().engine.id())); // distinct ID since Socket.IO v3
        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldHaveAnAccessibleSocketIdEqualToServerSideSocketIdOnCustomNamespace() throws InterruptedException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();
        socket = client("/foo");
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                values.offer(Optional.ofNullable(socket.id()));
            }
        });
        socket.connect();

        @SuppressWarnings("unchecked")
        Optional<String> id = values.take();
        assertThat(id.isPresent(), is(true));
        assertThat(id.get(), is(not(socket.io().engine.id()))); // distinct ID since Socket.IO v3
        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void clearsSocketIdUponDisconnection() throws InterruptedException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();
        socket = client();
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        values.offer(Optional.ofNullable(socket.id()));
                    }
                });

                socket.disconnect();
            }
        });
        socket.connect();
        @SuppressWarnings("unchecked")
        Optional<String> id = values.take();
        assertThat(id.isPresent(), is(false));
    }

    @Test(timeout = TIMEOUT)
    public void doesNotFireConnectErrorIfWeForceDisconnectInOpeningState() throws InterruptedException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();
        IO.Options opts = new IO.Options();
        opts.timeout = 100;
        socket = client(opts);
        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                values.offer(Optional.of(new Error("Unexpected")));
            }
        });
        socket.connect();
        socket.disconnect();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                values.offer(Optional.empty());
            }
        }, 300);

        @SuppressWarnings("unchecked")
        Optional<Error> err = values.take();
        if (err.isPresent()) throw err.get();
    }

    @Test(timeout = TIMEOUT)
    public void shouldChangeSocketIdUponReconnection() throws InterruptedException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();
        socket = client();
        socket.once(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                values.offer(Optional.ofNullable(socket.id()));

                socket.io().on(Manager.EVENT_RECONNECT_ATTEMPT, new Emitter.Listener() {
                    @Override
                    public void call(Object... objects) {
                        values.offer(Optional.ofNullable(socket.id()));
                    }
                });

                socket.once(Socket.EVENT_CONNECT, new Emitter.Listener() {
                    @Override
                    public void call(Object... objects) {
                        values.offer(Optional.ofNullable(socket.id()));
                    }
                });

                socket.io().engine.close();
            }
        });
        socket.connect();
        @SuppressWarnings("unchecked")
        Optional<String> id1 = values.take();

        @SuppressWarnings("unchecked")
        Optional<String> id2 = values.take();
        assertThat(id2.isPresent(), is(false));

        @SuppressWarnings("unchecked")
        Optional<String> id3 = values.take();
        assertThat(id3.get(), is(not(id1.get())));

        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldAcceptAQueryStringOnDefaultNamespace() throws InterruptedException, JSONException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();

        socket = client("/?c=d");
        socket.emit("getHandshake", new Ack() {
            @Override
            public void call(Object... args) {
                JSONObject handshake = (JSONObject)args[0];
                values.offer(Optional.ofNullable(handshake));
            }
        });
        socket.connect();

        @SuppressWarnings("unchecked")
        Optional<JSONObject> handshake = values.take();
        assertThat(handshake.get().getJSONObject("query").getString("c"), is("d"));

        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldAcceptAQueryString() throws InterruptedException, JSONException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();

        socket = client("/abc?b=c&d=e");
        socket.on("handshake", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject handshake = (JSONObject)args[0];
                values.offer(Optional.ofNullable(handshake));
            }
        });
        socket.connect();

        @SuppressWarnings("unchecked")
        Optional<JSONObject> handshake = values.take();
        JSONObject query = handshake.get().getJSONObject("query");
        assertThat(query.getString("b"), is("c"));
        assertThat(query.getString("d"), is("e"));

        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldAcceptAnAuthOption() throws InterruptedException, JSONException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();

        IO.Options opts = new IO.Options();
        opts.auth = singletonMap("token", "abcd");
        socket = client("/abc", opts);
        socket.on("handshake", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject handshake = (JSONObject)args[0];
                values.offer(Optional.ofNullable(handshake));
            }
        });
        socket.connect();

        @SuppressWarnings("unchecked")
        Optional<JSONObject> handshake = values.take();
        JSONObject query = handshake.get().getJSONObject("auth");
        assertThat(query.getString("token"), is("abcd"));

        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldFireAnErrorEventOnMiddlewareFailure() throws InterruptedException, JSONException {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();

        socket = client("/no");
        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                values.offer(Optional.ofNullable(args[0]));
            }
        });
        socket.connect();

        @SuppressWarnings("unchecked")
        JSONObject error = ((Optional<JSONObject>) values.take()).get();
        assertThat(error.getString("message"), is("auth failed"));
        assertThat(error.getJSONObject("data").getString("a"), is("b"));
        assertThat(error.getJSONObject("data").getInt("c"), is(3));

        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldThrowOnReservedEvent() {
        final BlockingQueue<Optional> values = new LinkedBlockingQueue<>();

        socket = client("/no");
        try {
            socket.emit("disconnecting", "goodbye");
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("'disconnecting' is a reserved event name"));
        }

        socket.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void shouldEmitEventsInOrder() throws InterruptedException {
        final BlockingQueue<String> values = new LinkedBlockingQueue<>();

        socket = client();

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                socket.emit("ack", "second", new Ack() {
                    @Override
                    public void call(Object... args) {
                        values.offer((String) args[0]);
                    }
                });
            }
        });

        socket.emit("ack", "first", new Ack() {
            @Override
            public void call(Object... args) {
                values.offer((String) args[0]);
            }
        });

        socket.connect();
        assertThat(values.take(), is("first"));
        assertThat(values.take(), is("second"));
    }
}
