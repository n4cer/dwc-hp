package controllers;

import com.google.inject.AbstractModule;

/**
 * Registered via play.modules.enabled so Quake3StatusScheduler and
 * N4botDemoImporter start their background work even though nothing else
 * injects them directly.
 */
public final class Quake3Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(Quake3StatusScheduler.class).asEagerSingleton();
        bind(N4botDemoImporter.class).asEagerSingleton();
    }
}
