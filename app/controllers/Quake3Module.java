package controllers;

import com.google.inject.AbstractModule;

/**
 * Registered via play.modules.enabled so Quake3StatusScheduler starts its
 * background refresh even though nothing else injects it directly.
 */
public final class Quake3Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(Quake3StatusScheduler.class).asEagerSingleton();
    }
}
