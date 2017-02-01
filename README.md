[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing Okhttp Client Instrumentation
OpenTracing instrumentation of Okhttp client.

## Configuration & Usage
```java
    TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer, Arrays.asList(SpanDecorators.STANDARD_TAGS));
    OkHttpClient client = OkHttpClient.Builder()
        .addInterceptor(tracingInterceptor)
        .addNetworkInterceptor(tracingInterceptor)
        .build();
    
    // create traced request 
    client.newCall(new Request.Builder()
            .url(server.url("foo"))
            // optional: client span started in inteceptor will be child of provided SpanContext
            // by default client span is in new trace
            .tag(new TagWrapper(parentSpan.context())) 
            .build())
            .execute();
```

## Development
```shell
./mvnw clean install
```

## Release
Follow instructions in [RELEASE](RELEASE.md)

   [ci-img]: https://travis-ci.org/opentracing-contrib/java-okhttp.svg?branch=master
   [ci]: https://travis-ci.org/opentracing-contrib/java-okhttp
   [maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-okhttp3.svg?maxAge=2592000
   [maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-okhttp3
