package se.fortnox.reactivewizard.helloworld;

import io.opentracing.Tracer;
import io.opentracing.rxjava.TracingActionSubscriber;
import rx.Observable;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import static rx.Observable.just;

@Path("/helloworld")
public class HelloWorldResource {
    private final Tracer tracer;

    @Inject
    public HelloWorldResource(Tracer tracer) {
        this.tracer = tracer;
    }

    @GET
    public Observable<String> greeting() {
        TracingActionSubscriber<String> tracingAction = new TracingActionSubscriber<>("logged operation for greeting()", tracer);
        Observable<String>              result        = just("Hello world!").cache();
        result.subscribe(tracingAction);
        return result;
    }
}
