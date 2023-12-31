# Frequently asked questions

<!-- MACRO{toc} -->

## How to deal with cookies

In order to store the cookies sent by the server and include them in all subsequent requests, you need to create an OkHttpClient with a custom [cookie jar](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-cookie-jar/).

You can either implement your own cookie jar:

```java
public class MyApp {

    public static void main(String[] argz) throws Exception {
        IO.Options options = new IO.Options();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .cookieJar(new MyCookieJar())
                .build();

        options.callFactory = okHttpClient;
        options.webSocketFactory = okHttpClient;

        Socket socket = IO.socket(URI.create("https://example.com"), options);

        socket.connect();
    }

    private static class MyCookieJar implements CookieJar {
        private Set<WrappedCookie> cache = new HashSet<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            for (Cookie cookie : cookies) {
                this.cache.add(new WrappedCookie(cookie));
            }
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = new ArrayList<>();
            Iterator<WrappedCookie> iterator = this.cache.iterator();
            while (iterator.hasNext()) {
                Cookie cookie = iterator.next().cookie;
                if (isCookieExpired(cookie)) {
                    iterator.remove();
                } else if (cookie.matches(url)) {
                    cookies.add(cookie);
                }
            }
            return cookies;
        }

        private static boolean isCookieExpired(Cookie cookie) {
            return cookie.expiresAt() < System.currentTimeMillis();
        }
    }

    private static class WrappedCookie {
        private final Cookie cookie;

        public WrappedCookie(Cookie cookie) {
            this.cookie = cookie;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WrappedCookie)) return false;
            WrappedCookie that = (WrappedCookie) o;
            return that.cookie.name().equals(this.cookie.name())
                    && that.cookie.domain().equals(this.cookie.domain())
                    && that.cookie.path().equals(this.cookie.path())
                    && that.cookie.secure() == this.cookie.secure()
                    && that.cookie.hostOnly() == this.cookie.hostOnly();
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = 31 * hash + cookie.name().hashCode();
            hash = 31 * hash + cookie.domain().hashCode();
            hash = 31 * hash + cookie.path().hashCode();
            hash = 31 * hash + (cookie.secure() ? 0 : 1);
            hash = 31 * hash + (cookie.hostOnly() ? 0 : 1);
            return hash;
        }
    }
}
```

Or use a package like [PersistentCookieJar](https://github.com/franmontiel/PersistentCookieJar):

```java
public class MyApp {

    public static void main(String[] argz) throws Exception {
        IO.Options options = new IO.Options();

        ClearableCookieJar cookieJar = new PersistentCookieJar(new SetCookieCache(), new SharedPrefsCookiePersistor(context));

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();

        options.callFactory = okHttpClient;
        options.webSocketFactory = okHttpClient;

        Socket socket = IO.socket(URI.create("https://example.com"), options);

        socket.connect();
    }
}
```

## How to use with AWS Load Balancing

When scaling to multiple Socket.IO servers, you must ensure that all the HTTP requests of a given session reach the same server (explanation [here](https://socket.io/docs/v4/using-multiple-nodes/#why-is-sticky-session-required)).

Sticky sessions can be enabled on AWS Application Load Balancers, which works by sending a cookie (`AWSALB`) to the client.

Please see [above](#how-to-deal-with-cookies) for how to deal with cookies.

Reference: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/sticky-sessions.html

## How to force TLS v1.2 and above

This library relies on the OkHttp library to create HTTP requests and WebSocket connections.

Reference: https://square.github.io/okhttp/

We currently depend on version `3.12.12`, which is the last version that supports Java 7+ and Android 2.3+ (API level 9+). With this version, the OkHttpClient allows `TLSv1` and `TLSv1.1` by default ([MODERN_TLS](https://square.github.io/okhttp/security/tls_configuration_history/#modern_tls-versions_1) configuration).

You can overwrite it by providing your own OkHttp client:

```java
OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .connectionSpecs(Arrays.asList(
            ConnectionSpec.RESTRICTED_TLS
        ))
        .readTimeout(1, TimeUnit.MINUTES) // important for HTTP long-polling
        .build();

IO.Options options = new IO.Options();
options.callFactory = okHttpClient;
options.webSocketFactory = okHttpClient;

Socket socket = IO.socket(URI.create("https://example.com"), options);
```

Note: we will upgrade to OkHttp 4 in the next major version.

## How to create a lot of clients

By default, you won't be able to create more than 5 Socket.IO clients (any additional client will be disconnected with "transport error" or "ping timeout" reason). That is due to the default OkHttp [dispatcher](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-dispatcher/), whose `maxRequestsPerHost` is set to 5 by default.

You can overwrite it by providing your own OkHttp client:

```java
int MAX_CLIENTS = 100;

Dispatcher dispatcher = new Dispatcher();
dispatcher.setMaxRequests(MAX_CLIENTS * 2);
dispatcher.setMaxRequestsPerHost(MAX_CLIENTS * 2);

OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .readTimeout(1, TimeUnit.MINUTES) // important for HTTP long-polling
        .build();

IO.Options options = new IO.Options();
options.callFactory = okHttpClient;
options.webSocketFactory = okHttpClient;

for (int i = 0; i < MAX_CLIENTS; i++) {
    Socket socket = IO.socket(URI.create("https://example.com"), options);
}
```

Note: we use `MAX_CLIENTS * 2` because a client in HTTP long-polling mode will have one long-running GET request for receiving data from the server, and will create a POST request for sending data to the server.

## How to properly close a client

Calling `socket.disconnect()` may not be sufficient, because the underlying OkHttp client [creates](https://github.com/square/okhttp/blob/06d38cb795d82d086f13c595a62ce0cbe60904ac/okhttp/src/main/java/okhttp3/Dispatcher.java#L65-L66) a ThreadPoolExecutor that will prevent your Java program from quitting for 60 seconds.

As a workaround, you can manually shut down this ThreadPoolExecutor:

```java
Dispatcher dispatcher = new Dispatcher();

OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .readTimeout(1, TimeUnit.MINUTES) // important for HTTP long-polling
        .build();

IO.Options options = new IO.Options();
options.callFactory = okHttpClient;
options.webSocketFactory = okHttpClient;

Socket socket = IO.socket(URI.create("https://example.com"), options);

socket.connect();

// then later

socket.disconnect();
dispatcher.executorService().shutdown();
```

## How to map the event arguments to POJO

This library uses the [JSONTokener](https://developer.android.com/reference/org/json/JSONTokener) class from the `org.json` package in order to parse the packets that are sent by the server, which means you will receive [JSONObjects](https://developer.android.com/reference/org/json/JSONObject) in your listeners.

Here's how you can convert these JSONObjects to Plain Old Java Objects (POJO):

- [with Jackson](#With_Jackson)
- [with Gson](#With_Gson)

### With Jackson

`pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>2.13.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.13.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-json-org</artifactId>
            <version>2.13.3</version>
        </dependency>
        ...
    </dependencies>
    ...
</project>
```

Maven repository:

- https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
- https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
- https://mvnrepository.com/artifact/com.fasterxml.jackson.datatype/jackson-datatype-json-org

`src/main/java/MyApp.java`

```java
public class MyApp {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JsonOrgModule());
    
    public static void main(String[] argz) throws Exception {
        Socket socket = IO.socket(URI.create("https://example.com"));

        socket.on("my-event", (args) -> {
            MyObject object = MAPPER.convertValue(args[0], MyObject.class);

            // ...
        });

        socket.connect();
    }

    public static class MyObject {
        public int id;
        public String label;
    }
}
```

### With Gson

`pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <dependencies>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.8.9</version>
        </dependency>
        ...
    </dependencies>
    ...
</project>
```

Maven repository: 

- https://mvnrepository.com/artifact/com.google.code.gson/gson

`src/main/java/MyApp.java`

You can either call `toString()` on the `JSONObject`:

```java
public class MyApp {
    private static final Gson GSON = new Gson();
    
    public static void main(String[] argz) throws Exception {
        Socket socket = IO.socket(URI.create("https://example.com"));

        socket.on("my-event", (args) -> {
            MyObject object = GSON.fromJson(args[0].toString(), MyObject.class);

            // ...
        });

        socket.connect();
    }

    public static class MyObject {
        public int id;
        public String label;
    }
}
```

Or manually convert the `JSONObject` to a `JsonObject` (for performance purposes):

```java
public class MyApp {
    private static final Gson GSON = new Gson();
    
    public static void main(String[] argz) throws Exception {
        Socket socket = IO.socket(URI.create("https://example.com"));

        socket.on("my-event", (args) -> {
            MyObject object = GSON.fromJson(map(args[0]), MyObject.class);

            // ...
        });

        socket.connect();
    }

    public static class MyObject {
        public int id;
        public String label;
    }

    public static JsonObject map(JSONObject source) throws JSONException {
        JsonObject output = new JsonObject();

        Iterator<String> iterator = source.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = source.get(key);

            if (value instanceof JSONObject) {
                output.add(key, map((JSONObject) value));
            } else if (value instanceof JSONArray) {
                output.add(key, map((JSONArray) value));
            } else if (value instanceof Number) {
                output.addProperty(key, (Number) value);
            } else if (value instanceof String) {
                output.addProperty(key, (String) value);
            } else if (value instanceof Boolean) {
                output.addProperty(key, (Boolean) value);
            } else if (value instanceof Character) {
                output.addProperty(key, (Character) value);
            }
        }

        return output;
    }

    public static JsonArray map(JSONArray source) throws JSONException {
        JsonArray output = new JsonArray();

        for (int i = 0; i < source.length(); i++) {
            Object value = source.get(i);

            if (value instanceof JSONObject) {
                output.add(map((JSONObject) value));
            } else if (value instanceof JSONArray) {
                output.add(map((JSONArray) value));
            } else if (value instanceof Number) {
                output.add((Number) value);
            } else if (value instanceof String) {
                output.add((String) value);
            } else if (value instanceof Boolean) {
                output.add((Boolean) value);
            } else if (value instanceof Character) {
                output.add((Character) value);
            }
        }

        return output;
    }
}
```