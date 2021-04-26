package io.socket.client;


import io.socket.parser.Parser;
import okhttp3.Call;
import okhttp3.WebSocket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class IO {

    private static final Logger logger = Logger.getLogger(IO.class.getName());

    private static final ConcurrentHashMap<String, Manager> managers = new ConcurrentHashMap<>();

    /**
     * Protocol version.
     */
    public static int protocol = Parser.protocol;

    public static void setDefaultOkHttpWebSocketFactory(WebSocket.Factory factory) {
        Manager.defaultWebSocketFactory = factory;
    }

    public static void setDefaultOkHttpCallFactory(Call.Factory factory) {
        Manager.defaultCallFactory = factory;
    }

    private IO() {}

    public static Socket socket(String uri) throws URISyntaxException {
        return socket(uri, null);
    }

    public static Socket socket(String uri, Options opts) throws URISyntaxException {
        return socket(new URI(uri), opts);
    }

    public static Socket socket(URI uri) {
        return socket(uri, null);
    }

    /**
     * Initializes a {@link Socket} from an existing {@link Manager} for multiplexing.
     *
     * @param uri uri to connect.
     * @param opts options for socket.
     * @return {@link Socket} instance.
     */
    public static Socket socket(URI uri, Options opts) {
        if (opts == null) {
            opts = new Options();
        }

        Url.ParsedURI parsed = Url.parse(uri);
        URI source = parsed.uri;
        String id = parsed.id;

        boolean sameNamespace = managers.containsKey(id)
                && managers.get(id).nsps.containsKey(source.getPath());
        boolean newConnection = opts.forceNew || !opts.multiplex || sameNamespace;
        Manager io;

        String query = source.getQuery();
        if (query != null && (opts.query == null || opts.query.isEmpty())) {
            opts.query = query;
        }

        if (newConnection) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("ignoring socket cache for %s", source));
            }
            io = new Manager(source, opts);
        } else {
            if (!managers.containsKey(id)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(String.format("new io instance for %s", source));
                }
                managers.putIfAbsent(id, new Manager(source, opts));
            }
            io = managers.get(id);
        }

        return io.socket(source.getPath(), opts);
    }


    public static class Options extends Manager.Options {

        public boolean forceNew;

        /**
         * Whether to enable multiplexing. Default is true.
         */
        public boolean multiplex = true;

        /**
         * <p>
         *   Retrieve new builder class that helps creating socket option as builder pattern.
         *   This method returns exactly same result as :
         * </p>
         * <code>
         * SocketOptionBuilder builder = SocketOptionBuilder.builder();
         * </code>
         *
         * @return builder class that helps creating socket option as builder pattern.
         * @see SocketOptionBuilder#builder()
         */
        public static SocketOptionBuilder builder() {
            return SocketOptionBuilder.builder();
        }
    }
}
