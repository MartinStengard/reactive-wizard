package se.fortnox.reactivewizard;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.uber.jaeger.Configuration;
import io.opentracing.Tracer;
import io.opentracing.rxjava.TracingRxJavaUtils;
import se.fortnox.reactivewizard.binding.AutoBindModules;
import se.fortnox.reactivewizard.config.ConfigFactory;

/**
 * Main application entry point. Will scan for all modules on the classpath and run them.
 * If you want to run code at startup, use a custom module.
 *
 * Requires a config file as last parameter.
 */
public class Main {
    public static com.uber.jaeger.Tracer initTracer(String service) {
        Configuration.SamplerConfiguration samplerConfig = new Configuration.SamplerConfiguration("const", 1);
        Configuration.ReporterConfiguration reporterConfig = new Configuration.ReporterConfiguration(true, null, null, null, null);
        Configuration config = new Configuration(service, samplerConfig, reporterConfig);
        return (com.uber.jaeger.Tracer) config.getTracer();
    }
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -jar app.jar config.yml");
            return;
        }
        Tracer tracer = initTracer("helloworld");
        TracingRxJavaUtils.enableTracing(tracer);

        String configFile = args[args.length - 1];
        Module bootstrap = new AbstractModule() {
            @Override
            protected void configure() {
                bind(ConfigFactory.class).toInstance(new ConfigFactory(configFile));
                bind(String[].class).annotatedWith(Names.named("args")).toInstance(args);
                bind(Tracer.class).toInstance(tracer);
            }
        };
        Guice.createInjector(new AutoBindModules(bootstrap));
    }
}