[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing OkHttp Client Instrumentation
OpenTracing instrumentation for OkHttp client.

## Configuration
Preferred way how to instrument OkHttpClient is to use `TracingCallFactory`:
```java
Call.Factory client = new TracingCallFactory(okHttpClient, tracer);
client.newCall(request)...
```
or use OkHttpClient directly. However when doing multiple async requests simultaneously, parent spans created
before invoking the client are not properly inferred.
```java
OkHttpClient client = TracingInterceptor.addTracing(new OkHttpClient.Builder(), tracer)
client.newCall(request)...
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
