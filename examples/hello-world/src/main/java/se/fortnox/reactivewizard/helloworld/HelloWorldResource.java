package se.fortnox.reactivewizard.helloworld;

import io.opentracing.Tracer;
import io.opentracing.rxjava.TracingObserverSubscriber;
import rx.Observable;
import rx.Observer;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/helloworld")
public class HelloWorldResource {
    private static <T> Observer<T> observer(final Tracer tracer) {
        return new Observer<T>() {
            @Override
            public void onCompleted() {
                tracer.scopeManager().active().span().log("Completed on " + Thread.currentThread().getName());
            }

            @Override
            public void onError(Throwable e) {
                tracer.scopeManager().active().span().log("Errored on " + Thread.currentThread().getName());
            }

            @Override
            public void onNext(T t) {
                // Noop
            }
        };
    }
    private final Tracer tracer;

    @Inject
    public HelloWorldResource(Tracer tracer) {
        this.tracer = tracer;
    }

    @GET
    public Observable<String> greeting() {
        Observable<String> cache = Observable.range(1, 5)

                .flatMap(integer -> {
                    return Observable.just(Thread.currentThread().getName() + ": Hello world! " + integer);
                });


        Observer<String> observer = observer(tracer);
        TracingObserverSubscriber<String> tracingObserverSubscriber =
                new TracingObserverSubscriber<>(observer, "During greeting()", tracer);

        cache.subscribe(tracingObserverSubscriber);

        return cache


                .map(value -> value + "\\n")
                .reduce("", String::concat);
    }
}
