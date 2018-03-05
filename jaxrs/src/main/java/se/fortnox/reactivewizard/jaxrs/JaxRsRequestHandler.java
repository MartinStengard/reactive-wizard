package se.fortnox.reactivewizard.jaxrs;

import io.netty.buffer.ByteBuf;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.rxjava.TracingActionSubscriber;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import se.fortnox.reactivewizard.ExceptionHandler;
import se.fortnox.reactivewizard.jaxrs.response.JaxRsResult;
import se.fortnox.reactivewizard.util.DebugUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handles incoming requests. If the request matches a resource an Observable which completes the request is returned.
 */
@Singleton
public class JaxRsRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private Tracer tracer;
    private JaxRsResources   resources;

    private ExceptionHandler exceptionHandler;

    @Inject
    public JaxRsRequestHandler(JaxRsResourcesProvider services,
        JaxRsResourceFactory jaxRsResourceFactory,
        ExceptionHandler exceptionHandler,
        Tracer tracer
    ) {
        this(services.getResources(),
            jaxRsResourceFactory,
            exceptionHandler,
            null,
            tracer
        );
    }

    public JaxRsRequestHandler(Object... services) {
        this(services, new JaxRsResourceFactory(), new ExceptionHandler(), null, null);
    }

    public JaxRsRequestHandler(Object[] services,
        JaxRsResourceFactory jaxRsResourceFactory,
        ExceptionHandler exceptionHandler,
        Boolean classReloading,
        Tracer tracer
    ) {
        this.exceptionHandler = exceptionHandler;
        if (classReloading == null) {
            classReloading = DebugUtil.IS_DEBUG;
        }
        this.tracer = tracer;
        this.resources = new JaxRsResources(services, jaxRsResourceFactory, classReloading);
    }

    /**
     * Handles incoming request if a matching resource is found.
     *
     * @param request  The incoming request
     * @param response The response that will be sent
     * @return an Observable which will complete the request when subsribed, or null if no resource matches the request.
     */
    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        JaxRsRequest     jaxRsRequest = new JaxRsRequest(request);
        JaxRsResource<?> resource     = resources.findResource(jaxRsRequest);

        if (resource == null) {
            return null;
        }

        long requestStartTime = System.currentTimeMillis();

        Observable<Void> resouceInvoicationObservable = resource.call(jaxRsRequest)
            .singleOrDefault(null)
            .flatMap(result -> writeResult(response, result))
            .cache();

        if (tracer != null) {
            TracingActionSubscriber<Void> tracingActionSubscriber = new TracingActionSubscriber<>(
                aVoid -> {
                    logForActiveSpan(tracer, "Great success: " + response.getStatus());
                },
                throwable -> {
                    if (throwable instanceof WebException) {
                        logForActiveSpan(tracer, "Not so great success: " + ((WebException)throwable).getError());
                    } else {
                        logForActiveSpan(tracer, "Not so great success: " + throwable.getCause());
                    }

                },
                "JaxRsRequestHandler#handle",
                tracer
            );
            resouceInvoicationObservable.subscribe(tracingActionSubscriber);
        }

        return resouceInvoicationObservable
            .onErrorResumeNext(e -> exceptionHandler.handleException(request, response, e))
            .doAfterTerminate(() -> resource.log(request, response, requestStartTime));

    }

    private void logForActiveSpan(final Tracer tracer, final String message) {
        Scope active = tracer.scopeManager().active();
        if (active != null) {
            active.span().log("stuff");
        }
    }

    private Observable<Void> writeResult(HttpServerResponse<ByteBuf> response, JaxRsResult<?> result) {
        if (result != null) {
            return result.write(response);
        }
        return Observable.empty();
    }
}
